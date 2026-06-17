package com.agent.shared.agent

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 Koog agent prompt 的桌面侧构造规则。
 */
class KoogPromptTest {

    /**
     * agent 基础 prompt 只应承载参数，不应提前写入用户正文。
     */
    @Test
    fun `should build agent prompt without duplicating user message`() {
        val prompt = buildAgentPrompt(
            profile = deepSeekProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
        )

        assertTrue(prompt.messages.isEmpty())
        val params = prompt.params as? OpenAIChatParams
        assertEquals(ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort.HIGH, params?.reasoningEffort)
    }

    private fun deepSeekProfile(): ConfigProfile = ConfigProfile(
        id = "deepseek",
        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "key",
        model = "deepseek-v4-flash",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
