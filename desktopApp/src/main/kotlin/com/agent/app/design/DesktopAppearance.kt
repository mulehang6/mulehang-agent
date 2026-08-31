package com.agent.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.agent.shared.session.DesktopAppearancePreferences
import com.agent.shared.session.normalizeDesktopUiScalePercent
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.lang.reflect.Method
import kotlin.math.abs
import org.jetbrains.skia.Font as SkiaFont
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/**
 * 当前设备可选择的界面与代码字体目录。
 */
internal data class DesktopFontCatalog(
    val uiFontFamilies: List<String>,
    val codeFontFamilies: List<String>,
    val awtFontFamilyNamesByFamily: Map<String, String> = emptyMap(),
    val canonicalFamilyNamesByAlias: Map<String, String> = emptyMap(),
)

/**
 * 已保存字体在当前系统中的解析结果。
 */
internal data class ResolvedDesktopFont(
    val configuredFamilyName: String?,
    val effectiveFamilyName: String?,
    val effectiveAwtFontFamilyName: String?,
    val isFallback: Boolean,
)

/**
 * 应用于 Compose 文本样式的界面与代码字体。
 */
internal data class DesktopTypography(
    val uiFontFamily: FontFamily,
    val codeFontFamily: FontFamily,
    val appliesUiFontOverride: Boolean,
    val appliesCodeFontOverride: Boolean,
)

/** 为未显式配置字体的组合层提供系统默认排版。 */
internal val LocalDesktopTypography = staticCompositionLocalOf {
    DesktopTypography(
        uiFontFamily = FontFamily.Default,
        codeFontFamily = FontFamily.Monospace,
        appliesUiFontOverride = false,
        appliesCodeFontOverride = false,
    )
}

/** 为需要额外绘制倍率的内容提供当前全局界面缩放百分比。 */
internal val LocalDesktopUiScalePercent = staticCompositionLocalOf {
    DesktopAppearancePreferences.DEFAULT_UI_SCALE_PERCENT
}

/**
 * 将持久化外观偏好与本机字体目录组合为可直接应用的桌面外观状态。
 */
internal data class DesktopAppearance(
    val preferences: DesktopAppearancePreferences,
    val fontCatalog: DesktopFontCatalog,
) {
    val uiFont: ResolvedDesktopFont
        get() = resolveDesktopFont(
            configuredFamilyName = preferences.uiFontFamily,
            availableFamilies = fontCatalog.uiFontFamilies,
            awtFontFamilyNamesByFamily = fontCatalog.awtFontFamilyNamesByFamily,
            canonicalFamilyNamesByAlias = fontCatalog.canonicalFamilyNamesByAlias,
        )

    val codeFont: ResolvedDesktopFont
        get() = resolveDesktopFont(
            configuredFamilyName = preferences.codeFontFamily,
            availableFamilies = fontCatalog.codeFontFamilies,
            awtFontFamilyNamesByFamily = fontCatalog.awtFontFamilyNamesByFamily,
            canonicalFamilyNamesByAlias = fontCatalog.canonicalFamilyNamesByAlias,
        )

    /** 返回与当前选择对应的 Compose 文本样式字体。 */
    fun typography(): DesktopTypography {
        val uiFontFamily = uiFont.effectiveFamilyName?.let(::resolveComposeFontFamily)
        val codeFontFamily = codeFont.effectiveFamilyName?.let(::resolveComposeFontFamily)
        return DesktopTypography(
            uiFontFamily = uiFontFamily ?: FontFamily.Default,
            codeFontFamily = codeFontFamily ?: FontFamily.Monospace,
            appliesUiFontOverride = uiFontFamily != null,
            appliesCodeFontOverride = codeFontFamily != null,
        )
    }
}

/**
 * 读取系统字体并生成按名称排序的界面与等宽代码字体目录。
 */
internal fun loadDesktopFontCatalog(): DesktopFontCatalog {
    val fontFamilyNames = runCatching {
        skiaSystemFontFamilyNames()
    }.getOrElse {
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
        }.getOrDefault(emptyList())
    }
    val awtFontMappings = awtFontMappingsBySystemFamily(fontFamilyNames)
    return createDesktopFontCatalog(
        fontFamilyNames = fontFamilyNames,
        awtFontFamilyNamesByFamily = awtFontMappings.preferredAwtFontFamilyNamesBySystemFamily,
        canonicalFamilyNamesByAlias = awtFontMappings.systemFamilyNamesByAwtAlias,
    )
}

/**
 * 使用指定字体名称生成目录，便于在不依赖图形环境的场景下验证筛选规则。
 */
