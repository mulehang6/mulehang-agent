package com.agent.shared.config

import com.agent.shared.agent.ReasoningEffort
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 profile 到模型能力的解析规则，避免 UI 层直接按字符串特判。
 */
class ModelCapabilitiesResolverTest {

    /**
     * DeepSeek chat-completions 模型应暴露官方有效的 high/max thinking 档位。
     */
    @Test
    fun `should expose reasoning efforts for deepseek chat completions profile`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-v4-flash",
            ),
        )

        assertEquals(true, capabilities.supportsReasoning)
        assertEquals(listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX), capabilities.reasoningEfforts)
        assertEquals(ReasoningEffort.HIGH, capabilities.defaultReasoningEffort)
        assertEquals(ModelLimit(context = 1_000_000, output = 384_000), capabilities.limit)
        assertEquals(
            mapOf(
                "high" to ModelVariant(id = "high", reasoningEffort = ReasoningEffort.HIGH),
                "max" to ModelVariant(id = "max", reasoningEffort = ReasoningEffort.MAX),
            ),
            capabilities.variants,
        )
    }

    /**
     * 非 reasoning 模型不应暴露 thinking 档位，发送层可据此省略 reasoning_effort。
     */
    @Test
    fun `should hide reasoning efforts for unsupported profile`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.ANTHROPIC,
                baseUrl = "https://api.anthropic.com",
                model = "claude-sonnet-4",
            ),
        )

        assertEquals(false, capabilities.supportsReasoning)
        assertEquals(emptyList(), capabilities.reasoningEfforts)
        assertEquals(null, capabilities.defaultReasoningEffort)
        assertEquals(emptyMap(), capabilities.variants)
        assertEquals(null, capabilities.limit)
    }

    /**
     * OpenAI-compatible 的 GPT reasoning family 应按 Kilo 思路生成 reasoning variants。
     */
    @Test
    fun `should generate reasoning variants for openai compatible gpt reasoning family`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://openrouter.ai/api/v1",
                model = "openai/gpt-5-codex",
            ),
        )

        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            capabilities.reasoningEfforts,
        )
        assertEquals(ReasoningEffort.MEDIUM, capabilities.variants["medium"]?.reasoningEffort)
    }

    /**
     * 显式 profile limit 应覆盖 provider 默认模型窗口。
     */
    @Test
    fun `should let profile limit override provider default limit`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-v4-pro",
                limit = ModelLimit(context = 128_000, output = 16_000),
            ),
        )

        assertEquals(ModelLimit(context = 128_000, output = 16_000), capabilities.limit)
    }

    private fun profile(
        providerType: ProviderType,
        baseUrl: String,
        model: String,
        limit: ModelLimit? = null,
    ): ConfigProfile = ConfigProfile(
        id = "profile-$model",
        providerType = providerType,
        baseUrl = baseUrl,
        apiKey = "key",
        model = model,
        enabled = true,
        layer = ConfigLayer.PROJECT,
        limit = limit,
    )
}
