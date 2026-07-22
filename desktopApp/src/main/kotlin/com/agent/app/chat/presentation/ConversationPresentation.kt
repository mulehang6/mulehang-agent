package com.agent.app.chat.presentation

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/**
 * 构造回答区下方的次级状态文案。
 */
internal fun buildSecondaryStatus(conversation: ChatConversationUiState): String? = when {
    conversation.pendingApproval != null -> conversation.pendingApproval.summary
    conversation.pendingQuestion != null -> conversation.pendingQuestion.question
    conversation.executionState == ExecutionState.Running ->
        "正在 ${buildWorkspaceLabel(conversation.workspacePath)} 中工作…"

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
 * 运行中或失败的工具事件默认展开，完成事件保持紧凑。
 */
internal fun shouldExpandToolEventByDefault(item: ToolEventItem): Boolean =
    toolEventHasDetails(item) && item.status in setOf(ToolEventStatus.Started, ToolEventStatus.Failed)

/**
 * 返回不含角色前缀的聊天消息正文。
 */
internal fun buildChatMessageText(item: ChatMessageItem): String = item.message.content

/**
 * 返回思考块标题，并区分流式和完成状态。
 */
internal fun buildReasoningHeadline(item: ReasoningItem): String =
    if (item.isStreaming) "正在思考…" else "思考过程"
