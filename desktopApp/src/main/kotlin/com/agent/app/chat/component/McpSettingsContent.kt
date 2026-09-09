@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.shared.agent.resource.AgentMcpServerResource
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import com.agent.shared.settings.model.SettingsDocument
import java.net.URI
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
    onDocumentChange: (SettingsDocument) -> Unit,
    onChangeNotification: (String) -> Unit,
) {
    val configuredServers = document.agentResources.mcpServers
    GroupHeader("MCP 服务")
    Text(
        "直接配置会在保存并重新加载后用于后续任务；项目级服务仍需先信任当前项目。",
        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
    )
    SettingsActionButton("添加 MCP 服务", emphasized = true) {
        val id = nextMcpServerId(configuredServers)
        onDocumentChange(document.withMcpServer(McpServerSettings(id = id, transport = McpServerTransport.STDIO)))
        onChangeNotification("已添加 MCP 服务：$id")
    }
    if (configuredServers.isEmpty()) {
        Text("尚未直接配置 MCP 服务。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
    } else {
        configuredServers.forEach { server ->
            McpServerEditor(
                server = server,
                onChange = { updated -> onDocumentChange(document.withUpdatedMcpServer(server.id, updated)) },
                onRemove = {
                    onDocumentChange(document.withoutMcpServer(server.id))
                    onChangeNotification("已移除 MCP 服务：${server.id}")
                },
            )
        }
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

/** 编辑单个 MCP 的连接方式、进程命令和最小环境变量表。 */
@Composable
private fun McpServerEditor(
    server: McpServerSettings,
    onChange: (McpServerSettings) -> Unit,
    onRemove: () -> Unit,
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
                            server.copy(transport = transport, url = null)
                        } else {
                            server.copy(transport = transport, command = emptyList())
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
            }
        }
        McpEnvironmentEditor(environment = server.environment) { environment ->
            onChange(server.copy(environment = environment))
        }
        SettingsActionButton("移除", destructive = true, onClick = onRemove)
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
    val servers = document.agentResources.mcpServers.filter(McpServerSettings::enabled)
    val ids = servers.map { it.id.trim() }
    if (ids.any(String::isBlank)) return "MCP 服务 ID 不能为空。"
    if (ids.distinct().size != ids.size) return "MCP 服务 ID 不能重复。"
    servers.forEach { server ->
        if (server.transport == McpServerTransport.STDIO && server.command.firstOrNull().isNullOrBlank()) {
            return "stdio MCP '${server.id}' 必须填写命令。"
        }
        if (server.transport != McpServerTransport.STDIO && !isHttpMcpUrl(server.url)) {
            return "HTTP MCP '${server.id}' 必须填写有效的 http(s) 服务地址。"
        }
        server.environment.keys.firstOrNull { key -> key.isBlank() || key.any { it == '=' || it.isWhitespace() } }?.let { key ->
            return "MCP '${server.id}' 存在无效环境变量名：${key.ifBlank { "<空>" }}。"
        }
    }
    return null
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

/** 仅允许可以交给 Koog HTTP MCP 客户端的绝对 http(s) 地址。 */
private fun isHttpMcpUrl(value: String?): Boolean = runCatching {
    val uri = URI(value?.trim().orEmpty())
    uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

/** 在 settings.json 中按服务 ID 新增或替换直接 MCP 记录。 */
private fun SettingsDocument.withMcpServer(server: McpServerSettings): SettingsDocument = copy(
    agentResources = agentResources.copy(
        mcpServers = agentResources.mcpServers.filterNot { current -> current.id == server.id } + server,
    ),
)

/** 按编辑前的稳定 ID 替换直接 MCP，允许用户修改服务 ID 而不保留旧记录。 */
private fun SettingsDocument.withUpdatedMcpServer(existingId: String, server: McpServerSettings): SettingsDocument = copy(
    agentResources = agentResources.copy(
        mcpServers = agentResources.mcpServers.map { current -> if (current.id == existingId) server else current },
    ),
)

/** 仅移除配置记录，不结束当前已经建立的 MCP 连接。 */
private fun SettingsDocument.withoutMcpServer(id: String): SettingsDocument = copy(
    agentResources = agentResources.copy(mcpServers = agentResources.mcpServers.filterNot { it.id == id }),
)

/** 供 UI 展示的 MCP 协议标签。 */
private fun mcpTransportLabel(transport: McpServerTransport): String = transport.name.lowercase().replace('_', '-')

private const val DIRECT_SETTINGS_MCP_PACKAGE_ID = "settings"
