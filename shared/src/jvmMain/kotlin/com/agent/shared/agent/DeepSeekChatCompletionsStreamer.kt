@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.agent.shared.config.ConfigProfile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * DeepSeek chat-completions 的专用流式适配器。
 *
 * Koog 1.0 的 chat-completions 流模型不会解析 DeepSeek 的 `reasoning_content`，
 * 这里直接读取原始 SSE chunk，并将思考增量映射回 Koog 的 `StreamFrame`。
 */
internal class DeepSeekChatCompletionsStreamer(
    private val chunkRunner: (DeepSeekChatCompletionRequest, ConfigProfile) -> Flow<DeepSeekChatCompletionChunk> =
        { request, config ->
            openDeepSeekSseChunks(
                request = request,
                settings = buildOpenAIClientSettings(config),
                apiKey = config.apiKey,
            )
        },
) {
    /**
     * 执行一次 DeepSeek chat-completions 流式请求。
     */
    fun stream(request: AgentRunRequest): Flow<StreamFrame> = flow {
        emitAllFrames(
            deepSeekRequest = buildRequest(request),
            config = request.profile,
        )
    }

    /**
     * 基于当前 Koog prompt 与可用工具执行一次 DeepSeek chat-completions 流式请求。
     */
    fun stream(
        prompt: Prompt,
        config: ConfigProfile,
        reasoningEffort: ReasoningEffort?,
        tools: List<ToolDescriptor> = emptyList(),
    ): Flow<StreamFrame> = flow {
        emitAllFrames(
            deepSeekRequest = buildRequest(
                prompt = prompt,
                config = config,
                reasoningEffort = reasoningEffort,
                tools = tools,
            ),
            config = config,
        )
    }

    /**
     * 真正执行 DeepSeek SSE 读取并映射成 Koog frame。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamFrame>.emitAllFrames(
        deepSeekRequest: DeepSeekChatCompletionRequest,
        config: ConfigProfile,
    ) {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null
        val reasoningBuffers = linkedMapOf<Int, StringBuilder>()
        log.info { buildDeepSeekRequestDiagnostic(deepSeekRequest) }

        chunkRunner(deepSeekRequest, config).collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
                    reasoningBuffers.getOrPut(choice.index) { StringBuilder() }.append(reasoning)
                    emit(StreamFrame.ReasoningDelta(text = reasoning, index = choice.index))
                }
                choice.delta.content?.takeIf { it.isNotBlank() }?.let { content ->
                    emit(StreamFrame.TextDelta(text = content, index = choice.index))
                }
                choice.delta.toolCalls.orEmpty().forEachIndexed { toolIndex, toolCall ->
                    emit(
                        StreamFrame.ToolCallDelta(
                            id = toolCall.id,
                            index = toolCall.index ?: toolIndex,
                            name = toolCall.function?.name,
                            content = toolCall.function?.arguments,
                        ),
                    )
                }
                choice.finishReason?.let { finishReason = it }
            }

            chunk.usage?.let { usage ->
                metaInfo = ResponseMetaInfo.create(
                    clock = KoogClock.System,
                    totalTokensCount = usage.totalTokens,
                    inputTokensCount = usage.promptTokens,
                    outputTokensCount = usage.completionTokens,
                    modelId = chunk.model,
                )
            }
        }

        reasoningBuffers.forEach { (index, text) ->
            if (text.isNotEmpty()) {
                emit(
                    StreamFrame.ReasoningComplete(
                        id = null,
                        content = listOf(text.toString()),
                        index = index,
                    ),
                )
            }
        }
        emit(StreamFrame.End(finishReason = finishReason, metaInfo = metaInfo ?: ResponseMetaInfo.Empty))
    }

    /**
     * 兼容旧调用方式，默认使用请求对象中的默认推理强度。
     */
    fun stream(prompt: String, config: ConfigProfile): Flow<StreamFrame> = stream(
        AgentRunRequest(
            prompt = prompt,
            profile = config,
        ),
    )

    /**
     * 构造 DeepSeek 所需的最小 chat-completions 请求。
     */
    internal fun buildRequest(request: AgentRunRequest): DeepSeekChatCompletionRequest =
        DeepSeekChatCompletionRequest(
            model = request.profile.model,
            messages = request.history.map(::toDeepSeekHistoryMessage) +
                DeepSeekChatMessage(role = "user", content = request.prompt),
            stream = true,
            streamOptions = DeepSeekStreamOptions(includeUsage = true),
            thinking = DeepSeekThinking(type = "enabled"),
            reasoningEffort = request.reasoningEffort?.wireValue,
        )

    /**
     * 将 Koog prompt + 工具描述映射为 DeepSeek chat-completions 请求。
     */
    internal fun buildRequest(
        prompt: Prompt,
        config: ConfigProfile,
        reasoningEffort: ReasoningEffort?,
        tools: List<ToolDescriptor> = emptyList(),
    ): DeepSeekChatCompletionRequest =
        DeepSeekChatCompletionRequest(
            model = config.model,
            messages = prompt.messages.flatMap(::toDeepSeekPromptMessages),
            tools = tools.takeIf { it.isNotEmpty() }?.map(::toDeepSeekToolDefinition),
            stream = true,
            streamOptions = DeepSeekStreamOptions(includeUsage = true),
            thinking = DeepSeekThinking(type = "enabled"),
            reasoningEffort = reasoningEffort?.wireValue,
        )

    private companion object {
        private val log = KotlinLogging.logger { }
    }
}

