package com.agent.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.standalone.dark
import org.jetbrains.jewel.intui.markdown.standalone.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.light
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.extensions.images.Coil3ImageRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.defaults
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle

/** 与 IDEA 一致的标题栏高度，100% 缩放时对应 54px。 */
internal val IDEA_TITLE_BAR_HEIGHT = 54.dp

/** 创建固定高度的标题栏度量，保持系统控制按钮在标题栏内垂直居中。 */
internal fun ideaTitleBarMetrics(): TitleBarMetrics = TitleBarMetrics.defaults(height = IDEA_TITLE_BAR_HEIGHT)

/**
 * 应用唯一的 Jewel 主题入口，同时提供 IDEA 标题栏所需的窗口样式。
 */
@Composable
@OptIn(ExperimentalJewelApi::class)
@Suppress("UnstableApiUsage")
internal fun MulehangTheme(
    isDark: Boolean,
    palette: DesktopPalette,
    content: @Composable () -> Unit,
) {
    val themeDefinition = remember(isDark) {
        if (isDark) {
            JewelTheme.darkThemeDefinition()
        } else {
            JewelTheme.lightThemeDefinition()
        }
    }
    val titleBarStyle = TitleBarStyle.dark(
        colors = TitleBarColors.dark(
            backgroundColor = palette.headerBackground,
            inactiveBackground = palette.headerBackground,
            contentColor = Color.White,
            borderColor = Color.Transparent,
            iconButtonHoveredBackground = Color.White.copy(alpha = 0.10f),
            iconButtonPressedBackground = Color.White.copy(alpha = 0.16f),
            dropdownHoveredBackground = Color.White.copy(alpha = 0.10f),
            dropdownPressedBackground = Color.White.copy(alpha = 0.16f),
        ),
        metrics = ideaTitleBarMetrics(),
    )

    IntUiTheme(
        theme = themeDefinition,
        styling = ComponentStyling.decoratedWindow(titleBarStyle = titleBarStyle),
        swingCompatMode = true,
    ) {
        val markdownStyling = remember(isDark) {
            if (isDark) MarkdownStyling.dark() else MarkdownStyling.light()
        }
        val tableStyling = remember(isDark) {
            if (isDark) GfmTableStyling.dark() else GfmTableStyling.light()
        }
        val markdownProcessor = remember {
            MarkdownProcessor(
                extensions = listOf(
                    AutolinkProcessorExtension,
                    GitHubStrikethroughProcessorExtension(),
                    GitHubTableProcessorExtension,
                ),
            )
        }
        val platformContext = LocalPlatformContext.current
        val imageRenderer = remember(platformContext) {
            Coil3ImageRendererExtension.withDefaultLoader(platformContext)
        }
        val rendererExtensions = remember(markdownStyling, tableStyling, imageRenderer) {
            listOf<MarkdownRendererExtension>(
                GitHubStrikethroughRendererExtension,
                GitHubTableRendererExtension(tableStyling, markdownStyling),
                imageRenderer,
            )
        }
        val markdownBlockRenderer = remember(isDark, markdownStyling, rendererExtensions) {
            if (isDark) {
                MarkdownBlockRenderer.dark(markdownStyling, rendererExtensions)
            } else {
                MarkdownBlockRenderer.light(markdownStyling, rendererExtensions)
            }
        }

        ProvideMarkdownStyling(
            markdownStyling = markdownStyling,
            markdownBlockRenderer = markdownBlockRenderer,
            markdownProcessor = markdownProcessor,
        ) {
            DesktopThemePaletteProvider(palette = palette, content = content)
        }
    }
}
