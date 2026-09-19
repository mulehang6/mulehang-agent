package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.shared.agent.resource.McpConnectionPhase
import com.agent.shared.agent.resource.McpServerConnectionStatus
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** 在可视化配置原位置渲染已保存的紧凑 MCP 卡片。 */
@Composable
internal fun McpSavedServerList(
    servers: List<McpServerSettings>,
    statuses: List<McpServerConnectionStatus>,
    layer: ConfigLayer,
    configurationPendingReload: Boolean,
    retryEnabled: Boolean,
    onEdit: (McpServerSettings) -> Unit,
    onToggle: (McpServerSettings) -> Unit,
    onDelete: (McpServerSettings) -> Unit,
    onRetry: (McpServerSettings) -> Unit,
) {
    if (servers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        servers.forEach { server ->
            val status = statuses
                .takeUnless { configurationPendingReload }
                ?.firstOrNull { it.serverId == server.id && it.packageId == DIRECT_SETTINGS_PACKAGE_ID }
            McpSavedServerRow(
                server = server,
                status = status,
                layer = layer,
                retryEnabled = retryEnabled,
                onEdit = onEdit,
                onToggle = onToggle,
                onDelete = onDelete,
                onRetry = onRetry,
            )
        }
    }
}

/** 一条紧凑服务行；点击行或更多按钮展开连接与工具详情。 */
@Composable
private fun McpSavedServerRow(
    server: McpServerSettings,
    status: McpServerConnectionStatus?,
    layer: ConfigLayer,
    retryEnabled: Boolean,
    onEdit: (McpServerSettings) -> Unit,
    onToggle: (McpServerSettings) -> Unit,
    onDelete: (McpServerSettings) -> Unit,
    onRetry: (McpServerSettings) -> Unit,
) {
    var expanded by remember(server.id) { mutableStateOf(false) }
    var actionsVisible by remember(server.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(AppChipBackground)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(mcpStatusColor(server, status)))
            Text(server.id, style = JewelTheme.defaultTextStyle.copy(color = AppText))
            Text(mcpToolCountLabel(server, status), style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            Box(Modifier.weight(1f))
            Text(
                if (layer == ConfigLayer.PROJECT) "Local" else "Global",
                style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
            )
            SettingsActionButton("⋯", compact = true, onClick = { actionsVisible = !actionsVisible })
        }
        if (expanded) McpSavedServerDetails(server, status)
        if (actionsVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsActionButton("编辑", onClick = { onEdit(server) })
                SettingsActionButton(if (server.enabled) "停用" else "启用", onClick = { onToggle(server) })
                SettingsActionButton("删除", destructive = true, onClick = { onDelete(server) })
                if (status?.phase == McpConnectionPhase.FAILED) {
                    SettingsActionButton("重试", emphasized = true, enabled = retryEnabled, onClick = { onRetry(server) })
                }
            }
        }
    }
}

/** 展示传输、连接错误与真实工具名称/描述；Header 只显示名称，不显示值。 */
@Composable
private fun McpSavedServerDetails(
    server: McpServerSettings,
    status: McpServerConnectionStatus?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(mcpConnectionLabel(server, status), style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        Text(
            when (server.transport) {
                McpServerTransport.STDIO -> server.command.joinToString(" ")
                McpServerTransport.SSE, McpServerTransport.STREAMABLE_HTTP -> server.url.orEmpty()
            },
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        if (server.headers.isNotEmpty()) {
            Text(
                "Headers：${server.headers.keys.joinToString()}",
                style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
            )
        }
        status?.error?.let { error ->
            Text(error, style = JewelTheme.defaultTextStyle.copy(color = AppDanger))
        }
        status?.tools.orEmpty().forEach { tool ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(tool.name, style = JewelTheme.defaultTextStyle.copy(color = AppText))
                if (tool.description.isNotBlank()) {
                    Text(tool.description, style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
                }
            }
        }
    }
}

/** 根据声明和真实连接状态返回工具数量或状态文案。 */
private fun mcpToolCountLabel(server: McpServerSettings, status: McpServerConnectionStatus?): String = when {
    !server.enabled -> "已停用"
    status == null -> "待重新加载"
    status.phase == McpConnectionPhase.CONNECTING -> "连接中"
    status.phase == McpConnectionPhase.FAILED -> "连接失败"
    else -> "${status.tools.size} ${if (status.tools.size == 1) "tool" else "tools"}"
}

/** 返回列表状态点颜色。 */
private fun mcpStatusColor(server: McpServerSettings, status: McpServerConnectionStatus?): Color = when {
    !server.enabled -> AppMuted
    status == null || status.phase == McpConnectionPhase.CONNECTING -> AppAccent
    status.phase == McpConnectionPhase.FAILED -> AppDanger
    else -> Color(0xFF21A67A)
}

/** 返回详情中的传输和连接状态说明。 */
private fun mcpConnectionLabel(server: McpServerSettings, status: McpServerConnectionStatus?): String {
    val transport = server.transport.name.lowercase().replace('_', '-')
    return "$transport · ${mcpToolCountLabel(server, status)}"
}

private const val DIRECT_SETTINGS_PACKAGE_ID = "settings"
