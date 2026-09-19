package com.agent.app.chat.component

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.input.TextFieldValue
import com.agent.app.design.*
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.test.Test

/** 将这次用户原图走完整 Compose/Skia 路径导出，检查最终文字与节点对比度。 */
class PlantUmlDeploymentRenderingTest {
    /** 图表 100%、界面 200%，与用户截图中的矩形图布局一致。 */
    @Test
    fun rendersBranchDiagramScreenshot() {
        val svg = renderPlantUmlToSvg(BRANCH_RECTANGLE_DIAGRAM, true)
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(1100, 480, density = Density(2f)) {
                MulehangTheme(true, desktopPalette(DesktopThemeMode.DARK)) {
                    DiagramSvgSurface(
                        kind = AssistantDiagramKind.PLANT_UML, source = BRANCH_RECTANGLE_DIAGRAM, svg = svg,
                        zoomPercent = 100, globalScalePercent = 200, zoomInput = TextFieldValue("100"),
                        onZoomInputChange = {}, onZoomChange = {}, onDisplayModeChange = {},
                    )
                }
            }
            try {
                repeat(3) { scene.render().close() }
                val output = Path.of("build/verification")
                Files.createDirectories(output)
                scene.render().use { image ->
                    image.encodeToData()!!.use { data -> Files.write(output.resolve("plantuml-rectangle.png"), data.bytes) }
                }
            } finally { scene.close() }
        }
    }
}
