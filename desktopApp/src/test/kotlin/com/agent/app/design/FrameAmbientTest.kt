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

    /** 一点五倍缩放下，Jewel 的一 dp 分隔区按整数布局像素占用两像素。 */
    @Test
    fun `should align the separator paint and content origin to integer layout pixels`() {
        val density = Density(1.5f)
        val titleBarHeightPx = with(density) { IDEA_TITLE_BAR_HEIGHT.roundToPx() }
        val separatorHeightPx = with(density) { IDEA_TITLE_BAR_SEPARATOR_HEIGHT.roundToPx() }
        val contentOriginYPx = (titleBarHeightPx + separatorHeightPx).toFloat()

        assertEquals(81, titleBarHeightPx)
        assertEquals(2, separatorHeightPx)
        assertEquals(83f, contentOriginYPx)
    }
}
