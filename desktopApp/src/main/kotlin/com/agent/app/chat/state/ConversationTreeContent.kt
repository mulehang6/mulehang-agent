package com.agent.app.chat.state

import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.chat.model.ConversationEntry
import java.nio.file.Path

private const val BRANCH_SUMMARY_ENTRY_CHAR_LIMIT = 8_000
private const val BRANCH_SUMMARY_TOTAL_CHAR_LIMIT = 60_000

/** 将条目转成不含附件二进制、但保留角色和工具结果的摘要输入。 */
internal fun branchSummaryContent(entries: List<ConversationEntry>): String = entries
    .joinToString("\n\n") { entry ->
        val content = when (entry) {
            is ConversationEntry.Message -> "${entry.message.role.name}: ${entry.message.content}"
            is ConversationEntry.Reasoning -> "Reasoning: ${
                entry.summaryText?.takeIf(String::isNotBlank)
                    ?: entry.rawText?.takeIf(String::isNotBlank).orEmpty()
            }"

            is ConversationEntry.ToolCall -> "Tool call ${entry.toolName}: ${entry.preview.orEmpty()}"
            is ConversationEntry.ToolResult ->
                "Tool result ${entry.toolName}: ${entry.resultDisplay ?: entry.resultPreview ?: entry.errorMessage.orEmpty()}"
            is ConversationEntry.Answers -> entry.answers.joinToString("\n") {
                "Answer to ${it.question}: ${it.answer}"
            }
            is ConversationEntry.BranchSummary -> "Earlier branch summary: ${entry.summary}"
            is ConversationEntry.Label -> "Label: ${entry.label}"
            is ConversationEntry.ModelChange -> "Model changed to: ${entry.profileId ?: "default"}"
            is ConversationEntry.ReasoningEffortChange -> "Reasoning effort changed to: ${entry.reasoningEffort}"
            is ConversationEntry.Custom -> "${entry.type}: ${entry.text}"
        }
        content.take(BRANCH_SUMMARY_ENTRY_CHAR_LIMIT)
    }
    .take(BRANCH_SUMMARY_TOTAL_CHAR_LIMIT)

/** 记录摘要覆盖范围与自定义指令，便于树视图解释条目来源。 */
internal fun branchSummaryDetails(
    entries: List<ConversationEntry>,
    customInstructions: String?,
): String = buildString {
    append("Summarized ${entries.size} branch entries.")
    customInstructions?.let { append(" Custom instructions: ").append(it) }
}

/** 从条目有序输入恢复 composer 文本和附件 token。 */
internal fun draftFromInputParts(parts: List<UserInputPart>): RestoredDraft {
    val attachments = mutableListOf<ChatAttachmentUiState>()
    val text = buildString {
        parts.forEach { part ->
            when (part) {
                is UserInputPart.Text -> append(part.text)
                is UserInputPart.FileSnapshot -> {
                    val name = runCatching { Path.of(part.path).fileName?.toString() }.getOrNull()
                        ?.ifBlank { null }
                        ?: part.path
                    val attachment = ChatAttachmentUiState(
                        path = part.path,
                        name = name,
                        token = "@$name",
                        kind = ChatAttachmentKind.FILE_SNAPSHOT,
                        snapshotContent = part.content,
                        mimeType = part.mimeType,
                    )
                    attachments += attachment
                    append(attachment.token)
                }

                is UserInputPart.Image -> {
                    val attachment = ChatAttachmentUiState(
                        path = part.storagePath,
                        name = part.label,
                        token = part.label,
                        kind = ChatAttachmentKind.IMAGE,
                        mimeType = part.mimeType,
                        mediaId = part.mediaId,
                        imageLabel = part.label,
                    )
                    attachments += attachment
                    append(attachment.token)
                }
            }
        }
    }
    return RestoredDraft(text, attachments)
}

/** 从条目恢复出的 composer 状态。 */
internal data class RestoredDraft(
    val text: String,
    val attachments: List<ChatAttachmentUiState>,
)

/** 为派生会话生成有区分度的稳定标题，并在截断时优先保留后缀。 */
internal fun derivedConversationTitle(sourceTitle: String, operation: String): String {
    val suffix = " - $operation"
    val source = sourceTitle.trim().ifBlank { DEFAULT_CONVERSATION_TITLE }
    return source.take((CONVERSATION_TITLE_MAX_LENGTH - suffix.length).coerceAtLeast(0)).trimEnd() + suffix
}

/** 当指定会话持有全局运行槽位时，先取消它再执行树导航或复制。 */
internal fun ChatWindowState.cancelRunIfOwnedBy(conversationId: String) {
    if (activeRunConversationId == conversationId) cancelActiveRun()
}
