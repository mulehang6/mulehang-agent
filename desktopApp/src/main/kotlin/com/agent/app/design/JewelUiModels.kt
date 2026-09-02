package com.agent.app.design

import androidx.compose.ui.graphics.Color

/** 标题栏、Composer 与侧栏共享的业务动作图标。 */
internal enum class HeaderGlyph {
    MENU,
    SHARE,
    SETTINGS,
    HELP,
    ADD,
    CODE,
    SEARCH,
    SEND,
    STOP,
}

/** 右侧工具栏可切换的业务视图。 */
internal enum class RightRailGlyph {
    CODE,
    TERMINAL,
    DOWNLOAD,
    UPLOAD,
    HISTORY,
    COPY,
    FILTER,
    NOTIFICATIONS,
    SETTINGS,
}

/** 右侧工具栏中的一个动作模型。 */
internal data class RightRailButtonModel(
    val glyph: RightRailGlyph,
    val active: Boolean = false,
)

/** 返回当前 Air 信息架构保留的右侧工具栏分组。 */
internal fun buildRightRailGroups(): List<List<RightRailButtonModel>> = listOf(
    listOf(RightRailButtonModel(glyph = RightRailGlyph.TERMINAL)),
    listOf(
        RightRailButtonModel(glyph = RightRailGlyph.NOTIFICATIONS),
        RightRailButtonModel(glyph = RightRailGlyph.SETTINGS),
    ),
)

/** 返回业务选择行的状态色，Jewel 原生下拉菜单不调用此函数。 */
internal fun selectMenuItemBackground(
    selected: Boolean,
    hovered: Boolean,
    enabled: Boolean,
    hoverBackground: Color = PopupMenuHoverBackground,
): Color = when {
    selected -> PopupMenuSelectedBackground
    hovered && enabled -> hoverBackground
    else -> Color.Transparent
}
