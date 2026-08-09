package com.agent.app.chat.component

/**
 * 表示一条 assistant Markdown 回复中需要独立处理的块。
 */
internal sealed interface AssistantMarkdownBlock {
    /** 交给通用 Markdown renderer 的普通文本。 */
    data class Text(
        val content: String,
    ) : AssistantMarkdownBlock

    /** 已闭合、可交给本地图表 renderer 的 PlantUML 源码。 */
    data class PlantUml(
        val source: String,
    ) : AssistantMarkdownBlock

    /** 带语言标记的围栏代码，使用原生高亮组件显示。 */
    data class Code(
        val language: String?,
        val source: String,
    ) : AssistantMarkdownBlock

    /** 独立图片，交给 Compose 的异步图片组件显示。 */
    data class Image(
        val alt: String,
        val url: String,
    ) : AssistantMarkdownBlock

    /** 扩展 Markdown 定义列表的一项。 */
    data class DefinitionList(
        val term: String,
        val definition: String,
    ) : AssistantMarkdownBlock

    /** 允许颜色白名单的内联 HTML span。 */
    data class HtmlSpan(
        val content: String,
        val colorName: String,
    ) : AssistantMarkdownBlock

    /** 允许对齐属性的 HTML div。 */
    data class HtmlBlock(
        val content: String,
        val alignment: AssistantHtmlAlignment,
    ) : AssistantMarkdownBlock

    /** 行内 LaTeX 公式。 */
    data class InlineMath(
        val source: String,
    ) : AssistantMarkdownBlock

    /** 块级 LaTeX 公式。 */
    data class DisplayMath(
        val source: String,
    ) : AssistantMarkdownBlock

    /** 可交互的安全 HTML 折叠块。 */
    data class Details(
        val summary: String,
        val content: String,
    ) : AssistantMarkdownBlock
}

/**
 * assistant 回复的结构化 Markdown 文档，以及其独立的脚注定义。
 */
internal data class AssistantMarkdownDocument(
    val blocks: List<AssistantMarkdownBlock>,
    val footnotes: List<AssistantFootnote>,
)

/**
 * 一个已解析的 Markdown 脚注。
 */
internal data class AssistantFootnote(
    val id: String,
    val content: String,
)

/**
 * 原生支持的 HTML 对齐方式。
 */
internal enum class AssistantHtmlAlignment {
    Start,
    Center,
    End,
}

/**
 * 解析富文本库尚不支持的轻量 Markdown 扩展，并保留普通 Markdown 文本供原有 renderer 显示。
 */
internal fun parseAssistantMarkdownDocument(content: String): AssistantMarkdownDocument {
    val safeContent = content.replace(UNSAFE_HTML_CONTENT, "")
    val footnotes = FOOTNOTE_DEFINITION.findAll(safeContent)
        .map { match ->
            AssistantFootnote(
                id = requireNotNull(match.groups[1]?.value).trim(),
                content = requireNotNull(match.groups[2]?.value).trim(),
            )
        }
        .toList()
    val contentWithoutDefinitions = safeContent.replace(FOOTNOTE_DEFINITION, "")
    val footnoteIndices = footnotes.mapIndexed { index, footnote -> footnote.id to index + 1 }.toMap()
    val contentWithFootnoteMarkers = FOOTNOTE_REFERENCE.replace(contentWithoutDefinitions) { match ->
        footnoteIndices[requireNotNull(match.groups[1]?.value)]
            ?.let(::toSuperscriptNumber)
            ?: match.value
    }
    return AssistantMarkdownDocument(
        blocks = splitAssistantMarkdownBlocks(contentWithFootnoteMarkers),
        footnotes = footnotes,
    )
}

/**
 * 流式阶段也提取围栏代码，避免围栏闭合前后在正文与代码卡之间跳变。
 * PlantUML 在流式阶段保留源码，防止不完整图表触发渲染。
 */
internal fun parseAssistantMarkdownStreamingDocument(content: String): AssistantMarkdownDocument {
    val partialFence = UNTERMINATED_FENCED_CODE.find(content)
    val completedContent = partialFence?.let { content.substring(0, it.range.first) } ?: content
    val completedDocument = parseAssistantMarkdownDocument(completedContent)
    val completedBlocks = completedDocument.blocks.map { block ->
        if (block is AssistantMarkdownBlock.PlantUml) {
            AssistantMarkdownBlock.Code(language = "plantuml", source = block.source)
        } else {
            block
        }
    }
    val partialBlock = partialFence?.let { match ->
        val language = requireNotNull(match.groups[1]?.value)
            .trim()
            .substringBefore(' ')
            .lowercase()
            .ifBlank { null }
        AssistantMarkdownBlock.Code(
            language = language,
            source = requireNotNull(match.groups[2]?.value),
        )
    }
    return AssistantMarkdownDocument(
        blocks = partialBlock?.let { completedBlocks + it } ?: completedBlocks,
        footnotes = completedDocument.footnotes,
    )
}

