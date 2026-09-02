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
import com.agent.shared.agent.provider.KoogProviderTransportAdapter
import com.agent.shared.agent.provider.ProviderKoogTransportAdapters
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.IllegalConfigExceptions
import com.agent.shared.settings.model.ProviderType
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
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
        createFactory(transportAdapter = null)
    }

    /**
     * 为单个 profile 创建 HTTP client factory，仅注入该 profile 命中的 wire-format 适配器。
     */
    fun factoryFor(config: ConfigProfile): KoogHttpClient.Factory =
        createFactory(ProviderKoogTransportAdapters.forProfile(config))

    /** 在创建 client 时捕获已解析的适配器，保证同一轮请求的传输规则稳定。 */
    private fun createFactory(
        transportAdapter: KoogProviderTransportAdapter?,
    ): KoogHttpClient.Factory {
        return object : KoogHttpClient.Factory {
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
                transportAdapter = transportAdapter,
            )
        }
    }
}

/**
 * 在 SSE JSON 解码前滤除 legacy `[DONE]` 终止标记及 Koog 当前无法承载的流式元数据，
 * 并补齐 Responses 重放元数据的 HTTP client 装饰器。
 *
 * `[DONE]` 是 OpenAI-compatible 流式传输的可选终止信号而非 JSON 事件；按传输协议过滤
 * 能让 Responses、Chat Completions 与任意兼容中转站共用同一行为。
 */
internal class SseTerminatorFilteringKoogHttpClient(
    internal val delegate: KoogHttpClient,
    private val transportAdapter: KoogProviderTransportAdapter? = null,
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
            ?.let { data -> transportAdapter?.normalizeRequestBody(data) ?: data }
            as? T ?: requestBody

        return delegate.sse(
        path = path,
        requestBody = replaySafeRequestBody,
        requestBodyType = requestBodyType,
        dataFilter = { data -> dataFilter(data) && shouldDecodeSseData(data) },
        decodeStreamingResponse = { rawData ->
            decodeStreamingResponse(transportAdapter?.normalizeSseData(rawData) ?: rawData)
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
 *
 * Anthropic extended thinking 的 `signature_delta` 仅承载签名元数据；Koog 1.1.1 的
 * Anthropic 流式实现未将它写入 [ai.koog.prompt.streaming.StreamFrame.ReasoningComplete]，
 * 并会为其记录警告。该版本本就不会把该签名回写到 reasoning frame，因此在此过滤只消除
 * 无效警告，不改变现有的模型上下文。
 */
internal fun shouldDecodeSseData(data: String?): Boolean {
    val normalizedData = data?.trim()
    if (normalizedData == "[DONE]") return false
    if (normalizedData == null) return true
    return runCatching {
        val event = Json.parseToJsonElement(normalizedData).jsonObject
        val eventType = event["type"]?.jsonPrimitive?.content
        eventType !in RESPONSE_CONTENT_PART_LIFECYCLE_TYPES &&
            !(eventType == ANTHROPIC_CONTENT_BLOCK_DELTA &&
                event["delta"]?.jsonObject?.get("type")?.jsonPrimitive?.content in
                    UNSUPPORTED_ANTHROPIC_STREAM_DELTA_TYPES)
    }.getOrDefault(true)
}

private val RESPONSE_CONTENT_PART_LIFECYCLE_TYPES = setOf(
    "response.content_part.added",
    "response.content_part.done",
)

private const val ANTHROPIC_CONTENT_BLOCK_DELTA = "content_block_delta"

private val UNSUPPORTED_ANTHROPIC_STREAM_DELTA_TYPES = setOf("signature_delta")

/**
 * 为 Anthropic 兼容端点构造 client settings，把运行时 LLModel 映射回配置的模型 ID。
 *
 * Koog 的 `AnthropicClientSettings.modelVersionsMap` 默认只包含内置 Claude 模型，
 * 序列化前用 `settings.modelVersionsMap[model] ?: throw` 查找请求模型；不提供自定义
 * 映射时，`deepseek-v4-flash` 等兼容端点模型会在网络请求前报 `Unsupported model`。
 * 运行时请求从同一 profile 构造等价的 LLModel，因此单条目映射即可覆盖所有请求。
 */
internal fun buildAnthropicClientSettings(config: ConfigProfile): AnthropicClientSettings =
    AnthropicClientSettings(
        modelVersionsMap = mapOf(buildLlmModel(config) to config.model),
        baseUrl = config.baseUrl,
    )

/**
 * 按配置创建 Desktop 平台使用的 Koog prompt executor。
 */
internal fun buildPromptExecutor(config: ConfigProfile): MultiLLMPromptExecutor {
    when (config.providerType) {
        ProviderType.OPENAI_CHAT_COMPLETIONS, ProviderType.OPENAI_RESPONSES -> {
            val openAILLMClient = OpenAILLMClient(
                apiKey = config.apiKey,
                settings = buildOpenAIClientSettings(config),
                httpClientFactory = DesktopKoogHttpClientFactoryProvider.factoryFor(config),
            )
            return MultiLLMPromptExecutor(openAILLMClient)
        }

        ProviderType.ANTHROPIC -> {
            val anthropicLLMClient = AnthropicLLMClient(
                apiKey = config.apiKey,
                settings = buildAnthropicClientSettings(config),
                httpClientFactory = DesktopKoogHttpClientFactoryProvider.factoryFor(config),
            )
            return MultiLLMPromptExecutor(anthropicLLMClient)
        }

        else -> {
            throw IllegalConfigExceptions { "暂不支持的 providerType: ${config.providerType}" }
        }
    }
}
