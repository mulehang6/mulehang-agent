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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole

/** 在主工作区上方显示并定位可关闭的 Air 任务侧栏。 */
@Composable
internal fun BoxScope.ChatSidebarOverlay(
    state: ChatWindowState,
    compact: Boolean,
    visible: Boolean,
    onSidebarPositioned: (bounds: Rect) -> Unit,
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
                top = edgeGapDp,
                bottom = edgeGapDp,
            )
            .width(airSidebarWidthDp(compact).dp)
            .fillMaxHeight(),
    ) {
        val positionedModifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                onSidebarPositioned(coordinates.boundsInRoot())
            }
        JewelSurface(
            role = JewelSurfaceRole.CHROME,
            radius = 12.dp,
            solidColor = AppWorkspaceBackground,
            borderWidth = 0.dp,
            modifier = positionedModifier,
        ) {
            TaskSidebar(state = state, compact = compact, modifier = Modifier.fillMaxSize())
        }
    }
}
