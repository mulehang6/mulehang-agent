package com.agent.app.chat.state

import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.agent.koog.agentSystemPromptEstimatedTokenCount
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 context window 解析与占用估算公式。
 */
class ContextUsageEstimatorTest {
    @Test
    fun `explicit profile context limit takes precedence`() {
        assertEquals(100, resolveContextWindow(profile()))
    }

    @Test
    fun `usage estimate includes the fixed system prompt plus text and attachment token constants`() {
        val expectedTokens = agentSystemPromptEstimatedTokenCount() + 2 + 64
        val result = estimateContextUsage(
            items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "12345678"))),
            attachmentCount = 1,
            contextWindow = expectedTokens * 2,
        )

        assertEquals(0.5f, result)
        assertEquals(0f, estimateContextUsage(emptyList(), attachmentCount = 0, contextWindow = null))
    }

    private fun profile(): ConfigProfile = ConfigProfile(
        id = "openai:gpt-4.1",
        providerId = "openai",
        providerLabel = "OpenAI",
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "key",
        model = "gpt-4.1",
        enabled = true,
        layer = ConfigLayer.PROJECT,
        limit = ModelLimit(context = 100, output = 20),
    )
}