/**
 * 将结构化历史消息映射为 DeepSeek/OpenAI 兼容消息。
 */
private fun toDeepSeekHistoryMessage(message: AgentConversationHistoryMessage): DeepSeekChatMessage =
    when (message) {
        is AgentConversationHistoryMessage.User -> DeepSeekChatMessage(
            role = "user",
            content = message.content,
        )

        is AgentConversationHistoryMessage.Assistant -> DeepSeekChatMessage(
            role = "assistant",
            content = serializeAssistantParts(message.parts),
        )
    }

/**
 * 将助手结构化片段压平成当前 DeepSeek 兼容消息文本。
 */
private fun serializeAssistantParts(parts: List<AgentConversationHistoryPart>): String =
    parts.joinToString(separator = "\n\n") { part ->
        when (part) {
            is AgentConversationHistoryPart.Text -> part.text

            is AgentConversationHistoryPart.Reasoning ->
                "[reasoning]\n${part.rawText ?: part.summary.orEmpty()}\n[/reasoning]"

            is AgentConversationHistoryPart.ToolCall ->
                "[tool_call:${part.name}]\n${part.argumentsPreview.orEmpty()}\n[/tool_call]"

            is AgentConversationHistoryPart.ToolResult ->
                "[tool_result:${part.name}]\n${part.resultPreview.orEmpty()}\n[/tool_result]"
        }
    }.trim()

/**
 * 构造不包含 prompt、messages、apiKey 的 DeepSeek 请求诊断摘要。
 */
internal fun buildDeepSeekRequestDiagnostic(request: DeepSeekChatCompletionRequest): String =
    "DeepSeek request: model=${request.model} " +
        "thinking=${request.thinking.type} " +
        "reasoning_effort=${request.reasoningEffort ?: "null"} " +
        "tools=${request.tools?.size ?: 0} " +
        "stream=${request.stream}"

/**
 * 打开 SSE 连接并按 chunk 产出 DeepSeek 原始流数据。
 */
private fun openDeepSeekSseChunks(
    request: DeepSeekChatCompletionRequest,
    settings: OpenAIClientSettings,
    apiKey: String,
): Flow<DeepSeekChatCompletionChunk> = flow {
    val httpClient = createDeepSeekHttpClient(settings = settings, apiKey = apiKey)
    try {
        httpClient.sse(
            path = settings.chatCompletionsPath,
            requestBody = request,
            requestBodyType = DeepSeekChatCompletionRequest::class,
            dataFilter = { it != "[DONE]" },
            decodeStreamingResponse = DeepSeekChatCompletionChunk.Companion::decode,
            processStreamingChunk = { it },
        ).collect(::emit)
    } finally {
        httpClient.close()
    }
}

/**
 * 创建带鉴权和超时配置的 HTTP client。
 */
private fun createDeepSeekHttpClient(settings: OpenAIClientSettings, apiKey: String): KoogHttpClient =
    AbstractOpenAILLMClient.createConfiguredHttpClient(
        apiKey = apiKey,
        settings = settings,
        httpClientFactory = DesktopKoogHttpClientFactoryProvider.factory,
        clientName = "DeepSeekChatCompletionsStreamer",
    )

