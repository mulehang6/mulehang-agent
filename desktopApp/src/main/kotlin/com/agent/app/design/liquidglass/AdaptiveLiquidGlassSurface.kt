package com.agent.app.design.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent.app.design.DesktopMaterialMode
import com.agent.app.design.LocalDesktopPalette

/** 全应用主表面在 Liquid Glass 模式下使用的材质重量。 */
internal enum class LiquidGlassSurfaceRole {
    CHROME,
    PANEL,
    INPUT,
    FLOATING,
}

/** 经典主题绘制纯色，Liquid Glass 主题绘制共享背景折射。 */
@Composable
internal fun AdaptiveLiquidGlassSurface(
    role: LiquidGlassSurfaceRole,
    radius: Dp,
    solidColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Transparent,
    materialAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val backdropState = LocalLiquidGlassBackdrop.current
    if (palette.materialMode == DesktopMaterialMode.LIQUID_GLASS && backdropState != null) {
        val style = liquidGlassRoleStyle(role, palette.isDark)
        LiquidGlassSurface(
            backdropState = backdropState,
            optics = style.optics,
            tint = style.tint,
            radius = radius,
            modifier = modifier,
            materialAlpha = materialAlpha,
            outerShadowElevation = style.outerShadowElevation,
            contactShadowElevation = style.contactShadowElevation,
        ) {
            Box(content = content)
        }
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(radius))
                .background(solidColor)
                .border(1.dp, borderColor, RoundedCornerShape(radius)),
            content = content,
        )
    }
}

/** 一个表面角色解析后的光学、染色与悬浮重量。 */
private data class LiquidGlassRoleStyle(
    val optics: LiquidGlassOptics,
    val tint: Color,
    val outerShadowElevation: Dp,
    val contactShadowElevation: Dp,
)

/** 根据表面层级和明暗主题返回稳定的材质参数。 */
private fun liquidGlassRoleStyle(role: LiquidGlassSurfaceRole, isDark: Boolean): LiquidGlassRoleStyle {
    val tint = when (role) {
        LiquidGlassSurfaceRole.CHROME -> if (isDark) Color(0xFF1A2431).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.68f)
        LiquidGlassSurfaceRole.PANEL -> if (isDark) Color(0xFF151C27).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.74f)
        LiquidGlassSurfaceRole.INPUT -> if (isDark) Color(0xFF111821).copy(alpha = 0.48f) else Color.White.copy(alpha = 0.64f)
        LiquidGlassSurfaceRole.FLOATING -> if (isDark) Color(0xFF202833).copy(alpha = 0.56f) else Color.White.copy(alpha = 0.72f)
    }
    return when (role) {
        LiquidGlassSurfaceRole.CHROME -> LiquidGlassRoleStyle(LiquidGlassChromeOptics, tint, 10.dp, 1.dp)
        LiquidGlassSurfaceRole.PANEL -> LiquidGlassRoleStyle(LiquidGlassPanelOptics, tint, 14.dp, 2.dp)
        LiquidGlassSurfaceRole.INPUT -> LiquidGlassRoleStyle(LiquidGlassInputOptics, tint, 4.dp, 1.dp)
        LiquidGlassSurfaceRole.FLOATING -> LiquidGlassRoleStyle(LiquidGlassMenuOptics, tint, 16.dp, 2.dp)
    }
}
