@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent

import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.agent.shared.config.ConfigProfile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null
        val deepSeekRequest = buildRequest(request)
        log.info { buildDeepSeekRequestDiagnostic(deepSeekRequest) }

        chunkRunner(deepSeekRequest, request.profile).collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
                    emit(StreamFrame.ReasoningDelta(text = reasoning, index = choice.index))
                }
                choice.delta.content?.takeIf { it.isNotBlank() }?.let { content ->
                    emit(StreamFrame.TextDelta(text = content, index = choice.index))
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
        httpClientFactory = HttpClientFactoryResolver.resolve(),
        clientName = "DeepSeekChatCompletionsStreamer",
    )

/**
 * DeepSeek chat-completions 的最小请求体。
 */
@Serializable
internal data class DeepSeekChatCompletionRequest(
    val model: String,
    val messages: List<DeepSeekChatMessage>,
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
    val content: String,
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
