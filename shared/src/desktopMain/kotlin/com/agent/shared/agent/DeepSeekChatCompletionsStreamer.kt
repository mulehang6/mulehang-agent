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
    fun stream(prompt: String, config: ConfigProfile): Flow<StreamFrame> = flow {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null

        chunkRunner(buildRequest(prompt, config), config).collect { chunk ->
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
     * 构造 DeepSeek 所需的最小 chat-completions 请求。
     */
    internal fun buildRequest(prompt: String, config: ConfigProfile): DeepSeekChatCompletionRequest =
        DeepSeekChatCompletionRequest(
            model = config.model,
            messages = listOf(DeepSeekChatMessage(role = "user", content = prompt)),
            stream = true,
            streamOptions = DeepSeekStreamOptions(includeUsage = true),
            thinking = DeepSeekThinking(type = "enabled"),
            reasoningEffort = "high",
        )

}

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
    val reasoningEffort: String,
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
