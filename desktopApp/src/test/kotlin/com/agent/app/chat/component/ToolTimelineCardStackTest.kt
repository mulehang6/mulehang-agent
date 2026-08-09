package com.agent.app.chat.component

import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 验证工具时间线卡片堆叠和最短运行展示规则。 */
class ToolTimelineCardStackTest {

    /** 非终端工具过快完成时，结果展示必须等待满两秒。 */
    @Test
    fun `should delay non terminal completion until running state is visible for two seconds`() {
        val item = toolEvent("read_file", ToolEventStatus.Finished)

        assertEquals(1_250L, toolCompletionDelayMillis(item, startedAtMillis = 100L, nowMillis = 850L))
        assertEquals(0L, toolCompletionDelayMillis(item, startedAtMillis = 100L, nowMillis = 2_100L))
    }

    /** 终端工具的真实完成状态必须立即展示。 */
    @Test
    fun `should never delay terminal completion`() {
        assertEquals(
            0L,
            toolCompletionDelayMillis(
                item = toolEvent("run_powershell", ToolEventStatus.Finished),
                startedAtMillis = 100L,
                nowMillis = 100L,
            ),
        )
    }

    /** 首次组合时已经结束的工具仍应先渲染运行态，避免动画被事件批次跳过。 */
    @Test
    fun `should synthesize running state when a non terminal completion is first observed`() {
        val displayed = initialTimelineToolDisplayItem(toolEvent("read_file", ToolEventStatus.Finished))

        assertEquals(ToolEventStatus.Started, displayed.status)
        assertFalse(shouldSynthesizeRunningToolDisplay(toolEvent("run_powershell", ToolEventStatus.Finished)))
    }

    /** 堆叠卡片只保留当前运行项和一张后置预览。 */
    @Test
    fun `should show current running tool and one next preview only`() {
        val items = listOf(
            toolEvent("first", ToolEventStatus.Finished),
            toolEvent("second", ToolEventStatus.Started),
            toolEvent("third", ToolEventStatus.Started),
            toolEvent("fourth", ToolEventStatus.Started),
        )

        assertEquals(listOf("second", "third"), visibleToolCardStack(items).map(ToolEventItem::toolName))
    }

}

/** 构造最小工具事件，避免测试耦合工具协议的无关字段。 */
private fun toolEvent(name: String, status: ToolEventStatus): ToolEventItem = ToolEventItem(
    toolName = name,
    status = status,
)
