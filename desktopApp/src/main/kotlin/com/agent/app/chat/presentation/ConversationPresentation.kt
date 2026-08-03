package com.agent.app.chat.presentation

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/**
 * 构造回答区下方的次级状态文案。
 */
internal fun buildSecondaryStatus(conversation: ChatConversationUiState): String? = when {
    conversation.pendingApproval != null -> conversation.pendingApproval.summary
    conversation.pendingQuestion != null -> conversation.pendingQuestion.question
    else -> null
}

/**
 * 返回工具事件标题。
 */
internal fun buildToolEventHeadline(item: ToolEventItem): String = when (item.status) {
    ToolEventStatus.Status -> item.preview.orEmpty().ifBlank { item.toolName }
    ToolEventStatus.Failed -> "失败: ${item.toolName}"
    else -> item.toolName
}

/**
 * 返回工具事件种类标签。
 */
internal fun buildToolEventKindLabel(item: ToolEventItem): String? = when (item.status) {
    ToolEventStatus.Started -> "输入"
    ToolEventStatus.Finished -> "输出"
    ToolEventStatus.Status -> null
    ToolEventStatus.Failed -> "错误"
}

/**
 * 判断工具事件是否存在可展开的详情文本。
 */
internal fun toolEventHasDetails(item: ToolEventItem): Boolean =
    item.status != ToolEventStatus.Status

/**
 * 返回工具卡片展开后的详情文案，避免把空输入渲染成难读的方括号。
 */
internal fun toolEventDetailText(item: ToolEventItem): String = item.preview
    ?.takeIf(String::isNotBlank)
    ?: when (item.status) {
        ToolEventStatus.Finished -> "无输出内容"
        else -> "无输入参数"
    }

/**
 * 返回展开工具卡片时应显示的完整输出，兼容旧事件的预览字段。
 */
internal fun toolEventOutputText(item: ToolEventItem): String? =
    item.resultDisplay ?: item.resultPreview

/**
 * 仅在进行中的终端调用已产生输出时自动展开，避免静默工具占用展开空间。
 */
internal fun shouldAutoExpandRunningTerminalOutput(item: ToolEventItem): Boolean =
    isTerminalToolEvent(item) &&
            item.status == ToolEventStatus.Started &&
            toolEventOutputText(item)?.isNotBlank() == true

/** 单个惰性终端输出文本块的最大字符数，避免超长单行阻塞排版。 */
internal const val TOOL_OUTPUT_CHUNK_MAX_CHARS = 2_000

/**
 * 将完整工具输出拆成适合惰性列表按需渲染的有界文本块。
 */
internal fun toolEventOutputChunks(item: ToolEventItem): List<String> {
    val output = toolEventOutputText(item) ?: return emptyList()
    if (output.isEmpty()) return emptyList()
    return output
        .splitToSequence('\n')
        .flatMap { line ->
            line.removeSuffix("\r")
                .chunked(TOOL_OUTPUT_CHUNK_MAX_CHARS)
                .ifEmpty { listOf("") }
                .asSequence()
        }
        .toList()
}

/**
 * 判断工具卡片默认行是否展示工具名；终端工具以具体命令作为唯一主标题。
 */
internal fun shouldShowToolEventHeadline(item: ToolEventItem): Boolean = !isTerminalToolEvent(item)

/**
 * 判断事件是否对应终端工具；兼容流式事件丢失工具名但仍保留完整参数的情况。
 */
internal fun isTerminalToolEvent(item: ToolEventItem): Boolean =
    item.toolName == "run_powershell" || terminalOperationIntentFromPreview(item.preview) != null

/**
 * 返回应在终端工具卡片外展示的操作意图。
 */
internal fun buildToolEventOperationIntent(item: ToolEventItem): String? =
    item.operationIntent ?: terminalOperationIntentFromPreview(item.preview)

/**
 * 返回应紧跟工具名展示的非空输入；无参数工具保持仅展示名称。
 */
internal fun buildToolEventInlineInput(item: ToolEventItem): String? {
    val preview = item.preview?.takeIf(String::isNotBlank) ?: return null
    return if (isTerminalToolEvent(item)) {
        terminalScriptFromPreview(preview) ?: preview
    } else {
        preview
    }
}

/**
 * 从终端工具参数中提取实际命令，避免在卡片标题重复展示 operation intent。
 */
private fun terminalScriptFromPreview(preview: String): String? = runCatching {
    TERMINAL_SCRIPT_PATTERN.find(preview)
        ?.groupValues
        ?.get(1)
        ?: TERMINAL_SCRIPT_MAP_PATTERN.find(preview)
            ?.groupValues
            ?.get(1)
            ?.trim()
        ?.takeIf(String::isNotBlank)
}.getOrNull()

/** 终端参数 JSON 中 script 字段的轻量提取规则。 */
private val TERMINAL_SCRIPT_PATTERN = Regex(""""script"\s*:\s*"((?:\\.|[^"\\])*)"""")

/** Koog 工具参数转为 Kotlin Map 文本后的 script 字段提取规则。 */
private val TERMINAL_SCRIPT_MAP_PATTERN = Regex("""(?:^|[,{]\s*)script\s*=\s*(.+?)(?=,\s*(?:operation_intent|timeout_ms)\s*=|})""")

/**
 * 从终端工具参数中提取操作意图，兼容 JSON 与 Koog Map 文本预览。
 */
private fun terminalOperationIntentFromPreview(preview: String?): String? = preview
    ?.let(TERMINAL_OPERATION_INTENT_PATTERN::find)
    ?.groupValues
    ?.getOrNull(1)
    ?.trim()
    ?.takeIf(String::isNotBlank)

/** 终端参数中的 operation_intent 字段提取规则。 */
private val TERMINAL_OPERATION_INTENT_PATTERN = Regex(
    """(?:"operation_intent"\s*:\s*"|operation_intent\s*=\s*)([^",}]+)""",
)

/**
 * 工具事件默认收起，仅在用户明确点击后展示输入或输出详情。
 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldExpandToolEventByDefault(item: ToolEventItem): Boolean = false

/**
 * 返回不含角色前缀的聊天消息正文。
 */
internal fun buildChatMessageText(item: ChatMessageItem): String = item.message.content

/**
 * 返回思考块标题；完成后以耗时替换流式中的 Thinking 文案。
 */
internal fun buildReasoningHeadline(item: ReasoningItem): String {
    if (item.isStreaming) return "Thinking..."
    return buildReasoningDurationLabel(item.durationMillis ?: 0L)
}

/**
 * 将已完成思考的耗时格式化为秒或毫秒文案。
 */
internal fun buildReasoningDurationLabel(durationMillis: Long): String {
    val normalizedDuration = durationMillis.coerceAtLeast(0L)
    return if (normalizedDuration < 1_000L) {
        "已思考 $normalizedDuration 毫秒"
    } else {
        "已思考 ${normalizedDuration / 1_000L} 秒"
    }
}
