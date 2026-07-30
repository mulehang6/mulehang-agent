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
 * 返回应紧跟工具名展示的非空输入；无参数工具保持仅展示名称。
 */
internal fun buildToolEventInlineInput(item: ToolEventItem): String? {
    val preview = item.preview?.takeIf(String::isNotBlank) ?: return null
    return if (item.toolName == "run_powershell") {
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
        ?.takeIf(String::isNotBlank)
}.getOrNull()

/** 终端参数 JSON 中 script 字段的轻量提取规则。 */
private val TERMINAL_SCRIPT_PATTERN = Regex(""""script"\s*:\s*"((?:\\.|[^"\\])*)"""")

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
 * 将已完成思考的耗时格式化为整秒文案。
 */
internal fun buildReasoningDurationLabel(durationMillis: Long): String =
    "已思考 ${durationMillis.coerceAtLeast(0L) / 1_000L} 秒"
