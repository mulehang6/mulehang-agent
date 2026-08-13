package com.agent.app.design.liquidglass

import androidx.compose.runtime.Immutable
import kotlin.math.max

/** 描述独立 Liquid Glass SkSL 渲染器使用的光学参数。 */
@Immutable
internal data class LiquidGlassOptics(
    val strength: Float,
    val depth: Float,
    val curvature: Float,
    val bend: Float,
    val bendWidth: Float,
    val dispersion: Float,
    val frost: Float,
    val saturation: Float,
    val sheen: Float,
    val sheenWidth: Float,
    val sheenFalloff: Float,
    val glow: Float,
    val glowSpread: Float,
    val glowFalloff: Float,
    val specular: Float,
    val sheenAngleDegrees: Float,
    val brightness: Float,
)

/** 参考项目 `MATERIAL_OPTICS` 的原始触发器参数。 */
internal val LiquidGlassTriggerOptics = LiquidGlassOptics(
    strength = 0.05f,
    depth = 0.5f,
    curvature = 0.3f,
    bend = 0.45f,
    bendWidth = 0.16f,
    dispersion = 0.32f,
    frost = 6f,
    saturation = 1.15f,
    sheen = 0.32f,
    sheenWidth = 3f,
    sheenFalloff = 1.5f,
    glow = 0.1f,
    glowSpread = 1f,
    glowFalloff = 0.5f,
    specular = 1f,
    sheenAngleDegrees = 45f,
    brightness = 0f,
)

/** 大型内容面板使用更厚、更安静的玻璃，避免文字区产生强烈畸变。 */
internal val LiquidGlassPanelOptics = LiquidGlassTriggerOptics.copy(
    strength = 0.035f,
    depth = 0.42f,
    dispersion = 0.12f,
    frost = 8f,
    sheen = 0.22f,
)

/** 标题栏与工具栏使用略轻的玻璃，保持轮廓和背景方向感。 */
internal val LiquidGlassChromeOptics = LiquidGlassTriggerOptics.copy(
    strength = 0.045f,
    depth = 0.46f,
    dispersion = 0.16f,
    frost = 6f,
    sheen = 0.28f,
)

/** 输入表面使用低位移玻璃，保证光标和输入文字稳定可读。 */
internal val LiquidGlassInputOptics = LiquidGlassTriggerOptics.copy(
    strength = 0.025f,
    depth = 0.34f,
    dispersion = 0.08f,
    frost = 5f,
    sheen = 0.18f,
)

/** 参考项目 `GlassContextMenu.MENU_LENS` 的原始菜单参数。 */
internal val LiquidGlassMenuOptics = LiquidGlassOptics(
    strength = 0.22f,
    depth = 0.65f,
    curvature = 0.26f,
    bend = 0.65f,
    bendWidth = 0.07f,
    dispersion = 0.16f,
    frost = 3.5f,
    saturation = 1.15f,
    sheen = 0.4f,
    sheenWidth = 1f,
    sheenFalloff = 1.5f,
    glow = 0.06f,
    glowSpread = 1f,
    glowFalloff = 0.8f,
    specular = 0.8f,
    sheenAngleDegrees = 45f,
    brightness = 0.55f,
)

/** 下拉主体相对触发器的展开方向。 */
internal enum class LiquidGlassExpansionDirection(val sign: Float) {
    DOWN(1f),
    UP(-1f),
}

/** 描述液滴菜单某一动画帧的主体、细颈和内容可见度。 */
@Immutable
internal data class LiquidGlassMenuMorph(
    val bodyScaleX: Float,
    val bodyScaleY: Float,
    val travelProgress: Float,
    val neckWidthFraction: Float,
    val neckLengthFraction: Float,
    val contentAlpha: Float,
)

/** Compose spring 的稳定参数值，方便测试和减弱动态分支复用。 */
@Immutable
internal data class LiquidGlassSpringValues(
    val dampingRatio: Float,
    val stiffness: Float,
)

/** 打开时保留明显果冻感和约十个百分点的最大拉伸。 */
internal val LiquidGlassOpenSpring = LiquidGlassSpringValues(dampingRatio = 0.58f, stiffness = 420f)

/** 关闭时更快收拢，避免下拉菜单阻塞连续操作。 */
internal val LiquidGlassCloseSpring = LiquidGlassSpringValues(dampingRatio = 0.85f, stiffness = 560f)

/** 根据触发器位置和可用空间选择向上或向下展开。 */
internal fun liquidGlassExpansionDirection(
    anchorTop: Float,
    anchorBottom: Float,
    viewportHeight: Float,
    menuHeight: Float,
    gap: Float,
): LiquidGlassExpansionDirection {
    val below = viewportHeight - anchorBottom - gap
    val above = anchorTop - gap
    return if (below < menuHeight && above > below) {
        LiquidGlassExpansionDirection.UP
    } else {
        LiquidGlassExpansionDirection.DOWN
    }
}

/** 将弹簧进度转换为液滴汇聚、细颈流动和最终菜单主体形变。 */
internal fun liquidGlassMenuMorph(
    progress: Float,
    reducedMotion: Boolean,
): LiquidGlassMenuMorph {
    val p = progress.coerceIn(0f, 1.1f)
    if (reducedMotion) {
        return LiquidGlassMenuMorph(
            bodyScaleX = 1f,
            bodyScaleY = 1f,
            travelProgress = 1f,
            neckWidthFraction = 0f,
            neckLengthFraction = 0f,
            contentAlpha = p.coerceIn(0f, 1f),
        )
    }
    val gathering = (p / 0.22f).coerceIn(0f, 1f)
    val flowing = ((p - 0.12f) / 0.5f).coerceIn(0f, 1f)
    val settling = ((p - 0.48f) / 0.52f).coerceIn(0f, 1f)
    val overshoot = max(0f, p - 1f)
    return LiquidGlassMenuMorph(
        bodyScaleX = (0.28f + 0.72f * settling + gathering * (1f - settling) * 0.12f).coerceAtMost(1.1f),
        bodyScaleY = (0.08f + 0.92f * flowing + overshoot).coerceAtMost(1.1f),
        travelProgress = flowing,
        neckWidthFraction = 0.24f * (1f - settling) * flowing,
        neckLengthFraction = 0.34f * (1f - settling) * flowing,
        contentAlpha = ((p - 0.42f) / 0.24f).coerceIn(0f, 1f),
    )
}
