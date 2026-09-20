package com.agent.app.chat.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport

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

    private var hiddenServerValues: List<HiddenMcpServerValues> = emptyList()

    /** 切换配置层级时丢弃旧层级的 JSON 临时输入。 */
    fun reset() {
        mode = McpEditorMode.VISUAL
        text = ""
        error = null
        sensitiveValuesVisible = false
        hiddenServerValues = emptyList()
    }

    /** 从可视化草稿生成规范 JSON，并带着其校验结果进入 JSON 视图。 */
    fun enterJson(servers: List<McpServerSettings>) {
        hiddenServerValues = captureHiddenServerValues(servers)
        sensitiveValuesVisible = false
        text = formatMcpJsonConfiguration(servers, maskSensitiveHeaders = true)
        error = (parseMcpJsonConfiguration(text) as? McpJsonParseResult.Failure)?.message
        mode = McpEditorMode.JSON
    }

    /** 显示或隐藏 JSON 中的 Header 敏感值；无效草稿保持原样，不因切换而丢失。 */
    fun toggleSensitiveValues() {
        when (val result = restoreHiddenHeaderValues(parseMcpJsonConfiguration(text))) {
            is McpJsonParseResult.Success -> {
                hiddenServerValues = captureHiddenServerValues(result.servers)
                sensitiveValuesVisible = !sensitiveValuesVisible
                text = formatMcpJsonConfiguration(
                    result.servers,
                    maskSensitiveHeaders = !sensitiveValuesVisible,
                )
                error = null
            }

            is McpJsonParseResult.Failure -> {
                error = result.message
            }
        }
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
        if (result is McpJsonParseResult.Success) {
            hiddenServerValues = captureHiddenServerValues(result.servers)
        }
        error = (result as? McpJsonParseResult.Failure)?.message
        return result
    }

    /** 校验并规范化当前文本，非法输入保持原样。 */
    fun format(): McpJsonParseResult {
        val result = restoreHiddenHeaderValues(parseMcpJsonConfiguration(text))
        when (result) {
            is McpJsonParseResult.Success -> {
                hiddenServerValues = captureHiddenServerValues(result.servers)
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

    /** 记录服务连接特征和真实 Header 值，允许 JSON 编辑器改名后继续恢复凭据。 */
    private fun captureHiddenServerValues(servers: List<McpServerSettings>): List<HiddenMcpServerValues> =
        servers.map { server ->
            HiddenMcpServerValues(
                id = server.id,
                transport = server.transport,
                url = server.url,
                command = server.command,
                headers = server.headers,
            )
        }

    /** 仅恢复未被用户改写的占位符，新的 Header 文本仍按用户输入保存。 */
    private fun restoreHiddenHeaderValues(result: McpJsonParseResult): McpJsonParseResult {
        if (result !is McpJsonParseResult.Success || sensitiveValuesVisible) return result
        val usedSources = mutableSetOf<String>()
        val restoredServers = mutableListOf<McpServerSettings>()
        result.servers.forEachIndexed { index, server ->
            val hasMaskedHeader = server.headers.values.any { value -> value == MCP_REDACTED_HEADER_VALUE }
            if (!hasMaskedHeader) {
                restoredServers += server
                return@forEachIndexed
            }
            val source = findHiddenServerValues(server, index, usedSources)
                ?: return McpJsonParseResult.Failure("Header 敏感值已失去对应关系，请先显示敏感值后再修改服务。")
            val restoredHeaders = restoreHiddenHeaders(server.headers, source.headers)
                ?: return McpJsonParseResult.Failure("Header 敏感值已失去对应关系，请先显示敏感值后再修改 Header。")
            usedSources += source.id
            restoredServers += server.copy(headers = restoredHeaders)
        }
        return McpJsonParseResult.Success(restoredServers)
    }

    /** 优先按服务 ID 或连接特征匹配，最后才使用原列表位置支持重命名。 */
    private fun findHiddenServerValues(
        server: McpServerSettings,
        index: Int,
        usedSources: Set<String>,
    ): HiddenMcpServerValues? = hiddenServerValues.firstOrNull { source ->
        source.id == server.id && source.id !in usedSources
    } ?: hiddenServerValues.firstOrNull { source ->
        source.id !in usedSources &&
                source.transport == server.transport &&
                source.url == server.url &&
                source.command == server.command
    } ?: hiddenServerValues.getOrNull(index)?.takeIf { source -> source.id !in usedSources }

    /** 按原 Header 名称、位置和剩余唯一值恢复改名后的遮罩项。 */
    private fun restoreHiddenHeaders(
        headers: Map<String, String>,
        hiddenHeaders: Map<String, String>,
    ): Map<String, String>? {
        val usedNames = mutableSetOf<String>()
        val hiddenEntries = hiddenHeaders.entries.toList()
        val restored = LinkedHashMap<String, String>()
        headers.entries.forEachIndexed { index, (name, value) ->
            if (value != MCP_REDACTED_HEADER_VALUE) {
                restored[name] = value
                return@forEachIndexed
            }
            val source = hiddenEntries.getOrNull(index)
                ?.takeIf { entry -> entry.key !in usedNames && entry.key.equals(name, ignoreCase = true) }
                ?: hiddenEntries.firstOrNull { entry ->
                    entry.key !in usedNames && entry.key.equals(name, ignoreCase = true)
                }
                ?: hiddenEntries.firstOrNull { entry -> entry.key !in usedNames }
                ?: return null
            usedNames += source.key
            restored[name] = source.value
        }
        return restored
    }

    /** JSON 编辑器进入隐藏模式时保留的单条服务上下文。 */
    private data class HiddenMcpServerValues(
        val id: String,
        val transport: McpServerTransport,
        val url: String?,
        val command: List<String>,
        val headers: Map<String, String>,
    )
}
