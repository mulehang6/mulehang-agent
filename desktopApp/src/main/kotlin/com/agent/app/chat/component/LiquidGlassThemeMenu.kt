package com.agent.app.chat.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.agent.app.design.AppText
import com.agent.app.design.DesktopMaterialMode
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.PopupMenuBackground
import com.agent.app.design.PopupMenuBorder
import com.agent.app.design.liquidglass.AdaptiveLiquidGlassSurface
import com.agent.app.design.liquidglass.LiquidGlassBackdropState
import com.agent.app.design.liquidglass.LiquidGlassCloseSpring
import com.agent.app.design.liquidglass.LiquidGlassExpansionDirection
import com.agent.app.design.liquidglass.LiquidGlassMenuOptics
import com.agent.app.design.liquidglass.LiquidGlassOpenSpring
import com.agent.app.design.liquidglass.LiquidGlassSurface
import com.agent.app.design.liquidglass.LiquidGlassSurfaceRole
import com.agent.app.design.liquidglass.liquidGlassExpansionDirection
import com.agent.app.design.liquidglass.liquidGlassMenuMorph
import java.awt.Toolkit
import kotlin.math.roundToInt

/** 在设置面板根 Box 中绘制同层菜单、细颈与外部点击捕获层。 */
@Composable
internal fun LiquidGlassThemeMenuOverlay(
    state: LiquidGlassSelectState,
    backdropState: LiquidGlassBackdropState,
    selectedMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val reducedMotion = remember { prefersReducedMotion() }
    LaunchedEffect(state.expanded, reducedMotion) {
        if (reducedMotion) {
            progress.animateTo(if (state.expanded) 1f else 0f, tween(90))
        } else {
            val values = if (state.expanded) LiquidGlassOpenSpring else LiquidGlassCloseSpring
            progress.animateTo(
                targetValue = if (state.expanded) 1f else 0f,
                animationSpec = spring(values.dampingRatio, values.stiffness, visibilityThreshold = 0.001f),
            )
        }
    }
    if (progress.value <= 0.001f && !state.expanded) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    val menuWidthPx = with(density) { LIQUID_GLASS_MENU_WIDTH_DP.dp.toPx() }
    val menuHeightPx = with(density) { (LIQUID_GLASS_MENU_ROW_HEIGHT_DP * DesktopThemeMode.entries.size + 10).dp.toPx() }
    val gapPx = with(density) { LIQUID_GLASS_MENU_GAP_DP.dp.toPx() }
    val placement = liquidGlassMenuPlacement(state, menuWidthPx, menuHeightPx, gapPx)
    val liquidGlassEnabled = LocalDesktopPalette.current.materialMode == DesktopMaterialMode.LIQUID_GLASS
    val morph = liquidGlassMenuMorph(progress.value, reducedMotion || !liquidGlassEnabled)
    val palette = LocalDesktopPalette.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .pointerInput(state.expanded, state.anchorBoundsInRoot, state.menuBoundsInRoot) {
                if (state.expanded) {
                    detectTapGestures { point ->
                        val rootPoint = state.panelOriginInRoot + point
                        if (state.anchorBoundsInRoot.contains(rootPoint) || !state.menuBoundsInRoot.contains(rootPoint)) {
                            state.close()
                        }
                    }
                }
            },
    ) {
        val neckWidth = (menuWidthPx * morph.neckWidthFraction).roundToInt().coerceAtLeast(1)
        val neckHeight = (menuHeightPx * morph.neckLengthFraction).roundToInt().coerceAtLeast(1)
        if (liquidGlassEnabled && !reducedMotion && neckWidth > 2 && neckHeight > 2) {
            LiquidGlassSurface(
                backdropState = backdropState,
                optics = LiquidGlassMenuOptics,
                tint = if (palette.isDark) Color(0xFF202833).copy(alpha = 0.56f) else Color.White.copy(alpha = 0.72f),
                radius = 20.dp,
                materialAlpha = progress.value.coerceIn(0f, 1f),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (state.anchorBoundsInRoot.center.x - state.panelOriginInRoot.x - neckWidth / 2f).roundToInt(),
                            y = liquidGlassNeckTop(state, placement.direction, neckHeight, gapPx).roundToInt(),
                        )
                    }
                    .size(with(density) { neckWidth.toDp() }, with(density) { neckHeight.toDp() }),
            ) { }
        }
        AdaptiveLiquidGlassSurface(
            role = LiquidGlassSurfaceRole.FLOATING,
            radius = 9.dp,
            solidColor = PopupMenuBackground,
            borderColor = PopupMenuBorder,
            materialAlpha = progress.value.coerceIn(0f, 1f),
            modifier = Modifier
                .offset { placement.offset }
                .width(LIQUID_GLASS_MENU_WIDTH_DP.dp)
                .height(with(density) { menuHeightPx.toDp() })
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = 0.5f,
                        pivotFractionY = if (placement.direction == LiquidGlassExpansionDirection.DOWN) 0f else 1f,
                    )
                    scaleX = morph.bodyScaleX
                    scaleY = morph.bodyScaleY
                    translationY = placement.direction.sign * (1f - morph.travelProgress) * 10.dp.toPx()
                }
                .onGloballyPositioned { state.updateMenu(it.boundsInRoot()) },
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 5.dp).graphicsLayer { alpha = morph.contentAlpha }) {
                DesktopThemeMode.entries.forEachIndexed { index, mode ->
                    LiquidGlassThemeMenuItem(
                        mode = mode,
                        selected = mode == selectedMode,
                        focused = index == state.focusedIndex,
                        onClick = {
                            onThemeChanged(mode)
                            state.close()
                        },
                    )
                }
            }
        }
    }
}

