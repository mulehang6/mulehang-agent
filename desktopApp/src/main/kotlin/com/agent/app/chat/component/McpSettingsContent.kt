@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.shared.agent.resource.AgentMcpServerResource
import com.agent.shared.agent.resource.McpServerConnectionStatus
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import com.agent.shared.settings.model.SettingsDocument
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** 在扩展设置页编辑直接 MCP 服务，并同时显示由扩展包声明的只读服务。 */
@Composable
internal fun McpSettingsContent(
    document: SettingsDocument,
    discoveredServers: List<AgentMcpServerResource>,
    savedServers: List<McpServerSettings>,
    connectionStatuses: List<McpServerConnectionStatus>,
    layer: ConfigLayer,
    editorState: McpJsonEditorState,
    configurationPendingReload: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDocumentChange: (SettingsDocument) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    val configuredServers = document.agentResources.mcpServers
    GroupHeader("MCP 服务")
    Text(
        "直接配置会在保存并重新加载后用于后续任务；项目级服务仍需先信任当前项目。",
        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
    )
    McpEditorTabs(
        editorState = editorState,
        servers = configuredServers,
        onValidationErrorChange = onValidationErrorChange,
    )
    when (editorState.mode) {
        McpEditorMode.VISUAL -> McpVisualEditor(
            document = document,
            savedServers = savedServers,
            connectionStatuses = connectionStatuses,
            layer = layer,
            configurationPendingReload = configurationPendingReload,
            retryEnabled = retryEnabled,
            onRetry = onRetry,
            onSave = onSave,
            onDocumentChange = onDocumentChange,
        )

        McpEditorMode.JSON -> McpJsonEditor(
            editorState = editorState,
            servers = configuredServers,
            onServersChange = { servers -> onDocumentChange(document.withMcpServers(servers)) },
            onValidationErrorChange = onValidationErrorChange,
            onSave = onSave,
        )
    }
    GroupHeader("来自扩展包")
    val packageServers = discoveredServers.filterNot { it.packageId == DIRECT_SETTINGS_MCP_PACKAGE_ID }
    if (packageServers.isEmpty()) {
        Text("当前没有来自扩展包的 MCP 声明。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
    } else {
        packageServers.forEach { server ->
            Text(
                text = "${server.id}  ·  ${server.transport.name.lowercase().replace('_', '-')}  ·  ${server.packageId}",
                style = JewelTheme.defaultTextStyle.copy(color = AppText),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 在可视化表单和完整 JSON 文本之间切换。 */
@Composable
private fun McpEditorTabs(
    editorState: McpJsonEditorState,
    servers: List<McpServerSettings>,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    IslandsTabStrip(
        tabs = listOf(
            IslandsTab(
                label = "可视化配置",
                selected = editorState.mode == McpEditorMode.VISUAL,
                onClick = {
                    if (editorState.enterVisual()) {
                        onValidationErrorChange(MCP_JSON_VALIDATION_KEY, null)
                    }
                },
            ),
            IslandsTab(
                label = "JSON 配置",
                selected = editorState.mode == McpEditorMode.JSON,
                onClick = {
                    editorState.enterJson(servers)
                    onValidationErrorChange(MCP_JSON_VALIDATION_KEY, editorState.error)
                },
            ),
        ),
    )
}

/** 渲染现有 MCP 卡片表单，所有操作只修改待保存文档。 */
@Composable
private fun McpVisualEditor(
    document: SettingsDocument,
    savedServers: List<McpServerSettings>,
    connectionStatuses: List<McpServerConnectionStatus>,
    layer: ConfigLayer,
    configurationPendingReload: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDocumentChange: (SettingsDocument) -> Unit,
) {
    val configuredServers = document.agentResources.mcpServers
    var editingSavedServers by remember(layer) { mutableStateOf(emptyMap<String, McpServerSettings>()) }
    LaunchedEffect(savedServers) {
        editingSavedServers = editingSavedServers.filterKeys { currentId ->
            configuredServers.firstOrNull { it.id == currentId } != savedServers.firstOrNull { it.id == currentId }
        }
    }
    SettingsActionButton("添加 MCP 服务", emphasized = true) {
        val id = nextMcpServerId(configuredServers)
        onDocumentChange(document.withMcpServer(McpServerSettings(id = id, transport = McpServerTransport.STDIO)))
    }
    if (configuredServers.isEmpty()) {
        Text("尚未直接配置 MCP 服务。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        if (configuredServers != savedServers) {
            ExtensionSettingsCard {
                Text("已移除全部直接 MCP 服务，保存后写入配置。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
                SettingsActionButton("保存 MCP", emphasized = true, onClick = onSave)
            }
        }
    } else {
        configuredServers.forEach { server ->
            val editingBaseline = editingSavedServers[server.id]
            val saved = editingBaseline ?: savedServers.firstOrNull { it.id == server.id }
            if (saved == server && server.id !in editingSavedServers) {
                McpSavedServerList(
                    servers = listOf(server),
                    statuses = connectionStatuses,
                    layer = layer,
                    configurationPendingReload = configurationPendingReload,
                    retryEnabled = retryEnabled,
                    onEdit = { editingSavedServers = editingSavedServers + (server.id to server) },
                    onToggle = {
                        editingSavedServers = editingSavedServers + (server.id to server)
                        onDocumentChange(document.withUpdatedMcpServer(server.id, server.copy(enabled = !server.enabled)))
                    },
                    onDelete = {
                        editingSavedServers = editingSavedServers - server.id
                        onDocumentChange(document.withoutMcpServer(server.id))
                    },
                    onRetry = { onRetry() },
                )
            } else {
                McpServerEditor(
                    server = server,
                    savedServer = saved,
                    onChange = { updated ->
                        if (editingBaseline != null && updated.id != server.id) {
                            editingSavedServers = editingSavedServers - server.id + (updated.id to editingBaseline)
                        }
                        onDocumentChange(document.withUpdatedMcpServer(server.id, updated))
                    },
                    onRemove = { onDocumentChange(document.withoutMcpServer(server.id)) },
                    onCancel = {
                        editingSavedServers = editingSavedServers - server.id
                        onDocumentChange(
                            saved?.let { baseline -> document.withUpdatedMcpServer(server.id, baseline) }
                                ?: document.withoutMcpServer(server.id),
                        )
                    },
                    onSave = onSave,
                )
            }
        }
    }
}

/** 编辑完整 MCP JSON，并把合法内容实时同步到同一份设置草稿。 */
@Composable
private fun McpJsonEditor(
    editorState: McpJsonEditorState,
    servers: List<McpServerSettings>,
    onServersChange: (List<McpServerSettings>) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
    onSave: () -> Unit,
) {
    ExtensionSettingsCard {
        Text(
            if (editorState.sensitiveValuesVisible) {
                "Header 敏感值当前可见。"
            } else {
                "Header 敏感值默认隐藏，点击按钮后才显示。"
            },
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        JsonCodeEditor(
            text = editorState.text,
            error = editorState.error,
            onTextChange = { text ->
                if (text == editorState.text) return@JsonCodeEditor
                when (val result = editorState.updateText(text)) {
                    is McpJsonParseResult.Success -> {
                        onValidationErrorChange(MCP_JSON_VALIDATION_KEY, null)
                        onServersChange(result.servers)
                    }

                    is McpJsonParseResult.Failure -> {
                        onValidationErrorChange(MCP_JSON_VALIDATION_KEY, result.message)
                    }
                }
            },
            onFormat = {
                applyMcpJsonFormat(editorState, onServersChange, onValidationErrorChange)
            },
            modifier = Modifier.fillMaxWidth().height(280.dp),
        )
        editorState.error?.let { error ->
            Text(error, style = JewelTheme.defaultTextStyle.copy(color = AppDanger))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsActionButton("保存 MCP", emphasized = true, onClick = onSave)
            SettingsActionButton("格式化 JSON") {
                applyMcpJsonFormat(editorState, onServersChange, onValidationErrorChange)
            }
            SettingsActionButton(
                if (editorState.sensitiveValuesVisible) "隐藏敏感值" else "显示敏感值",
                onClick = {
                    editorState.toggleSensitiveValues(servers)
                    onValidationErrorChange(MCP_JSON_VALIDATION_KEY, editorState.error)
                },
            )
        }
    }
}

/** 统一工具栏按钮与 Ctrl+Alt+L 的格式化结果处理。 */
private fun applyMcpJsonFormat(
    editorState: McpJsonEditorState,
    onServersChange: (List<McpServerSettings>) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
) {
    when (val result = editorState.format()) {
        is McpJsonParseResult.Success -> {
            onValidationErrorChange(MCP_JSON_VALIDATION_KEY, null)
            onServersChange(result.servers)
        }

        is McpJsonParseResult.Failure -> {
            onValidationErrorChange(MCP_JSON_VALIDATION_KEY, result.message)
        }
    }
}

/** 编辑单个 MCP 的连接方式、进程命令和最小环境变量表。 */
@Composable
private fun McpServerEditor(
    server: McpServerSettings,
    savedServer: McpServerSettings?,
    onChange: (McpServerSettings) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val transportStyle = rememberProviderProtocolComboBoxStyle()
    ExtensionSettingsCard {
        SettingsField("服务 ID", server.id) { onChange(server.copy(id = it)) }
        SettingsRow("启用服务") {
            Checkbox(checked = server.enabled, onCheckedChange = { onChange(server.copy(enabled = it)) })
        }
        SettingsRow("传输") {
            ListComboBox(
                items = McpServerTransport.entries,
                selectedIndex = McpServerTransport.entries.indexOf(server.transport),
                onSelectedItemChange = { index ->
                    val transport = McpServerTransport.entries[index]
                    onChange(
                        if (transport == McpServerTransport.STDIO) {
                            server.copy(transport = transport, url = null, headers = emptyMap())
                        } else {
                            server.copy(transport = transport, command = emptyList(), environment = emptyMap())
                        },
                    )
                },
                itemKeys = { _, transport -> transport.name },
                modifier = Modifier.fillMaxWidth(),
                style = transportStyle,
            ) { transport, selected, active ->
                SimpleListItem(text = mcpTransportLabel(transport), selected = selected, active = active)
            }
        }
        when (server.transport) {
            McpServerTransport.STDIO -> {
                SettingsField("命令", server.command.firstOrNull().orEmpty(), placeholder = "例如 npx 或 uvx") { command ->
                    onChange(server.copy(command = listOf(command) + server.command.drop(1)))
                }
                SettingsField(
                    "参数",
                    server.command.drop(1).joinToString(","),
                    placeholder = "用英文逗号分隔，例如 -y,@scope/server",
                ) { arguments ->
                    onChange(server.copy(command = server.command.firstOrNull().orEmpty().let(::listOf) + splitMcpArguments(arguments)))
                }
            }

            McpServerTransport.SSE, McpServerTransport.STREAMABLE_HTTP -> {
                SettingsField("服务地址", server.url.orEmpty(), placeholder = "https://example.com/mcp") { url ->
                    onChange(server.copy(url = url.ifBlank { null }))
                }
                McpHeadersEditor(headers = server.headers) { headers ->
                    onChange(server.copy(headers = headers))
                }
            }
        }
        if (server.transport == McpServerTransport.STDIO) {
            McpEnvironmentEditor(environment = server.environment) { environment ->
                onChange(server.copy(environment = environment))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsActionButton("保存", emphasized = true, enabled = savedServer == null || server != savedServer, onClick = onSave)
            SettingsActionButton("移除", destructive = true, onClick = onRemove)
            Box(Modifier.weight(1f))
            SettingsActionButton("取消", onClick = onCancel)
        }
    }
}

/** 以键值行编辑 stdio MCP 环境变量，避免把敏感值拼进一段难以修复的文本。 */
@Composable
private fun McpEnvironmentEditor(
    environment: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
) {
    SettingsRow("环境变量") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (environment.isEmpty()) {
                Text("未设置环境变量。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            }
            environment.entries.forEach { (name, value) ->
                McpEnvironmentRow(
                    name = name,
                    value = value,
                    onChange = { nextName, nextValue ->
                        onChange(environment.toMutableMap().apply { remove(name); put(nextName, nextValue) })
                    },
                    onRemove = { onChange(environment - name) },
                )
            }
            SettingsActionButton("添加变量", compact = true) {
                onChange(environment + (nextMcpEnvironmentName(environment) to ""))
            }
        }
    }
}

/** 渲染单条环境变量输入，空键不会写回当前配置。 */
@Composable
private fun McpEnvironmentRow(
    name: String,
    value: String,
    onChange: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    val keyValue = rememberExternalTextFieldValue(name)
    val environmentValue = rememberExternalTextFieldValue(value)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McpEnvironmentTextField(
            value = keyValue.value,
            onValueChange = { next ->
                keyValue.value = next
                next.text.trim().takeIf(String::isNotBlank)?.let { nextName -> onChange(nextName, environmentValue.value.text) }
            },
            weight = 0.8f,
            placeholder = "名称",
        )
        McpEnvironmentTextField(
            value = environmentValue.value,
            onValueChange = { next ->
                environmentValue.value = next
                onChange(keyValue.value.text.trim().ifBlank { name }, next.text)
            },
            weight = 1.2f,
            placeholder = "值",
        )
        SettingsActionButton("删除", destructive = true, compact = true, onClick = onRemove)
    }
}

/** 渲染环境变量行复用的紧凑输入框，并保持行内宽度比例一致。 */
@Composable
private fun RowScope.McpEnvironmentTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    weight: Float,
    placeholder: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.weight(weight),
        placeholder = { Text(placeholder) },
    )
}

/** 验证直接 MCP 设置能在资源重载前被明确修复。 */
internal fun validateMcpServerSettings(document: SettingsDocument): String? {
    return validateMcpServersForJson(document.agentResources.mcpServers.filter(McpServerSettings::enabled))
}

/** 拆分标准 UI 中的逗号参数列表，空段不会进入进程启动参数。 */
internal fun splitMcpArguments(value: String): List<String> =
    value.split(',').map(String::trim).filter(String::isNotBlank)

/** 为新服务生成稳定且不重复的默认 id。 */
internal fun nextMcpServerId(servers: List<McpServerSettings>): String {
    var index = 1
    while ("mcp-$index" in servers.map(McpServerSettings::id)) index += 1
    return "mcp-$index"
}

/** 为新环境变量生成稳定且不重复的默认键。 */
private fun nextMcpEnvironmentName(environment: Map<String, String>): String {
    var index = 1
    while ("MCP_ENV_$index" in environment) index += 1
    return "MCP_ENV_$index"
}

/** 在 settings.json 中按服务 ID 新增或替换直接 MCP 记录。 */
private fun SettingsDocument.withMcpServer(server: McpServerSettings): SettingsDocument = copy(
    agentResources = agentResources.copy(
        mcpServers = agentResources.mcpServers.filterNot { current -> current.id == server.id } + server,
    ),
)

/** 按编辑前的稳定 ID 替换直接 MCP，允许用户修改服务 ID 而不保留旧记录。 */
internal fun SettingsDocument.withUpdatedMcpServer(existingId: String, server: McpServerSettings): SettingsDocument = copy(
    agentResources = agentResources.copy(
        mcpServers = agentResources.mcpServers.map { current -> if (current.id == existingId) server else current },
    ),
)

/** 仅移除配置记录，不结束当前已经建立的 MCP 连接。 */
private fun SettingsDocument.withoutMcpServer(id: String): SettingsDocument = copy(
    agentResources = agentResources.copy(mcpServers = agentResources.mcpServers.filterNot { it.id == id }),
)

/** 用 JSON 编辑器解析出的完整列表替换直接 MCP 草稿。 */
private fun SettingsDocument.withMcpServers(servers: List<McpServerSettings>): SettingsDocument = copy(
    agentResources = agentResources.copy(mcpServers = servers),
)

/** 供 UI 展示的 MCP 协议标签。 */
private fun mcpTransportLabel(transport: McpServerTransport): String = transport.name.lowercase().replace('_', '-')

private const val DIRECT_SETTINGS_MCP_PACKAGE_ID = "settings"
internal const val MCP_JSON_VALIDATION_KEY = "mcp-json"
