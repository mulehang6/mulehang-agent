package com.agent.app.chat.state

import androidx.compose.runtime.mutableStateMapOf

/** 按工作区新会话或真实会话隔离尚未发送的输入。 */
internal class ChatDraftController(private val window: ChatWindowState) {
    private val drafts = mutableStateMapOf<DraftKey, ComposerDraft>()

    /** 当前输入目标的附件。 */
    val activeAttachments: List<ChatAttachmentUiState>
        get() = drafts[window.ui.activeTarget.draftKey()]?.attachments.orEmpty()

    /** 保存当前草稿并切到尚未落库的新会话页。 */
    fun showNewConversation(workspacePath: String) {
        rememberVisibleDraft()
        val next = drafts[DraftKey.New(workspacePath)] ?: ComposerDraft()
        window.ui = window.ui.copy(
            activeTaskId = "",
            newWorkspacePath = workspacePath,
            draft = next.text,
            draftSelectionStart = next.selectionStart,
            newConversationError = null,
        )
    }

    /** 保存当前草稿并切换到已有会话。 */
    fun showExistingConversation(conversationId: String, restored: ComposerDraft? = null) {
        if (window.findConversationOrNull(conversationId) == null) return
        rememberVisibleDraft()
        val key = DraftKey.Existing(conversationId)
        if (restored != null) drafts[key] = restored
        val next = drafts[key] ?: ComposerDraft()
        window.ui = window.ui.copy(
            activeTaskId = conversationId,
            draft = next.text,
            draftSelectionStart = next.selectionStart,
        )
        if (window.attentionController.windowVisibleAndFocused) window.attentionController.markViewed(conversationId)
    }

    /** 更新输入并仅保留文本仍引用的附件。 */
    fun updateDraft(value: String, selectionStart: Int) {
        val retainedAttachments = activeAttachments.filter { attachment -> value.contains(attachment.token) }
        val nextSelection = selectionStart.coerceIn(0, value.length)
        drafts[window.ui.activeTarget.draftKey()] = ComposerDraft(value, nextSelection, retainedAttachments)
        window.ui = window.ui.copy(
            draft = value,
            draftSelectionStart = nextSelection,
            newConversationError = null,
        )
    }

    /** 更新当前草稿附件并触发输入区重绘。 */
    fun updateAttachments(attachments: List<ChatAttachmentUiState>) {
        drafts[window.ui.activeTarget.draftKey()] =
            ComposerDraft(window.ui.draft, window.ui.draftSelectionStart, attachments)
    }

    /** 发送接受后清除来源和当前输入目标草稿。 */
    fun consumeAfterSend(source: ActiveConversationTarget) {
        drafts.remove(source.draftKey())
        drafts.remove(window.ui.activeTarget.draftKey())
        window.ui = window.ui.copy(draft = "", draftSelectionStart = 0)
    }

    /** 删除会话时丢弃尚未发送的输入。 */
    fun forgetConversation(conversationId: String) {
        drafts.remove(DraftKey.Existing(conversationId))
    }

    /** 在切换输入目标之前记录当前文本、光标和附件。 */
    private fun rememberVisibleDraft() {
        drafts[window.ui.activeTarget.draftKey()] =
            ComposerDraft(window.ui.draft, window.ui.draftSelectionStart, activeAttachments)
    }
}
