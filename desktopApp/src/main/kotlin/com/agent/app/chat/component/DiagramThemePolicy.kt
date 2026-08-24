package com.agent.app.chat.component

/** 判断 Mermaid 源码是否已声明自己的主题或主题变量。 */
internal fun hasMermaidSourceThemeConfiguration(source: String): Boolean =
    MERMAID_THEME_DIRECTIVE.containsMatchIn(source) ||
        MERMAID_THEME_FRONT_MATTER.containsMatchIn(source)

/** Mermaid 仅在源码没有主题配置时继承应用的深浅色方案。 */
internal fun shouldApplyMermaidAutomaticTheme(source: String): Boolean =
    !hasMermaidSourceThemeConfiguration(source)

private val MERMAID_THEME_DIRECTIVE = Regex(
    """(?is)%%\{\s*(?:init|config)\s*:.*?["']?theme(?:Variables)?["']?\s*:""",
)
private val MERMAID_THEME_FRONT_MATTER = Regex(
    """(?is)\A\s*---\s*\r?\n.*?(?m)^\s*theme(?:Variables)?\s*:""",
)
