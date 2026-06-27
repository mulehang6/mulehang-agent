package com.agent.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelVariant
import com.agent.shared.config.ProviderType
import com.agent.shared.state.ExecutionState
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
     * 只有当时间线已经停在底部附近时，新增内容才应自动跟随到底部。
     */
    @Test
    fun `should auto scroll only when timeline is already near the latest item`() {
        assertEquals(true, shouldAutoScrollToLatest(lastVisibleIndex = null, totalItems = 6))
        assertEquals(6, timelineAutoScrollAnchorIndex(totalItems = 6))
        assertEquals(true, shouldAutoScrollToLatest(lastVisibleIndex = 6, totalItems = 6))
        assertEquals(true, shouldAutoScrollToLatest(lastVisibleIndex = 5, totalItems = 6))
        assertEquals(false, shouldAutoScrollToLatest(lastVisibleIndex = 2, totalItems = 6))
    }

    /**
     * 如果原本已经跟随到底部，连续追加多个时间线块时不应因为可见索引短暂落后而丢失跟随状态。
     */
    @Test
    fun `should keep following latest while new timeline blocks are appended`() {
        assertEquals(
            true,
            nextAutoScrollFollowState(
                currentFollowLatest = true,
                lastVisibleIndex = 6,
                totalItems = 8,
                previousTotalItems = 6,
            ),
        )
        assertEquals(
            false,
            nextAutoScrollFollowState(
                currentFollowLatest = true,
                lastVisibleIndex = 2,
                totalItems = 8,
                previousTotalItems = 8,
            ),
        )
        assertEquals(
            false,
            nextAutoScrollFollowState(
                currentFollowLatest = false,
                lastVisibleIndex = 6,
                totalItems = 8,
                previousTotalItems = 6,
            ),
        )
    }

    /**
     * 执行中时主按钮应切为停止态，避免继续显示发送图标。
     */
    @Test
    fun `should expose stop action visual while agent is running`() {
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "■", danger = true),
            buildComposerPrimaryActionVisual(ExecutionState.Running),
        )
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "↑", danger = false),
            buildComposerPrimaryActionVisual(ExecutionState.Idle),
        )
    }

    /**
     * composer 应支持 Enter 发送，同时保留 Shift+Enter 换行。
     */
    @Test
    fun `should submit composer on enter without shift only`() {
        assertEquals(true, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyUp, isShiftPressed = false))
        assertEquals(false, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyUp, isShiftPressed = true))
        assertEquals(false, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyDown, isShiftPressed = false))
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
