package com.agent.app.chat.component

import com.agent.app.design.RightRailGlyph

/** 右侧工具栏上半区可承载的页面。 */
internal enum class UpperRightTool(val glyph: RightRailGlyph) {
    NOTIFICATIONS(RightRailGlyph.NOTIFICATIONS),
    SETTINGS(RightRailGlyph.SETTINGS),
}

/** 右侧工具栏下半区可承载的页面。 */
internal enum class LowerRightTool(val glyph: RightRailGlyph) {
    TERMINAL(RightRailGlyph.TERMINAL),
    CONVERSATION_TREE(RightRailGlyph.CONVERSATION_TREE),
}

/**
 * 记录右侧上下两组当前页面；同组互斥、跨组可同时显示。
 */
internal data class RightToolSelection(
    val upper: UpperRightTool? = null,
    val lower: LowerRightTool? = null,
) {
    /** 是否需要为右侧工具页保留布局空间。 */
    val visible: Boolean
        get() = upper != null || lower != null

    /** 返回所有已打开页面对应的工具栏图标。 */
    val selectedGlyphs: Set<RightRailGlyph>
        get() = buildSet {
            upper?.let { add(it.glyph) }
            lower?.let { add(it.glyph) }
        }

    /** 点击上组图标时切换或替换该组页面。 */
    fun toggle(tool: UpperRightTool): RightToolSelection =
        copy(upper = tool.takeUnless { upper == tool })

    /** 点击下组图标时切换或替换该组页面。 */
    fun toggle(tool: LowerRightTool): RightToolSelection =
        copy(lower = tool.takeUnless { lower == tool })
}

/** 把工具栏图标解析为上半区页面。 */
internal fun RightRailGlyph.toUpperRightTool(): UpperRightTool? = when (this) {
    RightRailGlyph.NOTIFICATIONS -> UpperRightTool.NOTIFICATIONS
    RightRailGlyph.SETTINGS -> UpperRightTool.SETTINGS
    else -> null
}

/** 把工具栏图标解析为下半区页面。 */
internal fun RightRailGlyph.toLowerRightTool(): LowerRightTool? = when (this) {
    RightRailGlyph.TERMINAL -> LowerRightTool.TERMINAL
    RightRailGlyph.CONVERSATION_TREE -> LowerRightTool.CONVERSATION_TREE
    else -> null
}
