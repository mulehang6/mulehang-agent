package com.agent.app.chat.component

import com.agent.shared.settings.model.ConfigLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证设置父项展开、子页选择和多父项持久展开策略。 */
class SettingsNavigationStateTest {
    /** 扩展子页必须保持导航顺序，默认进入概览。 */
    @Test
    fun extensionSubsectionsKeepExpectedOrder() {
        assertEquals(
            listOf(
                ExtensionSubsection.OVERVIEW,
                ExtensionSubsection.PACKAGES,
                ExtensionSubsection.SKILLS,
                ExtensionSubsection.PROMPTS,
                ExtensionSubsection.MCP,
                ExtensionSubsection.HOOKS,
                ExtensionSubsection.DIAGNOSTICS,
            ),
            ExtensionSubsection.entries,
        )
        assertEquals(ExtensionSubsection.OVERVIEW, SettingsPanelUiState().extensionSubsection)
    }

    /** 折叠父项点击后展开并跳到第一个子页。 */
    @Test
    fun collapsedParentExpandsAndSelectsFirstSubsection() {
        val state = SettingsPanelUiState()

        state.selectParent(SettingsSection.EXTENSIONS)

        assertEquals(SettingsSection.EXTENSIONS, state.section)
        assertEquals(ExtensionSubsection.OVERVIEW, state.extensionSubsection)
        assertTrue(SettingsSection.EXTENSIONS in state.expandedSections)
    }

    /** 设置页首次打开时父项折叠，主动点击外观后进入概览。 */
    @Test
    fun appearanceParentStartsCollapsedAndOpensOverview() {
        val state = SettingsPanelUiState()

        assertTrue(state.expandedSections.isEmpty())
        assertEquals(AppearanceSubsection.THEME, state.appearanceSubsection)

        state.selectParent(SettingsSection.APPEARANCE)

        assertEquals(SettingsSection.APPEARANCE, state.section)
        assertEquals(AppearanceSubsection.OVERVIEW, state.appearanceSubsection)
        assertTrue(SettingsSection.APPEARANCE in state.expandedSections)
    }

    /** 配置范围没有概览子页时，父项仍跳转到该范围的第一个可用页面。 */
    @Test
    fun parentUsesFirstAvailableSubsectionForCurrentScope() {
        val state = SettingsPanelUiState().also { it.layer = ConfigLayer.PROJECT }

        state.selectParent(SettingsSection.APPEARANCE)

        assertEquals(AppearanceSubsection.THEME, state.appearanceSubsection)
    }

    /** 已展开父项离开后仍保留展开状态，多个父项可以同时展开。 */
    @Test
    fun expandedParentsRemainOpenAcrossSectionChanges() {
        val state = SettingsPanelUiState()

        state.selectParent(SettingsSection.EXTENSIONS)
        state.section = SettingsSection.TOOLS
        state.selectParent(SettingsSection.APPEARANCE)

        assertEquals(SettingsSection.APPEARANCE, state.section)
        assertTrue(SettingsSection.EXTENSIONS in state.expandedSections)
        assertTrue(SettingsSection.APPEARANCE in state.expandedSections)
        assertEquals(AppearanceSubsection.OVERVIEW, state.appearanceSubsection)
    }

    /** 当前已展开父项再次点击时只收起导航，不破坏当前内容页。 */
    @Test
    fun selectedExpandedParentCanCollapseWithoutChangingPage() {
        val state = SettingsPanelUiState()
        state.selectParent(SettingsSection.EXTENSIONS)
        state.selectExtensionSubsection(ExtensionSubsection.HOOKS)

        state.selectParent(SettingsSection.EXTENSIONS)

        assertEquals(SettingsSection.EXTENSIONS, state.section)
        assertEquals(ExtensionSubsection.HOOKS, state.extensionSubsection)
        assertFalse(SettingsSection.EXTENSIONS in state.expandedSections)
    }

    /** 选择任意子页都会回到对应父项并重新展开父项。 */
    @Test
    fun subsectionSelectionRestoresParentExpansion() {
        val state = SettingsPanelUiState()
        state.expandedSections = emptySet()

        state.selectExtensionSubsection(ExtensionSubsection.MCP)

        assertEquals(SettingsSection.EXTENSIONS, state.section)
        assertEquals(ExtensionSubsection.MCP, state.extensionSubsection)
        assertTrue(SettingsSection.EXTENSIONS in state.expandedSections)
    }
}
