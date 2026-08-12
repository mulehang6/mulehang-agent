@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.DesktopAccentColor
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.ProviderCardBackground
import com.agent.app.design.ProviderCardHoverBackground
import com.agent.app.design.selectMenuItemBackground
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import java.nio.file.Path

internal const val PROVIDER_EDITOR_EXPAND_DURATION_MILLIS = 180
internal const val PROVIDER_EDITOR_COLLAPSE_DURATION_MILLIS = 140
private val ProviderEditorMotionEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

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
}

/** 参考 IDE 设置页层级的右侧设置 Island。 */
@Composable
internal fun SettingsPanel(
    projectRoot: Path?,
    userHome: Path,
    themeMode: DesktopThemeMode,
    accentColor: DesktopAccentColor,
    focused: Boolean,
    onThemeChanged: (DesktopThemeMode, DesktopAccentColor) -> Unit,
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppWorkspaceBackground)
            .border(1.dp, AppLine, RoundedCornerShape(12.dp))
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
                SettingsNavigation(section = uiState.section, onSectionChange = { uiState.section = it })
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, end = 18.dp, bottom = 18.dp)
                        .widthIn(max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (uiState.section) {
                        SettingsSection.THEME -> ThemeSettingsContent(
                            themeMode = themeMode,
                            accentColor = accentColor,
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
                    if (uiState.section == SettingsSection.PROVIDERS) {
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
                    }
                    uiState.feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = AppMuted)) }
                }
            }
        }
    }
}

/** 设置页左上角的紧凑标签和关闭按钮。 */
@Composable
private fun SettingsTitleTab(focused: Boolean, onClose: () -> Unit) {
    Row(modifier = Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (focused) AppHoverBackground else AppChipBackground)
                .border(1.dp, if (focused) AppAccent else AppLine, RoundedCornerShape(6.dp))
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◉", style = MaterialTheme.typography.labelMedium.copy(color = AppMuted))
            Text("设置", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge.copy(color = AppText))
            Text(
                "×",
                modifier = Modifier.padding(start = 7.dp, top = 2.dp, bottom = 2.dp).clickable(onClick = onClose),
                style = MaterialTheme.typography.labelLarge.copy(color = AppMuted),
            )
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
            .clip(RoundedCornerShape(6.dp))
            .background(AppPanelBackground)
            .border(1.dp, if (focused) AppAccent else AppLine, RoundedCornerShape(6.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", style = MaterialTheme.typography.titleMedium.copy(color = AppMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .onFocusChanged { focused = it.isFocused },
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

/** 全局与项目配置层级标签及分隔线。 */
@Composable
private fun SettingsScopeBar(layer: ConfigLayer, projectEnabled: Boolean, onLayerChange: (ConfigLayer) -> Unit) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsChoiceChip("全局", layer == ConfigLayer.USER) { onLayerChange(ConfigLayer.USER) }
            SettingsChoiceChip("当前项目", layer == ConfigLayer.PROJECT, projectEnabled) { onLayerChange(ConfigLayer.PROJECT) }
            if (!projectEnabled) Text("请选择工作区", style = MaterialTheme.typography.bodySmall.copy(color = AppMuted))
        }
        Spacer(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(AppLine))
    }
}

