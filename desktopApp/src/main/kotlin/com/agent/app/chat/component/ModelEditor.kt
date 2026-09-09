package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ModelProfile
import org.jetbrains.jewel.ui.component.Checkbox

/** 编辑 Provider 下单个模型的能力、窗口和请求覆盖配置。 */
@Composable
internal fun ModelEditor(
    providerId: String,
    model: ModelProfile,
    onChange: (ModelProfile) -> Unit,
    onDelete: () -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
    onValidationErrorsCleared: (String) -> Unit,
    onValidationErrorsRenamed: (String, String) -> Unit,
) {
    val validationPrefix = "$providerId:${model.id}"
    ProviderEditorSection("模型：${model.label ?: model.id}") {
        SettingsField("模型 ID", model.id) { nextId ->
            onValidationErrorsRenamed(validationPrefix, "$providerId:$nextId")
            onChange(model.copy(id = nextId))
        }
        SettingsField("显示名称", model.label.orEmpty()) { onChange(model.copy(label = it.ifBlank { null })) }
        SettingsRow("启用模型") {
            Checkbox(checked = model.isEnabled(), onCheckedChange = { onChange(model.copy(enabled = it)) })
        }
        SettingsRow("支持图片输入") {
            Checkbox(checked = model.supportsVision == true, onCheckedChange = { onChange(model.copy(supportsVision = it)) })
        }
        NumericModelLimitField(
            label = "上下文窗口",
            value = model.limit?.context,
            validationKey = "$validationPrefix:context",
            onValueChange = { context -> onChange(model.copy(limit = model.limit.withContextLimit(context))) },
            onValidationErrorChange = onValidationErrorChange,
        )
        NumericModelLimitField(
            label = "最大输入",
            value = model.limit?.input,
            validationKey = "$validationPrefix:input",
            onValueChange = { input -> onChange(model.copy(limit = model.limit.withInputLimit(input))) },
            onValidationErrorChange = onValidationErrorChange,
        )
        NumericModelLimitField(
            label = "最大输出",
            value = model.limit?.output,
            validationKey = "$validationPrefix:output",
            onValueChange = { output -> onChange(model.copy(limit = model.limit.withOutputLimit(output))) },
            onValidationErrorChange = onValidationErrorChange,
        )
        SettingsField(
            "可用思考等级",
            model.reasoningEfforts?.joinToString(",").orEmpty(),
            placeholder = "none,low,medium,high,xhigh,max",
        ) { raw ->
            val efforts = configuredReasoningEfforts(raw)
            val invalid = efforts.filterNot(SUPPORTED_REASONING_EFFORTS::contains)
            if (invalid.isEmpty()) {
                onValidationErrorChange("$validationPrefix:reasoning-efforts", null)
                onChange(model.copy(reasoningEfforts = efforts))
            } else {
                onValidationErrorChange(
                    "$validationPrefix:reasoning-efforts",
                    "不支持的思考等级：${invalid.joinToString(", ")}",
                )
            }
        }
        SettingsField(
            "默认思考等级",
            model.defaultReasoningEffort.orEmpty(),
            placeholder = "例如 medium；留空则由运行时选择",
        ) { raw ->
            val effort = raw.trim().ifBlank { null }
            if (effort == null || effort in SUPPORTED_REASONING_EFFORTS) {
                onValidationErrorChange("$validationPrefix:default-reasoning", null)
                onChange(model.copy(defaultReasoningEffort = effort))
            } else {
                onValidationErrorChange("$validationPrefix:default-reasoning", "不支持的思考等级：$effort")
            }
        }
        RequestOverridesEditor(
            title = "模型级请求覆盖",
            overrides = model.request,
            validationKeyPrefix = "$validationPrefix:request",
            onChange = { request -> onChange(model.copy(request = request)) },
            onValidationErrorChange = onValidationErrorChange,
        )
        SettingsActionButton("删除模型", destructive = true) {
            onValidationErrorsCleared(validationPrefix)
            onDelete()
        }
    }
}

/** 以正整数形式编辑模型 token 限制；空值表示交由服务端或内建默认值决定。 */
@Composable
private fun NumericModelLimitField(
    label: String,
    value: Int?,
    validationKey: String,
    onValueChange: (Int?) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    SettingsField(label, value?.toString().orEmpty(), placeholder = "留空使用默认值") { raw ->
        val parsed = raw.trim().toIntOrNull()
        when {
            raw.isBlank() -> {
                onValidationErrorChange(validationKey, null)
                onValueChange(null)
            }

            parsed != null && parsed > 0 -> {
                onValidationErrorChange(validationKey, null)
                onValueChange(parsed)
            }

            else -> onValidationErrorChange(validationKey, "$label 必须是正整数。")
        }
    }
}

/** 将逗号分隔的档位文本标准化为持久化配置可直接使用的 wire value 列表。 */
internal fun configuredReasoningEfforts(raw: String): List<String> =
    raw.split(',').map(String::trim).filter(String::isNotBlank).distinct()

/** 返回任一字段被清空时仍能维持 null 语义的模型限制。 */
private fun ModelLimit?.withContextLimit(context: Int?): ModelLimit? =
    (this ?: ModelLimit()).copy(context = context).takeIf(ModelLimit::hasConfiguredValue)

/** 返回任一字段被清空时仍能维持 null 语义的模型限制。 */
private fun ModelLimit?.withInputLimit(input: Int?): ModelLimit? =
    (this ?: ModelLimit()).copy(input = input).takeIf(ModelLimit::hasConfiguredValue)

/** 返回任一字段被清空时仍能维持 null 语义的模型限制。 */
private fun ModelLimit?.withOutputLimit(output: Int?): ModelLimit? =
    (this ?: ModelLimit()).copy(output = output).takeIf(ModelLimit::hasConfiguredValue)

/** 判断模型限制是否仍有需要写入 JSON 的显式字段。 */
private fun ModelLimit.hasConfiguredValue(): Boolean = context != null || input != null || output != null

private val SUPPORTED_REASONING_EFFORTS = ReasoningEffort.entries.map(ReasoningEffort::wireValue).toSet()
