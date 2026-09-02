@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.DesktopAppearance
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.iconKey
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.app.platform.TerminalShellCatalog
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.agent.resource.AgentExtensionPackageResource
import com.agent.shared.agent.resource.AgentMcpServerResource
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentSkillResource
import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import com.agent.shared.session.DesktopAppearancePreferences
import com.agent.shared.session.DesktopTerminalPreferences
import java.nio.file.Path
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 设置页可选择的主要分区。 */
internal enum class SettingsSection(val label: String) {
    APPEARANCE("外观"),
    THEME("主题"),
    TOOLS("工具"),
    PROVIDERS("AI 服务"),
    EXTENSIONS("扩展"),
}

/**
 * 返回指定配置范围可见的设置分类；外观和工具只属于用户级全局偏好。
 */
internal fun settingsSectionsFor(layer: ConfigLayer): List<SettingsSection> = when (layer) {
    ConfigLayer.USER -> SettingsSection.entries
    ConfigLayer.PROJECT -> listOf(SettingsSection.THEME, SettingsSection.PROVIDERS, SettingsSection.EXTENSIONS)
    ConfigLayer.ENVIRONMENT -> listOf(SettingsSection.THEME, SettingsSection.PROVIDERS)
}

/**
 * 切换配置范围后保留仍可用的分类，否则安全回退到主题分类。
 */
internal fun settingsSectionAfterScopeChange(
    currentSection: SettingsSection,
    nextLayer: ConfigLayer,
): SettingsSection = currentSection.takeIf { it in settingsSectionsFor(nextLayer) } ?: SettingsSection.THEME

internal const val SETTINGS_COMPACT_LAYOUT_THRESHOLD_DP = 600

/** 设置 Island 在窄侧栏中采用单列信息流，避免导航和说明相互挤占。 */
internal enum class SettingsPanelLayout {
    WIDE,
    COMPACT,
}

/** 根据可用宽度选择设置 Island 的导航方向。 */
internal fun settingsPanelLayout(widthDp: Int): SettingsPanelLayout =
    if (widthDp < SETTINGS_COMPACT_LAYOUT_THRESHOLD_DP) SettingsPanelLayout.COMPACT else SettingsPanelLayout.WIDE

/** 跨抽屉布局重组保留的设置页交互状态。 */
@Stable
internal class SettingsPanelUiState {
    var section by mutableStateOf(SettingsSection.THEME)
    var layer by mutableStateOf(ConfigLayer.USER)
    var document by mutableStateOf(SettingsDocument())
    var search by mutableStateOf("")
    var expandedProviderId by mutableStateOf<String?>(null)
    var feedback by mutableStateOf<String?>(null)
    /** 仅保留当前应用会话的设置变更记录，不写入任何配置文件。 */
    val changeNotifications = SettingsChangeNotifications()
    /** Provider 编辑器输入只在成功保存后汇总为一条通知。 */
    var providerFieldsChangedSinceLastSave by mutableStateOf(false)
    val contentScrollState = ScrollState(initial = 0)
}

