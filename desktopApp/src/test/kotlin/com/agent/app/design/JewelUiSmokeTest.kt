package com.agent.app.design

import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/** 验证 Jewel 关键组件可链接，并阻止旧 UI 依赖重新进入生产代码。 */
class JewelUiSmokeTest {
    /** 标题栏必须使用与 IDEA 一致的 54dp 高度度量。 */
    @Test
    fun `should use 54dp title bar metrics`() {
        assertEquals(54.dp, IDEA_TITLE_BAR_HEIGHT)
        assertEquals(IDEA_TITLE_BAR_HEIGHT, ideaTitleBarMetrics().height)
    }

    /** 关键 Jewel 入口和应用 Dialog 宿主必须能够由测试运行时加载。 */
    @Test
    fun `should link jewel theme controls markdown icons and decorated window`() {
        val classNames = listOf(
            "org.jetbrains.jewel.intui.standalone.theme.IntUiThemeKt",
            "org.jetbrains.jewel.window.DecoratedWindowKt",
            "org.jetbrains.jewel.window.TitleBarKt",
            "org.jetbrains.jewel.ui.component.TextFieldKt",
            "org.jetbrains.jewel.ui.component.TextAreaKt",
            "org.jetbrains.jewel.ui.component.MenuKt",
            "org.jetbrains.jewel.ui.component.TooltipKt",
            "org.jetbrains.jewel.ui.component.TabStripKt",
            "org.jetbrains.jewel.ui.component.SplitLayoutKt",
            "org.jetbrains.jewel.ui.component.CheckboxKt",
            "org.jetbrains.jewel.markdown.MarkdownKt",
            "com.agent.app.design.JewelDialogKt",
        )

        classNames.forEach { className -> assertNotNull(Class.forName(className), className) }
        assertNotNull(AllIconsKeys.General.Add)
        assertFalse(
            TabData.Default(
                selected = false,
                content = { _ -> },
                closable = false,
            ).selected,
        )
    }

    /** Jewel 处理器必须同时识别 autolink、GFM 删除线和表格扩展。 */
    @Test
    @OptIn(ExperimentalJewelApi::class)
    @Suppress("UnstableApiUsage")
    fun `should process enabled jewel markdown extensions`() {
        val processor = MarkdownProcessor(
            extensions = listOf(
                AutolinkProcessorExtension,
                GitHubStrikethroughProcessorExtension(),
                GitHubTableProcessorExtension,
            ),
        )
        val blocks = processor.processMarkdownDocument(
            """
            https://www.jetbrains.com and ~~removed~~

            | Name | Value |
            | --- | --- |
            | Jewel | enabled |
            """.trimIndent(),
        )

        assertTrue(blocks.any { block -> block.javaClass.simpleName == "TableBlock" })
        assertTrue(blocks.joinToString().contains("Link(destination='https://www.jetbrains.com'"))
        assertTrue(blocks.joinToString().contains("GitHubStrikethroughNode"))
    }

    /** 生产源码与显式依赖声明不得重新引用 Material、RichText 或旧 Ring 包装。 */
    @Test
    fun `should keep legacy ui systems out of production sources`() {
        val repositoryRoot = findRepositoryRoot()
        val sourceRoot = repositoryRoot.resolve("desktopApp/src/main/kotlin")
        val forbiddenTokens = listOf(
            "androidx.compose.material",
            "com.halilibo.richtext",
            "RingPrimaryButton",
            "RingInputField",
            "RingSelectChip",
            "RingTooltip",
            "LiquidGlass",
            "WindowChromeMode",
            "NativeWindowTitleBar",
        )
        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .flatMap { path ->
                    val content = path.readText()
                    forbiddenTokens
                        .filter(content::contains)
                        .map { token -> "${repositoryRoot.relativize(path)}: $token" }
                        .stream()
                }
                .toList()
        }
        val buildScript = repositoryRoot.resolve("desktopApp/build.gradle.kts").readText()

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
        assertFalse(buildScript.contains("com.halilibo.compose-richtext"))
        assertFalse(Regex("""implementation\("org\.jetbrains\.compose\.material3?""").containsMatchIn(buildScript))
    }
}

/** 从 Gradle 测试进程工作目录向上定位仓库根目录。 */
private fun findRepositoryRoot(): Path {
    var current: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (current != null) {
        if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) return current
        current = current.parent
    }
    error("Cannot locate repository root from ${System.getProperty("user.dir")}")
}
