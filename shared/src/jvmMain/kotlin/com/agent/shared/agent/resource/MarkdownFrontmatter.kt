package com.agent.shared.agent.resource

/** 宽容解析 Markdown YAML frontmatter 的最小结果，不把未知字段当成加载错误。 */
internal data class MarkdownFrontmatter(
    val fields: Map<String, String>,
    val body: String,
    val hasFrontmatter: Boolean,
) {
    /** 读取字段并去除常见单、双引号包裹。 */
    fun value(name: String): String? = fields[name.lowercase()]
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.takeIf(String::isNotBlank)

    /** 仅识别明确 true，其他值保持 false 并由调用方决定是否给出诊断。 */
    fun boolean(name: String): Boolean = value(name)?.equals("true", ignoreCase = true) == true
}

/**
 * 解析文件开头的 `---` 块。Frontmatter 缺失不是错误，调用方根据资源类型决定是否接受。
 */
internal fun parseMarkdownFrontmatter(content: String): MarkdownFrontmatter {
    val normalized = content.removePrefix("\uFEFF")
    val lines = normalized.lines()
    if (lines.firstOrNull()?.trim() != "---") {
        return MarkdownFrontmatter(fields = emptyMap(), body = normalized, hasFrontmatter = false)
    }
    val endIndex = lines.drop(1).indexOfFirst { line -> line.trim() == "---" || line.trim() == "..." }
    if (endIndex < 0) {
        return MarkdownFrontmatter(fields = emptyMap(), body = normalized, hasFrontmatter = false)
    }
    val delimiterIndex = endIndex + 1
    val fields = linkedMapOf<String, String>()
    lines.subList(1, delimiterIndex).forEach { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@forEach
        val key = line.substring(0, separator).trim().lowercase()
        if (!KEY_PATTERN.matches(key)) return@forEach
        fields[key] = line.substring(separator + 1).trim()
    }
    return MarkdownFrontmatter(
        fields = fields,
        body = lines.drop(delimiterIndex + 1).joinToString("\n"),
        hasFrontmatter = true,
    )
}

private val KEY_PATTERN = Regex("[a-z0-9_-]+")
