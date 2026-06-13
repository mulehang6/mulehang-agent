package com.agent.app.ui

import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelVariant
import com.agent.shared.config.ProviderType
import com.agent.shared.state.ReasoningItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证聊天时间线展示文案的最小规则。
 */
class ChatScreenPresentationTest {

    /**
     * 聊天正文应直接显示内容，不再拼接角色前缀。
     */
    @Test
    fun `should render chat message without role prefix`() {
        val userItem = ChatMessageItem(ChatMessage(role = ChatRole.User, content = "你好"))
        val assistantItem = ChatMessageItem(ChatMessage(role = ChatRole.Assistant, content = "世界"))

        assertEquals("你好", buildChatMessageText(userItem))
        assertEquals("世界", buildChatMessageText(assistantItem))
    }

    /**
     * 思考块标题应保留 Thinking 文案，并区分流式中与完成态。
     */
    @Test
    fun `should keep thinking headline for reasoning block`() {
        assertEquals("Thinking: 思考中...", buildReasoningHeadline(ReasoningItem(isStreaming = true)))
        assertEquals("Thinking:", buildReasoningHeadline(ReasoningItem(isStreaming = false)))
    }

    /**
     * pwd 分组标题应只显示末级目录名。
     */
    @Test
    fun `should map workspace path to terminal folder label`() {
        assertEquals("def", buildWorkspaceLabel("E:\\abc\\def"))
        assertEquals("repo-x", buildWorkspaceLabel("D:\\work\\repo-x"))
    }

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
     * 上下文剩余数值只应出现在 hover tooltip 文案中。
     */
    @Test
    fun `should keep context usage value inside tooltip text only`() {
        assertEquals("58% remaining", buildContextTooltip(0.58f))
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
