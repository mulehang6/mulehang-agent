package com.agent.app.design.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证 Liquid Glass 光学、几何、弹簧与减弱动态分支。 */
class LiquidGlassRendererTest {
    /** SkSL 必须在测试环境真实编译，禁止静默使用近似表面。 */
    @Test
    fun `should compile liquid glass sksl`() {
        LiquidGlassRenderer.requireCompiled()
    }

    /** 中心位移应接近零，弯边区域应产生明显折射。 */
    @Test
    fun `should displace edge more than center`() {
        val size = Size(210f, 112f)
        val center = liquidGlassDisplacement(Offset(105f, 56f), size, 9f, LiquidGlassMenuOptics)
        val edge = liquidGlassDisplacement(Offset(208f, 56f), size, 9f, LiquidGlassMenuOptics)

        assertEquals(Offset.Zero, center)
        assertTrue(edge.getDistance() > 0.5f)
    }

    /** RGB 三通道应围绕主位移对称分离。 */
    @Test
    fun `should create symmetric rgb dispersion`() {
        val displacement = Offset(8f, -3f)
        val (red, green, blue) = liquidGlassDispersionOffsets(displacement, 0.32f)

        assertEquals(displacement, green)
        assertTrue(red.x > green.x)
        assertTrue(blue.x < green.x)
    }

    /** 圆角 SDF 应在中心为负、边界附近趋零、外部为正。 */
    @Test
    fun `should clip with rounded rectangle sdf`() {
        val size = Size(100f, 60f)
        assertTrue(liquidGlassRoundedRectDistance(Offset(50f, 30f), size, 10f) < 0f)
        assertTrue(liquidGlassRoundedRectDistance(Offset(99f, 30f), size, 10f) <= 0f)
        assertTrue(liquidGlassRoundedRectDistance(Offset(102f, 30f), size, 10f) > 0f)
    }

    /** 45 度方向光应优先照亮相对光源的内缘。 */
    @Test
    fun `should preserve directional highlight`() {
        val lit = liquidGlassDirectionalHighlight(Offset(-0.707f, -0.707f), 45f, 1.5f)
        val shadowed = liquidGlassDirectionalHighlight(Offset(0.707f, 0.707f), 45f, 1.5f)

        assertTrue(lit > shadowed)
    }

    /** 下方不足且上方更宽裕时应翻转展开方向。 */
    @Test
    fun `should choose upward expansion when bottom space is insufficient`() {
        assertEquals(
            LiquidGlassExpansionDirection.UP,
            liquidGlassExpansionDirection(420f, 456f, 500f, 112f, 6f),
        )
        assertEquals(
            LiquidGlassExpansionDirection.DOWN,
            liquidGlassExpansionDirection(80f, 116f, 500f, 112f, 6f),
        )
    }

    /** 内容在 42% 后淡入，减弱动态则取消细颈和位移。 */
    @Test
    fun `should delay content and remove morph for reduced motion`() {
        assertEquals(0f, liquidGlassMenuMorph(0.4f, reducedMotion = false).contentAlpha)
        assertTrue(liquidGlassMenuMorph(0.7f, reducedMotion = false).contentAlpha > 0f)
        val reduced = liquidGlassMenuMorph(0.5f, reducedMotion = true)
        assertEquals(0f, reduced.neckLengthFraction)
        assertEquals(1f, reduced.bodyScaleY)
    }

    /** 打开与关闭弹簧参数应保持计划中的果冻感和快速收拢。 */
    @Test
    fun `should preserve liquid glass spring targets`() {
        assertEquals(LiquidGlassSpringValues(0.58f, 420f), LiquidGlassOpenSpring)
        assertEquals(LiquidGlassSpringValues(0.85f, 560f), LiquidGlassCloseSpring)
    }
}
