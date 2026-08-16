@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import com.agent.app.design.AppAccent
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppText
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.PANEL_TAB_ICON_SIZE
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

/** 跨抽屉布局重组保留的设置页交互状态。 */
@Stable
internal class SettingsPanelUiState {
    var section by mutableStateOf(SettingsSection.THEME)
    var layer by mutableStateOf(ConfigLayer.USER)
    var document by mutableStateOf(SettingsDocument())
    var search by mutableStateOf("")
    var expandedProviderId by mutableStateOf<String?>(null)
    var feedback by mutableStateOf<String?>(null)
    var sectionSpatialMotion by mutableStateOf(true)
}

/** 参考 IDE 设置页层级的右侧设置 Island。 */
@Composable
internal fun SettingsPanel(
    projectRoot: Path?,
    userHome: Path,
    themeMode: DesktopThemeMode,
    focused: Boolean,
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
        radius = 12.dp,
        solidColor = AppWorkspaceBackground,
        borderColor = AppLine,
        modifier = modifier
            .onPointerEvent(PointerEventType.Press) { onFocus() },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            SettingsTitleTab(focused = focused, onClose = onClose)
            SettingsSearchField(value = uiState.search, onValueChange = { uiState.search = it })
            SettingsScopeBar(
                layer = uiState.layer,
                projectEnabled = projectRoot != null,
                onLayerChange = { uiState.layer = it },
            )
            Row(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
                SettingsNavigation(
                    section = uiState.section,
                    onSectionChange = { section, spatialMotion ->
                        uiState.sectionSpatialMotion = spatialMotion
                        uiState.section = section
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 28.dp, end = 18.dp, bottom = 18.dp)
                        .widthIn(max = 760.dp),
                ) {
                    SettingsAnimatedContent(
                        target = SettingsContentTarget(uiState.layer, uiState.section, uiState.sectionSpatialMotion),
                        modifier = Modifier.weight(1f),
                    ) { section ->
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            when (section) {
                                SettingsSection.THEME -> ThemeSettingsContent(
                                    themeMode = themeMode,
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
        }
    }
}
/** 设置页左上角的紧凑标签和关闭按钮。 */
@Composable
private fun SettingsTitleTab(
    focused: Boolean,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        JewelSurface(
            role = JewelSurfaceRole.CHROME,
            radius = 7.dp,
            solidColor = if (focused) AppSelectedBackground else AppPanelBackground,
            borderColor = if (focused) AppAccent else AppLine,
            modifier = Modifier.height(30.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 9.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    key = RightRailGlyph.SETTINGS.iconKey,
                    contentDescription = "设置",
                    modifier = Modifier.size(PANEL_TAB_ICON_SIZE),
                )
                Text("设置", modifier = Modifier.padding(start = 6.dp), style = JewelTheme.defaultTextStyle.copy(color = AppText))
                Text(
                    "×",
                    modifier = Modifier.padding(start = 7.dp, top = 2.dp, bottom = 2.dp).clickable(onClick = onClose),
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
            }
        }
    }
}

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
