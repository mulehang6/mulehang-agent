package com.agent.shared.settings.resolver

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ProviderType
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
     * 官方 OpenAI Responses 的 GPT/Codex reasoning family 应暴露 reasoning variants。
     */
    @Test
    fun `should generate reasoning variants for official OpenAI reasoning family`() {
        val official = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_RESPONSES,
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-5-codex",
            ),
        )
        val customEndpoint = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_RESPONSES,
                baseUrl = "https://gateway.example/v1",
                model = "gpt-5-codex",
            ),
        )

        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            official.reasoningEfforts,
        )
        assertEquals(ReasoningEffort.MEDIUM, official.variants["medium"]?.reasoningEffort)
        assertEquals(emptyList(), customEndpoint.reasoningEfforts)
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

    /**
     * 显式配置应优先于 DeepSeek 的代码内置默认能力。
     */
    @Test
    fun `should let configured capabilities override deepseek defaults`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-v4-flash",
                reasoningEfforts = listOf(ReasoningEffort.LOW),
                defaultReasoningEffort = ReasoningEffort.LOW,
            ),
        )

        assertEquals(listOf(ReasoningEffort.LOW), capabilities.reasoningEfforts)
        assertEquals(ReasoningEffort.LOW, capabilities.defaultReasoningEffort)
    }

    /**
     * 未显式设置默认档位的通用 reasoning 模型应优先选择 medium。
     */
    @Test
    fun `should default configured reasoning efforts containing medium to medium`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_RESPONSES,
                baseUrl = "https://gateway.example/v1",
                model = "reasoning-model",
                reasoningEfforts = listOf(
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH,
                    ReasoningEffort.XHIGH,
                    ReasoningEffort.MAX,
                ),
            ),
        )

        assertEquals(ReasoningEffort.MEDIUM, capabilities.defaultReasoningEffort)
    }

    /**
     * 不含 medium 的通用 reasoning 模型应保留配置顺序中的最低档位作为默认值。
     */
    @Test
    fun `should default configured reasoning efforts without medium to first effort`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://gateway.example/v1",
                model = "reasoning-model",
                reasoningEfforts = listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX),
            ),
        )

        assertEquals(ReasoningEffort.HIGH, capabilities.defaultReasoningEffort)
    }

    /**
     * 空的显式配置应阻断后续 provider 的能力推断。
     */
    @Test
    fun `should expose no reasoning for explicitly empty configured efforts`() {
        val capabilities = ModelCapabilitiesResolver.resolve(
            profile = profile(
                providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-v4-flash",
                reasoningEfforts = emptyList(),
            ),
        )

        assertEquals(emptyList(), capabilities.reasoningEfforts)
        assertEquals(null, capabilities.defaultReasoningEffort)
    }

    private fun profile(
        providerType: ProviderType,
        baseUrl: String,
        model: String,
        limit: ModelLimit? = null,
        reasoningEfforts: List<ReasoningEffort>? = null,
        defaultReasoningEffort: ReasoningEffort? = null,
    ): ConfigProfile = ConfigProfile(
        id = "profile-$model",
        providerType = providerType,
        baseUrl = baseUrl,
        apiKey = "key",
        model = model,
        enabled = true,
        layer = ConfigLayer.PROJECT,
        limit = limit,
        reasoningEfforts = reasoningEfforts,
        defaultReasoningEffort = defaultReasoningEffort,
    )
}
