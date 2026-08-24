package com.agent.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 桌面应用可持久化的主题模式。 */
internal enum class DesktopThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    DARK("dark", "深色"),
    LIGHT("light", "浅色"),
    ;

    companion object {
        /** 将持久化值安全转换为主题模式。 */
        fun fromStorage(value: String?): DesktopThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: DARK
    }
}

/** 产品固定强调色；设置页不再暴露可变强调色能力。 */
internal val DesktopAccentBlue = Color(0xFF548AF7)

/** 桌面界面所有跨组件使用的语义色板。 */
internal data class DesktopPalette(
    val isDark: Boolean,
    val frameBackground: Color,
    val background: Color,
    val headerBackground: Color,
    val titleBarGradientStart: Color,
    val workspaceBackground: Color,
    val sidebarBackground: Color,
    val panelBackground: Color,
    val selectedBackground: Color,
    val hoverBackground: Color,
    val userCardBackground: Color,
    val chipBackground: Color,
    val composerBackground: Color,
    val composerInputBackground: Color,
    val providerCardBackground: Color,
    val providerCardHoverBackground: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val markdownLink: Color,
    val toolInteraction: Color,
    val success: Color,
    val reasoning: Color,
    val danger: Color,
    val popupBackground: Color,
    val popupHoverBackground: Color,
    val popupSelectedBackground: Color,
    val popupBorder: Color,
    val terminal: TerminalPalette,
)

/** 终端 Compose 与 Swing/JediTerm 互操作共享的颜色。 */
internal data class TerminalPalette(
    val background: Color,
    val foreground: Color,
    val scrollbarThumb: Color,
)

/** 将“跟随系统”解析为可渲染的深色或浅色模式，便于测试系统分支。 */
internal fun resolveDesktopThemeMode(
    mode: DesktopThemeMode,
    systemIsDark: Boolean,
): DesktopThemeMode = when (mode) {
    DesktopThemeMode.SYSTEM -> if (systemIsDark) DesktopThemeMode.DARK else DesktopThemeMode.LIGHT
    else -> mode
}

/** 根据模式创建完整、可即时替换的桌面色板。 */
internal fun desktopPalette(
    mode: DesktopThemeMode,
    systemIsDark: Boolean = true,
): DesktopPalette {
    val resolvedMode = resolveDesktopThemeMode(mode, systemIsDark)
    return if (resolvedMode == DesktopThemeMode.LIGHT) {
        DesktopPalette(
            isDark = false,
            frameBackground = Color(0xFFE9EAEE),
            background = Color(0xFFE9EAEE),
            headerBackground = Color(0xFFE9EAEE),
            titleBarGradientStart = Color(0xFFD8EDF0),
            workspaceBackground = Color(0xFFFFFFFF),
            sidebarBackground = Color(0xFFE9EAEE),
            panelBackground = Color(0xFFFFFFFF),
            selectedBackground = DesktopAccentBlue.copy(alpha = 0.18f),
            hoverBackground = Color(0xFFE6E9EE),
            userCardBackground = Color(0xFFE8EAEE),
            chipBackground = Color(0xFFE8EAEE),
            composerBackground = Color(0xFFF0F2F5),
            composerInputBackground = Color(0xFFFFFFFF),
            providerCardBackground = Color(0xFFF1F3F6),
            providerCardHoverBackground = Color(0xFFE3E7EC),
            line = Color(0xFFD5D9E0),
            text = Color(0xFF202124),
            muted = Color(0xFF656A73),
            accent = DesktopAccentBlue,
            markdownLink = DesktopAccentBlue.copy(alpha = 0.82f),
            toolInteraction = Color(0xFF317C75),
            success = Color(0xFF378445),
            reasoning = Color(0xFF7558B7),
            danger = Color(0xFFC5423D),
            popupBackground = Color(0xFFFFFFFF),
            popupHoverBackground = Color(0xFFE8EBEF),
            popupSelectedBackground = DesktopAccentBlue.copy(alpha = 0.2f),
            popupBorder = Color(0xFFD5D9E0),
            terminal = TerminalPalette(
                background = Color(0xFFFFFFFF),
                foreground = Color(0xFF1F2329),
                scrollbarThumb = Color(0xFFA6ABB4),
            ),
        )
    } else {
        DesktopPalette(
            isDark = true,
            frameBackground = Color(0xFF202226),
            background = Color(0xFF202226),
            headerBackground = Color(0xFF202226),
            titleBarGradientStart = Color(0xFF28434A),
            workspaceBackground = Color(0xFF191A1C),
            sidebarBackground = Color(0xFF26282C),
            panelBackground = Color(0xFF191A1C),
            selectedBackground = Color(0xFF2E436E),
            hoverBackground = Color(0xFF35383E),
            userCardBackground = Color(0xFF43454A),
            chipBackground = Color(0xFF43454A),
            composerBackground = Color(0xFF2B2D30),
            composerInputBackground = Color(0xFF0A0B0D),
            providerCardBackground = Color(0xFF252629),
            providerCardHoverBackground = Color(0xFF38393B),
            line = Color(0xFF393B40),
            text = Color(0xFFFFFFFF),
            muted = Color(0xFF9DA0A8),
            accent = DesktopAccentBlue,
            markdownLink = DesktopAccentBlue.copy(alpha = 0.68f),
            toolInteraction = Color(0xFF91CFC9),
            success = Color(0xFF5FAD65),
            reasoning = Color(0xFFB7A2F7),
            danger = Color(0xFFE37774),
            popupBackground = Color(0xFF252629),
            popupHoverBackground = Color(0xFF2E2F32),
            popupSelectedBackground = Color(0xFF194474),
            popupBorder = Color(0xFF3A3B3E),
            terminal = TerminalPalette(
                background = Color(0xFF191A1C),
                foreground = Color(0xFFE6E8EC),
                scrollbarThumb = Color(0xFF4B4D52),
            ),
        )
    }
}

