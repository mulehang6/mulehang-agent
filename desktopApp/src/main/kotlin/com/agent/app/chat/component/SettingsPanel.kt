@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.agent.app.design.JewelDialog
import com.agent.app.platform.TerminalShellCatalog
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.agent.resource.AgentExtensionPackageResource
import com.agent.shared.agent.resource.AgentMcpServerResource
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentSkillResource
import com.agent.shared.agent.resource.McpServerConnectionStatus
import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import com.agent.shared.session.DesktopAppearancePreferences
import com.agent.shared.session.DesktopTerminalPreferences
import java.nio.file.Path
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 设置页可选择的主要分区。 */
internal enum class SettingsSection(val label: String) {
    APPEARANCE("外观"),
    TOOLS("工具"),
    PROVIDERS("AI 服务"),
    EXTENSIONS("扩展"),
}

/** 外观分类下的二级设置；外观编辑器只在用户级范围开放。 */
internal enum class AppearanceSubsection(val label: String) {
    OVERVIEW("概览"),
    THEME("主题"),
}

/**
 * 返回指定配置范围可见的设置分类；AI 服务和运行参数只属于用户级全局配置。
 */
internal fun settingsSectionsFor(layer: ConfigLayer): List<SettingsSection> = when (layer) {
    ConfigLayer.USER -> SettingsSection.entries
    ConfigLayer.PROJECT -> listOf(SettingsSection.APPEARANCE, SettingsSection.EXTENSIONS)
    ConfigLayer.ENVIRONMENT -> listOf(SettingsSection.APPEARANCE)
}

/** 根据配置范围返回外观下可编辑的二级设置。 */
internal fun appearanceSubsectionsFor(layer: ConfigLayer): List<AppearanceSubsection> = when (layer) {
    ConfigLayer.USER -> AppearanceSubsection.entries
    ConfigLayer.PROJECT, ConfigLayer.ENVIRONMENT -> listOf(AppearanceSubsection.THEME)
}

/**
 * 切换配置范围后保留仍可用的分类，否则安全回退到外观分类。
 */
internal fun settingsSectionAfterScopeChange(
    currentSection: SettingsSection,
    nextLayer: ConfigLayer,
): SettingsSection = currentSection.takeIf { it in settingsSectionsFor(nextLayer) } ?: SettingsSection.APPEARANCE

