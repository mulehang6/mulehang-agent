package com.agent.app.chat.export

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证会话 Markdown 构建与文件写入。
 */
class ConversationMarkdownExporterTest {

    /**
     * 导出文件名应替换 Windows 不允许的字符。
     */
    @Test
    fun `should sanitize conversation markdown file name`() {
        assertEquals("Design---------notes", sanitizeFileName("Design\\/:*?\"<>|notes"))
    }

    /**
     * 导出 markdown 时应保留标题、状态和基础消息顺序。
     */
    @Test
    fun `should build conversation markdown transcript`() {
        val markdown = buildConversationMarkdown(
            ChatConversationUiState(
                id = "task-1",
                title = "Prototype Match",
                workspacePath = "D:\\repo\\prototype",
                items = listOf(
                    ChatMessageItem(ChatMessage(role = ChatRole.User, content = "Please refactor UI")),
                    ToolEventItem(
                        toolName = "update_plan",
                        status = ToolEventStatus.Started,
                        preview = """{"step":"Inspect"}""",
                    ),
                    ChatMessageItem(ChatMessage(role = ChatRole.Assistant, content = "Done.")),
                ),
            ),
        )

        assertEquals(true, markdown.contains("# Prototype Match"))
        assertEquals(true, markdown.contains("- Workspace: D:\\repo\\prototype"))
        assertEquals(true, markdown.contains("## User"))
        assertEquals(true, markdown.contains("## Tool `update_plan`"))
        assertEquals(true, markdown.contains("## Assistant"))
    }

    /**
     * 导出 markdown 文件应显式使用 UTF-8，避免 Windows 默认编码破坏 Unicode 内容。
     */
    @Test
    fun `should write conversation markdown using utf 8`() {
        val target = Files.createTempFile("conversation-transcript", ".md").toFile()
        val markdown = "# 中文标题\n\n工具参数: {\"emoji\":\"😀\"}\n"

        try {
            writeConversationMarkdown(target, markdown)

            assertEquals(markdown, target.readText(Charsets.UTF_8))
            assertEquals(markdown.toByteArray(Charsets.UTF_8).toList(), target.readBytes().toList())
        } finally {
            target.delete()
        }
    }
}