internal fun createDesktopFontCatalog(
    fontFamilyNames: Collection<String>,
    isMonospaced: (String) -> Boolean = ::isMonospacedFontFamily,
    awtFontFamilyNamesByFamily: Map<String, String> = emptyMap(),
    canonicalFamilyNamesByAlias: Map<String, String> = emptyMap(),
): DesktopFontCatalog {
    val uiFontFamilies = fontFamilyNames
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    val resolvedCanonicalFamilyNamesByAlias = buildMap {
        canonicalFamilyNamesByAlias.forEach { (alias, familyName) ->
            val canonicalFamilyName = uiFontFamilies.firstOrNull { candidate ->
                candidate.equals(familyName, ignoreCase = true)
            } ?: return@forEach
            if (alias.isNotBlank()) put(alias, canonicalFamilyName)
        }
    }
    return DesktopFontCatalog(
        uiFontFamilies = uiFontFamilies,
        codeFontFamilies = uiFontFamilies.filter(isMonospaced),
        awtFontFamilyNamesByFamily = awtFontFamilyNamesByFamily.filterKeys { candidate ->
            uiFontFamilies.any { it.equals(candidate, ignoreCase = true) }
        },
        canonicalFamilyNamesByAlias = resolvedCanonicalFamilyNamesByAlias,
    )
}

/**
 * 通过多个代表性字符的实际字宽判断一个系统字体是否等宽。
 */
internal fun isMonospacedFontFamily(fontFamilyName: String): Boolean {
    isMonospacedSkiaFontFamily(fontFamilyName)?.let { return it }

    val font = Font(fontFamilyName, Font.PLAIN, FONT_MEASUREMENT_SIZE)
    if (!font.family.equals(fontFamilyName, ignoreCase = true)) return false
    val context = FontRenderContext(AffineTransform(), true, true)
    val widths = MONOSPACE_MEASUREMENT_CHARACTERS.map { character ->
        font.getStringBounds(character.toString(), context).width
    }
    val firstWidth = widths.firstOrNull() ?: return false
    return widths.all { width -> abs(width - firstWidth) < WIDTH_EPSILON }
}

/**
 * 在可用字体目录中解析已保存的名称；不可用名称保留在结果中并回退至默认字体。
 */
internal fun resolveDesktopFont(
    configuredFamilyName: String?,
    availableFamilies: Collection<String>,
    awtFontFamilyNamesByFamily: Map<String, String> = emptyMap(),
    canonicalFamilyNamesByAlias: Map<String, String> = emptyMap(),
): ResolvedDesktopFont {
    val configured = configuredFamilyName?.takeIf(String::isNotBlank)
    val effective = configured?.let { configuredName ->
        availableFamilies.firstOrNull { candidate -> candidate.equals(configuredName, ignoreCase = true) }
            ?: canonicalFamilyNamesByAlias.entries
                .firstOrNull { (alias, _) -> alias.equals(configuredName, ignoreCase = true) }
                ?.value
                ?.let { aliasTarget ->
                    availableFamilies.firstOrNull { candidate -> candidate.equals(aliasTarget, ignoreCase = true) }
                }
    }
    val effectiveAwtFontFamilyName = effective?.let { familyName ->
        awtFontFamilyNamesByFamily.entries
            .firstOrNull { (candidate, _) -> candidate.equals(familyName, ignoreCase = true) }
            ?.value
    }
    return ResolvedDesktopFont(
        configuredFamilyName = configured,
        effectiveFamilyName = effective,
        effectiveAwtFontFamilyName = effectiveAwtFontFamilyName,
        isFallback = configured != null && effective == null,
    )
}

/**
 * 将当前缩放按指定档位数调整，并保持在支持范围内。
 */
internal fun adjustedDesktopUiScalePercent(currentPercent: Int, steps: Int): Int =
    normalizeDesktopUiScalePercent(
        currentPercent + steps * DesktopAppearancePreferences.UI_SCALE_STEP_PERCENT,
    )

/**
 * 按全局缩放倍率创建新的 Compose 密度；保留系统字体缩放，避免把两个倍率相乘。
 */
internal fun scaledDesktopDensity(baseDensity: Density, scalePercent: Int): Density = Density(
    density = baseDensity.density * normalizeDesktopUiScalePercent(scalePercent) / 100f,
    fontScale = baseDensity.fontScale,
)

/**
 * 移除 [ProvideDesktopAppearance] 额外叠加的全局缩放，供以 AWT 像素报告尺寸的 Swing 互操作组件测量。
 *
 * JediTerm 会将实际像素网格反写为 Swing 偏好尺寸；若继续使用全局缩放后的 Compose 密度，
 * SwingPanel 会再次放大该尺寸并触发持续的终端重排。字体缩放由终端自身的外观状态负责，
 * 因此这里保留系统字体倍率但只还原布局密度。
 */
