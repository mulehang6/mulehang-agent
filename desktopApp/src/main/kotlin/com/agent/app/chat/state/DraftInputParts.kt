package com.agent.app.chat.state

import com.agent.shared.agent.api.UserInputPart

/**
 * 从 composer 的可见 token 重建发送顺序。
 *
 * 每个可见 token 都按其出现位置转换；用户删除全部 token 即表示移除附件，未被引用的附件不会
 * 在发送时偷偷追加到尾部。这样“文字、图 1、文字、图 2”可严格保留为同一个有序 part 列表。
 */
internal fun buildOrderedDraftInputParts(
    draft: String,
    attachments: List<ChatAttachmentUiState>,
): List<UserInputPart> {
    val candidates = attachments
        .filter { attachment -> attachment.token.isNotBlank() }
    val parts = mutableListOf<UserInputPart>()
    var cursor = 0
    while (candidates.isNotEmpty()) {
        val next = candidates.mapIndexedNotNull { index, attachment ->
            draft.indexOf(attachment.token, startIndex = cursor)
                .takeIf { position -> position >= 0 }
                ?.let { position -> IndexedAttachment(index, position, attachment) }
        }.minWithOrNull(
            compareBy<IndexedAttachment> { it.position }
                .thenByDescending { it.attachment.token.length }
                .thenBy { it.index },
        ) ?: break
        draft.substring(cursor, next.position)
            .takeIf(String::isNotEmpty)
            ?.let { text -> parts += UserInputPart.Text(text) }
        next.attachment.toUserInputPart()?.let(parts::add)
        cursor = next.position + next.attachment.token.length
    }
    draft.substring(cursor)
        .takeIf(String::isNotEmpty)
        ?.let { text -> parts += UserInputPart.Text(text) }
    return parts
}

/** 将一个 composer token 转为持久化、可重放的共享输入 part。 */
private fun ChatAttachmentUiState.toUserInputPart(): UserInputPart? = when (kind) {
    ChatAttachmentKind.FILE_SNAPSHOT -> UserInputPart.FileSnapshot(
        path = path,
        content = snapshotContent ?: "[文件快照不可用：$name]",
        mimeType = mimeType ?: "text/plain",
    )

    ChatAttachmentKind.IMAGE -> {
        val stableMediaId = mediaId ?: return null
        val stableLabel = imageLabel ?: name
        UserInputPart.Image(
            mediaId = stableMediaId,
            storagePath = path,
            mimeType = mimeType ?: "image/png",
            label = stableLabel,
        )
    }
}

private data class IndexedAttachment(
    val index: Int,
    val position: Int,
    val attachment: ChatAttachmentUiState,
)
