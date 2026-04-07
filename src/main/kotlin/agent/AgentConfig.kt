package agent

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OpenRouterLLMProvider
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Duration
import kotlinx.serialization.json.Json
import mulehang.config.ConfigLoader
import mulehang.provider.ExecutorFactory
import mulehang.provider.ProviderRegistry

internal const val CHAT_HISTORY_DIR = "my_chat_history"
internal const val CHAT_HISTORY_WINDOW_SIZE = 20
internal const val CHAT_TEMPERATURE = 0.7
internal const val OPENROUTER_REASONING_EFFORT = "high"
internal const val STREAMING_FALLBACK_NOTICE = "[系统] OpenRouter 流式请求失败（HTTP 400/429），已自动切换到非流式模式重试。"
internal val CURRENT_WORKING_DIRECTORY: String = Path.of("").toAbsolutePath().normalize().toString()
internal val APP_CONFIG = ConfigLoader().load()
internal val DEFAULT_BINDING = runCatching { ExecutorFactory(APP_CONFIG).defaultBinding() }.getOrNull()
internal val CHAT_MODEL_ID = DEFAULT_BINDING?.modelId ?: APP_CONFIG.defaultModel
internal val CHAT_BASE_URL =
    DEFAULT_BINDING?.baseUrl
        ?: APP_CONFIG.providers[APP_CONFIG.defaultProvider]?.baseUrl
        ?: ProviderRegistry.providers[APP_CONFIG.defaultProvider]?.defaultBaseUrl
        ?: "https://openrouter.ai/api/v1"
internal val SYSTEM_PROMPT: String = """
你是一个友好的 AI 助手。对于普通聊天和常规问答，请直接给出自然、简洁的回答。
你当前的工作目录是：$CURRENT_WORKING_DIRECTORY
""".trimIndent()

/**
 * 当前命令行智能体使用的聊天模型定义。
 */
internal val CHAT_MODEL = LLModel(
    provider = OpenRouterLLMProvider(),
    id = CHAT_MODEL_ID,
    capabilities = listOf(
        LLMCapability.Completion,
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.Vision.Image
    )
)

/**
 * OpenRouter 聊天补全接口地址。
 */
internal val OPENROUTER_CHAT_COMPLETIONS_URI: URI =
    URI("${CHAT_BASE_URL.trimEnd('/')}/chat/completions")

/**
 * 发送 OpenRouter 请求时复用的 HTTP 客户端。
 */
internal val openRouterHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .build()

/**
 * 用于解析 OpenRouter JSON 负载的共享序列化配置。
 */
internal val openRouterJson = Json { ignoreUnknownKeys = true }