/** 左侧设置导航。 */
@Composable
private fun SettingsNavigation(section: SettingsSection, onSectionChange: (SettingsSection) -> Unit) {
    Column(modifier = Modifier.width(166.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SettingsSection.entries.forEach { entry ->
            val selected = entry == section
            var hovered by remember(entry) { mutableStateOf(false) }
            Text(
                text = entry.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(settingsItemBackground(selected = selected, hovered = hovered))
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .clickable { onSectionChange(entry) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodyMedium.copy(color = AppText),
            )
        }
    }
}

/** 与参考图一致的主题设置组。 */
@Composable
private fun ThemeSettingsContent(
    themeMode: DesktopThemeMode,
    accentColor: DesktopAccentColor,
    onThemeChanged: (DesktopThemeMode, DesktopAccentColor) -> Unit,
) {
    Text("主题", style = MaterialTheme.typography.headlineSmall.copy(color = AppText))
    SettingsGroup(
        background = providerCardBackground(),
        hoverBackground = ProviderCardHoverBackground,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsChoiceChip("默认主题", true) {}
            SettingsChoiceChip("自定义主题", false) {}
        }
        SettingsRow("主题模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DesktopThemeMode.entries.forEach { mode ->
                    SettingsChoiceChip(mode.label, mode == themeMode) { onThemeChanged(mode, accentColor) }
                }
            }
        }
        SettingsRow("强调色") {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DesktopAccentColor.entries.forEach { accent ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.color)
                            .border(2.dp, if (accent == accentColor) AppText else accent.color, RoundedCornerShape(6.dp))
                            .clickable { onThemeChanged(themeMode, accent) },
                    )
                }
            }
        }
        Text("主题偏好会立即应用到后续界面。", style = MaterialTheme.typography.bodySmall.copy(color = AppMuted))
    }
}

/** AI Provider 摘要列表和按需展开的编辑器。 */
@Composable
private fun ProviderSettingsContent(
    document: SettingsDocument,
    search: String,
    expandedProviderId: String?,
    onExpandedProviderChange: (String?) -> Unit,
    onDocumentChange: (SettingsDocument) -> Unit,
) {
    Text("AI 服务", style = MaterialTheme.typography.headlineSmall.copy(color = AppText))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsActionButton("新增服务") {
            val id = "provider-${document.providers.size + 1}"
            onDocumentChange(document.copy(providers = document.providers + newProvider(id)))
            onExpandedProviderChange(id)
        }
    }
    document.providers.filter { provider ->
        search.isBlank() || provider.id.contains(search, true) || provider.label.orEmpty().contains(search, true)
    }.forEach { provider ->
        val expanded = provider.id == expandedProviderId
        var hovered by remember(provider.id) { mutableStateOf(false) }
        SettingsGroup(background = providerCardBackground()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(providerSummaryBackground(expanded = expanded, hovered = hovered))
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .clickable { onExpandedProviderChange(if (expanded) null else provider.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.label ?: provider.id, style = MaterialTheme.typography.titleSmall.copy(color = AppText))
                    Text("${provider.providerType.name.lowercase().replace('_', '-')} · ${provider.models.size} 个模型", style = MaterialTheme.typography.bodySmall.copy(color = AppMuted))
                }
                ProviderDisclosureArrow(
                    expanded = expanded,
                    providerLabel = provider.label ?: provider.id,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(PROVIDER_EDITOR_EXPAND_DURATION_MILLIS, easing = ProviderEditorMotionEasing),
                ) + fadeIn(
                    animationSpec = tween(PROVIDER_EDITOR_EXPAND_DURATION_MILLIS, easing = ProviderEditorMotionEasing),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(PROVIDER_EDITOR_COLLAPSE_DURATION_MILLIS, easing = ProviderEditorMotionEasing),
                ) + fadeOut(
                    animationSpec = tween(PROVIDER_EDITOR_COLLAPSE_DURATION_MILLIS, easing = ProviderEditorMotionEasing),
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderEditor(
                        provider = provider,
                        onChange = { updated -> onDocumentChange(document.copy(providers = document.providers.map { if (it.id == provider.id) updated else it })) },
                        onDelete = {
                            onDocumentChange(document.copy(providers = document.providers - provider))
                            onExpandedProviderChange(null)
                        },
                    )
                }
            }
        }
    }
    if (document.providers.isEmpty()) Text("尚未配置服务。", style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted))
}

/** 返回 Provider 摘要箭头的无障碍说明，不在可视界面重复显示操作文字。 */
internal fun providerDisclosureDescription(providerLabel: String, expanded: Boolean): String =
    if (expanded) "收起服务 $providerLabel" else "展开服务 $providerLabel"

