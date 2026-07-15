package com.agent.app.chat.presentation

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.resolver.ModelVariant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 composer 的纯展示转换。
 */
class ComposerPresentationTest {

    /**
     * provider 标签和 reasoning 档位应来自 profile 能力解析。
     */
    @Test
    fun `should expose provider label and reasoning support from profile`() {
        assertEquals("OpenAI Compatible", providerLabel(ProviderType.OPENAI_CHAT_COMPLETIONS))
        assertEquals(
            listOf(
                ModelVariant(id = "high", reasoningEffort = ReasoningEffort.HIGH),
                ModelVariant(id = "max", reasoningEffort = ReasoningEffort.MAX),
            ),
            modelVariantsFor(profile(model = "deepseek-v4-flash")),
        )
        assertEquals(emptyList(), modelVariantsFor(profile(model = "claude-sonnet-4")))
    }

    /**
     * Provider 分组应使用配置 providerId，而不是 providerType。
     */
    @Test
    fun `should group model picker entries by provider id`() {
        val profiles = listOf(
            profile(model = "deepseek-v4-flash").copy(
                id = "deepseek:deepseek-v4-flash",
                providerId = "deepseek",
                providerLabel = "DeepSeek",
            ),
            profile(model = "deepseek-v4-pro").copy(
                id = "deepseek:deepseek-v4-pro",
                providerId = "deepseek",
                providerLabel = "DeepSeek",
            ),
            profile(model = "openai/gpt-5-codex").copy(
                id = "openrouter:openai/gpt-5-codex",
                providerId = "openrouter",
                providerLabel = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
            ),
        )

        val grouped = groupProfilesByProvider(profiles)

        assertEquals(setOf("deepseek", "openrouter"), grouped.keys)
        assertEquals(listOf("deepseek-v4-flash", "deepseek-v4-pro"), grouped["deepseek"]?.map { it.model })
    }

    /**
     * 上下文剩余数值只应出现在 hover tooltip 文案中。
     */
    @Test
    fun `should keep context usage value inside tooltip text only`() {
        assertEquals("58% used", buildContextTooltip(0.58f))
        assertEquals("<0.1% used", buildContextTooltip(0.00002f))
    }

    /**
     * 上下文圆环旁应直接展示当前计算出的占用百分比。
     */
    @Test
    fun `should expose context usage percentage as visible chip label`() {
        assertEquals("58%", buildContextUsageLabel(0.58f))
        assertEquals("<0.1%", buildContextUsageLabel(0.00002f))
    }

    /**
     * 上下文圆环的 sweep angle 应按 0..1 占比换算，并在非零时保留最小可见弧度。
     */
    @Test
    fun `should clamp context ring sweep angle from usage fraction`() {
        assertEquals(208.8f, contextRingSweepAngle(0.58f), 0.001f)
        assertEquals(0f, contextRingSweepAngle(-0.2f), 0.001f)
        assertEquals(360f, contextRingSweepAngle(1.4f), 0.001f)
        assertEquals(6f, contextRingSweepAngle(0.00002f), 0.001f)
    }

    /**
     * 执行中、等待输入或等待审批时主按钮应切为停止态，避免继续显示发送图标。
     */
    @Test
    fun `should expose stop action visual for running and waiting states`() {
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "■", danger = true),
            buildComposerPrimaryActionVisual(ExecutionState.Running),
        )
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "■", danger = true),
            buildComposerPrimaryActionVisual(ExecutionState.WaitingForUserInput),
        )
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "■", danger = true),
            buildComposerPrimaryActionVisual(ExecutionState.WaitingForApproval),
        )
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "↑", danger = false),
            buildComposerPrimaryActionVisual(ExecutionState.Idle),
        )
    }

    private fun profile(model: String): ConfigProfile = ConfigProfile(
        id = "profile-$model",
        providerType = if (model.startsWith("deepseek", ignoreCase = true)) {
            ProviderType.OPENAI_CHAT_COMPLETIONS
        } else {
            ProviderType.ANTHROPIC
        },
        baseUrl = if (model.startsWith("deepseek", ignoreCase = true)) {
            "https://api.deepseek.com/v1"
        } else {
            "https://api.anthropic.com"
        },
        apiKey = "key",
        model = model,
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
