@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import com.agent.shared.exceptions.IllegalConfigExceptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java

/**
 * Desktop 平台统一持有 Koog HTTP client factory，避免默认 ServiceLoader 解析到 Apache5。
 *
 * 当前桌面链路以 JDK HttpClient 引擎作为统一底座，减少 DeepSeek SSE 与 Apache5 HTTP/2
 * 协商不兼容带来的随机协议错误，同时让 OpenAI-compatible 与 Anthropic 客户端共享同一套
 * 可控的网络实现。
 */
internal object DesktopKoogHttpClientFactoryProvider {
    /**
     * 供桌面侧所有 Koog 客户端复用的统一工厂。
     */
    val factory: KoogHttpClient.Factory by lazy {
        KtorKoogHttpClient.Factory(
            baseClient = HttpClient(Java),
        )
    }
}

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
