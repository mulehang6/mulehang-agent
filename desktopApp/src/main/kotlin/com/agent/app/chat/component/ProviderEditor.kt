@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.styling.ComboBoxColors
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle

/** 渲染展开后的 Provider 紧凑字段。 */
@Composable
internal fun ProviderEditor(provider: ProviderProfile, onChange: (ProviderProfile) -> Unit, onDelete: () -> Unit) {
    var apiKeyVisible by remember(provider.id) { mutableStateOf(false) }
    val protocolComboBoxStyle = rememberProviderProtocolComboBoxStyle()
    ProviderEditorSection("基本信息") {
        SettingsField("服务 ID", provider.id) { onChange(provider.copy(id = it)) }
        SettingsField("显示名称", provider.label.orEmpty()) { onChange(provider.copy(label = it.ifBlank { null })) }
        SettingsRow("协议") {
            ListComboBox(
                items = ProviderType.entries,
                selectedIndex = ProviderType.entries.indexOf(provider.providerType),
                onSelectedItemChange = { index ->
                    onChange(provider.copy(providerType = ProviderType.entries[index]))
                },
                itemKeys = { _, type -> type.name },
                modifier = Modifier.fillMaxWidth(),
                style = protocolComboBoxStyle,
            ) { type, selected, active ->
                SimpleListItem(
                    text = providerTypeLabel(type),
                    selected = selected,
                    active = active,
                )
            }
        }
        SettingsRow("启用服务") {
            Checkbox(checked = provider.isEnabled(), onCheckedChange = { onChange(provider.copy(enabled = it)) })
        }
    }
    ProviderEditorSection("连接") {
        SettingsField("Base URL", provider.baseUrl) { onChange(provider.copy(baseUrl = it)) }
        SettingsField(
            "API Key",
            provider.apiKey,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = if (apiKeyVisible) "隐藏" else "显示",
        ) {
            if (it == "__toggle_visibility__") apiKeyVisible = !apiKeyVisible else onChange(provider.copy(apiKey = it))
        }
    }
    ProviderEditorSection("模型") {
        SettingsField("辅助模型", provider.defaultModel.orEmpty(), placeholder = auxiliaryModelPlaceholder(provider)) {
            onChange(provider.copy(defaultModel = it.ifBlank { null }))
        }
        provider.models.forEach { model ->
            SettingsField(
                label = "模型 ID",
                value = model.id,
                trailing = "删除",
                trailingDestructive = true,
            ) { updatedValue ->
                if (updatedValue == "__toggle_visibility__") {
                    onChange(provider.copy(models = provider.models - model))
                } else {
                    onChange(provider.copy(models = provider.models.map { if (it.id == model.id) model.copy(id = updatedValue) else it }))
                }
            }
        }
        SettingsActionButton("新增", emphasized = true) {
            onChange(provider.copy(models = provider.models + ModelProfile(id = "model-${provider.models.size + 1}")))
        }
    }
    SettingsActionButton("删除", destructive = true, onClick = onDelete)
}

/** 绘制 Provider 连接页的分组标题和清晰内容层。 */
@Composable
private fun ProviderEditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(AppPanelBackground.copy(alpha = 0.54f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        content()
    }
}

/** 绘制左标签右控件的统一设置行。 */
@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(132.dp), style = JewelTheme.defaultTextStyle.copy(color = AppText))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { content() }
    }
}

/** 绘制无浮动标签的紧凑文本字段。 */
@Composable
private fun SettingsField(
    label: String,
    value: String,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: String? = null,
    trailingDestructive: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val editorValue = rememberExternalTextFieldValue(value)
    SettingsRow(label) {
        TextField(
            value = editorValue.value,
            onValueChange = { nextValue ->
                editorValue.value = nextValue
                onValueChange(nextValue.text)
            },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = visualTransformation,
            placeholder = placeholder?.let { hint ->
                { Text(hint) }
            },
            trailingIcon = trailing?.let { action ->
                {
                    if (trailingDestructive) {
                        SettingsActionButton(
                            text = action,
                            destructive = true,
                            compact = true,
                            modifier = Modifier.offset(x = PROVIDER_FIELD_TRAILING_ACTION_END_OFFSET),
                        ) {
                            onValueChange("__toggle_visibility__")
                        }
                    } else {
                        IconActionButton(
                            key = AllIconsKeys.Actions.Show,
                            contentDescription = action,
                            onClick = { onValueChange("__toggle_visibility__") },
                            modifier = Modifier.offset(x = PROVIDER_FIELD_TRAILING_ACTION_END_OFFSET),
                        )
                    }
                }
            },
        )
    }
}

/** 让只读协议下拉框沿用同组文本字段的默认底色。 */
@Composable
private fun rememberProviderProtocolComboBoxStyle(): ComboBoxStyle {
    val baseStyle = JewelTheme.comboBoxStyle
    val textFieldStyle = JewelTheme.textFieldStyle
    return remember(baseStyle, textFieldStyle) {
        val baseColors = baseStyle.colors
        ComboBoxStyle(
            colors = ComboBoxColors(
                background = baseColors.background,
                nonEditableBackground = textFieldStyle.colors.background,
                backgroundDisabled = baseColors.backgroundDisabled,
                backgroundFocused = baseColors.backgroundFocused,
                backgroundPressed = baseColors.backgroundPressed,
                backgroundHovered = baseColors.backgroundHovered,
                content = baseColors.content,
                contentDisabled = baseColors.contentDisabled,
                contentFocused = baseColors.contentFocused,
                contentPressed = baseColors.contentPressed,
                contentHovered = baseColors.contentHovered,
                border = baseColors.border,
                borderDisabled = baseColors.borderDisabled,
                borderFocused = baseColors.borderFocused,
                borderPressed = baseColors.borderPressed,
                borderHovered = baseColors.borderHovered,
            ),
            metrics = baseStyle.metrics,
            icons = baseStyle.icons,
        )
    }
}

/** 返回 Provider 类型在下拉框中使用的稳定、单行文本。 */
internal fun providerTypeLabel(type: ProviderType): String = type.name.lowercase().replace('_', '-')

private val PROVIDER_FIELD_TRAILING_ACTION_END_OFFSET = 4.dp
