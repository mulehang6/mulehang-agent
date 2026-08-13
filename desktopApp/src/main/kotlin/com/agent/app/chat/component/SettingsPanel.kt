@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppText
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.RightRailGlyphIcon
import com.agent.app.design.liquidglass.AdaptiveLiquidGlassSurface
import com.agent.app.design.liquidglass.LiquidGlassSurfaceRole
import com.agent.app.design.liquidglass.LocalLiquidGlassBackdrop
import com.agent.app.design.liquidglass.rememberLiquidGlassBackdropState
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import java.nio.file.Path

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
    liquidGlassEnabled: Boolean,
    focused: Boolean,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    onLiquidGlassEnabledChanged: (Boolean) -> Unit,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onSettingsSaved: () -> Unit,
    uiState: SettingsPanelUiState,
    modifier: Modifier = Modifier,
) {
    val fallbackBackdrop = rememberLiquidGlassBackdropState()
    val liquidGlassBackdrop = LocalLiquidGlassBackdrop.current ?: fallbackBackdrop
    val themeSelectState = remember { LiquidGlassSelectState() }
    val palette = LocalDesktopPalette.current
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
    LaunchedEffect(uiState.section, themeMode, themeSelectState.expanded, palette.isDark) {
        withFrameNanos { }
        liquidGlassBackdrop.refresh()
    }
    LaunchedEffect(uiState.section) {
        if (uiState.section != SettingsSection.THEME) themeSelectState.close()
    }

    AdaptiveLiquidGlassSurface(
        role = LiquidGlassSurfaceRole.PANEL,
        radius = 12.dp,
        solidColor = AppWorkspaceBackground,
        borderColor = AppLine,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                themeSelectState.updatePanelGeometry(coordinates.positionInRoot(), coordinates.size)
            }
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
                                    liquidGlassEnabled = liquidGlassEnabled,
                                    selectState = themeSelectState,
                                    onThemeChanged = onThemeChanged,
                                    onLiquidGlassEnabledChanged = onLiquidGlassEnabledChanged,
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
                            uiState.feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = AppMuted)) }
                        }
                    }
                }
            }
        }
        if (uiState.section == SettingsSection.THEME) {
            LiquidGlassThemeMenuOverlay(
                state = themeSelectState,
                backdropState = liquidGlassBackdrop,
                selectedMode = themeMode,
                onThemeChanged = onThemeChanged,
            )
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
        AdaptiveLiquidGlassSurface(
            role = LiquidGlassSurfaceRole.CHROME,
            radius = 7.dp,
            solidColor = if (focused) AppSelectedBackground else AppPanelBackground,
            borderColor = if (focused) AppAccent else AppLine,
            modifier = Modifier.height(30.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 9.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RightRailGlyphIcon(
                    glyph = RightRailGlyph.SETTINGS,
                    tint = if (focused) AppText else AppMuted,
                    glyphSize = 19.dp,
                )
                Text("设置", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge.copy(color = AppText))
                Text(
                    "×",
                    modifier = Modifier.padding(start = 7.dp, top = 2.dp, bottom = 2.dp).clickable(onClick = onClose),
                    style = MaterialTheme.typography.labelLarge.copy(color = AppMuted),
                )
            }
        }
    }
}

/** 带焦点边框的紧凑设置搜索框。 */
@Composable
private fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        AdaptiveLiquidGlassSurface(
            role = LiquidGlassSurfaceRole.INPUT,
            radius = 6.dp,
            solidColor = AppPanelBackground,
            borderColor = if (focused) AppAccent else AppLine,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", style = MaterialTheme.typography.titleMedium.copy(color = AppMuted))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).padding(start = 8.dp).onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    cursorBrush = SolidColor(AppAccent),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppText),
                    decorationBox = { inner ->
                        if (value.isBlank()) Text("搜索", style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted))
                        inner()
                    },
                )
            }
        }
    }
}
