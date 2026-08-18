package com.agent.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 应用布局中 Jewel 尚未提供直接容器组件时使用的静态表面角色。
 */
internal enum class JewelSurfaceRole {
    CHROME,
    PANEL,
    INPUT,
    FLOATING,
}

/** `0.dp` 在 Compose 边框 API 中会变成 hairline，因此显式无边框必须跳过绘制修饰符。 */
internal fun shouldDrawJewelSurfaceBorder(borderWidth: Dp): Boolean = borderWidth.value > 0f

/**
 * 使用 Jewel 全局颜色绘制静态容器，不模拟玻璃、折射或动态背景采样。
 */
@Composable
internal fun JewelSurface(
    role: JewelSurfaceRole,
    radius: Dp,
    modifier: Modifier = Modifier,
    solidColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = JewelTheme.globalColors
    val shape = RoundedCornerShape(radius)
    val background = solidColor.takeOrElse {
        when (role) {
            JewelSurfaceRole.CHROME,
            JewelSurfaceRole.PANEL,
            -> colors.panelBackground

            JewelSurfaceRole.INPUT -> colors.toolwindowBackground.takeOrElse { colors.panelBackground }
            JewelSurfaceRole.FLOATING -> colors.panelBackground
        }
    }
    val border = borderColor.takeOrElse { colors.borders.normal }
    val elevation = if (role == JewelSurfaceRole.FLOATING) 8.dp else 0.dp

    val surfaceModifier = modifier
        .shadow(elevation = elevation, shape = shape, clip = false)
        .background(background, shape)
    Box(
        modifier = if (shouldDrawJewelSurfaceBorder(borderWidth)) {
            surfaceModifier.border(width = borderWidth, color = border, shape = shape)
        } else {
            surfaceModifier
        },
        content = content,
    )
}
