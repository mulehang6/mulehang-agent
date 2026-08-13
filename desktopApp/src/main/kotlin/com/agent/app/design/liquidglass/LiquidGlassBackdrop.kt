package com.agent.app.design.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 保存 Liquid Glass 独立背景录制层与最近一次像素快照。 */
@Stable
internal class LiquidGlassBackdropState internal constructor(
    val layer: GraphicsLayer,
) {
    var originInRoot by mutableStateOf(Offset.Zero)
        internal set

    var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set

    internal var capturing: Boolean = false

    /** 将已录制的清晰背景栅格化，供 SkSL 的 child shader 采样。 */
    suspend fun refresh() {
        if (layer.size.width > 0 && layer.size.height > 0) snapshot = layer.toImageBitmap()
    }

    /** 关闭玻璃材质时释放可观察快照，避免继续持有无用像素。 */
    fun clear() {
        snapshot = null
    }
}

/** 创建与组合生命周期绑定的独立 Liquid Glass 背景状态。 */
@Composable
internal fun rememberLiquidGlassBackdropState(): LiquidGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdropState(layer) }
}

/**
 * 先录制不含玻璃材质的背景，再正常绘制完整内容。
 *
 * 玻璃控件在 [LiquidGlassBackdropState.capturing] 为 true 时只绘制清晰内容，
 * 因而不会把自身折射结果递归写入采样源。
 */
internal fun Modifier.captureLiquidGlassBackdrop(state: LiquidGlassBackdropState): Modifier =
    onGloballyPositioned { state.originInRoot = it.positionInRoot() }
        .drawWithCache {
            onDrawWithContent capture@{
                if (size.width > 0f && size.height > 0f) {
                    state.capturing = true
                    try {
                        state.layer.record { this@capture.drawContent() }
                    } finally {
                        state.capturing = false
                    }
                }
                this@capture.drawContent()
            }
        }

/** 绘制带真实背景采样、双层边缘和悬浮阴影的液态玻璃表面。 */
@Composable
internal fun LiquidGlassSurface(
    backdropState: LiquidGlassBackdropState,
    optics: LiquidGlassOptics,
    tint: Color,
    radius: Dp,
    modifier: Modifier = Modifier,
    materialAlpha: Float = 1f,
    outerShadowElevation: Dp = 16.dp,
    contactShadowElevation: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    remember { LiquidGlassRenderer.requireCompiled() }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
            .shadow(
                elevation = outerShadowElevation,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(radius),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f * materialAlpha),
                spotColor = Color.Black.copy(alpha = 0.26f * materialAlpha),
            )
            .shadow(
                elevation = contactShadowElevation,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(radius),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f * materialAlpha),
                spotColor = Color.Black.copy(alpha = 0.18f * materialAlpha),
            )
            .drawWithCache {
                val radiusPx = radius.toPx()
                onDrawWithContent {
                    if (backdropState.capturing) return@onDrawWithContent
                    backdropState.snapshot?.let { snapshot ->
                        drawIntoCanvas { canvas ->
                            LiquidGlassRenderer.draw(
                                canvas = canvas.skiaCanvas,
                                backdrop = snapshot.asSkiaBitmap(),
                                size = size,
                                sourceOffset = originInRoot - backdropState.originInRoot,
                                radiusPx = radiusPx,
                                optics = optics,
                                tint = tint,
                                alpha = materialAlpha,
                            )
                        }
                    }
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.18f * materialAlpha),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.5f * materialAlpha),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius((radiusPx - 0.5.dp.toPx()).coerceAtLeast(0f)),
                        style = Stroke(width = 0.5.dp.toPx()),
                    )
                    drawContent()
                }
            },
    ) {
        content()
    }
}
