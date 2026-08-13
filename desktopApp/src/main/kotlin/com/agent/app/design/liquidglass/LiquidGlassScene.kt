package com.agent.app.design.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** 当前应用根场景提供的共享 Liquid Glass 背景；经典材质下为 null。 */
internal val LocalLiquidGlassBackdrop = compositionLocalOf<LiquidGlassBackdropState?> { null }

/**
 * 绘制独立背景并把它作为全应用玻璃表面的唯一折射源。
 *
 * 前景内容从不写入该图层，因此多个玻璃表面不会互相递归采样。
 */
@Composable
internal fun LiquidGlassScene(
    enabled: Boolean,
    isDark: Boolean,
    solidBackground: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdropState = rememberLiquidGlassBackdropState()
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(enabled, isDark, sceneSize) {
        if (enabled && sceneSize.width > 0 && sceneSize.height > 0) {
            withFrameNanos { }
            backdropState.refresh()
        } else if (!enabled) {
            backdropState.clear()
        }
    }

    Box(modifier = modifier.background(solidBackground)) {
        if (enabled) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { sceneSize = it }
                    .captureLiquidGlassBackdrop(backdropState),
            ) {
                drawRect(if (isDark) Color(0xFF0E1118) else Color(0xFFF1F6FF))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF3E5F9E).copy(alpha = 0.72f), Color.Transparent)
                        } else {
                            listOf(Color(0xFF8CCBFF).copy(alpha = 0.76f), Color.Transparent)
                        },
                        center = Offset(size.width * 0.14f, size.height * 0.12f),
                        radius = size.maxDimension * 0.72f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF6E4B9E).copy(alpha = 0.55f), Color.Transparent)
                        } else {
                            listOf(Color(0xFFFFB7C8).copy(alpha = 0.62f), Color.Transparent)
                        },
                        center = Offset(size.width * 0.92f, size.height * 0.18f),
                        radius = size.maxDimension * 0.66f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF216F76).copy(alpha = 0.48f), Color.Transparent)
                        } else {
                            listOf(Color(0xFFFFD19B).copy(alpha = 0.58f), Color.Transparent)
                        },
                        center = Offset(size.width * 0.72f, size.height * 0.92f),
                        radius = size.maxDimension * 0.78f,
                    ),
                )
            }
        }
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides backdropState.takeIf { enabled },
        ) {
            content()
        }
    }
}
