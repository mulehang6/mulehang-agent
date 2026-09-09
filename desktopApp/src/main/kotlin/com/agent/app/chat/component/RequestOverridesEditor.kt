@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.RequestOverrides
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField

/**
 * 编辑不依赖厂商名称的请求头、静态 JSON 请求体和思考档位请求体。
 */
@Composable
internal fun RequestOverridesEditor(
    title: String,
    overrides: RequestOverrides,
    validationKeyPrefix: String,
    onChange: (RequestOverrides) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    ProviderEditorSection(title) {
        Text(
            "静态 JSON 先于思考档位 JSON 合并；模型级配置覆盖服务级配置。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        RequestHeadersEditor(
            headers = overrides.headers,
            onChange = { headers -> onChange(overrides.copy(headers = headers)) },
        )
        RequestJsonObjectEditor(
            value = overrides.body,
            validationKey = "$validationKeyPrefix:body",
            onValueChange = { body -> onChange(overrides.copy(body = body)) },
            onValidationErrorChange = onValidationErrorChange,
        )
        reasoningBodyOptions(overrides).forEach { effort ->
            val currentBody = overrides.reasoningBodyByEffort[effort]
            SettingsRow("思考：$effort") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = currentBody != null,
                            onCheckedChange = { enabled ->
                                val updatedBodies = overrides.reasoningBodyByEffort.toMutableMap().apply {
                                    if (enabled) put(effort, JsonObject(emptyMap())) else remove(effort)
                                }
                                onValidationErrorChange("$validationKeyPrefix:reasoning:$effort", null)
                                onChange(overrides.copy(reasoningBodyByEffort = updatedBodies))
                            },
                        )
                        Text(
                            if (currentBody == null) "不覆盖此档位" else "此档位启用覆盖",
                            style = JewelTheme.defaultTextStyle.copy(color = AppText),
                        )
                    }
                    currentBody?.let { body ->
                        JsonObjectInput(
                            value = body,
                            validationKey = "$validationKeyPrefix:reasoning:$effort",
                            placeholder = "{\"thinking\": {\"type\": \"enabled\"}}",
                            onValueChange = { nextBody ->
                                onChange(
                                    overrides.copy(
                                        reasoningBodyByEffort = overrides.reasoningBodyByEffort + (effort to nextBody),
                                    ),
                                )
                            },
                            onValidationErrorChange = onValidationErrorChange,
                        )
                    }
                }
            }
        }
    }
}

/** 返回设置页应显示的所有思考映射档位，并保留旧配置中的未知键以便用户移除。 */
internal fun reasoningBodyOptions(overrides: RequestOverrides): List<String> {
    val supported = ReasoningEffort.entries.map(ReasoningEffort::wireValue)
    return supported + overrides.reasoningBodyByEffort.keys.filterNot(supported::contains).sorted()
}

/** 编辑一组 HTTP 请求头，模型和服务两层均可复用。 */
@Composable
private fun RequestHeadersEditor(
    headers: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
) {
    SettingsRow("请求头") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (headers.isEmpty()) {
                Text("未设置额外请求头。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            }
            headers.entries.forEach { (name, value) ->
                RequestHeaderRow(
                    name = name,
                    value = value,
                    onChange = { nextName, nextValue ->
                        val updated = headers.toMutableMap().apply {
                            remove(name)
                            put(nextName, nextValue)
                        }
                        onChange(updated)
                    },
                    onRemove = { onChange(headers - name) },
                )
            }
            SettingsActionButton("添加请求头", compact = true) {
                onChange(headers + (nextRequestHeaderName(headers) to ""))
            }
        }
    }
}

/** 渲染单条可编辑请求头，并避免空键写入配置。 */
@Composable
private fun RequestHeaderRow(
    name: String,
    value: String,
    onChange: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    val nameValue = rememberExternalTextFieldValue(name)
    val headerValue = rememberExternalTextFieldValue(value)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = nameValue.value,
            onValueChange = { next ->
                nameValue.value = next
                next.text.trim().takeIf(String::isNotBlank)?.let { nextName -> onChange(nextName, headerValue.value.text) }
            },
            modifier = Modifier.weight(0.8f),
            placeholder = { Text("Header 名称") },
        )
        TextField(
            value = headerValue.value,
            onValueChange = { next ->
                headerValue.value = next
                onChange(nameValue.value.text.trim().ifBlank { name }, next.text)
            },
            modifier = Modifier.weight(1.2f),
            placeholder = { Text("值") },
        )
        SettingsActionButton("删除", destructive = true, compact = true, onClick = onRemove)
    }
}

/** 把 JSON 文本输入组织为一个带标签的设置行。 */
@Composable
private fun RequestJsonObjectEditor(
    value: JsonObject,
    validationKey: String,
    onValueChange: (JsonObject) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    SettingsRow("静态 JSON") {
        JsonObjectInput(
            value = value,
            validationKey = validationKey,
            placeholder = "{\"provider_option\": true}",
            onValueChange = onValueChange,
            onValidationErrorChange = onValidationErrorChange,
        )
    }
}

/**
 * 使用 Jewel 多行输入编辑 JSON 对象；无效文本不写回配置，但会阻止用户保存。
 */
@Composable
private fun JsonObjectInput(
    value: JsonObject,
    validationKey: String,
    placeholder: String,
    onValueChange: (JsonObject) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    val editorValue = rememberExternalTextFieldValue(formatRequestJson(value))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextArea(
            value = editorValue.value,
            onValueChange = { nextValue ->
                editorValue.value = nextValue
                parseRequestJsonObject(nextValue)?.let { parsed ->
                    onValidationErrorChange(validationKey, null)
                    onValueChange(parsed)
                } ?: onValidationErrorChange(validationKey, "必须填写有效的 JSON 对象。")
            },
            modifier = Modifier.fillMaxWidth().height(112.dp),
            placeholder = { Text(placeholder) },
        )
    }
}

/** 将 JSON 对象格式化为稳定的设置页文本。 */
internal fun formatRequestJson(value: JsonObject): String = REQUEST_OVERRIDE_JSON.encodeToString(value)

/** 从多行编辑器输入解析 JSON 对象；数组、字符串和无效文本均不接受。 */
internal fun parseRequestJsonObject(value: TextFieldValue): JsonObject? =
    runCatching { REQUEST_OVERRIDE_JSON.parseToJsonElement(value.text).jsonObject }.getOrNull()

/** 为新请求头生成一个不会与当前配置冲突的占位键。 */
internal fun nextRequestHeaderName(headers: Map<String, String>): String {
    var index = 1
    while ("X-Custom-$index" in headers) index += 1
    return "X-Custom-$index"
}

private val REQUEST_OVERRIDE_JSON = Json { prettyPrint = true }
