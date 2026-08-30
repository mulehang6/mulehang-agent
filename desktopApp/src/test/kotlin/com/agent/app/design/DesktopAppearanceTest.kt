package com.agent.app.design

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.shared.session.DesktopAppearancePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证桌面外观的缩放边界、字体目录和缺失字体回退。 */
class DesktopAppearanceTest {

    /** 快捷键调整缩放时必须始终落在受支持的首尾档位内。 */
    @Test
    fun `should keep adjusted ui scale within supported range`() {
        assertEquals(50, adjustedDesktopUiScalePercent(currentPercent = 50, steps = -1))
        assertEquals(130, adjustedDesktopUiScalePercent(currentPercent = 120, steps = 1))
        assertEquals(200, adjustedDesktopUiScalePercent(currentPercent = 200, steps = 1))
    }

    /** 代码字体目录只保留字宽检测为等宽的候选，并按名称稳定排序。 */
    @Test
    fun `should list only monospaced code font candidates`() {
        val catalog = createDesktopFontCatalog(
            fontFamilyNames = listOf("UI Sans", "Mono Code", "Display"),
            isMonospaced = { it == "Mono Code" },
        )

        assertEquals(listOf("Display", "Mono Code", "UI Sans"), catalog.uiFontFamilies)
        assertEquals(listOf("Mono Code"), catalog.codeFontFamilies)
    }

    /** 已保存但已卸载的字体保留配置名称，并在运行时安全回退到系统默认。 */
    @Test
    fun `should preserve missing font configuration while resolving fallback`() {
        val appearance = DesktopAppearance(
            preferences = DesktopAppearancePreferences(
                uiFontFamily = "Removed UI Font",
                codeFontFamily = "Removed Code Font",
            ),
            fontCatalog = DesktopFontCatalog(
                uiFontFamilies = listOf("Available UI Font"),
                codeFontFamilies = listOf("Available Code Font"),
            ),
        )

        assertEquals("Removed UI Font", appearance.uiFont.configuredFamilyName)
        assertEquals(null, appearance.uiFont.effectiveFamilyName)
        assertNull(appearance.uiFont.effectiveAwtFontFamilyName)
        assertTrue(appearance.uiFont.isFallback)
        assertEquals("Removed Code Font", appearance.codeFont.configuredFamilyName)
        assertTrue(appearance.codeFont.isFallback)
        assertFalse(resolveDesktopFont(null, emptyList()).isFallback)
    }

    /** 旧版 AWT 字重别名应解析到 Compose 使用的真实字体家族，并保留终端可用的名称。 */
    @Test
    fun `should resolve legacy awt font alias to the actual family`() {
        val resolved = resolveDesktopFont(
            configuredFamilyName = "AnnotationM NF ExtraBold",
            availableFamilies = listOf("AnnotationM Nerd Font"),
            awtFontFamilyNamesByFamily = mapOf("AnnotationM Nerd Font" to "AnnotationM NF"),
            canonicalFamilyNamesByAlias = mapOf(
                "AnnotationM NF ExtraBold" to "AnnotationM Nerd Font",
            ),
        )

        assertEquals("AnnotationM NF ExtraBold", resolved.configuredFamilyName)
        assertEquals("AnnotationM Nerd Font", resolved.effectiveFamilyName)
        assertEquals("AnnotationM NF", resolved.effectiveAwtFontFamilyName)
        assertFalse(resolved.isFallback)
    }

    /** 缩放密度只增加全局倍率，保留系统字体倍率，使 dp 与 sp 同步缩放一次。 */
    @Test
    fun `should scale layout and text while preserving system font scale`() {
        val density = scaledDesktopDensity(
            baseDensity = Density(density = 2f, fontScale = 1.25f),
            scalePercent = 150,
        )

        assertEquals(3f, density.density)
        assertEquals(1.25f, density.fontScale)
        with(density) {
            assertEquals(30f, 10.dp.toPx())
            assertEquals(37.5f, 10.sp.toPx())
        }
    }
}
