package com.agent.app.chat.export

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.chat.model.AnsweredQuestionsItem
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import java.io.File

/**
 * 使用 UTF-8 将 Markdown 导出到磁盘，避免平台默认编码破坏 Unicode 内容。
 */
internal fun writeConversationMarkdown(target: File, markdown: String) {
    target.writeText(markdown, Charsets.UTF_8)
}

/**
 * 生成会话 Markdown。
 */
internal fun buildConversationMarkdown(conversation: ChatConversationUiState): String = buildString {
    appendLine("# ${conversation.title}")
    appendLine()
    appendLine("- Workspace: ${conversation.workspacePath}")
    appendLine("- Status: ${conversation.executionState}")
    appendLine()
    conversation.items.forEach { item ->
        when (item) {
            is ChatMessageItem -> {
                appendLine("## ${if (item.message.role == ChatRole.User) "User" else "Assistant"}")
                appendLine(item.message.content)
            }

            is ReasoningItem -> {
                appendLine("## Reasoning")
                appendLine(item.displayText)
            }

            is AnsweredQuestionsItem -> {
                appendLine("## Answers")
                item.answers.forEach { answer ->
                    appendLine("### ${answer.question}")
                    appendLine(answer.answer)
                }
            }

            is ToolEventItem -> {
                appendLine("## Tool `${item.toolName}`")
                item.preview?.takeIf(String::isNotBlank)?.let(::appendLine)
                item.errorMessage?.takeIf(String::isNotBlank)?.let {
                    appendLine("> **Error:** $it")
                }
            }
        }
        appendLine()
    }
}

/**
 * 将标题中的 Windows 非法文件名字符替换为连字符。
 */
internal fun sanitizeFileName(title: String): String =
    title.replace(Regex("""[\\/:*?"<>|]"""), "-")
