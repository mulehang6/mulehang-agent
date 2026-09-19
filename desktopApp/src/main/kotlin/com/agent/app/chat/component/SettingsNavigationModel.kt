package com.agent.app.chat.component

import androidx.compose.runtime.staticCompositionLocalOf

/** 扩展设置页的子页面顺序与左侧导航呈现顺序一致。 */
internal enum class ExtensionSubsection(val label: String) {
    OVERVIEW("概览"),
    PACKAGES("扩展包"),
    SKILLS("Skills"),
    PROMPTS("Prompts"),
    MCP("MCP"),
    HOOKS("Hooks"),
    DIAGNOSTICS("资源诊断"),
}

/** 供 Provider 编辑器复用的宽窄布局标记。 */
internal val LocalSettingsCompact = staticCompositionLocalOf { false }
