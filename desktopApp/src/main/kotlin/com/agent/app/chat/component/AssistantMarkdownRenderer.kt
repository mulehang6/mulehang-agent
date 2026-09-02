package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.extensions.markdownBlockRenderer
import org.jetbrains.jewel.markdown.Markdown
/**
 * 单条助手回答块。
 */
@Composable
internal fun AssistantMessageBlock(
    content: String,
    isStreaming: Boolean,
) {
    val document = remember(content, isStreaming) {
        if (isStreaming) {
            parseAssistantMarkdownStreamingDocument(content.trim())
        } else {
            parseAssistantMarkdownDocument(content.trim())
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        document.blocks.forEach { block ->
            when (block) {
                is AssistantMarkdownBlock.Text -> AssistantMarkdownText(block.content)
                is AssistantMarkdownBlock.Diagram -> AssistantDiagramPreview(
                    kind = block.kind,
                    source = block.source,
                )
                is AssistantMarkdownBlock.Code -> AssistantCodeBlock(block.language, block.source)
                is AssistantMarkdownBlock.Image -> AssistantMarkdownImage(block.alt, block.url)
                is AssistantMarkdownBlock.DefinitionList -> AssistantDefinitionListItem(block.term, block.definition)
                is AssistantMarkdownBlock.HtmlSpan -> AssistantSafeHtmlSpan(block.content, block.colorName)
                is AssistantMarkdownBlock.HtmlBlock -> AssistantSafeHtmlBlock(block.content, block.alignment)
                is AssistantMarkdownBlock.InlineMath -> AssistantMathFormula(block.source, display = false)
                is AssistantMarkdownBlock.DisplayMath -> AssistantMathFormula(block.source, display = true)
                is AssistantMarkdownBlock.Details -> AssistantMarkdownDetails(block.summary, block.content)
            }
        }
        AssistantFootnoteSection(document.footnotes)
    }
}

/**
 * 以 Jewel Markdown 渲染普通文本；扩展处理器和样式由应用主题统一提供。
 */
@Composable
@OptIn(ExperimentalJewelApi::class)
@Suppress("UnstableApiUsage")
internal fun AssistantMarkdownText(content: String) {
    val normalizedContent = remember(content) { normalizeAssistantMarkdown(content).trim() }
    if (normalizedContent.isBlank()) return
    if (containsAssistantMarkdownInlineExtensions(normalizedContent)) {
        AssistantMarkdownInlineExtensionsText(normalizedContent)
        return
    }
    Markdown(
        markdown = normalizedContent,
        modifier = Modifier.fillMaxWidth(),
        selectable = true,
        blockRenderer = JewelTheme.markdownBlockRenderer,
    )
}

/**
 * 将回复中的常见安全 HTML 语义降级为 Markdown，避开 Compose Desktop 对 HTML 块的字面输出。
 */
internal fun normalizeAssistantMarkdown(content: String): String = content
    .replace(UNSAFE_HTML_BLOCK, "")
    .replace(HTML_LINE_BREAK, "\n")
    .replace(HTML_BOLD, "**$1**")
    .replace(HTML_ITALIC, "*$1*")
    .replace(HTML_INLINE_CODE, "`$1`")
    .replace(HTML_BLOCK_OPEN, "\n")
    .replace(HTML_BLOCK_CLOSE, "\n")
    .replace(HTML_TAG, "")

private val UNSAFE_HTML_BLOCK = Regex(
    pattern = "<\\s*(?:script|style)\\b[^>]*>.*?<\\s*/\\s*(?:script|style)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_LINE_BREAK = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
private val HTML_BOLD = Regex(
    pattern = "<\\s*(?:b|strong)\\b[^>]*>(.*?)<\\s*/\\s*(?:b|strong)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_ITALIC = Regex(
    pattern = "<\\s*(?:i|em)\\b[^>]*>(.*?)<\\s*/\\s*(?:i|em)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_INLINE_CODE = Regex(
    pattern = "<\\s*(?:code|kbd)\\b[^>]*>(.*?)<\\s*/\\s*(?:code|kbd)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_BLOCK_OPEN = Regex("<\\s*(?:div|p|details|summary|dl|dt|dd)\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_BLOCK_CLOSE = Regex("<\\s*/\\s*(?:div|p|details|summary|dl|dt|dd)\\s*>", RegexOption.IGNORE_CASE)
private val HTML_TAG = Regex("<\\s*/?\\s*[A-Za-z][A-Za-z0-9-]*(?=\\s|/?>)[^>]*>")