internal fun unscaledDesktopInteropDensity(contentDensity: Density, scalePercent: Int): Density = Density(
    density = contentDensity.density * DesktopAppearancePreferences.DEFAULT_UI_SCALE_PERCENT /
        normalizeDesktopUiScalePercent(scalePercent).toFloat(),
    fontScale = contentDensity.fontScale,
)

/**
 * 在实际窗口内容组合中注入全局缩放、字体和代码字体，保证窗口子组合也能读取这些外观值。
 */
@Composable
internal fun ProvideDesktopAppearance(
    appearance: DesktopAppearance,
    content: @Composable () -> Unit,
) {
    val fontOverrides = remember(appearance) { appearance.typography() }
    val baseDensity = LocalDensity.current
    val inheritedTextStyle = org.jetbrains.jewel.foundation.theme.LocalTextStyle.current
    val inheritedEditorTextStyle = org.jetbrains.jewel.foundation.theme.LocalEditorTextStyle.current
    val inheritedConsoleTextStyle = org.jetbrains.jewel.foundation.theme.LocalConsoleTextStyle.current
    val typography = remember(
        fontOverrides,
        inheritedTextStyle.fontFamily,
        inheritedEditorTextStyle.fontFamily,
        inheritedConsoleTextStyle.fontFamily,
    ) {
        DesktopTypography(
            uiFontFamily = if (fontOverrides.appliesUiFontOverride) {
                fontOverrides.uiFontFamily
            } else {
                inheritedTextStyle.fontFamily ?: FontFamily.Default
            },
            codeFontFamily = if (fontOverrides.appliesCodeFontOverride) {
                fontOverrides.codeFontFamily
            } else {
                inheritedEditorTextStyle.fontFamily ?: inheritedConsoleTextStyle.fontFamily ?: FontFamily.Monospace
            },
            appliesUiFontOverride = fontOverrides.appliesUiFontOverride,
            appliesCodeFontOverride = fontOverrides.appliesCodeFontOverride,
        )
    }
    val scaledDensity = remember(baseDensity, appearance.preferences.scalePercent) {
        scaledDesktopDensity(baseDensity, appearance.preferences.scalePercent)
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        org.jetbrains.jewel.foundation.theme.LocalTextStyle provides if (typography.appliesUiFontOverride) {
            inheritedTextStyle.copy(fontFamily = typography.uiFontFamily)
        } else {
            inheritedTextStyle
        },
        org.jetbrains.jewel.foundation.theme.LocalEditorTextStyle provides if (typography.appliesCodeFontOverride) {
            inheritedEditorTextStyle.copy(fontFamily = typography.codeFontFamily)
        } else {
            inheritedEditorTextStyle
        },
        org.jetbrains.jewel.foundation.theme.LocalConsoleTextStyle provides if (typography.appliesCodeFontOverride) {
            inheritedConsoleTextStyle.copy(fontFamily = typography.codeFontFamily)
        } else {
            inheritedConsoleTextStyle
        },
        LocalDesktopUiScalePercent provides appearance.preferences.scalePercent,
        LocalDesktopTypography provides typography,
        content = content,
    )
}

/** 使用 IDEA 的已解析字体家族创建 Compose 字体，避免 Windows 将字重误当作独立家族。 */
@OptIn(ExperimentalTextApi::class)
private fun resolveComposeFontFamily(fontFamilyName: String): FontFamily =
    FontFamily(fontFamilyName)

/** 返回 Compose/Skia 实际可解析的系统字体家族，避免 AWT 在 Windows 上把字重当成独立家族。 */
private fun skiaSystemFontFamilyNames(): List<String> {
    val fontManager = FontMgr.default
    return (0 until fontManager.familiesCount).map(fontManager::getFamilyName)
}

/**
 * 将 Skia 的真实家族名映射为 JediTerm 可使用的 AWT 名称，并迁移旧版 AWT 字体别名。
 */
