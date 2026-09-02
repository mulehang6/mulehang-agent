package com.agent.app.chat.component

import com.agent.app.design.PopupMenuHoverBackground
import com.agent.app.design.PopupMenuSelectedBackground
import com.agent.app.design.AppAccent
import com.agent.shared.agent.resource.AgentResourceOrigin
import com.agent.shared.agent.resource.AgentSkillResource
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import androidx.compose.ui.graphics.Color
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

/** 设置 Island 的交互色与稳定状态回归测试。 */
class SettingsPanelInteractionTest {

    /** 外观和工具只属于用户级全局设置；扩展可按用户或项目范围管理，环境层保持只读配置范围。 */
    @Test
    fun `should show appearance only for global settings scope`() {
        assertEquals(
            listOf(
                SettingsSection.APPEARANCE,
                SettingsSection.THEME,
                SettingsSection.TOOLS,
                SettingsSection.PROVIDERS,
                SettingsSection.EXTENSIONS,
            ),
            settingsSectionsFor(ConfigLayer.USER),
        )
        assertEquals(
            listOf(SettingsSection.THEME, SettingsSection.PROVIDERS, SettingsSection.EXTENSIONS),
            settingsSectionsFor(ConfigLayer.PROJECT),
        )
        assertEquals(
            SettingsSection.THEME,
            settingsSectionAfterScopeChange(SettingsSection.APPEARANCE, ConfigLayer.ENVIRONMENT),
        )
        assertEquals(
            SettingsSection.THEME,
            settingsSectionAfterScopeChange(SettingsSection.TOOLS, ConfigLayer.PROJECT),
        )
    }

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

    /** 强调操作在悬停和按下时提供可见反馈，普通/危险操作不改变原有缩放语义。 */
    @Test
    fun `should provide hover and press feedback for emphasized settings actions`() {
        assertEquals(AppAccent, emphasizedSettingsActionBackground(hovered = false, pressed = false))
        assertNotEquals(AppAccent, emphasizedSettingsActionBackground(hovered = true, pressed = false))
        assertNotEquals(AppAccent, emphasizedSettingsActionBackground(hovered = true, pressed = true))
        assertEquals(0.97f, settingsActionScale(emphasized = true, pressed = true))
        assertEquals(1f, settingsActionScale(emphasized = true, pressed = false))
        assertEquals(1f, settingsActionScale(emphasized = false, pressed = true))
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

    /** 窄侧栏改用单列信息流，使主题说明和下拉不再相互挤压。 */
    @Test
    fun `should switch settings content to compact layout below threshold`() {
        assertEquals(600, SETTINGS_COMPACT_LAYOUT_THRESHOLD_DP)
        assertEquals(96, SETTINGS_NAVIGATION_WIDE_WIDTH_DP)
        assertEquals(SettingsPanelLayout.COMPACT, settingsPanelLayout(599))
        assertEquals(SettingsPanelLayout.WIDE, settingsPanelLayout(600))
    }

    /** 设置内容只在真实溢出时占用滚动条轨道，协议下拉保留所有 Provider 类型。 */
    @Test
    fun `should use overflow scrollbar and full provider protocol list`() {
        assertEquals(false, shouldShowSettingsContentScrollbar(0))
        assertEquals(true, shouldShowSettingsContentScrollbar(1))
        assertEquals("openai-chat-completions", providerTypeLabel(ProviderType.OPENAI_CHAT_COMPLETIONS))
        assertEquals(ProviderType.entries.size, ProviderType.entries.map(::providerTypeLabel).distinct().size)
    }

    /** Settings、终端与范围页签共享 IDEA Islands 的浅深色选中填充和描边。 */
    @Test
    fun `should use islands selection colors for all settings tabs`() {
        assertEquals(Color(0xFFE3EBFE), islandsTabSelectedFill(isDark = false))
        assertEquals(Color(0xFFA7C5FF), islandsTabSelectedBorder(isDark = false))
        assertEquals(Color(0xFF233558), islandsTabSelectedFill(isDark = true))
        assertEquals(Color(0xFF2E4D89), islandsTabSelectedBorder(isDark = true))
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

    /** 自动加载区域只显示当前快照中由默认用户级 `.agents/skills` 根发现的 Skill。 */
    @Test
    fun `should show loaded user agents skills only`() {
        val userHome = Files.createTempDirectory("mulehang-settings-home")
        val agentsSkill = AgentSkillResource(
            name = "review",
            description = "review code",
            location = userHome.resolve(".agents/skills/review/SKILL.md"),
            content = "review code",
            disableModelInvocation = false,
            origin = AgentResourceOrigin.USER_AUTO_DISCOVERY,
        )
        val mulehangSkill = agentsSkill.copy(
            name = "mulehang",
            location = userHome.resolve(".mulehang/skills/mulehang/SKILL.md"),
        )
        val configuredSkill = agentsSkill.copy(
            name = "configured",
            origin = AgentResourceOrigin.USER_CONFIGURATION,
        )

        assertEquals(
            listOf(agentsSkill),
            autoLoadedUserAgentsSkills(
                loadedSkills = listOf(agentsSkill, mulehangSkill, configuredSkill),
                userHome = userHome,
            ),
        )
    }
}
