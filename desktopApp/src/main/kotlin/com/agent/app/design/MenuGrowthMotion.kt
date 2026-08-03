package com.agent.app.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.TransformOrigin

/** 菜单内容相对其触发位置生长的空间原点。 */
internal enum class MenuGrowthOrigin {
    Dropdown,
    Context,
}

/**
 * 菜单在单个展开状态下的视觉目标值。
 */
internal data class MenuGrowthTargets(
    val scale: Float,
    val alpha: Float,
    val translationYDp: Float,
)

/**
 * Compose 菜单当前一帧使用的可动画视觉值。
 */
internal data class MenuGrowthMotion(
    val scale: Float,
    val alpha: Float,
    val translationYDp: Float,
)

/** 菜单展开时使用较完整的节奏，关闭时更快退出。 */
internal fun menuGrowthDurationMillis(expanded: Boolean): Int = if (expanded) 180 else 110

/** 返回菜单展开或收起时的目标视觉值。 */
internal fun menuGrowthTargets(expanded: Boolean): MenuGrowthTargets = if (expanded) {
    MenuGrowthTargets(scale = 1f, alpha = 1f, translationYDp = 0f)
} else {
    MenuGrowthTargets(scale = 0.96f, alpha = 0f, translationYDp = -4f)
}

/** 返回下拉菜单触发器或右键点击点对应的缩放原点。 */
internal fun menuGrowthTransformOrigin(origin: MenuGrowthOrigin): TransformOrigin = when (origin) {
    MenuGrowthOrigin.Dropdown -> TransformOrigin(0.5f, 0f)
    MenuGrowthOrigin.Context -> TransformOrigin(0f, 0f)
}

/**
 * 创建可被 DropdownMenu 持续重定向的生长动效状态。
 */
@Composable
internal fun rememberMenuGrowthMotion(
    expanded: Boolean,
    label: String,
): MenuGrowthMotion {
    val targets = menuGrowthTargets(expanded)
    val animationSpec = tween<Float>(
        durationMillis = menuGrowthDurationMillis(expanded),
        easing = MenuGrowthEasing,
    )
    val scale by animateFloatAsState(
        targetValue = targets.scale,
        animationSpec = animationSpec,
        label = "$label-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = targets.alpha,
        animationSpec = animationSpec,
        label = "$label-alpha",
    )
    val translationYDp by animateFloatAsState(
        targetValue = targets.translationYDp,
        animationSpec = animationSpec,
        label = "$label-translation-y",
    )
    return MenuGrowthMotion(
        scale = scale,
        alpha = alpha,
        translationYDp = translationYDp,
    )
}

/** 强 ease-out 让菜单快速响应，并在接近最终尺寸时柔和减速。 */
private val MenuGrowthEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
