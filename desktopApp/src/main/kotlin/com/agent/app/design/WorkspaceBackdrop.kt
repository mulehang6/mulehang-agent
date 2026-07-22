package com.agent.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * 保存主界面的可重放绘制层和根坐标，供浮动玻璃表面采样。
 */
@Stable
internal class WorkspaceBackdropState internal constructor(
    val layer: GraphicsLayer,
) {
    var originInRoot by mutableStateOf(Offset.Zero)
        internal set
}

/**
 * 创建随组合生命周期释放的工作区背景采样状态。
 */
@Composable
internal fun rememberWorkspaceBackdropState(): WorkspaceBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { WorkspaceBackdropState(layer) }
}

/**
 * 通过缓存绘制作用域重定向内容画布，将工作区录入可重放图层并正常显示。
 *
 * 普通 DrawScope 的 record 不会重定向 ContentDrawScope，必须使用 CacheDrawScope 提供的扩展。
 */
internal fun Modifier.captureWorkspaceBackdrop(
    state: WorkspaceBackdropState,
): Modifier =
    onGloballyPositioned { coordinates ->
        state.originInRoot = coordinates.positionInRoot()
    }.drawWithCache {
        onDrawWithContent capture@{
            if (size.width > 0f && size.height > 0f) {
                state.layer.record {
                    this@capture.drawContent()
                }
                drawLayer(state.layer)
            } else {
                drawContent()
            }
        }
    }

/**
 * 返回工作区副本在侧栏局部坐标中的平移量。
 */
internal fun workspaceBackdropOffset(
    workspaceOrigin: Offset,
    sidebarOrigin: Offset,
): Offset = workspaceOrigin - sidebarOrigin
