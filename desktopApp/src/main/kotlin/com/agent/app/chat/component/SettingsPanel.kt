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
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.iconKey
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import java.nio.file.Path
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 设置页可选择的主要分区。 */
internal enum class SettingsSection(val label: String) {
    THEME("主题"),
    PROVIDERS("AI 服务"),
}

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
    val contentScrollState = ScrollState(initial = 0)
}

/** 参考 IDE 设置页层级的右侧设置 Island。 */
@Composable
internal fun SettingsPanel(
    projectRoot: Path?,
    userHome: Path,
    themeMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onSettingsSaved: () -> Unit,
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
                    onLayerChange = { uiState.layer = it },
                )
                if (layout == SettingsPanelLayout.COMPACT) {
                    Column(modifier = Modifier.fillMaxSize().padding(top = 14.dp)) {
                        SettingsNavigation(
                            section = uiState.section,
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
                            onSettingsSaved = onSettingsSaved,
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
                            onSectionChange = { section, _ ->
                                uiState.section = section
                            },
                        )
                        SettingsPanelContent(
                            uiState = uiState,
                            repository = repository,
                            themeMode = themeMode,
                            onThemeChanged = onThemeChanged,
                            onSettingsSaved = onSettingsSaved,
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
    onSettingsSaved: () -> Unit,
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
                    SettingsSection.THEME -> ThemeSettingsContent(
                        themeMode = themeMode,
                        compact = compact,
                        onThemeChanged = onThemeChanged,
                    )

                    SettingsSection.PROVIDERS -> ProviderSettingsContent(
                        document = uiState.document,
                        search = uiState.search,
                        expandedProviderId = uiState.expandedProviderId,
                        onExpandedProviderChange = { uiState.expandedProviderId = it },
                        onDocumentChange = { uiState.document = it },
                    )
                }
            }
            if (shouldShowSettingsContentScrollbar(scrollState.maxValue)) {
                CompositionLocalProvider(
                    LocalScrollbarStyle provides LocalScrollbarStyle.current,
                ) {
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
            }
        }
        if (uiState.section == SettingsSection.PROVIDERS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsActionButton("保存配置", emphasized = true) {
                    val validation = validateSettingsDocument(uiState.document)
                    if (validation == null) {
                        runCatching { repository.saveDocument(uiState.layer, uiState.document) }
                            .onSuccess {
                                uiState.feedback = "已保存，后续任务将使用最新配置。"
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
