package com.agent.app.chat.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证本地 LaTeX 位图在深色会话主题中保持可读。 */
class AssistantMathFormulaTest {

    /** 公式笔画必须使用亮色前景，而不能回退为 JLaTeXMath 的默认黑色。 */
    @Test
    fun `should paint latex formula with a light foreground`() {
        val image = renderLatexFormulaImage("E = mc^2", display = false)
        val opaquePixels = buildList {
            for (x in 0 until image.width) {
                for (y in 0 until image.height) {
                    image.getRGB(x, y).takeIf { color -> color ushr 24 != 0 }?.let(::add)
                }
            }
        }

        assertTrue(opaquePixels.any { color ->
            (color ushr 16 and 0xFF) > 200 &&
                (color ushr 8 and 0xFF) > 200 &&
                (color and 0xFF) > 200
        })
    }

    /** 公式位图尺寸必须按其生成密度换算为 dp，避免高 DPI 下被 Compose 二次放大。 */
    @Test
    fun `should preserve formula pixels when converting image dimensions to dp`() {
        assertEquals(20.dp, formulaImageDimensionDp(pixelSize = 30, renderScale = 1.5f))
    }
}