/**
 * DeepSeek chat-completions 的最小请求体。
 */
@Serializable
internal data class DeepSeekChatCompletionRequest(
    val model: String,
    val messages: List<DeepSeekChatMessage>,
    val tools: List<DeepSeekToolDefinition>? = null,
    val stream: Boolean,
    @SerialName("stream_options")
    val streamOptions: DeepSeekStreamOptions,
    val thinking: DeepSeekThinking,
    @SerialName("reasoning_effort")
    val reasoningEffort: String?,
)

/**
 * DeepSeek/OpenAI 兼容消息体。
 */
@Serializable
internal data class DeepSeekChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
)

/**
 * DeepSeek/OpenAI 兼容工具定义。
 */
@Serializable
internal data class DeepSeekToolDefinition(
    val type: String = "function",
    val function: DeepSeekToolFunctionDefinition,
)

/**
 * DeepSeek/OpenAI 兼容函数定义。
 */
@Serializable
internal data class DeepSeekToolFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * DeepSeek/OpenAI 兼容工具调用。
 */
@Serializable
internal data class DeepSeekToolCall(
    val id: String,
    val function: DeepSeekToolFunctionCall,
    val type: String = "function",
)

/**
 * DeepSeek/OpenAI 兼容函数调用体。
 */
@Serializable
internal data class DeepSeekToolFunctionCall(
    val name: String,
    val arguments: String = "",
)

/**
 * DeepSeek thinking 开关配置。
 */
@Serializable
internal data class DeepSeekThinking(
    val type: String,
)

/**
 * DeepSeek stream 选项。
 */
@Serializable
internal data class DeepSeekStreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean,
)

/**
 * DeepSeek chat-completions 的 SSE chunk。
 */
@Serializable
internal data class DeepSeekChatCompletionChunk(
    val id: String,
    val created: Long,
    val model: String,
    val choices: List<DeepSeekChatChoice> = emptyList(),
    val usage: DeepSeekUsage? = null,
) {
    /**
     * 统一入口，负责把原始 JSON chunk 反序列化成数据对象。
     */
    companion object {
        private val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /**
         * 反序列化单个 SSE data payload。
         */
        fun decode(raw: String): DeepSeekChatCompletionChunk = json.decodeFromString(serializer(), raw)
    }
}

/**
 * DeepSeek 流式 choice。
 */
@Serializable
internal data class DeepSeekChatChoice(
    val index: Int,
    val delta: DeepSeekChatDelta,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

/**
 * DeepSeek 流式 delta，额外包含 reasoning_content。
 */
@Serializable
internal data class DeepSeekChatDelta(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekStreamToolCall>? = null,
)

/**
 * DeepSeek chat-completions 流式工具调用 delta。
 */
@Serializable
internal data class DeepSeekStreamToolCall(
    val index: Int? = null,
    val id: String? = null,
    val function: DeepSeekStreamFunction? = null,
    val type: String? = "function",
)

/**
 * DeepSeek chat-completions 流式函数调用 delta。
 */
@Serializable
internal data class DeepSeekStreamFunction(
    val name: String? = null,
    val arguments: String? = null,
)

/**
 * DeepSeek 流式 usage 统计。
 */
@Serializable
internal data class DeepSeekUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
)

/**
 * 将 Koog prompt 中的消息展平为 DeepSeek/OpenAI 兼容消息。
 */
private fun toDeepSeekPromptMessages(message: Message): List<DeepSeekChatMessage> = when (message) {
    is Message.System -> listOf(
        DeepSeekChatMessage(
            role = "system",
            content = message.textContent().takeIf { it.isNotBlank() },
        ),
    )

    is Message.User -> message.parts.toDeepSeekUserMessages()
    is Message.Assistant -> listOfNotNull(message.toDeepSeekAssistantMessage())
}

/**
 * 将用户消息拆成文本消息与 tool result 消息，保留 part 顺序。
 */