/** 参考 IDE 设置页层级的右侧设置 Island。 */
@Composable
internal fun SettingsPanel(
    projectRoot: Path?,
    userHome: Path,
    themeMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    appearance: DesktopAppearance,
    onAppearanceChanged: (DesktopAppearancePreferences) -> Unit,
    onAppearanceChangeFinished: (DesktopAppearancePreferences) -> Unit,
    terminalPreferences: DesktopTerminalPreferences,
    terminalShellCatalog: TerminalShellCatalog,
    onTerminalPreferencesChanged: (DesktopTerminalPreferences) -> Unit,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onSettingsSaved: () -> Unit,
    onReloadResources: () -> Boolean,
    extensionPackages: List<AgentExtensionPackageResource>,
    loadedSkills: List<AgentSkillResource>,
    resourceDiagnostics: List<AgentResourceDiagnostic>,
    mcpServers: List<AgentMcpServerResource>,
    uiState: SettingsPanelUiState,
    modifier: Modifier = Modifier,
) {
    val repository = remember(projectRoot, userHome) {
        DesktopSettingsRepository(
            pathResolver = DesktopPathResolver(userHome, projectRoot ?: userHome),
            environmentOverrides = DesktopEnvironmentOverrides(),
        )
    }
    LaunchedEffect(uiState.layer, repository) {
        uiState.document = repository.loadDocument(uiState.layer)
        uiState.expandedProviderId = null
        uiState.feedback = null
        uiState.providerFieldsChangedSinceLastSave = false
    }
    LaunchedEffect(uiState.section, uiState.layer) {
        uiState.contentScrollState.scrollTo(0)
    }
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 14.dp,
        solidColor = AppWorkspaceBackground,
        borderColor = androidx.compose.ui.graphics.Color.Transparent,
        borderWidth = 0.dp,
        modifier = modifier
            .onPointerEvent(PointerEventType.Press) { onFocus() },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = settingsPanelLayout(maxWidth.value.toInt())
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                SettingsTitleTab(onClose = onClose)
                SettingsSearchField(value = uiState.search, onValueChange = { uiState.search = it })
                SettingsScopeBar(
                    layer = uiState.layer,
                    projectEnabled = projectRoot != null,
                    onLayerChange = { nextLayer ->
                        uiState.layer = nextLayer
                        uiState.section = settingsSectionAfterScopeChange(uiState.section, nextLayer)
                    },
                )
                val visibleSections = settingsSectionsFor(uiState.layer)
                if (layout == SettingsPanelLayout.COMPACT) {
                    Column(modifier = Modifier.fillMaxSize().padding(top = 14.dp)) {
                        SettingsNavigation(
                            section = uiState.section,
                            sections = visibleSections,
                            compact = true,
                            onSectionChange = { section, _ ->
                                uiState.section = section
                            },
                        )
                        SettingsPanelContent(
                            uiState = uiState,
                            repository = repository,
                            themeMode = themeMode,
                            onThemeChanged = onThemeChanged,
                            appearance = appearance,
                            onAppearanceChanged = onAppearanceChanged,
                            onAppearanceChangeFinished = onAppearanceChangeFinished,
                            terminalPreferences = terminalPreferences,
                            terminalShellCatalog = terminalShellCatalog,
                            onTerminalPreferencesChanged = onTerminalPreferencesChanged,
                            onSettingsSaved = onSettingsSaved,
                            onReloadResources = onReloadResources,
                            projectRoot = projectRoot,
                            userHome = userHome,
                            extensionPackages = extensionPackages,
                            loadedSkills = loadedSkills,
                            resourceDiagnostics = resourceDiagnostics,
                            mcpServers = mcpServers,
                            compact = true,
                            scrollState = uiState.contentScrollState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
                        SettingsNavigation(
                            section = uiState.section,
                            sections = visibleSections,
                            onSectionChange = { section, _ ->
                                uiState.section = section
                            },
                        )
                        SettingsPanelContent(
                            uiState = uiState,
                            repository = repository,
                            themeMode = themeMode,
                            onThemeChanged = onThemeChanged,
                            appearance = appearance,
                            onAppearanceChanged = onAppearanceChanged,
                            onAppearanceChangeFinished = onAppearanceChangeFinished,
                            terminalPreferences = terminalPreferences,
                            terminalShellCatalog = terminalShellCatalog,
                            onTerminalPreferencesChanged = onTerminalPreferencesChanged,
                            onSettingsSaved = onSettingsSaved,
                            onReloadResources = onReloadResources,
                            projectRoot = projectRoot,
                            userHome = userHome,
                            extensionPackages = extensionPackages,
                            loadedSkills = loadedSkills,
                            resourceDiagnostics = resourceDiagnostics,
                            mcpServers = mcpServers,
                            compact = false,
                            scrollState = uiState.contentScrollState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 28.dp, end = 18.dp, bottom = 18.dp)
                                .widthIn(max = 760.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 复用宽窄布局共用的设置内容、滚动区和保存动作。 */
@Composable
private fun SettingsPanelContent(
    uiState: SettingsPanelUiState,
    repository: DesktopSettingsRepository,
    themeMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    appearance: DesktopAppearance,
    onAppearanceChanged: (DesktopAppearancePreferences) -> Unit,
    onAppearanceChangeFinished: (DesktopAppearancePreferences) -> Unit,
    terminalPreferences: DesktopTerminalPreferences,
    terminalShellCatalog: TerminalShellCatalog,
    onTerminalPreferencesChanged: (DesktopTerminalPreferences) -> Unit,
    onSettingsSaved: () -> Unit,
    onReloadResources: () -> Boolean,
    projectRoot: Path?,
    userHome: Path,
    extensionPackages: List<AgentExtensionPackageResource>,
    loadedSkills: List<AgentSkillResource>,
    resourceDiagnostics: List<AgentResourceDiagnostic>,
    mcpServers: List<AgentMcpServerResource>,
    compact: Boolean,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = if (shouldShowSettingsContentScrollbar(scrollState.maxValue)) 10.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (uiState.section) {
                    SettingsSection.APPEARANCE -> AppearanceSettingsContent(
                        appearance = appearance,
                        compact = compact,
                        onPreferencesChanged = onAppearanceChanged,
                        onPreferencesChangeFinished = onAppearanceChangeFinished,
                    )

                    SettingsSection.THEME -> ThemeSettingsContent(
                        themeMode = themeMode,
                        compact = compact,
                        onThemeChanged = onThemeChanged,
                    )

                    SettingsSection.TOOLS -> ToolsSettingsContent(
                        preferences = terminalPreferences,
                        shellCatalog = terminalShellCatalog,
                        compact = compact,
                        onPreferencesChanged = onTerminalPreferencesChanged,
                    )

                    SettingsSection.PROVIDERS -> ProviderSettingsContent(
                        document = uiState.document,
                        search = uiState.search,
                        expandedProviderId = uiState.expandedProviderId,
                        onExpandedProviderChange = { uiState.expandedProviderId = it },
                        onDocumentChange = { uiState.document = it },
                        onChangeNotification = { message ->
                            uiState.changeNotifications.record(
                                SettingsChangeNotificationCategory.AI_SERVICES,
                                "${settingsChangeScopeLabel(uiState.layer)}：$message",
                            )
                        },
                        onProviderFieldsChanged = { uiState.providerFieldsChangedSinceLastSave = true },
                    )

                    SettingsSection.EXTENSIONS -> ExtensionSettingsContent(
                        document = uiState.document,
                        layer = uiState.layer,
                        projectRoot = projectRoot,
                        userHome = userHome,
                        extensionPackages = extensionPackages,
                        loadedSkills = loadedSkills,
                        resourceDiagnostics = resourceDiagnostics,
                        mcpServers = mcpServers,
                        onDocumentChange = { uiState.document = it },
                        onChangeNotification = { message ->
                            uiState.changeNotifications.record(
                                SettingsChangeNotificationCategory.EXTENSIONS,
                                "${settingsChangeScopeLabel(uiState.layer)}：$message",
                            )
                        },
                        onReloadResources = onReloadResources,
                    )
                }
            }
            if (shouldShowSettingsContentScrollbar(scrollState.maxValue)) {
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
        if (uiState.section == SettingsSection.PROVIDERS || uiState.section == SettingsSection.EXTENSIONS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsActionButton("保存", emphasized = true) {
                    val validation = validateSettingsDocument(uiState.document)
                    if (validation == null) {
                        runCatching { repository.saveDocument(uiState.layer, uiState.document) }
                            .onSuccess {
                                uiState.feedback = "已保存，后续任务将使用最新配置。"
                                if (uiState.section == SettingsSection.PROVIDERS && uiState.providerFieldsChangedSinceLastSave) {
                                    uiState.changeNotifications.record(
                                        SettingsChangeNotificationCategory.AI_SERVICES,
                                        "${settingsChangeScopeLabel(uiState.layer)}：已保存 AI 服务修改。",
                                    )
                                    uiState.providerFieldsChangedSinceLastSave = false
                                }
                                onSettingsSaved()
                            }
                            .onFailure { uiState.feedback = "保存失败：${it.message ?: "未知错误"}" }
                    } else {
                        uiState.feedback = validation
                    }
                }
                uiState.feedback?.let { Text(it, style = JewelTheme.defaultTextStyle.copy(color = AppMuted)) }
            }
        }
    }
}
/** 设置页左上角使用与终端一致的 IDEA Islands 页签。 */
@Composable
private fun SettingsTitleTab(
    onClose: () -> Unit,
) {
    IslandsTabStrip(
        tabs = listOf(
            IslandsTab(
                label = "设置",
                selected = true,
                iconKey = RightRailGlyph.SETTINGS.iconKey,
                closable = true,
                onClick = {},
                onClose = onClose,
            ),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 仅在设置内容真实溢出时绘制右侧滚动条。 */
internal fun shouldShowSettingsContentScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0

/** 带焦点边框的紧凑设置搜索框。 */
@Composable
private fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    val editorValue = rememberExternalTextFieldValue(value)
    TextField(
        value = editorValue.value,
        onValueChange = { nextValue ->
            editorValue.value = nextValue
            onValueChange(nextValue.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .height(38.dp),
        placeholder = { Text("搜索") },
        leadingIcon = { Icon(AllIconsKeys.Actions.Find, "搜索") },
    )
}