/** 切换配置范围后保留外观二级项，否则回退到该范围的首个可用项。 */
internal fun appearanceSubsectionAfterScopeChange(
    currentSubsection: AppearanceSubsection,
    nextLayer: ConfigLayer,
): AppearanceSubsection = currentSubsection.takeIf { it in appearanceSubsectionsFor(nextLayer) }
    ?: appearanceSubsectionsFor(nextLayer).first()

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
    var section by mutableStateOf(SettingsSection.APPEARANCE)
    /** 保留原先打开设置页时直接进入主题的行为，同时让主题归属于外观。 */
    var appearanceSubsection by mutableStateOf(AppearanceSubsection.THEME)
    /** 当前扩展页；切换扩展子页不会重建 MCP JSON 编辑状态。 */
    var extensionSubsection by mutableStateOf(ExtensionSubsection.OVERVIEW)
    /** 宽屏侧栏允许多个父项同时展开，离开父项后不自动收起；首次打开由用户主动展开。 */
    var expandedSections by mutableStateOf(emptySet<SettingsSection>())
    var layer by mutableStateOf(ConfigLayer.USER)
    var document by mutableStateOf(SettingsDocument())
    /** MCP 和 Hooks 保存通知只与最近一次成功载入或保存的文档比较。 */
    var lastSavedDocument by mutableStateOf(SettingsDocument())
    var search by mutableStateOf("")
    var expandedProviderId by mutableStateOf<String?>(null)
    var feedback by mutableStateOf<String?>(null)
    /** 仅保留当前应用会话的设置变更记录，不写入任何配置文件。 */
    val changeNotifications = SettingsChangeNotifications()
    /** Provider 编辑器输入只在成功保存后汇总为一条通知。 */
    var providerFieldsChangedSinceLastSave by mutableStateOf(false)
    /** 未写回文档的表单错误按稳定字段键保存，防止 JSON 半成品被误保存。 */
    val settingsValidationErrors = mutableStateMapOf<String, String>()
    /** 设置分类切换时保留、配置层级切换时重置的 MCP JSON 临时输入。 */
    val mcpJsonEditorState = McpJsonEditorState()
    /** 任一资源区域保存成功后持续保留，直到运行时重载成功。 */
    var resourceReloadPending by mutableStateOf(false)
    /** MCP 配置与当前连接代不一致时，列表必须隐藏旧工具数量。 */
    var mcpReloadPending by mutableStateOf(false)
    /** 关闭含草稿的设置页前显示的确认状态。 */
    var discardConfirmationVisible by mutableStateOf(false)
    val contentScrollState = ScrollState(initial = 0)

    /** 点击父项时展开并进入第一个子页；再次点击当前已展开父项只收起导航。 */
    fun selectParent(nextSection: SettingsSection) {
        if (nextSection in expandedSections && section == nextSection) {
            expandedSections = expandedSections - nextSection
            return
        }
        expandedSections = expandedSections + nextSection
        section = nextSection
        when (nextSection) {
            SettingsSection.APPEARANCE -> appearanceSubsection = appearanceSubsectionsFor(layer).first()
            SettingsSection.EXTENSIONS -> extensionSubsection = ExtensionSubsection.OVERVIEW
            SettingsSection.TOOLS,
            SettingsSection.PROVIDERS,
                -> Unit
        }
    }

    /** 选择外观子页时同步保持外观父项展开。 */
    fun selectAppearanceSubsection(subsection: AppearanceSubsection) {
        section = SettingsSection.APPEARANCE
        expandedSections = expandedSections + SettingsSection.APPEARANCE
        appearanceSubsection = subsection
    }

    /** 选择扩展子页时同步保持扩展父项展开。 */
    fun selectExtensionSubsection(subsection: ExtensionSubsection) {
        section = SettingsSection.EXTENSIONS
        expandedSections = expandedSections + SettingsSection.EXTENSIONS
        extensionSubsection = subsection
    }
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
    onReloadResources: suspend () -> Boolean,
    canReloadResources: Boolean,
    extensionPackages: List<AgentExtensionPackageResource>,
    loadedSkills: List<AgentSkillResource>,
    resourceDiagnostics: List<AgentResourceDiagnostic>,
    mcpServers: List<AgentMcpServerResource>,
    mcpConnectionStatuses: List<McpServerConnectionStatus>,
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
        val loadedDocument = repository.loadDocument(uiState.layer)
        uiState.document = loadedDocument
        uiState.lastSavedDocument = loadedDocument
        uiState.expandedProviderId = null
        uiState.feedback = null
        uiState.providerFieldsChangedSinceLastSave = false
        uiState.settingsValidationErrors.clear()
        uiState.mcpJsonEditorState.reset()
    }
    LaunchedEffect(uiState.section, uiState.layer, uiState.appearanceSubsection, uiState.extensionSubsection) {
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
                SettingsTitleTab(
                    onClose = {
                        if (hasUnsavedSettingsChanges(uiState)) uiState.discardConfirmationVisible = true else onClose()
                    },
                )
                SettingsSearchField(value = uiState.search, onValueChange = { uiState.search = it })
                SettingsScopeBar(
                    layer = uiState.layer,
                    projectEnabled = projectRoot != null,
                    onLayerChange = { nextLayer ->
                        uiState.layer = nextLayer
                        uiState.section = settingsSectionAfterScopeChange(uiState.section, nextLayer)
                        uiState.appearanceSubsection = appearanceSubsectionAfterScopeChange(
                            uiState.appearanceSubsection,
                            nextLayer,
                        )
                    },
                )
                val visibleSections = settingsSectionsFor(uiState.layer)
                ResponsiveSettingsLayout(
                    compact = layout == SettingsPanelLayout.COMPACT,
                    section = uiState.section,
                    sections = visibleSections,
                    expandedSections = uiState.expandedSections,
                    appearanceSubsections = appearanceSubsectionsFor(uiState.layer),
                    appearanceSubsection = uiState.appearanceSubsection,
                    extensionSubsections = ExtensionSubsection.entries,
                    extensionSubsection = uiState.extensionSubsection,
                    onSectionChange = { section -> uiState.section = section },
                    onParentClick = uiState::selectParent,
                    onAppearanceSubsectionChange = uiState::selectAppearanceSubsection,
                    onExtensionSubsectionChange = uiState::selectExtensionSubsection,
                ) { compact ->
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
                        canReloadResources = canReloadResources,
                        projectRoot = projectRoot,
                        userHome = userHome,
                        extensionPackages = extensionPackages,
                        loadedSkills = loadedSkills,
                        resourceDiagnostics = resourceDiagnostics,
                        mcpServers = mcpServers,
                        mcpConnectionStatuses = mcpConnectionStatuses,
                        compact = compact,
                        scrollState = uiState.contentScrollState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    if (uiState.discardConfirmationVisible) {
        JewelDialog(
            title = "放弃未保存的修改？",
            confirmLabel = "放弃修改",
            dismissLabel = "继续编辑",
            onConfirm = {
                uiState.document = uiState.lastSavedDocument
                uiState.settingsValidationErrors.clear()
                uiState.mcpJsonEditorState.reset()
                uiState.discardConfirmationVisible = false
                onClose()
            },
            onDismiss = { uiState.discardConfirmationVisible = false },
        ) {
            Text("MCP、Hooks、扩展或 AI 服务中仍有未保存内容。")
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
    onReloadResources: suspend () -> Boolean,
    canReloadResources: Boolean,
    projectRoot: Path?,
    userHome: Path,
    extensionPackages: List<AgentExtensionPackageResource>,
    loadedSkills: List<AgentSkillResource>,
    resourceDiagnostics: List<AgentResourceDiagnostic>,
    mcpServers: List<AgentMcpServerResource>,
    mcpConnectionStatuses: List<McpServerConnectionStatus>,
    compact: Boolean,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val triggerResourceReload: () -> Unit = {
        scope.launch {
            if (onReloadResources()) {
                uiState.resourceReloadPending = false
                uiState.mcpReloadPending = false
                uiState.feedback = "资源已重新加载；当前运行不会改变。"
            } else {
                uiState.feedback = "资源重新加载失败，当前运行时保持不变。"
            }
        }
    }
    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = if (shouldShowSettingsContentScrollbar(scrollState.maxValue)) 10.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CompositionLocalProvider(LocalSettingsCompact provides compact) {
                when (uiState.section) {
                    SettingsSection.APPEARANCE -> when (uiState.appearanceSubsection) {
                        AppearanceSubsection.OVERVIEW -> AppearanceSettingsContent(
                            appearance = appearance,
                            compact = compact,
                            onPreferencesChanged = onAppearanceChanged,
                            onPreferencesChangeFinished = onAppearanceChangeFinished,
                        )

                        AppearanceSubsection.THEME -> ThemeSettingsContent(
                            themeMode = themeMode,
                            compact = compact,
                            onThemeChanged = onThemeChanged,
                        )
                    }

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
                        onValidationErrorChange = { key, error ->
                            if (error == null) uiState.settingsValidationErrors.remove(key)
                            else uiState.settingsValidationErrors[key] = error
                        },
                        onValidationErrorsRenamed = { oldPrefix, newPrefix ->
                            renameSettingsValidationErrors(uiState.settingsValidationErrors, oldPrefix, newPrefix)
                        },
                        onValidationErrorsCleared = { prefix ->
                            uiState.settingsValidationErrors.keys
                                .filter { key -> key == prefix || key.startsWith("$prefix:") }
                                .toList()
                                .forEach(uiState.settingsValidationErrors::remove)
                        },
                    )

                    SettingsSection.EXTENSIONS -> ExtensionSettingsContent(
                        subsection = uiState.extensionSubsection,
                        document = uiState.document,
                        layer = uiState.layer,
                        projectRoot = projectRoot,
                        userHome = userHome,
                        extensionPackages = extensionPackages,
                        loadedSkills = loadedSkills,
                        resourceDiagnostics = resourceDiagnostics,
                        mcpServers = mcpServers,
                        savedMcpServers = uiState.lastSavedDocument.agentResources.mcpServers,
                        savedHooks = uiState.lastSavedDocument.hooks,
                        mcpConnectionStatuses = mcpConnectionStatuses,
                        mcpJsonEditorState = uiState.mcpJsonEditorState,
                        mcpReloadPending = uiState.mcpReloadPending,
                        mcpRetryEnabled = canReloadResources,
                        onRetryMcp = triggerResourceReload,
                        onSaveMcp = {
                            persistSettingsArea(uiState, repository, SettingsSaveArea.MCP, onSettingsSaved)
                        },
                        onSaveHooks = {
                            persistSettingsArea(uiState, repository, SettingsSaveArea.HOOKS, onSettingsSaved)
                        },
                        onSaveExtensions = {
                            persistSettingsArea(uiState, repository, SettingsSaveArea.EXTENSIONS, onSettingsSaved)
                        },
                        onDocumentChange = { uiState.document = it },
                        onChangeNotification = { message ->
                            uiState.changeNotifications.record(
                                SettingsChangeNotificationCategory.EXTENSIONS,
                                "${settingsChangeScopeLabel(uiState.layer)}：$message",
                            )
                        },
                        onValidationErrorChange = { key, error ->
                            if (error == null) uiState.settingsValidationErrors.remove(key)
                            else uiState.settingsValidationErrors[key] = error
                        },
                        onResourceFilesChanged = { uiState.resourceReloadPending = true },
                    )
                }
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
        if (uiState.section == SettingsSection.EXTENSIONS) {
            uiState.feedback?.let { feedback ->
                Text(
                    feedback,
                    modifier = Modifier.padding(top = 12.dp),
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
            }
        }
        if (uiState.section == SettingsSection.EXTENSIONS && uiState.resourceReloadPending) {
            ResourceReloadBanner(
                reloadEnabled = canReloadResources,
                onReload = triggerResourceReload,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (uiState.section == SettingsSection.PROVIDERS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsActionButton("保存", emphasized = true) {
                    persistSettingsArea(uiState, repository, SettingsSaveArea.PROVIDERS, onSettingsSaved)
                }
                uiState.feedback?.let { Text(it, style = JewelTheme.defaultTextStyle.copy(color = AppMuted)) }
            }
        }
    }
}
