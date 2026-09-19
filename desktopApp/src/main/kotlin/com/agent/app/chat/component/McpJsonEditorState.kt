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

    /** 切换配置层级时丢弃旧层级的 JSON 临时输入。 */
    fun reset() {
        mode = McpEditorMode.VISUAL
        text = ""
        error = null
    }

    /** 从可视化草稿生成规范 JSON，并带着其校验结果进入 JSON 视图。 */
    fun enterJson(servers: List<McpServerSettings>) {
        text = formatMcpJsonConfiguration(servers)
        error = (parseMcpJsonConfiguration(text) as? McpJsonParseResult.Failure)?.message
        mode = McpEditorMode.JSON
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
        val result = parseMcpJsonConfiguration(value)
        error = (result as? McpJsonParseResult.Failure)?.message
        return result
    }

    /** 校验并规范化当前文本，非法输入保持原样。 */
    fun format(): McpJsonParseResult {
        val result = parseMcpJsonConfiguration(text)
        when (result) {
            is McpJsonParseResult.Success -> {
                text = formatMcpJsonConfiguration(result.servers)
                error = null
            }

            is McpJsonParseResult.Failure -> error = result.message
        }
        return result
    }
}
