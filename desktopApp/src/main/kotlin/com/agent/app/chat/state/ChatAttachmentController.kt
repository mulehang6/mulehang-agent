package com.agent.app.chat.state

import com.agent.app.platform.ClipboardPngImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** 管理草稿附件的文件快照、图片存储与引用位置。 */
internal class ChatAttachmentController(private val window: ChatWindowState) {
    /**
     * 为当前会话挂载附件。
     */
    fun attachFiles(paths: List<String>) {
        paths.asSequence()
            .mapNotNull(::createFileAttachmentOrNull)
            .forEach { attachment -> appendDraftAttachment(attachment) }
    }

    /**
     * 将工作区内的文本文件作为 `@` 引用插入。真实路径与工作区真实路径比较，符号链接也不能借此
     * 逃出项目边界。
     */
    fun attachWorkspaceFile(path: String): String? {
        with(window) {
            val conversation = ui.activeConversationOrNull ?: return "请先选择工作区。"
            val workspace = runCatching {
                Paths.get(conversation.workspacePath).toRealPath()
            }.getOrElse {
                return "当前工作区不可用，无法引用文件。"
            }
            val target = runCatching { Paths.get(path).toRealPath() }.getOrElse {
                return "找不到要引用的文件。"
            }
            if (!target.startsWith(workspace) || !Files.isRegularFile(target)) {
                return "只能引用当前工作区内的普通文件。"
            }
            val relativePath = workspace.relativize(target).toString().replace('\\', '/')
            val attachment = createFileAttachmentOrNull(
                path = target,
                displayName = relativePath,
                preferredToken = "@$relativePath",
            ) ?: return "无法读取要引用的文件。"
            appendDraftAttachment(attachment, replaceActiveAtReference = true)
            return null
        }
    }

    /**
     * 把剪贴板 PNG 存入会话媒体库并在插入点写入稳定的“图 N” token。
     */
    fun addClipboardImage(image: ClipboardPngImage): String? {
        with(window) {
            val conversation = ui.activeConversationOrNull ?: return "请先选择会话。"
            val mediaStore = sessionMediaStore ?: return "当前环境未配置会话媒体库，无法粘贴图片。"
            val storedImage = runCatching {
                mediaStore.storePng(conversation.id, image.bytes)
            }.getOrElse {
                return "保存粘贴图片失败：${it.message ?: "未知错误"}"
            }
            val label = nextImageLabel(conversation.attachments)
            appendDraftAttachment(
                ChatAttachmentUiState(
                    path = storedImage.path.toString(),
                    name = label,
                    token = "[$label]",
                    kind = ChatAttachmentKind.IMAGE,
                    mimeType = storedImage.mimeType,
                    mediaId = storedImage.mediaId,
                    imageLabel = label,
                ),
            )
            return null
        }
    }

    /**
     * 从当前会话输入区移除指定路径的附件并重新估算上下文占用。
     */
    fun removeAttachment(path: String) {
        with(window) {
            val removedTokens = ui.activeConversationOrNull
                ?.attachments
                ?.filter { attachment -> attachment.path == path }
                ?.map(ChatAttachmentUiState::token)
                .orEmpty()
            mutateActiveConversation { conversation ->
                val attachments = conversation.attachments.filterNot { it.path == path }
                conversation.copy(
                    attachments = attachments,
                    contextUsageFraction = estimateContextUsage(
                        items = conversation.items,
                        attachmentCount = attachments.size,
                        contextWindow = contextWindowForConversation(conversation),
                    ),
                )
            }
            if (removedTokens.isNotEmpty()) {
                val nextDraft = removedTokens.fold(ui.draft) { draft, token -> draft.replace(token, "") }
                ui = ui.copy(
                    draft = nextDraft,
                    draftSelectionStart = ui.draftSelectionStart.coerceAtMost(nextDraft.length),
                )
            }
        }
    }

    /** 将用户通过文件选择器显式添加的文件转换为不可变文本快照附件。 */
    private fun createFileAttachmentOrNull(path: String): ChatAttachmentUiState? = runCatching {
        Paths.get(path).toRealPath()
    }.getOrNull()?.let(::createFileAttachmentOrNull) ?: path
        .takeIf(String::isNotBlank)
        ?.let { unresolvedPath ->
            val displayName = unresolvedPath.substringAfterLast('\\').substringAfterLast('/')
            ChatAttachmentUiState(
                path = unresolvedPath,
                name = displayName,
                snapshotContent = "[文件快照不可用：$displayName]",
                mimeType = "text/plain",
            )
        }

