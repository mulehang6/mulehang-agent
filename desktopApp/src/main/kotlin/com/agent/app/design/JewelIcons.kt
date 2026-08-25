package com.agent.app.design

import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 应用自带图标的类加载器锚点。 */
internal object ApplicationIconResources

/** 设置和终端面板标签使用的统一图标尺寸。 */
internal val PANEL_TAB_ICON_SIZE = 16.dp

/** 使用随应用打包的 IntelliJ 终端工具窗口图标。 */
internal val TERMINAL_ICON_KEY: IconKey = PathIconKey(
    path = "icons/terminal.svg",
    iconClass = ApplicationIconResources::class.java,
)

/** 将业务级标题栏动作映射到 IntelliJ 图标包。 */
internal val HeaderGlyph.iconKey: IconKey
    get() = when (this) {
        HeaderGlyph.MENU -> AllIconsKeys.General.Menu
        HeaderGlyph.SHARE -> AllIconsKeys.Actions.Upload
        HeaderGlyph.SETTINGS -> AllIconsKeys.General.Settings
        HeaderGlyph.HELP -> AllIconsKeys.General.ContextHelp
        HeaderGlyph.ADD -> AllIconsKeys.General.Add
        HeaderGlyph.CODE -> AllIconsKeys.FileTypes.Any_type
        HeaderGlyph.SEARCH -> AllIconsKeys.Actions.Find
        HeaderGlyph.SEND -> AllIconsKeys.Actions.Execute
        HeaderGlyph.STOP -> AllIconsKeys.Actions.Suspend
    }

/** 将右侧工具栏业务动作映射到 IntelliJ 图标包。 */
internal val RightRailGlyph.iconKey: IconKey
    get() = when (this) {
        RightRailGlyph.CODE -> AllIconsKeys.FileTypes.Any_type
        RightRailGlyph.TERMINAL -> TERMINAL_ICON_KEY
        RightRailGlyph.DOWNLOAD -> AllIconsKeys.Actions.Download
        RightRailGlyph.UPLOAD -> AllIconsKeys.Actions.Upload
        RightRailGlyph.HISTORY -> AllIconsKeys.Vcs.History
        RightRailGlyph.COPY -> AllIconsKeys.Actions.Copy
        RightRailGlyph.FILTER -> AllIconsKeys.General.Filter
        RightRailGlyph.SETTINGS -> AllIconsKeys.General.Settings
    }

/** 返回右侧工具栏动作的可访问名称和 Tooltip 文案。 */
internal val RightRailGlyph.tooltip: String
    get() = when (this) {
        RightRailGlyph.CODE -> "代码"
        RightRailGlyph.TERMINAL -> "终端"
        RightRailGlyph.DOWNLOAD -> "下载"
        RightRailGlyph.UPLOAD -> "上传"
        RightRailGlyph.HISTORY -> "历史"
        RightRailGlyph.COPY -> "复制"
        RightRailGlyph.FILTER -> "筛选"
        RightRailGlyph.SETTINGS -> "设置"
    }