/**
 * 从 Markdown 中提取已闭合的代码、公式、图片、定义列表和安全 HTML；其余文本交给通用 renderer。
 */
internal fun splitAssistantMarkdownBlocks(content: String): List<AssistantMarkdownBlock> {
    val blocks = mutableListOf<AssistantMarkdownBlock>()
    var cursor = 0
    while (cursor < content.length) {
        val next = findNextAssistantMarkdownBlock(content, cursor) ?: break
        val match = next.match
        content.substring(cursor, match.range.first)
            .takeIf(String::isNotEmpty)
            ?.let { text -> blocks += AssistantMarkdownBlock.Text(text) }
        blocks += next.block
        cursor = match.range.last + 1
    }
    content.substring(cursor)
        .takeIf(String::isNotEmpty)
        ?.let { text -> blocks += AssistantMarkdownBlock.Text(text) }
    return blocks
}

/** 找到当前位置之后最先出现的受支持独立 Markdown 扩展块。 */
private fun findNextAssistantMarkdownBlock(content: String, startIndex: Int): AssistantMarkdownBlockMatch? = listOfNotNull(
    FENCED_CODE.find(content, startIndex)?.let(::toCodeBlockMatch),
    DISPLAY_MATH.find(content, startIndex)?.let(::toDisplayMathBlockMatch),
    INLINE_MATH.find(content, startIndex)?.let(::toInlineMathBlockMatch),
    STANDALONE_IMAGE.find(content, startIndex)?.let(::toImageBlockMatch),
    DEFINITION_LIST.find(content, startIndex)?.let(::toDefinitionListBlockMatch),
    QUOTED_HTML_DETAILS.find(content, startIndex)?.let(::toDetailsBlockMatch),
    HTML_DETAILS.find(content, startIndex)?.let(::toDetailsBlockMatch),
    SAFE_HTML_DIV.find(content, startIndex)?.let(::toHtmlDivBlockMatch),
    SAFE_HTML_SPAN.find(content, startIndex)
        ?.takeIf { match -> isSupportedHtmlColor(requireNotNull(match.groups[1]?.value)) }
        ?.let(::toHtmlSpanBlockMatch),
).minByOrNull { candidate -> candidate.match.range.first }

/** 将围栏代码的正则匹配转换为对应的原生块。 */
private fun toCodeBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch {
    val language = requireNotNull(match.groups[1]?.value)
        .trim()
        .substringBefore(' ')
        .lowercase()
        .ifBlank { null }
    val source = requireNotNull(match.groups[2]?.value).trim()
    val block = if (language in PLANT_UML_LANGUAGES) {
        AssistantMarkdownBlock.PlantUml(source = source)
    } else {
        AssistantMarkdownBlock.Code(language = language, source = source)
    }
    return AssistantMarkdownBlockMatch(match, block)
}

/** 将单独一行的 Markdown 图片转换为原生图片块。 */
private fun toImageBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch = AssistantMarkdownBlockMatch(
    match = match,
    block = AssistantMarkdownBlock.Image(
        alt = requireNotNull(match.groups[1]?.value),
        url = requireNotNull(match.groups[2]?.value),
    ),
)

/** 将 `$$...$$` 公式转换为块级数学渲染任务。 */
private fun toDisplayMathBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch = AssistantMarkdownBlockMatch(
    match = match,
    block = AssistantMarkdownBlock.DisplayMath(
        source = requireNotNull(match.groups[1]?.value).trim(),
    ),
)

/** 将 `$...$` 公式转换为行内数学渲染任务。 */
private fun toInlineMathBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch = AssistantMarkdownBlockMatch(
    match = match,
    block = AssistantMarkdownBlock.InlineMath(
        source = requireNotNull(match.groups[1]?.value).trim(),
    ),
)

/** 将 `术语` 加 `: 定义` 的扩展 Markdown 转换为定义列表块。 */
private fun toDefinitionListBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch = AssistantMarkdownBlockMatch(
    match = match,
    block = AssistantMarkdownBlock.DefinitionList(
        term = requireNotNull(match.groups[1]?.value).trim(),
        definition = requireNotNull(match.groups[2]?.value).trim(),
    ),
)

/** 将受限的 `div align` 转换为原生对齐块。 */
private fun toHtmlDivBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch = AssistantMarkdownBlockMatch(
    match = match,
    block = AssistantMarkdownBlock.HtmlBlock(
        content = requireNotNull(match.groups[2]?.value).trim(),
        alignment = when (requireNotNull(match.groups[1]?.value).lowercase()) {
            "center" -> AssistantHtmlAlignment.Center
            "right" -> AssistantHtmlAlignment.End
            else -> AssistantHtmlAlignment.Start
        },
    ),
)

/** 将颜色白名单中的 HTML span 转换为原生文字块。 */
private fun toHtmlSpanBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch = AssistantMarkdownBlockMatch(
    match = match,
    block = AssistantMarkdownBlock.HtmlSpan(
        content = requireNotNull(match.groups[2]?.value).trim(),
        colorName = requireNotNull(match.groups[1]?.value).lowercase(),
    ),
)

