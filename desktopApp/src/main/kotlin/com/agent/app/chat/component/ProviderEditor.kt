@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** 渲染展开后的 Provider 紧凑字段。 */
@Composable
internal fun ProviderEditor(provider: ProviderProfile, onChange: (ProviderProfile) -> Unit, onDelete: () -> Unit) {
    var apiKeyVisible by remember(provider.id) { mutableStateOf(false) }
    ProviderEditorSection("基本信息") {
        SettingsField("服务 ID", provider.id) { onChange(provider.copy(id = it)) }
        SettingsField("显示名称", provider.label.orEmpty()) { onChange(provider.copy(label = it.ifBlank { null })) }
        SettingsRow("协议") {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ProviderType.entries.forEach { type ->
                    SettingsChoiceChip(type.name.lowercase().replace('_', '-'), provider.providerType == type) {
                        onChange(provider.copy(providerType = type))
                    }
                }
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
            SettingsField("模型 ID", model.id, trailing = "删除") { updatedValue ->
                if (updatedValue == "__toggle_visibility__") {
                    onChange(provider.copy(models = provider.models - model))
                } else {
                    onChange(provider.copy(models = provider.models.map { if (it.id == model.id) model.copy(id = updatedValue) else it }))
                }
            }
        }
        SettingsActionButton("新增模型") {
            onChange(provider.copy(models = provider.models + ModelProfile(id = "model-${provider.models.size + 1}")))
        }
    }
    SettingsActionButton("删除服务", destructive = true, onClick = onDelete)
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
                    Text(
                        action,
                        modifier = Modifier.clickable { onValueChange("__toggle_visibility__") },
                    )
                }
            },
        )
    }
}

/** 绘制扁平选择标签，供 Provider 协议复用。 */
@Composable
internal fun SettingsChoiceChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    RadioButtonRow(
        text = text,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
    )
}
