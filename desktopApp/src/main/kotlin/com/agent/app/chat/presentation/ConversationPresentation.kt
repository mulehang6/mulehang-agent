package com.agent.app.chat.presentation

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/**
 * 返回回答区标题。
 */
internal fun buildAnswerTitle(conversation: ChatConversationUiState): String {
    if (conversation.executionState == ExecutionState.Running) return "Updating plan..."
    if (conversation.pendingApproval != null) return "Awaiting approval..."
    if (conversation.pendingQuestion != null) return "Waiting for more input..."
    return when (conversation.items.lastOrNull()) {
        is ReasoningItem -> "Reasoning update"
        is ToolEventItem -> "Tool activity"
        is ChatMessageItem -> "Latest answer"
        null -> "Ready for a new task"
    }
}

/**
 * 把当前任务的最近回答转换为段落块。
 */
internal fun buildAnswerParagraphs(conversation: ChatConversationUiState): List<String> {
    val assistant = conversation.items
        .asReversed()
        .filterIsInstance<ChatMessageItem>()
        .firstOrNull { it.message.role == ChatRole.Assistant }
        ?.message
        ?.content
        ?.trim()
        ?.takeIf(String::isNotBlank)
    if (assistant != null) {
        return assistant
            .split(Regex("\n\\s*\n"))
            .map(String::trim)
            .filter(String::isNotBlank)
    }
    val reasoning = conversation.items
        .asReversed()
        .filterIsInstance<ReasoningItem>()
        .firstOrNull()
        ?.displayText
        ?.trim()
    if (!reasoning.isNullOrBlank()) {
        return listOf(reasoning)
    }
    return listOf("No assistant output yet for this task.")
}

/**
 * 构造回答区下方的次级状态文案。
 */
internal fun buildSecondaryStatus(conversation: ChatConversationUiState): String? = when {
    conversation.pendingApproval != null -> conversation.pendingApproval.summary
    conversation.pendingQuestion != null -> conversation.pendingQuestion.question
    conversation.executionState == ExecutionState.Running ->
        "Working in ${buildWorkspaceLabel(conversation.workspacePath)}..."
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
    item.status != ToolEventStatus.Status && !item.preview.isNullOrBlank()

/**
 * 返回不含角色前缀的聊天消息正文。
 */
internal fun buildChatMessageText(item: ChatMessageItem): String = item.message.content

/**
 * 返回思考块标题，并区分流式和完成状态。
 */
internal fun buildReasoningHeadline(item: ReasoningItem): String =
    if (item.isStreaming) "Thinking: 思考中..." else "Thinking:"