private fun List<MessagePart.RequestPart>.toDeepSeekUserMessages(): List<DeepSeekChatMessage> {
    val messages = mutableListOf<DeepSeekChatMessage>()
    val textBuffer = StringBuilder()

    fun flushTextBuffer() {
        if (textBuffer.isNotEmpty()) {
            messages += DeepSeekChatMessage(role = "user", content = textBuffer.toString())
            textBuffer.setLength(0)
        }
    }

    forEach { part ->
        when (part) {
            is MessagePart.Text -> {
                if (textBuffer.isNotEmpty()) {
                    textBuffer.append('\n')
                }
                textBuffer.append(part.text)
            }

            is MessagePart.Tool.Result -> {
                flushTextBuffer()
                messages += DeepSeekChatMessage(
                    role = "tool",
                    content = part.output,
                    toolCallId = part.id ?: part.tool,
                )
            }

            else -> Unit
        }
    }

    flushTextBuffer()
    return messages
}

/**
 * 将助手消息映射为单条 assistant role 消息，保留 reasoning 与 tool call。
 */
private fun Message.Assistant.toDeepSeekAssistantMessage(): DeepSeekChatMessage? {
    val textContent = parts
        .filterIsInstance<MessagePart.Text>()
        .joinToString(separator = "\n") { it.text }
        .takeIf { it.isNotBlank() }
    val reasoningContent = parts
        .filterIsInstance<MessagePart.Reasoning>()
        .joinToString(separator = "\n") { it.content.joinToString(separator = "") }
        .takeIf { it.isNotBlank() }
    val toolCalls = parts
        .filterIsInstance<MessagePart.Tool.Call>()
        .map { part ->
            DeepSeekToolCall(
                id = part.id ?: part.tool,
                function = DeepSeekToolFunctionCall(
                    name = part.tool,
                    arguments = part.args,
                ),
            )
        }
        .takeIf { it.isNotEmpty() }
    if (textContent == null && reasoningContent == null && toolCalls.isNullOrEmpty()) {
        return null
    }
    return DeepSeekChatMessage(
        role = "assistant",
        content = textContent,
        reasoningContent = reasoningContent,
        toolCalls = toolCalls,
    )
}

/**
 * 将 ToolDescriptor 转成 DeepSeek/OpenAI 兼容工具 schema。
 */
private fun toDeepSeekToolDefinition(tool: ToolDescriptor): DeepSeekToolDefinition = DeepSeekToolDefinition(
    function = DeepSeekToolFunctionDefinition(
        name = tool.name,
        description = tool.description,
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                (tool.requiredParameters + tool.optionalParameters).forEach { parameter ->
                    put(parameter.name, parameter.toDeepSeekJsonSchema())
                }
            }
            putJsonArray("required") {
                tool.requiredParameters.forEach { parameter -> add(parameter.name) }
            }
        },
    ),
)

/**
 * 将工具参数描述映射为兼容 OpenAI/DeepSeek 的 JSON schema。
 */
private fun ToolParameterDescriptor.toDeepSeekJsonSchema(): JsonObject = buildJsonObject {
    put("description", description)
    fillDeepSeekJsonSchema(type)
}

/**
 * 递归填充 JSON schema 类型定义。
 */
private fun JsonObjectBuilder.fillDeepSeekJsonSchema(type: ToolParameterType) {
    when (type) {
        ToolParameterType.Boolean -> put("type", "boolean")
        ToolParameterType.Float -> put("type", "number")
        ToolParameterType.Integer -> put("type", "integer")
        ToolParameterType.String -> put("type", "string")
        ToolParameterType.Null -> put("type", "null")

        is ToolParameterType.Enum -> {
            put("type", "string")
            putJsonArray("enum") {
                type.entries.forEach(::add)
            }
        }

        is ToolParameterType.List -> {
            put("type", "array")
            putJsonObject("items") {
                fillDeepSeekJsonSchema(type.itemsType)
            }
        }

        is ToolParameterType.Object -> {
            put("type", "object")
            type.additionalProperties?.let { put("additionalProperties", it) }
            putJsonObject("properties") {
                type.properties.forEach { property ->
                    putJsonObject(property.name) {
                        fillDeepSeekJsonSchema(property.type)
                        put("description", property.description)
                    }
                }
            }
            putJsonArray("required") {
                type.requiredProperties.forEach(::add)
            }
        }

        is ToolParameterType.AnyOf -> {
            putJsonArray("anyOf") {
                type.types.forEach { parameter ->
                    add(parameter.toDeepSeekJsonSchema())
                }
            }
        }
    }
}
