@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.koog

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.agent.prompt.buildOpenAIClientSettings
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.IllegalConfigExceptions
import com.agent.shared.settings.model.ProviderType
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass

/**
 * Desktop 平台统一持有 Koog HTTP client factory，避免默认 ServiceLoader 解析到 Apache5。
 *
 * 当前桌面链路以 JDK HttpClient 引擎作为统一底座，减少 DeepSeek SSE 与 Apache5 HTTP/2
 * 协商不兼容带来的随机协议错误，同时让 OpenAI-compatible 与 Anthropic 客户端共享同一套
 * 可控的网络实现。
 */
internal object DesktopKoogHttpClientFactoryProvider {
    private val baseFactory: KoogHttpClient.Factory = KtorKoogHttpClient.Factory(
        baseClient = HttpClient(Java),
    )

    /**
     * 供桌面侧所有 Koog 客户端复用的统一工厂，并过滤协议级 SSE 终止标记。
     */
    val factory: KoogHttpClient.Factory by lazy {
        object : KoogHttpClient.Factory {
            override fun create(
                clientName: String,
                baseUrl: String,
                headers: Map<String, String>,
                queryParameters: Map<String, String>,
                requestTimeoutMillis: Long,
                connectTimeoutMillis: Long,
                socketTimeoutMillis: Long,
                json: Json,
            ): KoogHttpClient = SseTerminatorFilteringKoogHttpClient(
                delegate = baseFactory.create(
                    clientName = clientName,
                    baseUrl = baseUrl,
                    headers = headers,
                    queryParameters = queryParameters,
                    requestTimeoutMillis = requestTimeoutMillis,
                    connectTimeoutMillis = connectTimeoutMillis,
                    socketTimeoutMillis = socketTimeoutMillis,
                    json = json,
                ),
            )
        }
    }
}

/**
 * 在 SSE JSON 解码前滤除 legacy `[DONE]` 终止标记，并补齐 Responses 重放元数据的 HTTP client 装饰器。
 *
 * `[DONE]` 是 OpenAI-compatible 流式传输的可选终止信号而非 JSON 事件；按传输协议过滤
 * 能让 Responses、Chat Completions 与任意兼容中转站共用同一行为。
 */
internal class SseTerminatorFilteringKoogHttpClient(
    internal val delegate: KoogHttpClient,
) : KoogHttpClient by delegate {
    /**
     * 保留调用方的筛选条件，并额外排除不应进入 JSON 解码器的终止标记。
     */
    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Flow<O> {
        @Suppress("UNCHECKED_CAST")
        val replaySafeRequestBody = (requestBody as? String)
            ?.let(::normalizeResponsesRequestBodyForKoog)
            as? T ?: requestBody

        return delegate.sse(
        path = path,
        requestBody = replaySafeRequestBody,
        requestBodyType = requestBodyType,
        dataFilter = { data -> dataFilter(data) && shouldDecodeSseData(data) },
        decodeStreamingResponse = { rawData ->
            decodeStreamingResponse(normalizeSseDataForKoog(rawData))
        },
        processStreamingChunk = processStreamingChunk,
        parameters = parameters,
        headers = headers,
        )
    }
}

/**
 * 判断 SSE data 字段是否为可交给 Koog JSON 事件解码器的有效数据。
 *
 * Koog 1.1.1 不消费 Responses 的 content-part 生命周期事件，却会先反序列化其 `part`。
 * 在传输层跳过这些事件，可兼容尚未被 Koog 注册的合法 part 类型，同时保留实际增量事件。
 */
internal fun shouldDecodeSseData(data: String?): Boolean {
    val normalizedData = data?.trim()
    if (normalizedData == "[DONE]") return false
    if (normalizedData == null) return true
    return runCatching {
        Json.parseToJsonElement(normalizedData)
            .jsonObject["type"]
            ?.jsonPrimitive
            ?.content !in RESPONSE_CONTENT_PART_LIFECYCLE_TYPES
    }.getOrDefault(true)
}

private val RESPONSE_CONTENT_PART_LIFECYCLE_TYPES = setOf(
    "response.content_part.added",
    "response.content_part.done",
)

/**
 * 移除 Responses 事件中 Koog 不消费、但其枚举可能无法识别的推理配置回显。
 *
 * 输出 item 内的 reasoning 文本不会被修改；它们仍由 reasoning delta 与 output-item 事件处理。
 */
internal fun normalizeSseDataForKoog(data: String): String = runCatching {
    val event = Json.parseToJsonElement(data).jsonObject
    val response = event["response"]?.jsonObject ?: return@runCatching data
    if ("reasoning" !in response) return@runCatching data
    JsonObject(
        event + ("response" to JsonObject(response - "reasoning")),
    ).toString()
}.getOrDefault(data)

/**
 * 补齐 Responses stateless replay 中被 Koog 消息模型丢弃的 reasoning 完成状态。
 *
 * Responses 服务端可要求调用方原样回传带工具调用的推理 item；Koog 1.1.1 从
 * [ai.koog.prompt.message.MessagePart.Reasoning] 重建该 item 时不保留输出侧的 `status`。
 * 仅对请求 `input` 中缺失状态的 `reasoning` item 写入标准完成状态，其他协议和字段保持不变。
 */
internal fun normalizeResponsesRequestBodyForKoog(data: String): String = runCatching {
    val request = Json.parseToJsonElement(data).jsonObject
    val input = request["input"]?.jsonArray ?: return@runCatching data
    var changed = false
    val normalizedInput = input.map { item ->
        val itemObject = item as? JsonObject ?: return@map item
        val isReasoningItem = itemObject["type"]?.jsonPrimitive?.content == "reasoning"
        if (!isReasoningItem || "status" in itemObject) {
            item
        } else {
            changed = true
            JsonObject(itemObject + ("status" to JsonPrimitive("completed")))
        }
    }
    if (changed) {
        JsonObject(request + ("input" to JsonArray(normalizedInput))).toString()
    } else {
        data
    }
}.getOrDefault(data)

/**
 * 按配置创建 Desktop 平台使用的 Koog prompt executor。
 */
internal fun buildPromptExecutor(config: ConfigProfile): MultiLLMPromptExecutor {
    when (config.providerType) {
        ProviderType.OPENAI_CHAT_COMPLETIONS, ProviderType.OPENAI_RESPONSES -> {
            val openAILLMClient = OpenAILLMClient(
                apiKey = config.apiKey,
                settings = buildOpenAIClientSettings(config),
                httpClientFactory = DesktopKoogHttpClientFactoryProvider.factory,
            )
            return MultiLLMPromptExecutor(openAILLMClient)
        }

        ProviderType.ANTHROPIC -> {
            val anthropicLLMClient = AnthropicLLMClient(
                apiKey = config.apiKey,
                settings = AnthropicClientSettings(baseUrl = config.baseUrl),
                httpClientFactory = DesktopKoogHttpClientFactoryProvider.factory,
            )
            return MultiLLMPromptExecutor(anthropicLLMClient)
        }

        else -> {
            throw IllegalConfigExceptions { "暂不支持的 providerType: ${config.providerType}" }
        }
    }
}
