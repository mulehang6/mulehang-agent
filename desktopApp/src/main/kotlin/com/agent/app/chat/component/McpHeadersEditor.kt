@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.rememberExternalTextFieldValue
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** 编辑远程 MCP 请求头；值默认遮盖，避免肩窥和截图泄露认证信息。 */
@Composable
internal fun McpHeadersEditor(
    headers: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
) {
    SettingsRow("Headers") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (headers.isEmpty()) {
                Text("未设置请求头。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            }
            headers.entries.forEach { (name, value) ->
                McpHeaderRow(
                    name = name,
                    value = value,
                    onChange = { nextName, nextValue ->
                        onChange(headers.toMutableMap().apply { remove(name); put(nextName, nextValue) })
                    },
                    onRemove = { onChange(headers - name) },
                )
            }
            SettingsActionButton("添加 Header", emphasized = true) {
                onChange(headers + (nextMcpHeaderName(headers) to ""))
            }
        }
    }
}

/** 单条 Header 输入行；只有用户主动点击时才显示明文值。 */
@Composable
private fun McpHeaderRow(
    name: String,
    value: String,
    onChange: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    val nameValue = rememberExternalTextFieldValue(name)
    val headerValue = rememberExternalTextFieldValue(value)
    var revealed by remember(name) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = nameValue.value,
            onValueChange = { next ->
                nameValue.value = next
                next.text.trim().takeIf(String::isNotBlank)?.let { nextName ->
                    onChange(nextName, headerValue.value.text)
                }
            },
            modifier = Modifier.weight(0.9f),
            placeholder = { Text("名称") },
        )
        TextField(
            value = headerValue.value,
            onValueChange = { next ->
                headerValue.value = next
                onChange(nameValue.value.text.trim().ifBlank { name }, next.text)
            },
            modifier = Modifier.weight(1.2f),
            placeholder = { Text("值") },
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        )
        SettingsActionButton(if (revealed) "隐藏" else "显示", onClick = { revealed = !revealed })
        SettingsActionButton("删除", destructive = true, onClick = onRemove)
    }
}

/** 生成不与现有请求头冲突的临时名称，供用户立即开始编辑。 */
private fun nextMcpHeaderName(headers: Map<String, String>): String {
    var index = 1
    while (headers.keys.any { it.equals("X-MCP-Header-$index", ignoreCase = true) }) index += 1
    return "X-MCP-Header-$index"
}
