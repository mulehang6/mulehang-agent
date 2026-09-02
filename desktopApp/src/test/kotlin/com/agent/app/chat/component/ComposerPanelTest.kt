package com.agent.app.chat.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.desktopPalette
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

    /** `/` 仅在消息开头且还未开始输入参数时打开命令浏览器。 */
    @Test
    fun `should expose slash command query only at active command prefix`() {
        assertEquals("review", activeSlashCommandQuery("/review", 7))
        assertEquals("", activeSlashCommandQuery("/", 1))
        assertEquals(null, activeSlashCommandQuery("请 /review", 9))
        assertEquals(null, activeSlashCommandQuery("/review inspect", 15))
    }

    /** `@` 浏览器仅解析独立的工作区文件输入，不抢占电子邮件或已完成的普通文本。 */
    @Test
    fun `should expose workspace file query only after standalone at sign`() {
        assertEquals("src/App", activeWorkspaceReferenceQuery("读取 @src/App", 11))
        assertEquals(null, activeWorkspaceReferenceQuery("mail@example.com", 16))
        assertEquals(null, activeWorkspaceReferenceQuery("读取 @src App", 11))
    }

    /** 五种权限语义不得因紧凑菜单视觉重做而变化。 */
    @Test
    fun `should preserve every permission presentation in compact menu`() {
        assertEquals(
            setOf("Ask", "Auto", "Allow Edits", "Plan", "Full Access"),
            PermissionPreset.entries.map { permissionPresentation(it).label }.toSet(),
        )
    }

    /** 选择器组从右向左收窄，权限变化期间不会推挤其左侧的选择器。 */
    @Test
    fun `should collapse permission before the selectors to its left`() {
        val specs = listOf(
            ComposerSelectorWidthSpec(100.dp, 80.dp, 32.dp),
            ComposerSelectorWidthSpec(100.dp, 80.dp, 32.dp),
            ComposerSelectorWidthSpec(60.dp, 40.dp, 28.dp),
            ComposerSelectorWidthSpec(60.dp, 40.dp, 28.dp),
        )

        assertEquals(
            listOf(
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 100.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 100.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 60.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.LABEL_ONLY, 40.dp),
            ),
            composerSelectorWidthAllocations(specs, availableWidth = 312.dp),
        )
        assertEquals(
            listOf(
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 100.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 100.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 60.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.PREFIX, 32.dp),
            ),
            composerSelectorWidthAllocations(specs, availableWidth = 304.dp),
        )
    }

    /** 去除箭头后，权限文本按可用字符宽度保留前缀，不显示省略号。 */
    @Test
    fun `should shorten permission label from ask to as before hiding it`() {
        val measureMonospace = { text: String -> text.length * 10 }

        assertEquals("Ask", composerSelectorLabelPrefix("Ask", 30, measureMonospace))
        assertEquals("As", composerSelectorLabelPrefix("Ask", 20, measureMonospace))
        assertEquals("", composerSelectorLabelPrefix("Ask", 9, measureMonospace))
    }

    /** 权限完全隐藏后，才轮到思考等级从右侧开始收窄。 */
    @Test
    fun `should collapse reasoning only after permission is hidden`() {
        val specs = listOf(
            ComposerSelectorWidthSpec(100.dp, 80.dp, 32.dp),
            ComposerSelectorWidthSpec(100.dp, 80.dp, 32.dp),
            ComposerSelectorWidthSpec(60.dp, 40.dp, 28.dp),
            ComposerSelectorWidthSpec(60.dp, 40.dp, 28.dp),
        )

        assertEquals(
            listOf(
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 100.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.FULL, 100.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.LABEL_ONLY, 40.dp),
                ComposerSelectorWidthAllocation(ComposerSelectorCompressionState.HIDDEN, 0.dp),
            ),
            composerSelectorWidthAllocations(specs, availableWidth = 267.dp),
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

    /** 浅色菜单的未选权限项使用语义前景色，不能保留固定白字。 */
    @Test
    fun `should use readable theme text for permission menu`() {
        val lightPalette = desktopPalette(DesktopThemeMode.LIGHT)

        assertEquals(lightPalette.text, permissionPresetMenuTextColor(lightPalette))
        assertFalse(permissionPresetMenuTextColor(lightPalette) == Color.White)
    }
}
