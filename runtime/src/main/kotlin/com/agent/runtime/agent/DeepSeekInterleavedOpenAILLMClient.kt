@file:Suppress("UnstableApiUsage")

package com.agent.runtime.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import ai.koog.prompt.streaming.requireEndFrame
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 复刻 Kilo 对 DeepSeek interleaved reasoning 的处理：
 * 把 reasoning_content 与 tool_calls 合并回同一条 assistant message，并在 streaming 时显式产出 reasoning 帧。
 */
internal class DeepSeekInterleavedOpenAILLMClient @JvmOverloads constructor(
    apiKey: String,
    private val clientSettings: OpenAIClientSettings = OpenAIClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System,
) : OpenAILLMClient(
    apiKey = apiKey,
    settings = clientSettings,
    baseClient = baseClient,
    clock = clock,
) {

    /**
     * 使用 interleaved-aware 的消息转换，确保 DeepSeek thinking mode 的多轮 tool call 能正确回放 reasoning_content。
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.debug { "Executing DeepSeek interleaved prompt: $prompt with tools: $tools and model: $model" }
        model.requireCapability(LLMCapability.Completion)
        if (tools.isNotEmpty()) {
            model.requireCapability(LLMCapability.Tools)
        }

        val request = serializeProviderChatRequest(
            messages = convertPromptToOpenAIMessagesPreservingInterleavedReasoning(prompt) { message ->
                message.toMessageContent(model)
            },
            model = model,
            tools = tools.takeIf { it.isNotEmpty() }?.map { descriptor -> descriptor.toOpenAIChatTool() },
            toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice(),
            params = prompt.params,
            stream = false,
        )

        val response = try {
            httpClient.post(
                path = clientSettings.chatCompletionsPath,
                request = request,
                requestBodyType = String::class,
                responseType = String::class,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = error.message,
                cause = error,
            )
        }

        return processProviderChatResponse(decodeResponse(response)).first()
    }

    /**
     * 使用自定义 chunk 解析，把 DeepSeek 的 reasoning_content 显式翻译为 reasoning delta。
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        logger.debug { "Executing DeepSeek interleaved streaming prompt: $prompt with model: $model" }
        model.requireCapability(LLMCapability.Completion)
        if (tools.isNotEmpty()) {
            model.requireCapability(LLMCapability.Tools)
        }

        val request = serializeProviderChatRequest(
            messages = convertPromptToOpenAIMessagesPreservingInterleavedReasoning(prompt) { message ->
                message.toMessageContent(model)
            },
            model = model,
            tools = tools.takeIf { it.isNotEmpty() }?.map { descriptor -> descriptor.toOpenAIChatTool() },
            toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice(),
            params = prompt.params,
            stream = true,
        )

        return try {
            val response = httpClient.sse(
                path = clientSettings.chatCompletionsPath,
                request = request,
                requestBodyType = String::class,
                dataFilter = { it != "[DONE]" },
                decodeStreamingResponse = { payload ->
                    json.decodeFromString(DeepSeekOpenAIChatCompletionStreamResponse.serializer(), payload)
                },
                processStreamingChunk = { chunk -> chunk },
            )

            buildStreamFrameFlow {
                var finishReason: String? = null
                var metaInfo: ResponseMetaInfo? = null

                response.collect { chunk ->
                    chunk.choices.firstOrNull()?.let { choice ->
                        choice.delta.reasoningContent?.let { reasoning ->
                            emitReasoningDelta(text = reasoning, index = choice.index)
                        }
                        choice.delta.content?.let { content ->
                            emitTextDelta(content, choice.index)
                        }
                        choice.delta.toolCalls?.forEach { toolCall ->
                            emitToolCallDelta(
                                id = toolCall.id,
                                name = toolCall.function?.name,
                                args = toolCall.function?.arguments,
                                index = toolCall.index,
                            )
                        }
                        choice.finishReason?.let { finishReason = it }
                    }
                    chunk.usage?.let { usage -> metaInfo = createMetaInfo(usage) }
                }

                emitEnd(finishReason, metaInfo)
            }.requireEndFrame()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = error.message,
                cause = error,
            )
        }
    }
}

/**
 * 复刻 Kilo 的 interleaved transform：对带 tool_calls 的 assistant turn 总是保留 reasoning_content，空串也不丢。
 */
@OptIn(ExperimentalUuidApi::class)
internal fun convertPromptToOpenAIMessagesPreservingInterleavedReasoning(
    prompt: Prompt,
    userContent: (Message.User) -> Content,
): List<OpenAIMessage> {
    val messages = mutableListOf<OpenAIMessage>()
    val pendingCalls = mutableListOf<OpenAIToolCall>()
    val pendingReasoning = StringBuilder()
    var sawPendingReasoning = false

    fun flushPendingAssistant() {
        if (pendingCalls.isNotEmpty()) {
            messages += OpenAIMessage.Assistant(
                reasoningContent = if (sawPendingReasoning) pendingReasoning.toString() else "",
                toolCalls = pendingCalls.toList(),
            )
            pendingCalls.clear()
            pendingReasoning.clear()
            sawPendingReasoning = false
            return
        }

        if (sawPendingReasoning) {
            val reasoningContent = pendingReasoning.toString()
            messages += OpenAIMessage.Assistant(
                content = null,
                reasoningContent = reasoningContent,
            )
            pendingReasoning.clear()
            sawPendingReasoning = false
        }
    }

    prompt.messages.forEach { message ->
        when (message) {
            is Message.System -> {
                flushPendingAssistant()
                messages += OpenAIMessage.System(content = Content.Text(message.content))
            }

            is Message.User -> {
                flushPendingAssistant()
                messages += OpenAIMessage.User(content = userContent(message))
            }

            is Message.Assistant -> {
                if (pendingCalls.isNotEmpty()) {
                    flushPendingAssistant()
                }
                messages += OpenAIMessage.Assistant(
                    content = Content.Text(message.content),
                    reasoningContent = if (sawPendingReasoning) pendingReasoning.toString() else "",
                )
                pendingReasoning.clear()
                sawPendingReasoning = false
            }

            is Message.Reasoning -> {
                if (pendingCalls.isNotEmpty()) {
                    flushPendingAssistant()
                }
                pendingReasoning.append(message.content)
                sawPendingReasoning = true
            }

            is Message.Tool.Result -> {
                flushPendingAssistant()
                messages += OpenAIMessage.Tool(
                    content = Content.Text(message.content),
                    toolCallId = message.id ?: Uuid.random().toString(),
                )
            }

            is Message.Tool.Call -> {
                pendingCalls += OpenAIToolCall(
                    message.id ?: Uuid.random().toString(),
                    function = OpenAIFunction(message.tool, message.content),
                )
            }
        }
    }

    flushPendingAssistant()
    return messages
}

/**
 * 仅覆盖 DeepSeek 在 streaming chunk 里额外返回的 reasoning_content 字段。
 */
@Serializable
private data class DeepSeekOpenAIChatCompletionStreamResponse(
    val choices: List<DeepSeekOpenAIStreamChoice> = emptyList(),
    val usage: OpenAIUsage? = null,
)

/**
 * 表示单个 DeepSeek streaming choice。
 */
@Serializable
private data class DeepSeekOpenAIStreamChoice(
    val delta: DeepSeekOpenAIStreamDelta = DeepSeekOpenAIStreamDelta(),
    val finishReason: String? = null,
    val index: Int = 0,
)

/**
 * 在 OpenAI delta 基础上补充 DeepSeek 的 reasoning_content。
 */
@Serializable
private data class DeepSeekOpenAIStreamDelta(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenAIStreamToolCall>? = null,
)
