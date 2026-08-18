package com.agent.app.chat.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
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

    /** 输入文本必须始终落在黑色编辑区的安全内边距内。 */
    @Test
    fun `should reserve composer editing insets`() {
        assertEquals(12, COMPOSER_INPUT_HORIZONTAL_PADDING_DP)
        assertEquals(8, COMPOSER_INPUT_VERTICAL_PADDING_DP)
        assertEquals(DpOffset(12.dp, 8.dp), composerInputContentOffset())
    }

    /** 五种权限语义不得因紧凑菜单视觉重做而变化。 */
    @Test
    fun `should preserve every permission presentation in compact menu`() {
        assertEquals(
            setOf("Ask", "Auto", "Allow Edits", "Plan", "Full Access"),
            PermissionPreset.entries.map { permissionPresentation(it).label }.toSet(),
        )
    }

    /** 权限色替换 Jewel 默认蓝色；已选中行在悬停和按下时保持不变。 */
    @Test
    fun `should preserve selected permission row color over hover and press`() {
        val tone = Color(0xFF245286)

        assertEquals(
            Color.Transparent,
            permissionPresetMenuRowBackground(tone, selected = false, hovered = false, pressed = false),
        )
        assertEquals(
            tone.copy(alpha = PERMISSION_MENU_HOVERED_ALPHA),
            permissionPresetMenuRowBackground(tone, selected = false, hovered = true, pressed = false),
        )
        assertEquals(
            tone.copy(alpha = PERMISSION_MENU_PRESSED_ALPHA),
            permissionPresetMenuRowBackground(tone, selected = false, hovered = true, pressed = true),
        )
        assertEquals(
            tone.copy(alpha = PERMISSION_MENU_SELECTED_ALPHA),
            permissionPresetMenuRowBackground(tone, selected = true, hovered = true, pressed = false),
        )
        assertEquals(
            tone.copy(alpha = PERMISSION_MENU_SELECTED_ALPHA),
            permissionPresetMenuRowBackground(tone, selected = true, hovered = true, pressed = true),
        )
    }
}
