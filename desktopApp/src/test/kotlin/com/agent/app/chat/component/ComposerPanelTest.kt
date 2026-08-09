package com.agent.app.chat.component

import com.agent.shared.chat.model.ExecutionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证 Composer 的执行态流光反馈约束。 */
class ComposerPanelTest {
    /** 仅实际运行中的任务需要连续流光，空闲态保留静态描边。 */
    @Test
    fun `should animate composer border only while task is running`() {
        assertTrue(shouldAnimateComposerBorder(ExecutionState.Running))
        assertFalse(shouldAnimateComposerBorder(ExecutionState.Idle))
    }

    /** 流光周期应足够从容，避免与工具运行图标形成高频竞争。 */
    @Test
    fun `should use a calm composer border flow duration`() {
        assertEquals(2_200, COMPOSER_BORDER_FLOW_DURATION_MILLIS)
    }

    /** 流光跨越闭合边框末端时必须从路径起点继续，而不能离开输入框。 */
    @Test
    fun `should wrap composer border flow at path boundary`() {
        assertEquals(
            listOf(
                ComposerBorderFlowSegment(82f, 100f),
                ComposerBorderFlowSegment(0f, 0f),
            ).filter { it.startDistance < it.endDistance },
            composerBorderFlowSegments(pathLength = 100f, progress = 0f),
        )
        assertEquals(
            listOf(
                ComposerBorderFlowSegment(72f, 90f),
            ),
            composerBorderFlowSegments(pathLength = 100f, progress = 0.9f),
        )
    }
}
