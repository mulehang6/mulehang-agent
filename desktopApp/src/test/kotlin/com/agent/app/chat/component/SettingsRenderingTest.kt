@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.agent.app.design.*
import com.agent.shared.settings.model.*
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.test.*

/** 使用真实 Compose/Jewel 内容离屏渲染，不启动应用或读取用户设置。 */
class SettingsRenderingTest {
    /** 验证各宽度的布局，并产出可审阅的真实组件截图。 */
    @Test
    fun rendersResponsiveSettingsAndPreservesComposition() {
        SwingUtilities.invokeAndWait {
            val width = mutableStateOf(900)
            val scroll = ScrollState(0)
            val subsection = mutableStateOf(ExtensionSubsection.PACKAGES)
            var rememberedDraft: MutableState<String>? = null
            var mounts = 0
            val scene = ImageComposeScene(900, 900) {
                val palette = desktopPalette(DesktopThemeMode.DARK)
                MulehangTheme(true, palette) {
                    Box(Modifier.width(width.value.dp).fillMaxHeight().background(palette.workspaceBackground).padding(12.dp)) {
                        ResponsiveSettingsLayout(
                            compact = width.value < 600,
                            section = SettingsSection.EXTENSIONS,
                            sections = SettingsSection.entries,
                            expandedSections = setOf(SettingsSection.EXTENSIONS),
                            appearanceSubsections = appearanceSubsectionsFor(ConfigLayer.USER),
                            appearanceSubsection = AppearanceSubsection.THEME,
                            extensionSubsections = ExtensionSubsection.entries,
                            extensionSubsection = subsection.value,
                            onSectionChange = {},
                            onParentClick = {},
                            onAppearanceSubsectionChange = {},
                            onExtensionSubsectionChange = { subsection.value = it },
                        ) { compact ->
                            val draft = remember { mutableStateOf("未保存的 Git 草稿").also { mounts++ } }
                            rememberedDraft = draft
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    CompositionLocalProvider(LocalSettingsCompact provides compact) {
                                        ExtensionSettingsContent(
                                            subsection = subsection.value,
                                            document = SettingsDocument(hooks = AgentHookSettings(mapOf(
                                                AgentHookEvent.SESSION_START to listOf(AgentHookMatcher(hooks = listOf(
                                                    AgentHookCommand(command = "jbcontext index --silent", runAsync = true, timeout = 2),
                                                ))),
                                            ))), layer = ConfigLayer.USER,
                                            projectRoot = null, userHome = Path.of("verification-home"),
                                            extensionPackages = emptyList(), loadedSkills = emptyList(), resourceDiagnostics = emptyList(),
                                            mcpServers = emptyList(), savedMcpServers = emptyList(), savedHooks = AgentHookSettings(),
                                            mcpConnectionStatuses = emptyList(), mcpJsonEditorState = remember { McpJsonEditorState() },
                                            mcpReloadPending = false, mcpRetryEnabled = true,
                                            onRetryMcp = {}, onSaveMcp = {}, onSaveHooks = {}, onSaveExtensions = {},
                                            onDocumentChange = {}, onChangeNotification = {}, onValidationErrorChange = { _, _ -> },
                                            onResourceFilesChanged = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            try {
                repeat(3) { scene.render().close() }
                assertTrue(
                    ExtensionSubsection.entries.all { entry -> containsText(scene, entry) },
                    "宽屏必须显示完整的扩展子菜单",
                )
                assertTrue(
                    nodes(scene).any { it.config.getOrNull(SemanticsProperties.StateDescription) == "已展开" },
                    "宽屏父项必须暴露展开状态",
                )
                val packageField = nodes(scene).first { it.config.contains(SemanticsActions.SetText) }
                assertTrue(packageField.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("draft-package")))
                for (nextWidth in listOf(900, 600, 599, 480, 360, 900)) {
                    width.value = nextWidth
                    repeat(3) { scene.render().close() }
                    assertEquals(1, mounts, "切换宽度不得销毁内容")
                    assertEquals("未保存的 Git 草稿", rememberedDraft?.value)
                    assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "draft-package" })
                    save(scene, "settings-${nextWidth}dp", cropWidth = nextWidth)
                }
                subsection.value = ExtensionSubsection.HOOKS
                repeat(2) { scene.render().close() }
                assertTrue(containsAgentHooksText(scene))
                save(scene, "settings-hooks-page-900dp")
                width.value = 360
                repeat(3) { scene.render().close() }
                assertTrue(containsAgentHooksText(scene))
                subsection.value = ExtensionSubsection.PACKAGES
                repeat(2) { scene.render().close() }
                assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "draft-package" })
                save(scene, "settings-packages-page-360dp", cropWidth = 360)
            } finally { scene.close() }
        }
    }

    /** 原始日志图在不同界面倍率下仍完整放入图表视口。 */
    @Test
    fun rendersOriginalDiagramAtMultipleUiScales() {
        val source = LOG_COMPONENT_DIAGRAM
        val svg = renderPlantUmlToSvg(source, isDark = true)
        for (scale in listOf(100, 150, 200)) {
            SwingUtilities.invokeAndWait {
                val scene = ImageComposeScene(1400, 700, density = Density(scale / 100f)) {
                    MulehangTheme(true, desktopPalette(DesktopThemeMode.DARK)) {
                        DiagramSvgSurface(
                            kind = AssistantDiagramKind.PLANT_UML, source = source, svg = svg,
                            zoomPercent = 100, globalScalePercent = scale, zoomInput = TextFieldValue("100"),
                            onZoomInputChange = {}, onZoomChange = {}, onDisplayModeChange = {},
                        )
                    }
                }
                try { repeat(2) { scene.render().close() }; save(scene, "plantuml-ui-${scale}") }
                finally { scene.close() }
            }
        }
    }

    /** 把实际场景导出到被忽略的构建目录，避免把机器路径写入仓库。 */
    private fun save(scene: ImageComposeScene, name: String, cropWidth: Int? = null) {
        val directory = Path.of("build/verification")
        Files.createDirectories(directory)
        scene.render().use { image ->
            image.encodeToData()!!.use { data ->
                val file = directory.resolve("$name.png")
                if (cropWidth == null) Files.write(file, data.bytes)
                else {
                    val raster = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(data.bytes))
                    javax.imageio.ImageIO.write(raster.getSubimage(0, 0, cropWidth, raster.height), "png", file.toFile())
                }
            }
        }
    }

    /** 遍历实际语义树，通过真实输入动作验证编辑器草稿。 */
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }

    /** 读取真实 Compose 文本语义，验证页面切换没有继续渲染旧子页。 */
    private fun containsAgentHooksText(scene: ImageComposeScene): Boolean =
        nodes(scene).any { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Agent Hooks" }
        }

    /** 判断导航或内容中是否存在指定的可访问文本。 */
    private fun containsText(scene: ImageComposeScene, expected: ExtensionSubsection): Boolean =
        nodes(scene).any { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == expected.label }
        }
}
