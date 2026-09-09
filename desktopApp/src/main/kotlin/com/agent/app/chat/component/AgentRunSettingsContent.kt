@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.agent.app.design.AppMuted
import com.agent.shared.settings.model.AgentIterationLimit
import com.agent.shared.settings.model.FasterModelProfile
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.model.SettingsDocument
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text

/** 编辑只允许存在于全局 settings.json 的 Agent 循环上限和快速模型。 */
@Composable
internal fun AgentRunSettingsContent(
    document: SettingsDocument,
    onDocumentChange: (SettingsDocument) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    ProviderEditorSection("全局 Agent 运行") {
        SettingsField(
            label = "最大迭代次数",
            value = document.maxIterations?.toString().orEmpty(),
            placeholder = "50 到 ${AgentIterationLimit.KOOG_MAXIMUM}，或 -1",
        ) { raw ->
            val parsed = raw.trim().toIntOrNull()
            when {
                raw.isBlank() -> {
                    onValidationErrorChange(MAX_ITERATIONS_VALIDATION_KEY, null)
                    onDocumentChange(document.copy(maxIterations = null))
                }

                parsed == AgentIterationLimit.UNLIMITED || parsed != null && parsed >= AgentIterationLimit.MINIMUM -> {
                    onValidationErrorChange(MAX_ITERATIONS_VALIDATION_KEY, null)
                    onDocumentChange(document.copy(maxIterations = parsed))
                }

                else -> onValidationErrorChange(
                    MAX_ITERATIONS_VALIDATION_KEY,
                    "最大迭代次数必须为 -1 或至少 ${AgentIterationLimit.MINIMUM}。",
                )
            }
        }
        Text(
            "达到上限时会保留会话并提示你继续，而不是让会话因循环错误失效。-1 表示 Koog 可表达的最大值。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
    }
    FasterModelSettings(document = document, onDocumentChange = onDocumentChange)
}

/** 显式快速模型配置；未配置时解析器仍可退回当前 Provider 的首个可用模型。 */
@Composable
private fun FasterModelSettings(
    document: SettingsDocument,
    onDocumentChange: (SettingsDocument) -> Unit,
) {
    val fastModel = document.fasterModel
    ProviderEditorSection("快速模型") {
        Text(
            "用于低延迟的辅助任务。留空时会按当前服务的首个启用模型回退。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        if (fastModel == null) {
            SettingsActionButton("配置快速模型", emphasized = true) {
                onDocumentChange(document.copy(fasterModel = newFasterModel()))
            }
        } else {
            FasterModelFields(
                value = fastModel,
                onChange = { next -> onDocumentChange(document.copy(fasterModel = next)) },
            )
            SettingsActionButton("移除快速模型", destructive = true) {
                onDocumentChange(document.copy(fasterModel = null))
            }
        }
    }
}

/** 绘制快速模型的完整连接与 token 限制字段。 */
@Composable
private fun FasterModelFields(
    value: FasterModelProfile,
    onChange: (FasterModelProfile) -> Unit,
) {
    var apiKeyVisible by remember { mutableStateOf(false) }
    val protocolStyle = rememberProviderProtocolComboBoxStyle()
    SettingsRow("启用快速模型") {
        Checkbox(checked = value.isEnabled(), onCheckedChange = { onChange(value.copy(enabled = it)) })
    }
    SettingsRow("协议") {
        ListComboBox(
            items = ProviderType.entries,
            selectedIndex = ProviderType.entries.indexOf(value.providerType),
            onSelectedItemChange = { index -> onChange(value.copy(providerType = ProviderType.entries[index])) },
            itemKeys = { _, type -> type.name },
            modifier = Modifier,
            style = protocolStyle,
        ) { type, selected, active ->
            SimpleListItem(text = providerTypeLabel(type), selected = selected, active = active)
        }
    }
    SettingsField("Base URL", value.baseUrl) { onChange(value.copy(baseUrl = it)) }
    SettingsField(
        label = "API Key",
        value = value.apiKey,
        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingAction = SettingsFieldAction(
            label = if (apiKeyVisible) "隐藏" else "显示",
            onClick = { apiKeyVisible = !apiKeyVisible },
        ),
    ) { onChange(value.copy(apiKey = it)) }
    SettingsField("模型 ID", value.model) { onChange(value.copy(model = it)) }
    FasterModelLimitField("上下文窗口", value.limit?.context) { context ->
        onChange(value.copy(limit = value.limit.withFasterContextLimit(context)))
    }
    FasterModelLimitField("最大输入", value.limit?.input) { input ->
        onChange(value.copy(limit = value.limit.withFasterInputLimit(input)))
    }
    FasterModelLimitField("最大输出", value.limit?.output) { output ->
        onChange(value.copy(limit = value.limit.withFasterOutputLimit(output)))
    }
}

/** 编辑快速模型的可选正整数 token 限制；非法输入保留现有值以免写入错误配置。 */
@Composable
private fun FasterModelLimitField(label: String, value: Int?, onValueChange: (Int?) -> Unit) {
    SettingsField(label, value?.toString().orEmpty(), placeholder = "留空使用默认值") { raw ->
        val parsed = raw.trim().toIntOrNull()
        when {
            raw.isBlank() -> onValueChange(null)
            parsed != null && parsed > 0 -> onValueChange(parsed)
            else -> Unit
        }
    }
}

/** 创建只含可编辑占位字段的快速模型，避免在配置中伪造凭据。 */
private fun newFasterModel(): FasterModelProfile = FasterModelProfile(
    providerType = ProviderType.OPENAI_RESPONSES,
    baseUrl = "",
    apiKey = "",
    model = "",
)

/** 返回任一字段被清空时仍能维持 null 语义的快速模型限制。 */
private fun ModelLimit?.withFasterContextLimit(context: Int?): ModelLimit? =
    (this ?: ModelLimit()).copy(context = context).takeIf(ModelLimit::hasFastConfiguredValue)

/** 返回任一字段被清空时仍能维持 null 语义的快速模型限制。 */
private fun ModelLimit?.withFasterInputLimit(input: Int?): ModelLimit? =
    (this ?: ModelLimit()).copy(input = input).takeIf(ModelLimit::hasFastConfiguredValue)

/** 返回任一字段被清空时仍能维持 null 语义的快速模型限制。 */
private fun ModelLimit?.withFasterOutputLimit(output: Int?): ModelLimit? =
    (this ?: ModelLimit()).copy(output = output).takeIf(ModelLimit::hasFastConfiguredValue)

/** 判断快速模型限制是否仍有需要写入 JSON 的显式字段。 */
private fun ModelLimit.hasFastConfiguredValue(): Boolean = context != null || input != null || output != null

private const val MAX_ITERATIONS_VALIDATION_KEY = "global:max-iterations"
