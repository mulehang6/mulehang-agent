package com.agent.app.chat.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.settings.model.McpServerSettings

/** MCP 设置区当前显示的编辑方式。 */
internal enum class McpEditorMode {
    VISUAL,
    JSON,
}

/** 保存 MCP 双视图的临时文本与校验状态。 */
@Stable
internal class McpJsonEditorState {
    var mode by mutableStateOf(McpEditorMode.VISUAL)
        private set

    var text by mutableStateOf("")
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** JSON 编辑器是否已通过用户操作显示 Header 敏感值。 */
    var sensitiveValuesVisible by mutableStateOf(false)
        private set

    private var hiddenHeaderValuesByServerId: Map<String, Map<String, String>> = emptyMap()

    /** 切换配置层级时丢弃旧层级的 JSON 临时输入。 */
    fun reset() {
        mode = McpEditorMode.VISUAL
        text = ""
        error = null
        sensitiveValuesVisible = false
        hiddenHeaderValuesByServerId = emptyMap()
    }

    /** 从可视化草稿生成规范 JSON，并带着其校验结果进入 JSON 视图。 */
    fun enterJson(servers: List<McpServerSettings>) {
        hiddenHeaderValuesByServerId = captureHeaderValues(servers)
        sensitiveValuesVisible = false
        text = formatMcpJsonConfiguration(servers, maskSensitiveHeaders = true)
        error = (parseMcpJsonConfiguration(text) as? McpJsonParseResult.Failure)?.message
        mode = McpEditorMode.JSON
    }

    /** 显示或隐藏 JSON 中的 Header 敏感值；隐藏时保留真实值，避免保存占位符。 */
    fun toggleSensitiveValues(servers: List<McpServerSettings>) {
        if (sensitiveValuesVisible) hiddenHeaderValuesByServerId = captureHeaderValues(servers)
        sensitiveValuesVisible = !sensitiveValuesVisible
        text = formatMcpJsonConfiguration(
            servers,
            maskSensitiveHeaders = !sensitiveValuesVisible,
        )
        error = (parseMcpJsonConfiguration(text) as? McpJsonParseResult.Failure)?.message
    }

    /** 仅在 JSON 文本有效时返回可视化视图。 */
    fun enterVisual(): Boolean {
        if (error != null) return false
        mode = McpEditorMode.VISUAL
        return true
    }

    /** 保存用户原始输入，并在合法时返回可同步的服务列表。 */
    fun updateText(value: String): McpJsonParseResult {
        text = value
        val result = restoreHiddenHeaderValues(parseMcpJsonConfiguration(value))
        error = (result as? McpJsonParseResult.Failure)?.message
        return result
    }

    /** 校验并规范化当前文本，非法输入保持原样。 */
    fun format(): McpJsonParseResult {
        val result = restoreHiddenHeaderValues(parseMcpJsonConfiguration(text))
        when (result) {
            is McpJsonParseResult.Success -> {
                text = formatMcpJsonConfiguration(
                    result.servers,
                    maskSensitiveHeaders = !sensitiveValuesVisible,
                )
                error = null
            }

            is McpJsonParseResult.Failure -> error = result.message
        }
        return result
    }

    /** 记录进入 JSON 视图时的真实 Header 值，供占位符解析后恢复。 */
    private fun captureHeaderValues(servers: List<McpServerSettings>): Map<String, Map<String, String>> =
        servers.filter { server -> server.headers.isNotEmpty() }
            .associate { server -> server.id to server.headers }

    /** 仅恢复未被用户改写的占位符，新的 Header 文本仍按用户输入保存。 */
    private fun restoreHiddenHeaderValues(result: McpJsonParseResult): McpJsonParseResult {
        if (result !is McpJsonParseResult.Success || sensitiveValuesVisible) return result
        return result.copy(
            servers = result.servers.map { server ->
                val hiddenHeaders = hiddenHeaderValuesByServerId[server.id].orEmpty()
                if (hiddenHeaders.isEmpty()) return@map server
                server.copy(
                    headers = server.headers.mapValues { (name, value) ->
                        if (value != MCP_REDACTED_HEADER_VALUE) {
                            value
                        } else {
                            hiddenHeaders.entries.firstOrNull { entry ->
                                entry.key.equals(name, ignoreCase = true)
                            }?.value ?: value
                        }
                    },
                )
            },
        )
    }
}
