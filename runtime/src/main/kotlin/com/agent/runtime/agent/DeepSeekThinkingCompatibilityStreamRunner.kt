package com.agent.runtime.agent

import ai.koog.prompt.streaming.StreamFrame
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 为当前 runtime 提供可插拔的 streaming 兼容层，处理底层 SDK 尚未覆盖的 provider 特性。
 */
interface CompatibilityStreamRunner {
    /**
     * 判断当前 binding 和工具形态是否需要走兼容层。
     */
    fun supports(binding: ProviderBinding, hasTools: Boolean): Boolean

    /**
     * 直接产出兼容后的标准 [StreamFrame]。
     */
    fun stream(binding: ProviderBinding, userPrompt: String): Flow<StreamFrame>
}

/**
 * 抽象 DeepSeek thinking SSE 的底层事件流来源，便于在单元测试里注入假数据。
 */
interface DeepSeekThinkingTransport {
    /**
     * 打开一次原始 SSE 事件流；每个元素都是一条 `data:` 载荷或 `DONE` 结束标记。
     */
    fun openEventStream(binding: ProviderBinding, userPrompt: String): Flow<String>
}

/**
 * 兼容 Koog 0.8.0 对 DeepSeek OpenAI chat-completions `reasoning_content` 未建模的问题。
 */
class DeepSeekThinkingCompatibilityStreamRunner(
    private val transport: DeepSeekThinkingTransport = HttpDeepSeekThinkingTransport(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CompatibilityStreamRunner {

    /**
     * 仅在 `DeepSeek + OpenAI-compatible + thinking + 无工具` 时启用兼容路径，避免影响其他 provider。
     */
    override fun supports(binding: ProviderBinding, hasTools: Boolean): Boolean =
        !hasTools &&
            binding.providerType == ProviderType.OPENAI_COMPATIBLE &&
            binding.enableThinking &&
            binding.usesDeepSeekThinkingMode()

    /**
     * 把 DeepSeek 原始 SSE 增量恢复成标准 reasoning/text stream frame。
     */
    override fun stream(binding: ProviderBinding, userPrompt: String): Flow<StreamFrame> = flow {
        transport.openEventStream(binding, userPrompt).collect { payload ->
            emitChunkFrames(payload)
        }
    }

    /**
     * 解析单条 SSE 数据载荷；`DONE` 结束标记只表示自然结束，不额外产出 frame。
     */
    private suspend fun FlowCollector<StreamFrame>.emitChunkFrames(payload: String) {
        if (payload == "[DONE]") {
            return
        }

        val chunk = json.decodeFromString(DeepSeekChatCompletionChunk.serializer(), payload)
        chunk.choices.forEach { choice ->
            choice.delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let { reasoning ->
                emit(StreamFrame.ReasoningDelta(text = reasoning))
            }
            choice.delta.content?.takeIf { it.isNotEmpty() }?.let { content ->
                emit(StreamFrame.TextDelta(content))
            }
        }
    }
}

/**
 * 使用 JDK HttpClient 直接读取 DeepSeek 的 SSE 流，作为 Koog 缺口的窄兼容层。
 */
class HttpDeepSeekThinkingTransport(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DeepSeekThinkingTransport {

    /**
     * 发起一轮最小 chat/completions 请求，并逐条吐出 SSE `data:` 载荷。
     */
    override fun openEventStream(binding: ProviderBinding, userPrompt: String): Flow<String> = flow {
        val request = HttpRequest.newBuilder(buildDeepSeekChatCompletionsUri(binding.baseUrl))
            .timeout(Duration.ofMinutes(10))
            .header("Authorization", "Bearer ${binding.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.encodeToString(
                        DeepSeekChatCompletionRequest.serializer(),
                        DeepSeekChatCompletionRequest(
                            model = binding.modelId,
                            messages = listOf(
                                DeepSeekChatMessage(role = "system", content = DEFAULT_AGENT_SYSTEM_PROMPT),
                                DeepSeekChatMessage(role = "user", content = userPrompt),
                            ),
                            stream = true,
                            streamOptions = DeepSeekStreamOptions(includeUsage = true),
                            thinking = DeepSeekThinkingMode(type = "enabled"),
                        ),
                    ),
                    StandardCharsets.UTF_8,
                ),
            )
            .build()
        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }
        response.body().use { body ->
            if (response.statusCode() !in 200..299) {
                val message = body.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                throw IllegalStateException(
                    "DeepSeek streaming request failed with status ${response.statusCode()}: $message",
                )
            }

            body.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val dataBuffer = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) {
                        emitBufferedEvent(dataBuffer)
                        continue
                    }
                    if (!line.startsWith(SSE_DATA_PREFIX)) {
                        continue
                    }
                    if (dataBuffer.isNotEmpty()) {
                        dataBuffer.append('\n')
                    }
                    dataBuffer.append(line.removePrefix(SSE_DATA_PREFIX).trimStart())
                }
                emitBufferedEvent(dataBuffer)
            }
        }
    }

    /**
     * 在 SSE 事件分隔处把已收集的 `data:` 载荷发给上层解析器。
     */
    private suspend fun FlowCollector<String>.emitBufferedEvent(dataBuffer: StringBuilder) {
        val payload = dataBuffer.toString().trim()
        dataBuffer.clear()
        if (payload.isEmpty()) {
            return
        }
        emit(payload)
    }

    /**
     * 根据用户给出的 base URL 还原 chat/completions 端点，兼容 `.../v1` 与根路径两种写法。
     */
    private fun buildDeepSeekChatCompletionsUri(baseUrl: String): URI {
        val normalized = baseUrl.trimEnd('/')
        val suffix = if (normalized.endsWith("/v1")) "/chat/completions" else "/v1/chat/completions"
        return URI.create(normalized + suffix)
    }

    private companion object {
        private const val SSE_DATA_PREFIX = "data:"
    }
}

/**
 * 表示 DeepSeek chat/completions 的最小请求体。
 */
@Serializable
data class DeepSeekChatCompletionRequest(
    val model: String,
    val messages: List<DeepSeekChatMessage>,
    val stream: Boolean,
    @SerialName("stream_options")
    val streamOptions: DeepSeekStreamOptions,
    val thinking: DeepSeekThinkingMode,
)

/**
 * 表示 chat/completions 里的单条 message。
 */
@Serializable
data class DeepSeekChatMessage(
    val role: String,
    val content: String,
)

/**
 * 表示 thinking mode 的最小透传载荷。
 */
@Serializable
data class DeepSeekThinkingMode(
    val type: String,
)

/**
 * 表示是否在流结束时返回 usage。
 */
@Serializable
data class DeepSeekStreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean,
)

/**
 * 表示 DeepSeek SSE chunk 的最小可见字段。
 */
@Serializable
data class DeepSeekChatCompletionChunk(
    val choices: List<DeepSeekChatCompletionChoice> = emptyList(),
)

/**
 * 表示单个 choice 增量。
 */
@Serializable
data class DeepSeekChatCompletionChoice(
    val delta: DeepSeekChatCompletionDelta = DeepSeekChatCompletionDelta(),
)

/**
 * 表示 chat/completions delta 里 runtime 真正关心的文本字段。
 */
@Serializable
data class DeepSeekChatCompletionDelta(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
)