/** 返回 Provider 摘要箭头的朝向：展开朝下，收起朝右。 */
internal fun providerDisclosureRotationDegrees(expanded: Boolean): Float = if (expanded) 0f else -90f

/** 绘制 Provider 摘要行末端的紧凑 disclosure 箭头。 */
@Composable
private fun ProviderDisclosureArrow(expanded: Boolean, providerLabel: String) {
    val rotationDegrees by animateFloatAsState(
        targetValue = providerDisclosureRotationDegrees(expanded),
        animationSpec = tween(160, easing = ProviderEditorMotionEasing),
        label = "provider-disclosure-arrow",
    )
    Canvas(
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer { rotationZ = rotationDegrees }
            .semantics {
                contentDescription = providerDisclosureDescription(providerLabel, expanded)
                stateDescription = if (expanded) "已展开" else "已收起"
            },
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val halfWidth = size.minDimension * 0.2f
        val halfHeight = size.minDimension * 0.12f
        val strokeWidth = 1.65.dp.toPx()
        drawLine(
            color = AppMuted,
            start = androidx.compose.ui.geometry.Offset(centerX - halfWidth, centerY - halfHeight),
            end = androidx.compose.ui.geometry.Offset(centerX, centerY + halfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = AppMuted,
            start = androidx.compose.ui.geometry.Offset(centerX, centerY + halfHeight),
            end = androidx.compose.ui.geometry.Offset(centerX + halfWidth, centerY - halfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/** 展开的 Provider 紧凑字段行。 */
@Composable
private fun ProviderEditor(provider: ProviderProfile, onChange: (ProviderProfile) -> Unit, onDelete: () -> Unit) {
    var apiKeyVisible by remember(provider.id) { mutableStateOf(false) }
    SettingsField("服务 ID", provider.id) { onChange(provider.copy(id = it)) }
    SettingsField("显示名称", provider.label.orEmpty()) { onChange(provider.copy(label = it.ifBlank { null })) }
    SettingsRow("协议") {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ProviderType.entries.forEach { type ->
                SettingsChoiceChip(type.name.lowercase().replace('_', '-'), provider.providerType == type) { onChange(provider.copy(providerType = type)) }
            }
        }
    }
    SettingsField("Base URL", provider.baseUrl) { onChange(provider.copy(baseUrl = it)) }
    SettingsField("API Key", provider.apiKey, visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(), trailing = if (apiKeyVisible) "隐藏" else "显示") {
        if (it == "__toggle_visibility__") apiKeyVisible = !apiKeyVisible else onChange(provider.copy(apiKey = it))
    }
    SettingsRow("启用服务") { Switch(checked = provider.isEnabled(), onCheckedChange = { onChange(provider.copy(enabled = it)) }) }
    SettingsField(
        label = "辅助模型",
        value = provider.defaultModel.orEmpty(),
        placeholder = auxiliaryModelPlaceholder(provider),
    ) { onChange(provider.copy(defaultModel = it.ifBlank { null })) }
    Text("模型", style = MaterialTheme.typography.titleSmall.copy(color = AppText))
    provider.models.forEach { model ->
        SettingsField("模型 ID", model.id, trailing = "删除") { updatedValue ->
            if (updatedValue == "__toggle_visibility__") onChange(provider.copy(models = provider.models - model))
            else onChange(provider.copy(models = provider.models.map { candidate ->
                if (candidate.id == model.id) model.copy(id = updatedValue) else candidate
            }))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsActionButton("新增模型") { onChange(provider.copy(models = provider.models + ModelProfile(id = "model-${provider.models.size + 1}"))) }
        SettingsActionButton("删除服务", destructive = true, onClick = onDelete)
    }
}

/** 中性设置组表面，Provider 可传入独立卡片层级色。 */
@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    background: Color = AppChipBackground,
    hoverBackground: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var hovered by remember(hoverBackground) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) hoverBackground ?: background else background)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 左标签右控件的统一设置行。 */
@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(132.dp), style = MaterialTheme.typography.bodyMedium.copy(color = AppText))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { content() }
    }
}

