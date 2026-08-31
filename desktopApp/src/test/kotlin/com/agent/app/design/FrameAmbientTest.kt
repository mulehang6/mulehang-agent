package com.agent.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** 验证从 IDEA Islands 移植的窗口根画布渐变规格。 */
class FrameAmbientTest {
    private val frameColor = Color(0xFF202226)
    private val projectColor = Color(0xFF28434A)

    /** 未完成标题栏布局时必须回退到 IDEA 的 150dp 默认锚点。 */
    @Test
    fun `should use the IDEA fallback anchor and canvas dimensions`() {
        val spec = ideaFrameAmbientSpec(anchorXPx = null, densityScale = 2f)

        assertEquals(300f, spec.anchorXPx)
        assertEquals(1_400f, spec.rightFadeWidthPx)
        assertEquals(400f, spec.heightPx)
    }

    /** 项目色应在图标锚点达到峰值，并在右侧 700dp 后完全回到窗口底板色。 */
    @Test
    fun `should use the IDEA horizontal project color ramp`() {
        val spec = FrameAmbientSpec(anchorXPx = 150f, rightFadeWidthPx = 700f, heightPx = 200f)
        val expectedProjectMix = blendFrameAmbientColors(
            from = frameColor,
            to = projectColor,
            value = IDEA_FRAME_PROJECT_COLOR_SATURATION,
        )

        assertEquals(frameColor, spec.colorAt(frameColor, projectColor, xPx = 0f, yPx = 0f))
        assertEquals(expectedProjectMix, spec.colorAt(frameColor, projectColor, xPx = 150f, yPx = 0f))
        assertEquals(frameColor, spec.colorAt(frameColor, projectColor, xPx = 850f, yPx = 0f))
    }

    /** 标题栏后的内容区必须继续读取同一张画布，而不是从纯色底板重新开始。 */
    @Test
    fun `should retain project ambience below the title bar`() {
        val spec = FrameAmbientSpec(anchorXPx = 150f, rightFadeWidthPx = 700f, heightPx = 200f)
        val contentStartColor = spec.colorAt(
            frameColor = frameColor,
            projectColor = projectColor,
            xPx = 150f,
            yPx = 55f,
        )

        assertNotEquals(frameColor, contentStartColor)
        assertEquals(frameColor, spec.colorAt(frameColor, projectColor, xPx = 150f, yPx = 200f))
    }

    /** 标题栏、固定分隔像素与内容首行必须直接采样同一根画布的连续纵坐标。 */
    @Test
    fun `should keep one ambient canvas through title bar separator and content`() {
        val spec = FrameAmbientSpec(anchorXPx = 150f, rightFadeWidthPx = 700f, heightPx = 200f)
        val titleBarColor = spec.colorAt(frameColor, projectColor, xPx = 150f, yPx = 53f)
        val separatorColor = spec.colorAt(frameColor, projectColor, xPx = 150f, yPx = 54f)
        val contentColor = spec.colorAt(frameColor, projectColor, xPx = 150f, yPx = 55f)

        assertNotEquals(Color.White, titleBarColor)
        assertNotEquals(Color.White, separatorColor)
        assertNotEquals(Color.White, contentColor)
        assertNotEquals(frameColor, titleBarColor)
        assertNotEquals(frameColor, separatorColor)
        assertNotEquals(frameColor, contentColor)
        assertNotEquals(titleBarColor, separatorColor)
        assertNotEquals(separatorColor, contentColor)
    }

    /** 一点五倍系统密度下，标题栏、固定分隔线和正文环境光起点必须共用实际布局高度。 */
    @Test
    fun `should align title bar separator and content origin for every global scale`() {
        val density = Density(1.5f)
        val separatorHeightPx = with(density) { IDEA_TITLE_BAR_SEPARATOR_HEIGHT.roundToPx() }

        assertEquals(2, separatorHeightPx)
        assertEquals(51f, ideaTitleBarContentOriginPx(baseDensity = density, scalePercent = 60))
        assertEquals(83f, ideaTitleBarContentOriginPx(baseDensity = density, scalePercent = 100))
        assertEquals(107f, ideaTitleBarContentOriginPx(baseDensity = density, scalePercent = 130))
    }

    /** 原生标题栏和缩放正文必须复用同一实际环境光画布密度。 */
    @Test
    fun `should use one scaled ambient canvas density for title bar and content`() {
        val baseDensity = Density(1.5f)
        val compactDensityScale = scaledFrameAmbientDensityScale(baseDensity, scalePercent = 60)
        val defaultDensityScale = scaledFrameAmbientDensityScale(baseDensity, scalePercent = 100)
        val enlargedDensityScale = scaledFrameAmbientDensityScale(baseDensity, scalePercent = 130)

        assertEquals(0.9f, compactDensityScale, absoluteTolerance = 0.0001f)
        assertEquals(1.5f, defaultDensityScale, absoluteTolerance = 0.0001f)
        assertEquals(1.95f, enlargedDensityScale, absoluteTolerance = 0.0001f)
        assertEquals(
            180f,
            ideaFrameAmbientSpec(anchorXPx = null, densityScale = compactDensityScale).heightPx,
            absoluteTolerance = 0.01f,
        )
        assertEquals(
            300f,
            ideaFrameAmbientSpec(anchorXPx = null, densityScale = defaultDensityScale).heightPx,
            absoluteTolerance = 0.01f,
        )
        assertEquals(
            390f,
            ideaFrameAmbientSpec(anchorXPx = null, densityScale = enlargedDensityScale).heightPx,
            absoluteTolerance = 0.01f,
        )
    }
}