private fun awtFontMappingsBySystemFamily(fontFamilyNames: Collection<String>): AwtFontMappings {
    val allFonts = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.toList()
    }.getOrDefault(emptyList())
    if (allFonts.isEmpty()) return AwtFontMappings()

    val systemFamiliesByAwtAlias = linkedMapOf<String, String>()
    val fontsBySystemFamily = linkedMapOf<String, MutableList<Font>>()
    allFonts.forEach { font ->
        val typographicFamilyName = JbrFontFamilyNameResolver.typographicFamilyName(font)
        val systemFamilyName = typographicFamilyName?.let { typographicName ->
            fontFamilyNames.firstOrNull { familyName ->
                familyName.equals(typographicName, ignoreCase = true)
            }
        } ?: fontFamilyNames.firstOrNull { familyName ->
            font.family.equals(familyName, ignoreCase = true) ||
                font.fontName.equals(familyName, ignoreCase = true)
        } ?: return@forEach
        fontsBySystemFamily.getOrPut(systemFamilyName) { mutableListOf() }.add(font)
        systemFamiliesByAwtAlias.putIfAbsent(font.family, systemFamilyName)
        systemFamiliesByAwtAlias.putIfAbsent(font.fontName, systemFamilyName)
    }
    val preferredAwtFontFamilyNamesBySystemFamily = fontsBySystemFamily.mapValues { (_, fonts) ->
        fonts.minWith(
            compareBy<Font> { font -> font.fontName.length }
                .thenBy { font -> font.fontName },
        ).fontName
    }
    return AwtFontMappings(
        preferredAwtFontFamilyNamesBySystemFamily = preferredAwtFontFamilyNamesBySystemFamily,
        systemFamilyNamesByAwtAlias = systemFamiliesByAwtAlias,
    )
}

/** 保存 Skia 真实家族和 AWT/JBR 名称之间的双向兼容映射。 */
private data class AwtFontMappings(
    val preferredAwtFontFamilyNamesBySystemFamily: Map<String, String> = emptyMap(),
    val systemFamilyNamesByAwtAlias: Map<String, String> = emptyMap(),
)

/** 使用实际渲染引擎的字宽验证等宽字体；渲染引擎不可用时由调用方退回 AWT 检测。 */
private fun isMonospacedSkiaFontFamily(fontFamilyName: String): Boolean? = runCatching {
    val typeface = FontMgr.default.matchFamilyStyle(fontFamilyName, FontStyle.NORMAL) ?: return@runCatching false
    typeface.use { resolvedTypeface ->
        SkiaFont(resolvedTypeface, FONT_MEASUREMENT_SIZE.toFloat()).use { font ->
            val widths = MONOSPACE_MEASUREMENT_CHARACTERS.map { character ->
                font.measureTextWidth(character.toString())
            }
            val firstWidth = widths.firstOrNull() ?: return@runCatching false
            widths.all { width -> abs(width - firstWidth) < WIDTH_EPSILON }
        }
    }
}.getOrNull()

/**
 * 用 JBR 提供的字体元数据把 AWT 的 Windows 别名折叠到真实字族名，与 IDEA 的字体服务保持同一解析原则。
 */
private object JbrFontFamilyNameResolver {
    private const val LOGICAL_FALLBACK = 2

    private val api: FontReflectionApi? by lazy {
        runCatching {
            val fontManagerFactoryClass = Class.forName("sun.font.FontManagerFactory")
            val fontManagerClass = Class.forName("sun.font.FontManager")
            val font2DClass = Class.forName("sun.font.Font2D")
            FontReflectionApi(
                getFontManager = fontManagerFactoryClass
                    .getDeclaredMethod("getInstance")
                    .requireAccessible(),
                findFont2D = fontManagerClass
                    .getDeclaredMethod(
                        "findFont2D",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    )
                    .requireAccessible(),
                getTypographicFamilyName = font2DClass
                    .getDeclaredMethod("getTypographicFamilyName")
                    .requireAccessible(),
            )
        }.getOrNull()
    }

    /** 返回给定 AWT 字体对应的 OpenType 真实字族名；运行环境不可访问 JBR 内部 API 时返回 null。 */
    fun typographicFamilyName(font: Font): String? = api?.typographicFamilyName(font)

    /** 保存已经验证可调用的 JBR 字体反射入口。 */
    private data class FontReflectionApi(
        val getFontManager: Method,
        val findFont2D: Method,
        val getTypographicFamilyName: Method,
    ) {
        /** 解析一个物理 AWT 字体的 OpenType typographic family 名称。 */
        fun typographicFamilyName(font: Font): String? = runCatching {
            val fontManager = getFontManager.invoke(null) ?: return@runCatching null
            val font2D = findFont2D.invoke(
                fontManager,
                font.name,
                font.style,
                LOGICAL_FALLBACK,
            ) ?: return@runCatching null
            getTypographicFamilyName.invoke(font2D) as? String
        }.getOrNull()
    }

    /** 确保 JBR 模块已经通过启动参数向当前应用开放字体反射入口。 */
    private fun Method.requireAccessible(): Method = apply {
        check(trySetAccessible()) { "JBR font metadata access is unavailable" }
    }
}

/** 用于字体字宽探测的字号。 */
private const val FONT_MEASUREMENT_SIZE = 20

/** 用于识别比例字体差异的一组代表性字符。 */
private const val MONOSPACE_MEASUREMENT_CHARACTERS = "ilMW0@# "

/** 比较浮点字宽时可接受的误差。 */
private const val WIDTH_EPSILON = 0.01
