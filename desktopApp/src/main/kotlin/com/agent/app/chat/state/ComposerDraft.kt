package com.agent.app.chat.state

/** 当前输入区指向未落库的新会话或已存在的会话。 */
sealed interface ActiveConversationTarget {
    /** 工作区内唯一的临时新会话页。 */
    data class New(val workspacePath: String) : ActiveConversationTarget

    /** 已保存的真实会话。 */
    data class Existing(val conversationId: String) : ActiveConversationTarget
}

/** 每个真实会话和每个工作区的新会话各自占有一份草稿。 */
sealed interface DraftKey {
    /** 尚未发送的工作区草稿。 */
    data class New(val workspacePath: String) : DraftKey

    /** 已保存会话的草稿。 */
    data class Existing(val conversationId: String) : DraftKey
}

/** 仅保存在进程内的 composer 内容和附件。 */
data class ComposerDraft(
    val text: String = "",
    val selectionStart: Int = text.length,
    val attachments: List<ChatAttachmentUiState> = emptyList(),
)

/** 把活动目标映射到内存草稿键。 */
internal fun ActiveConversationTarget.draftKey(): DraftKey = when (this) {
    is ActiveConversationTarget.New -> DraftKey.New(workspacePath)
    is ActiveConversationTarget.Existing -> DraftKey.Existing(conversationId)
}
