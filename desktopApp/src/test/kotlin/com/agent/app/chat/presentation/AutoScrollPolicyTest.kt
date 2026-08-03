package com.agent.app.chat.presentation

import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证时间线自动跟随策略。
 */
class AutoScrollPolicyTest {

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
     * 用户提交非空草稿时，必须显式回到最新消息，而不受先前手动滚动位置影响。
     */
    @Test
    fun `should force following the latest item when submitting a message`() {
        assertEquals(true, shouldForceScrollToLatestAfterSubmit("继续构建项目"))
        assertEquals(false, shouldForceScrollToLatestAfterSubmit("   "))
    }

    /**
     * 工具运行完成后新增的意图和输出也必须触发时间线跟随。
     */
    @Test
    fun `should include tool intent and output in timeline content fingerprint`() {
        val started = ToolEventItem(
            toolName = "run_powershell",
            status = ToolEventStatus.Started,
            preview = "Get-ChildItem",
        )
        val finished = started.copy(
            operationIntent = "列出 Kotlin 源文件",
            resultPreview = "shared/src/commonMain/kotlin/A.kt",
        )

        assertEquals(true, itemContentSize(finished) > itemContentSize(started))
    }
}
