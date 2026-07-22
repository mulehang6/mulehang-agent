package com.agent.app.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Air 浮动侧栏的暗色磨砂材质参数。
 */
@Immutable
internal data class AirSidebarStyleTokens(
    val cornerRadiusDp: Int,
    val shadowElevationDp: Int,
    val blurRadiusPx: Float,
    val tintAlpha: Float,
    val borderAlpha: Float,
    val fallbackColor: Color,
)

/**
 * Air 浮动侧栏的默认磨砂材质。
 */
internal val AirSidebarStyle = AirSidebarStyleTokens(
    cornerRadiusDp = 12,
    shadowElevationDp = 16,
    blurRadiusPx = 22f,
    tintAlpha = 0.78f,
    borderAlpha = 0.075f,
    fallbackColor = Color(0xFF1D1F21),
)

/**
 * 绘制接近 Air 的暗色磨砂侧栏；背景副本被模糊和染色，前景内容保持清晰。
 */
@Composable
internal fun AirSidebarSurface(
    backdropState: WorkspaceBackdropState,
    sidebarOrigin: Offset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(AirSidebarStyle.cornerRadiusDp.dp)
    val blurredLayer = rememberGraphicsLayer()
    Surface(
        modifier = modifier.shadow(
            elevation = AirSidebarStyle.shadowElevationDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.28f),
            spotColor = Color.Black.copy(alpha = 0.38f),
        ),
        shape = shape,
        color = AirSidebarStyle.fallbackColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = AirSidebarStyle.borderAlpha)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val recordedSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    val sourceReady =
                        backdropState.layer.size.width > 0 && backdropState.layer.size.height > 0
                    if (sourceReady && recordedSize.width > 0 && recordedSize.height > 0) {
                        val offset = workspaceBackdropOffset(backdropState.originInRoot, sidebarOrigin)
                        blurredLayer.record(this, layoutDirection, recordedSize) {
                            translate(offset.x, offset.y) {
                                drawLayer(backdropState.layer)
                            }
                        }
                        blurredLayer.renderEffect = BlurEffect(
                            radiusX = AirSidebarStyle.blurRadiusPx,
                            radiusY = AirSidebarStyle.blurRadiusPx,
                            edgeTreatment = TileMode.Clamp,
                        )
                        drawLayer(blurredLayer)
                    }
                    drawRect(AirSidebarStyle.fallbackColor.copy(alpha = AirSidebarStyle.tintAlpha))
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.045f),
                            0.22f to Color.White.copy(alpha = 0.012f),
                            0.55f to Color.Transparent,
                        ),
                    )
                    drawContent()
                },
        ) {
            content()
        }
    }
}