private val defaultDesktopPalette = desktopPalette(DesktopThemeMode.DARK)

/** 当前 Compose 子树的桌面色板，供新组件显式依赖。 */
internal val LocalDesktopPalette = compositionLocalOf { defaultDesktopPalette }

private var applicationDesktopPalette by mutableStateOf(defaultDesktopPalette)

/** 在应用根部同时提供局部 palette，并更新旧组件使用的兼容访问器。 */
@Composable
internal fun DesktopThemePaletteProvider(
    palette: DesktopPalette,
    content: @Composable () -> Unit,
) {
    SideEffect {
        applicationDesktopPalette = palette
    }
    CompositionLocalProvider(LocalDesktopPalette provides palette, content = content)
}

/** 保留标题栏背景 token，供尚未迁移的调用点继续链接。 */
@Suppress("unused")
internal val AppHeaderBackground: Color get() = applicationDesktopPalette.headerBackground
internal val AppWorkspaceBackground: Color get() = applicationDesktopPalette.workspaceBackground
internal val AppSidebarBackground: Color get() = applicationDesktopPalette.sidebarBackground
internal val AppPanelBackground: Color get() = applicationDesktopPalette.panelBackground
internal val AppSelectedBackground: Color get() = applicationDesktopPalette.selectedBackground
internal val AppHoverBackground: Color get() = applicationDesktopPalette.hoverBackground
internal val AppUserCardBackground: Color get() = applicationDesktopPalette.userCardBackground
internal val AppChipBackground: Color get() = applicationDesktopPalette.chipBackground
internal val ComposerBackground: Color get() = applicationDesktopPalette.composerBackground
internal val ComposerInputBackground: Color get() = applicationDesktopPalette.composerInputBackground
internal val ProviderCardBackground: Color get() = applicationDesktopPalette.providerCardBackground
internal val ProviderCardHoverBackground: Color get() = applicationDesktopPalette.providerCardHoverBackground
internal val AppLine: Color get() = applicationDesktopPalette.line
internal val AppText: Color get() = applicationDesktopPalette.text
internal val AppMuted: Color get() = applicationDesktopPalette.muted
internal val AppAccent: Color get() = applicationDesktopPalette.accent
internal val AppMarkdownLink: Color get() = applicationDesktopPalette.markdownLink
@Suppress("unused")
internal val AppToolInteraction: Color get() = applicationDesktopPalette.toolInteraction
internal val AppSuccess: Color get() = applicationDesktopPalette.success
internal val AppReasoning: Color get() = applicationDesktopPalette.reasoning
internal val AppDanger: Color get() = applicationDesktopPalette.danger
internal val PopupMenuBackground: Color get() = applicationDesktopPalette.popupBackground
internal val PopupMenuHoverBackground: Color get() = applicationDesktopPalette.popupHoverBackground
internal val PopupMenuSelectedBackground: Color get() = applicationDesktopPalette.popupSelectedBackground
/** 保留菜单边框 token，避免分拆的侧栏组件发生二进制链接回归。 */
@Suppress("unused")
internal val PopupMenuBorder: Color get() = applicationDesktopPalette.popupBorder
internal val TerminalSurfaceBackground: Color get() = applicationDesktopPalette.terminal.background
