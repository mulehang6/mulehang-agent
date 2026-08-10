package com.agent.app.chat.component

import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证工具时间线卡片堆叠和即时结果展示规则。 */
class ToolTimelineCardStackTest {

    /** 非终端工具完成后不再为了图标动效刻意等待。 */
    @Test
    fun `should immediately display non terminal completion`() {
        val item = toolEvent("read_file", ToolEventStatus.Finished)

        assertEquals(0L, toolCompletionDelayMillis(item, startedAtMillis = 100L, nowMillis = 100L))
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

    /** 首次组合时已结束的工具直接呈现真实状态。 */
    @Test
    fun `should not synthesize running state when a completion is first observed`() {
        val displayed = initialTimelineToolDisplayItem(toolEvent("read_file", ToolEventStatus.Finished))

        assertEquals(ToolEventStatus.Finished, displayed.status)
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
