package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AirSidebarSurface
import com.agent.app.design.AppLine
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.DesktopMaterialMode
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.WorkspaceBackdropState
import com.agent.app.design.liquidglass.AdaptiveLiquidGlassSurface
import com.agent.app.design.liquidglass.LiquidGlassSurfaceRole

private const val CHAT_SIDEBAR_TOP_OFFSET_DP = 56

/** 在主工作区上方显示并定位可关闭的 Air 任务侧栏。 */
@Composable
internal fun BoxScope.ChatSidebarOverlay(
    state: ChatWindowState,
    compact: Boolean,
    visible: Boolean,
    backdropState: WorkspaceBackdropState,
    sidebarOrigin: Offset,
    onSidebarPositioned: (origin: Offset, bounds: Rect) -> Unit,
) {
    val edgeGapDp = if (compact) 8.dp else 12.dp
    val edgeGapPx = with(LocalDensity.current) { edgeGapDp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetX = { width -> sidebarHiddenOffsetPx(width, edgeGapPx) },
        ),
        exit = slideOutHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            targetOffsetX = { width -> sidebarHiddenOffsetPx(width, edgeGapPx) },
        ),
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(
                start = edgeGapDp,
                top = CHAT_SIDEBAR_TOP_OFFSET_DP.dp,
                bottom = edgeGapDp,
            )
            .width(airSidebarWidthDp(compact).dp)
            .fillMaxHeight(),
    ) {
        val positionedModifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                onSidebarPositioned(coordinates.positionInRoot(), coordinates.boundsInRoot())
            }
        if (LocalDesktopPalette.current.materialMode == DesktopMaterialMode.LIQUID_GLASS) {
            AdaptiveLiquidGlassSurface(
                role = LiquidGlassSurfaceRole.CHROME,
                radius = 12.dp,
                solidColor = AppSidebarBackground,
                borderColor = AppLine.copy(alpha = 0.52f),
                modifier = positionedModifier,
            ) {
                TaskSidebar(state = state, compact = compact, modifier = Modifier.fillMaxSize())
            }
        } else {
            AirSidebarSurface(
                backdropState = backdropState,
                sidebarOrigin = sidebarOrigin,
                modifier = positionedModifier,
            ) {
                TaskSidebar(state = state, compact = compact, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