/** 绘制菜单中的单个主题选项。 */
@Composable
private fun LiquidGlassThemeMenuItem(
    mode: DesktopThemeMode,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LIQUID_GLASS_MENU_ROW_HEIGHT_DP.dp)
            .padding(horizontal = 5.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    focused -> palette.popupHoverBackground.copy(alpha = 0.78f)
                    selected -> palette.popupSelectedBackground.copy(alpha = 0.68f)
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mode.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(color = AppText))
        if (selected) Text("✓", style = MaterialTheme.typography.labelLarge.copy(color = palette.accent))
    }
}

/** 描述同层菜单的像素位置与展开方向。 */
internal data class LiquidGlassMenuPlacement(
    val offset: IntOffset,
    val direction: LiquidGlassExpansionDirection,
)

/** 根据面板和锚点几何计算菜单位置。 */
internal fun liquidGlassMenuPlacement(
    state: LiquidGlassSelectState,
    menuWidthPx: Float,
    menuHeightPx: Float,
    gapPx: Float,
): LiquidGlassMenuPlacement {
    val localTop = state.anchorBoundsInRoot.top - state.panelOriginInRoot.y
    val localBottom = state.anchorBoundsInRoot.bottom - state.panelOriginInRoot.y
    val direction = liquidGlassExpansionDirection(
        anchorTop = localTop,
        anchorBottom = localBottom,
        viewportHeight = state.panelSize.height.toFloat(),
        menuHeight = menuHeightPx,
        gap = gapPx,
    )
    val x = state.anchorBoundsInRoot.right - state.panelOriginInRoot.x - menuWidthPx
    val y = if (direction == LiquidGlassExpansionDirection.DOWN) {
        localBottom + gapPx
    } else {
        localTop - gapPx - menuHeightPx
    }
    return LiquidGlassMenuPlacement(IntOffset(x.roundToInt(), y.roundToInt()), direction)
}

/** 返回连接触发器和菜单主体的细颈顶部坐标。 */
private fun liquidGlassNeckTop(
    state: LiquidGlassSelectState,
    direction: LiquidGlassExpansionDirection,
    neckHeight: Int,
    gapPx: Float,
): Float {
    val anchorTop = state.anchorBoundsInRoot.top - state.panelOriginInRoot.y
    val anchorBottom = state.anchorBoundsInRoot.bottom - state.panelOriginInRoot.y
    return if (direction == LiquidGlassExpansionDirection.DOWN) {
        anchorBottom - gapPx * 0.25f
    } else {
        anchorTop - neckHeight + gapPx * 0.25f
    }
}

/** 读取 Windows 动效偏好，并允许测试通过系统属性显式覆盖。 */
internal fun prefersReducedMotion(): Boolean {
    System.getProperty("mulehang.reducedMotion")?.toBooleanStrictOrNull()?.let { return it }
    return Toolkit.getDefaultToolkit().getDesktopProperty("win.animation") == false
}
