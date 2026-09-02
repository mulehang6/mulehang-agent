package com.agent.app.chat.state

import com.agent.shared.agent.api.UserInputPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 验证 composer token 到 Agent 输入 part 的严格顺序映射。 */
class DraftInputPartsTest {
    /** 文本、文件与图像必须按草稿中 token 的实际位置交错，而不是按附件条排序。 */
    @Test
    fun `should preserve text file and image ordering from draft tokens`() {
        val attachments = listOf(
            ChatAttachmentUiState(
                path = "src/App.kt",
                name = "App.kt",
                token = "@src/App.kt",
                snapshotContent = "fun main() = Unit",
                mimeType = "text/x-kotlin",
            ),
            ChatAttachmentUiState(
                path = "C:/media/image-1.png",
                name = "图1",
                token = "[图1]",
                kind = ChatAttachmentKind.IMAGE,
                mediaId = "image-1",
                imageLabel = "图1",
                mimeType = "image/png",
            ),
            ChatAttachmentUiState(
                path = "C:/media/image-2.png",
                name = "图2",
                token = "[图2]",
                kind = ChatAttachmentKind.IMAGE,
                mediaId = "image-2",
                imageLabel = "图2",
                mimeType = "image/png",
            ),
        )

        val parts = buildOrderedDraftInputParts(
            draft = "先读 @src/App.kt，再看 [图1]，最后对照 [图2]。",
            attachments = attachments,
        )

        assertEquals(7, parts.size)
        assertEquals("先读 ", assertIs<UserInputPart.Text>(parts[0]).text)
        assertEquals("src/App.kt", assertIs<UserInputPart.FileSnapshot>(parts[1]).path)
        assertEquals("，再看 ", assertIs<UserInputPart.Text>(parts[2]).text)
        assertEquals("图1", assertIs<UserInputPart.Image>(parts[3]).label)
        assertEquals("，最后对照 ", assertIs<UserInputPart.Text>(parts[4]).text)
        assertEquals("图2", assertIs<UserInputPart.Image>(parts[5]).label)
        assertEquals("。", assertIs<UserInputPart.Text>(parts[6]).text)
    }

    /** 被用户从草稿删除的 token 不会在发送时偷偷追加为附件。 */
    @Test
    fun `should omit attachments whose tokens are absent from draft`() {
        val parts = buildOrderedDraftInputParts(
            draft = "只保留正文",
            attachments = listOf(
                ChatAttachmentUiState(
                    path = "src/hidden.kt",
                    name = "hidden.kt",
                    token = "@src/hidden.kt",
                    snapshotContent = "hidden",
                ),
            ),
        )

        assertEquals(listOf(UserInputPart.Text("只保留正文")), parts)
    }
}