    /** 读取一个常规文件的当前内容；发送后的历史只使用这份快照，不会再次读取工作区。 */
    private fun createFileAttachmentOrNull(
        path: Path,
        displayName: String = path.fileName.toString(),
        preferredToken: String = "@$displayName",
    ): ChatAttachmentUiState? {
        if (!Files.isRegularFile(path)) return null
        val snapshot = readFileSnapshot(path) ?: return null
        return ChatAttachmentUiState(
            path = path.toString(),
            name = displayName,
            token = preferredToken,
            kind = ChatAttachmentKind.FILE_SNAPSHOT,
            snapshotContent = snapshot.content,
            mimeType = snapshot.mimeType,
        )
    }

    /**
     * 在当前选择位置插入附件 token。选择 `@` 文件时，用该引用片段替换而不是再追加一个 token。
     */
    private fun appendDraftAttachment(
        attachment: ChatAttachmentUiState,
        replaceActiveAtReference: Boolean = false,
    ) {
        with(window) {
            val conversation = ui.activeConversationOrNull ?: return
            val existing = conversation.attachments.firstOrNull { current -> current.path == attachment.path }
            val attachmentToInsert = existing ?: attachment.copy(
                token = uniqueAttachmentToken(attachment.token, conversation.attachments),
            )
            if (existing == null) {
                mutateActiveConversation { current ->
                    val attachments = current.attachments + attachmentToInsert
                    current.copy(
                        attachments = attachments,
                        contextUsageFraction = estimateContextUsage(
                            items = current.items,
                            attachmentCount = attachments.size,
                            contextWindow = contextWindowForConversation(current),
                        ),
                    )
                }
            }

            val draft = ui.draft
            val selection = ui.draftSelectionStart.coerceIn(0, draft.length)
            val referenceRange = if (replaceActiveAtReference) {
                activeAtReferenceRange(draft, selection)
            } else {
                null
            }
            val replacementStart = referenceRange?.first ?: selection
            val replacementEndExclusive = referenceRange?.last?.plus(1) ?: selection
            val nextDraft = draft.replaceRange(
                replacementStart,
                replacementEndExclusive,
                attachmentToInsert.token,
            )
            ui = ui.copy(
                draft = nextDraft,
                draftSelectionStart = replacementStart + attachmentToInsert.token.length,
            )
        }
    }

    /** 返回光标前尚未完成的 `@path` 输入范围；电子邮件等普通文本不视为文件引用。 */
    private fun activeAtReferenceRange(
        draft: String,
        selection: Int,
    ): IntRange? {
        if (selection == 0) return null
        val atIndex = draft.lastIndexOf('@', startIndex = selection - 1)
        if (atIndex < 0) return null
        if (atIndex > 0 && !draft[atIndex - 1].isWhitespace()) return null
        if (draft.substring(atIndex + 1, selection).any(Char::isWhitespace)) return null
        return atIndex until selection
    }

    /** 在同一条草稿中为同名文件或图片生成不会混淆顺序的可见 token。 */
    private fun uniqueAttachmentToken(
        preferredToken: String,
        attachments: List<ChatAttachmentUiState>,
    ): String {
        if (attachments.none { attachment -> attachment.token == preferredToken }) return preferredToken
        var index = 2
        while (attachments.any { attachment -> attachment.token == "$preferredToken ($index)" }) {
            index += 1
        }
        return "$preferredToken ($index)"
    }

    /** 根据已在草稿中的图号生成下一个稳定编号。 */
    private fun nextImageLabel(attachments: List<ChatAttachmentUiState>): String {
        val highestImageNumber = attachments
            .mapNotNull { attachment -> IMAGE_LABEL_PATTERN.matchEntire(attachment.imageLabel.orEmpty()) }
            .maxOfOrNull { match -> match.groupValues[1].toIntOrNull() ?: 0 }
            ?: 0
        return "图${highestImageNumber + 1}"
    }

    /** 将文本文件限制在安全、可预测的大小内，并为非文本内容保留清晰说明。 */
    private fun readFileSnapshot(path: Path): FileSnapshot? = runCatching {
        val mimeType = Files.probeContentType(path) ?: "text/plain"
        val size = Files.size(path)
        if (size > MAX_FILE_SNAPSHOT_BYTES) {
            return@runCatching FileSnapshot(
                content = "[文件快照未展开：文件超过 ${MAX_FILE_SNAPSHOT_BYTES / 1024} KB 限制。]",
                mimeType = mimeType,
            )
        }
        val bytes = Files.readAllBytes(path)
        val content = if (bytes.any { byte -> byte == 0.toByte() }) {
            "[文件快照未展开：检测到二进制内容。]"
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        FileSnapshot(content = content, mimeType = mimeType)
    }.getOrNull()

}

/** 已读取文件的不可变内容与探测到的 MIME 类型。 */
private data class FileSnapshot(
    val content: String,
    val mimeType: String,
)

private val IMAGE_LABEL_PATTERN = Regex("图(\\d+)")
private const val MAX_FILE_SNAPSHOT_BYTES = 1_024 * 1_024