/** 将 `details/summary` 转换为原生可交互的折叠块。 */
private fun toDetailsBlockMatch(match: MatchResult): AssistantMarkdownBlockMatch = AssistantMarkdownBlockMatch(
    match = match,
    block = AssistantMarkdownBlock.Details(
        summary = removeBlockQuotePrefix(requireNotNull(match.groups[1]?.value)).trim(),
        content = removeBlockQuotePrefix(requireNotNull(match.groups[2]?.value)).trim(),
    ),
)

/** 记录正则匹配及其应当渲染的块，便于按出现顺序混排。 */
private data class AssistantMarkdownBlockMatch(
    val match: MatchResult,
    val block: AssistantMarkdownBlock,
)

/** 返回可安全交给本地渲染器显示的有限 CSS 颜色名称。 */
private fun isSupportedHtmlColor(colorName: String): Boolean = colorName.lowercase() in SAFE_HTML_COLORS

/** 去除引用里的 `>` 前缀，使 details 的内容在展开后恢复正常 Markdown。 */
private fun removeBlockQuotePrefix(content: String): String = content.lineSequence()
    .joinToString(separator = "\n") { line ->
        line.removePrefix("> ").removePrefix(">")
    }

/** 将脚注序号转换为不会被 CommonMark 再解析的 Unicode 上标。 */
private fun toSuperscriptNumber(number: Int): String = number.toString().map { digit ->
    SUPERSCRIPT_DIGITS[digit] ?: digit
}.joinToString(separator = "")

private val FENCED_CODE = Regex(
    pattern = "```([^`\\r\\n]*)\\r?\\n(.*?)\\r?\\n```",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
/** 匹配流式回答末尾尚未闭合的围栏代码。 */
private val UNTERMINATED_FENCED_CODE = Regex(
    pattern = "```([^`\\r\\n]*)\\r?\\n((?:(?!\\r?\\n```)[\\s\\S])*)$",
)
private val DISPLAY_MATH = Regex(
    pattern = "\\$\\$\\s*(.*?)\\s*\\$\\$",
    options = setOf(RegexOption.DOT_MATCHES_ALL),
)
private val INLINE_MATH = Regex("(?<!\\$)\\$([^$\\r\\n]+)\\$(?!\\$)")

private val FOOTNOTE_DEFINITION = Regex("(?m)^\\[\\^([^]\\r\\n]+)]\\:\\s*(.+)\\s*$")
private val FOOTNOTE_REFERENCE = Regex("\\[\\^([^]\\r\\n]+)]")
private val UNSAFE_HTML_CONTENT = Regex(
    pattern = "<\\s*(?:script|style)\\b[^>]*>.*?<\\s*/\\s*(?:script|style)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val STANDALONE_IMAGE = Regex(
    pattern = "(?m)^\\h*(?:\\[)?!\\[([^]]*)]\\(([^\\s)]+)(?:\\s+\\\"[^\\\"]*\\\")?\\)(?:]\\([^\\r\\n)]*\\))?\\h*$",
)
private val DEFINITION_LIST = Regex("(?m)^(?!\\[\\^)([^\\r\\n:][^\\r\\n]*?)\\r?\\n:\\s+([^\\r\\n]+)")
private val SAFE_HTML_DIV = Regex(
    pattern = "<\\s*div\\b[^>]*\\balign\\s*=\\s*[\\\"']?(center|left|right)[\\\"']?[^>]*>(.*?)<\\s*/\\s*div\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val SAFE_HTML_SPAN = Regex(
    pattern = "<\\s*span\\b[^>]*\\bstyle\\s*=\\s*[\\\"'][^\\\"']*\\bcolor\\s*:\\s*([#a-zA-Z0-9]+)[^\\\"']*[\\\"'][^>]*>(.*?)<\\s*/\\s*span\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val QUOTED_HTML_DETAILS = Regex(
    pattern = "^>\\h*<\\s*details\\b[^>]*>\\h*\\r?\\n>\\h*<\\s*summary\\b[^>]*>(.*?)<\\s*/\\s*summary\\s*>(.*?)^>\\h*<\\s*/\\s*details\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
)
private val HTML_DETAILS = Regex(
    pattern = "<\\s*details\\b[^>]*>\\s*<\\s*summary\\b[^>]*>(.*?)<\\s*/\\s*summary\\s*>(.*?)<\\s*/\\s*details\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val PLANT_UML_LANGUAGES = setOf("plantuml", "puml")
private val SAFE_HTML_COLORS = setOf("red", "green", "blue", "orange", "yellow", "purple", "gray", "grey")
private val SUPERSCRIPT_DIGITS = mapOf(
    '0' to '⁰',
    '1' to '¹',
    '2' to '²',
    '3' to '³',
    '4' to '⁴',
    '5' to '⁵',
    '6' to '⁶',
    '7' to '⁷',
    '8' to '⁸',
    '9' to '⁹',
)
