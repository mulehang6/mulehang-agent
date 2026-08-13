package com.agent.app.chat.component

import com.agent.app.design.PopupMenuHoverBackground
import com.agent.app.design.PopupMenuSelectedBackground
import com.agent.app.design.DesktopMaterialMode
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.liquidglass.LiquidGlassExpansionDirection
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 设置 Island 的交互色与稳定状态回归测试。 */
class SettingsPanelInteractionTest {

    /** 设置导航的悬浮和选中态必须与下拉菜单完全同源。 */
    @Test
    fun `should use popup menu colors for settings interaction states`() {
        assertEquals(
            PopupMenuHoverBackground,
            settingsItemBackground(selected = false, hovered = true),
        )
        assertEquals(
            PopupMenuSelectedBackground,
            settingsItemBackground(selected = true, hovered = true),
        )
    }

    /** 未设置辅助模型时，只用 Provider 的首个模型作为不写入配置的占位回退。 */
    @Test
    fun `should use first provider model as auxiliary model placeholder`() {
        val provider = ProviderProfile(
            id = "gateway",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://gateway.example.com/v1",
            apiKey = "key",
            models = listOf(ModelProfile(id = "first-model"), ModelProfile(id = "second-model")),
        )

        assertEquals("first-model", auxiliaryModelPlaceholder(provider))
        assertNull(auxiliaryModelPlaceholder(provider.copy(models = emptyList())))
    }

    /** Provider 外层卡片稳定不变，交互色只应用到内层可点击摘要。 */
    @Test
    fun `should scope provider interaction colors to summary island`() {
        assertEquals(Color(0xFF252629), providerCardBackground())
        assertEquals(Color(0xFF252629).copy(alpha = 0.58f), providerCardBackground(DesktopMaterialMode.LIQUID_GLASS))
        assertEquals(Color.Transparent, providerSummaryBackground(expanded = false, hovered = false))
        assertEquals(Color(0xFF38393B), providerSummaryBackground(expanded = false, hovered = true))
        assertEquals(
            PopupMenuSelectedBackground,
            providerSummaryBackground(expanded = true, hovered = true),
        )
    }

    /** 终端开关导致设置重新布局时，提升的状态不得重置到主题分区。 */
    @Test
    fun `should retain settings navigation state across layout changes`() {
        val state = SettingsPanelUiState()

        state.section = SettingsSection.PROVIDERS
        state.search = "gateway"
        state.expandedProviderId = "provider-2"

        assertEquals(SettingsSection.PROVIDERS, state.section)
        assertEquals("gateway", state.search)
        assertEquals("provider-2", state.expandedProviderId)
    }

    /** Provider 摘要不显示操作文字，但图标必须保留完整的无障碍状态描述。 */
    @Test
    fun `should expose provider disclosure state through semantics`() {
        assertEquals("展开服务 Mulehang", providerDisclosureDescription("Mulehang", expanded = false))
        assertEquals("收起服务 Mulehang", providerDisclosureDescription("Mulehang", expanded = true))
        assertEquals(-90f, providerDisclosureRotationDegrees(expanded = false))
        assertEquals(0f, providerDisclosureRotationDegrees(expanded = true))
    }

    /** Provider 编辑器以进入慢于退出的短过渡避免表单突兀出现或消失。 */
    @Test
    fun `should use asymmetric provider editor transition durations`() {
        assertEquals(180, PROVIDER_EDITOR_EXPAND_DURATION_MILLIS)
        assertEquals(140, PROVIDER_EDITOR_COLLAPSE_DURATION_MILLIS)
    }

    /** 任意 Island 外点击都应清除设置和终端的强调状态。 */
    @Test
    fun `should clear island focus after external press`() {
        assertEquals(WorkspaceIslandFocus.NONE, workspaceFocusAfterExternalPress())
        assertEquals(
            WorkspaceIslandFocus.TERMINAL,
            workspaceFocusAfterPanelClosed(settingsVisible = false, terminalVisible = true),
        )
        assertEquals(
            WorkspaceIslandFocus.SETTINGS,
            workspaceFocusAfterPanelClosed(settingsVisible = true, terminalVisible = false),
        )
    }

    /** Provider 摘要只展示 Endpoint 主机名，并安全处理空值和不完整地址。 */
    @Test
    fun `should derive readable provider endpoint host`() {
        assertEquals("gateway.example.com", providerEndpointHost("https://gateway.example.com/v1"))
        assertEquals("localhost:11434", providerEndpointHost("localhost:11434/v1"))
        assertEquals("未配置地址", providerEndpointHost("  "))
    }

    /** 方向键、Home、End 与 Escape 共用稳定的主题选择状态。 */
    @Test
    fun `should navigate and close liquid glass theme select state`() {
        val state = LiquidGlassSelectState()

        state.open(DesktopThemeMode.DARK.ordinal)
        state.move(1)
        assertEquals(DesktopThemeMode.LIGHT.ordinal, state.focusedIndex)
        state.moveToEdge(last = false)
        assertEquals(DesktopThemeMode.SYSTEM.ordinal, state.focusedIndex)
        state.moveToEdge(last = true)
        assertEquals(DesktopThemeMode.LIGHT.ordinal, state.focusedIndex)
        state.close()
        assertEquals(false, state.expanded)
    }

    /** 菜单应在设置面板底部空间不足时向上展开。 */
    @Test
    fun `should place liquid glass menu above near panel bottom`() {
        val state = LiquidGlassSelectState().apply {
            updatePanelGeometry(Offset(20f, 40f), IntSize(900, 500))
            updateAnchor(Rect(650f, 460f, 820f, 496f))
        }

        val placement = liquidGlassMenuPlacement(state, menuWidthPx = 210f, menuHeightPx = 112f, gapPx = 6f)

        assertEquals(LiquidGlassExpansionDirection.UP, placement.direction)
        assertEquals(302, placement.offset.y)
    }

    /** Liquid Glass 菜单保持参考实现的 210 宽度、24 行高和 6 间距。 */
    @Test
    fun `should preserve reference liquid glass menu dimensions`() {
        assertEquals(210, LIQUID_GLASS_MENU_WIDTH_DP)
        assertEquals(24, LIQUID_GLASS_MENU_ROW_HEIGHT_DP)
        assertEquals(6, LIQUID_GLASS_MENU_GAP_DP)
    }

    /** 减弱动态覆盖必须直接关闭位移与回弹分支。 */
    @Test
    fun `should respect reduced motion override`() {
        val previous = System.getProperty("mulehang.reducedMotion")
        try {
            System.setProperty("mulehang.reducedMotion", "true")
            assertEquals(true, prefersReducedMotion())
        } finally {
            if (previous == null) System.clearProperty("mulehang.reducedMotion")
            else System.setProperty("mulehang.reducedMotion", previous)
        }
    }
}