/** 无浮动标签的紧凑文本字段。 */
@Composable
private fun SettingsField(
    label: String,
    value: String,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: String? = null,
    onValueChange: (String) -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    SettingsRow(label) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(AppPanelBackground)
                .border(1.dp, if (focused) AppAccent else AppLine, RoundedCornerShape(5.dp))
                .padding(start = 9.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                singleLine = true,
                cursorBrush = SolidColor(AppAccent),
                visualTransformation = visualTransformation,
                textStyle = TextStyle(color = AppText, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                decorationBox = { inner ->
                    if (value.isBlank() && !focused && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted.copy(alpha = 0.58f)),
                        )
                    }
                    inner()
                },
            )
            trailing?.let { action ->
                Text(
                    action,
                    modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 5.dp).clickable { onValueChange("__toggle_visibility__") },
                    style = MaterialTheme.typography.labelMedium.copy(color = AppMuted),
                )
            }
        }
    }
}

/** 扁平选择标签。 */
@Composable
private fun SettingsChoiceChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    var hovered by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(settingsItemBackground(selected = selected, hovered = hovered, enabled = enabled))
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge.copy(color = if (enabled) AppText else AppMuted),
    )
}

/** 小型操作按钮，仅保存操作使用强调色。 */
@Composable
private fun SettingsActionButton(text: String, emphasized: Boolean = false, destructive: Boolean = false, onClick: () -> Unit) {
    var hovered by remember(text) { mutableStateOf(false) }
    val color = when {
        emphasized -> AppAccent
        destructive -> AppDanger.copy(alpha = if (hovered) 0.9f else 0.62f)
        else -> settingsItemBackground(selected = false, hovered = hovered)
    }
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge.copy(color = AppText),
    )
}

/** 设置页的可选项与下拉菜单使用同一套 hover/选中背景。 */
internal fun settingsItemBackground(
    selected: Boolean,
    hovered: Boolean,
    enabled: Boolean = true,
): Color = selectMenuItemBackground(selected = selected, hovered = hovered, enabled = enabled)

/** Provider 外层卡片保持稳定的中性表面，悬浮反馈仅出现在可点击的摘要 Island。 */
internal fun providerCardBackground(): Color = ProviderCardBackground

/** Provider 摘要 Island 在悬浮时提亮，展开态恢复下拉菜单的选中色。 */
internal fun providerSummaryBackground(expanded: Boolean, hovered: Boolean): Color = when {
    expanded -> settingsItemBackground(selected = true, hovered = hovered)
    hovered -> ProviderCardHoverBackground
    else -> Color.Transparent
}

/** 未显式配置辅助模型时，仅以首个模型作为不可写入的视觉回退提示。 */
internal fun auxiliaryModelPlaceholder(provider: ProviderProfile): String? =
    provider.models.firstOrNull()?.id?.takeIf(String::isNotBlank)

/** 创建可立即编辑的默认 Provider。 */
private fun newProvider(id: String): ProviderProfile = ProviderProfile(
    id = id,
    providerType = ProviderType.OPENAI_RESPONSES,
    baseUrl = "https://api.openai.com/v1",
    apiKey = "",
    models = listOf(ModelProfile(id = "gpt-4.1")),
)

/** 返回表单可直接修复的首个设置错误。 */
private fun validateSettingsDocument(document: SettingsDocument): String? {
    val ids = document.providers.map(ProviderProfile::id)
    if (ids.any(String::isBlank)) return "服务 ID 不能为空。"
    if (ids.distinct().size != ids.size) return "服务 ID 不能重复。"
    document.providers.forEach { provider ->
        if (provider.baseUrl.isBlank()) return "${provider.id} 的 Base URL 不能为空。"
        if (provider.apiKey.isBlank()) return "${provider.id} 的 API Key 不能为空。"
        if (provider.models.isEmpty()) return "${provider.id} 至少需要一个模型。"
        if (provider.models.any { it.id.isBlank() }) return "${provider.id} 存在空的模型 ID。"
    }
    return null
}
