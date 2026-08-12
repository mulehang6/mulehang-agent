package com.agent.app.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

/** 可选强调色，名称也是持久化标识。 */
internal enum class DesktopAccentColor(val storageValue: String, val label: String, val color: Color) {
    BLUE("blue", "蓝色", Color(0xFF548AF7)),
    TEAL("teal", "青色", Color(0xFF28A9B8)),
    GREEN("green", "绿色", Color(0xFF3DAA7A)),
    ORANGE("orange", "橙色", Color(0xFFD97A20)),
    PURPLE("purple", "紫色", Color(0xFF9A65DB)),
    ;

    companion object {
        /** 将持久化值安全转换为强调色。 */
        fun fromStorage(value: String?): DesktopAccentColor =
            entries.firstOrNull { it.storageValue == value } ?: BLUE
    }
}

/** 桌面界面所有跨组件使用的语义色板。 */
internal data class DesktopPalette(
    val isDark: Boolean,
    val background: Color,
    val headerBackground: Color,
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
    val tabActiveBackground: Color,
    val tabHoverBackground: Color,
    val tabSelectedBorder: Color,
)

/** 将“跟随系统”解析为可渲染的深色或浅色模式，便于测试系统分支。 */
internal fun resolveDesktopThemeMode(
    mode: DesktopThemeMode,
    systemIsDark: Boolean,
): DesktopThemeMode = when (mode) {
    DesktopThemeMode.SYSTEM -> if (systemIsDark) DesktopThemeMode.DARK else DesktopThemeMode.LIGHT
    else -> mode
}

/** 根据模式与强调色创建完整、可即时替换的桌面色板。 */
internal fun desktopPalette(
    mode: DesktopThemeMode,
    accent: DesktopAccentColor,
    systemIsDark: Boolean = true,
): DesktopPalette {
    val resolvedMode = resolveDesktopThemeMode(mode, systemIsDark)
    return if (resolvedMode == DesktopThemeMode.LIGHT) {
        DesktopPalette(
            isDark = false,
            background = Color(0xFFF6F7F9),
            headerBackground = Color(0xFFFFFFFF),
            workspaceBackground = Color(0xFFFFFFFF),
            sidebarBackground = Color(0xFFEFF1F4),
            panelBackground = Color(0xFFFFFFFF),
            selectedBackground = accent.color.copy(alpha = 0.18f),
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
            accent = accent.color,
            markdownLink = accent.color.copy(alpha = 0.82f),
            toolInteraction = Color(0xFF317C75),
            success = Color(0xFF378445),
            reasoning = Color(0xFF7558B7),
            danger = Color(0xFFC5423D),
            popupBackground = Color(0xFFFFFFFF),
            popupHoverBackground = Color(0xFFE8EBEF),
            popupSelectedBackground = accent.color.copy(alpha = 0.2f),
            popupBorder = Color(0xFFD5D9E0),
            terminal = TerminalPalette(
                background = Color(0xFFF7F8FA),
                foreground = Color(0xFF1F2329),
                scrollbarThumb = Color(0xFFA6ABB4),
                tabActiveBackground = accent.color.copy(alpha = 0.14f),
                tabHoverBackground = Color(0xFFE3E7EC),
                tabSelectedBorder = accent.color,
            ),
        )
    } else {
        DesktopPalette(
            isDark = true,
            background = Color(0xFF1E1F22),
            headerBackground = Color(0xFF1E1F22),
            workspaceBackground = Color(0xFF18191B),
            sidebarBackground = Color(0xFF2B2D30),
            panelBackground = Color(0xFF1E1F22),
            selectedBackground = if (accent == DesktopAccentColor.BLUE) Color(0xFF2E436E) else accent.color.copy(alpha = 0.32f),
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
            accent = accent.color,
            markdownLink = accent.color.copy(alpha = 0.68f),
            toolInteraction = Color(0xFF91CFC9),
            success = Color(0xFF5FAD65),
            reasoning = Color(0xFFB7A2F7),
            danger = Color(0xFFE37774),
            popupBackground = Color(0xFF252629),
            popupHoverBackground = Color(0xFF2E2F32),
            popupSelectedBackground = if (accent == DesktopAccentColor.BLUE) Color(0xFF194474) else accent.color.copy(alpha = 0.38f),
            popupBorder = Color(0xFF3A3B3E),
            terminal = TerminalPalette(
                background = Color(0xFF17181A),
                foreground = Color(0xFFE6E8EC),
                scrollbarThumb = Color(0xFF4B4D52),
                tabActiveBackground = if (accent == DesktopAccentColor.BLUE) Color(0xFF202A38) else accent.color.copy(alpha = 0.16f),
                tabHoverBackground = Color(0xFF24272D),
                tabSelectedBorder = if (accent == DesktopAccentColor.BLUE) Color(0xFF2F81D6) else accent.color,
            ),
        )
    }
}

private val defaultDesktopPalette = desktopPalette(DesktopThemeMode.DARK, DesktopAccentColor.BLUE)

/** 当前 Compose 子树的桌面色板，供新组件显式依赖。 */
internal val LocalDesktopPalette = compositionLocalOf { defaultDesktopPalette }

private var applicationDesktopPalette by mutableStateOf(defaultDesktopPalette)

@Volatile
private var desktopInteropPalette: DesktopPalette = defaultDesktopPalette

/** 供 Swing/JediTerm 等非 Compose 绘制路径安全读取的当前桌面色板。 */
internal val DesktopInteropPalette: DesktopPalette
    get() = desktopInteropPalette

/** 在应用根部同时提供局部 palette，并更新旧组件使用的兼容访问器。 */
@Composable
internal fun DesktopThemePaletteProvider(
    palette: DesktopPalette,
    content: @Composable () -> Unit,
) {
    SideEffect {
        applicationDesktopPalette = palette
        desktopInteropPalette = palette
    }
    CompositionLocalProvider(LocalDesktopPalette provides palette, content = content)
}

/** 以下访问器保留既有调用点，并通过 Compose state 触发整树重组。 */
internal val AppBackground: Color get() = applicationDesktopPalette.background
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
internal val AppRailBackground: Color get() = applicationDesktopPalette.headerBackground
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
internal val PopupMenuBorder: Color get() = applicationDesktopPalette.popupBorder
internal val TerminalSurfaceBackground: Color get() = applicationDesktopPalette.terminal.background
internal val TerminalTabActiveBackground: Color get() = applicationDesktopPalette.terminal.tabActiveBackground
internal val TerminalTabHoverBackground: Color get() = applicationDesktopPalette.terminal.tabHoverBackground
internal val TerminalTabSelectedBorder: Color get() = applicationDesktopPalette.terminal.tabSelectedBorder

/** 根据 palette 创建 Material 色板，避免 Material 与自定义界面分裂。 */
internal fun desktopColorScheme(palette: DesktopPalette): ColorScheme = if (palette.background.red > 0.5f) {
    lightColorScheme(
        primary = palette.accent,
        background = palette.background,
        surface = palette.sidebarBackground,
        surfaceVariant = palette.chipBackground,
        onBackground = palette.text,
        onSurface = palette.text,
        onSurfaceVariant = palette.muted,
        error = palette.danger,
    )
} else {
    darkColorScheme(
        primary = palette.accent,
        background = palette.background,
        surface = palette.sidebarBackground,
        surfaceVariant = palette.chipBackground,
        secondary = palette.success,
        error = palette.danger,
        onBackground = palette.text,
        onSurface = palette.text,
        onSurfaceVariant = palette.muted,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onError = Color.White,
    )
}
