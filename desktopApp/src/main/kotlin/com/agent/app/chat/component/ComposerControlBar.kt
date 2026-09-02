package com.agent.app.chat.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.agent.app.design.ComposerBackground

private val COMPOSER_CONTROL_ICON_SIZE = 36.dp
private val COMPOSER_CONTEXT_INDICATOR_SIZE = 34.dp
private val COMPOSER_CONTROL_GAP = 8.dp
private val COMPOSER_CONTEXT_OCCLUSION_WIDTH = 24.dp

/** Composer 底栏固定核心操作，将剩余宽度独占交给可压缩的选择器组。 */
@Composable
internal fun ComposerControlBar(
    attachment: @Composable () -> Unit,
    selectorGroup: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit,
    contextIndicator: @Composable () -> Unit,
    primaryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            attachment()
            Spacer(modifier = Modifier.width(COMPOSER_CONTROL_GAP))
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                selectorGroup()
            }
            Spacer(modifier = Modifier.width(COMPOSER_CONTROL_GAP))
            Spacer(modifier = Modifier.width(COMPOSER_CONTEXT_INDICATOR_SIZE))
            Spacer(modifier = Modifier.width(COMPOSER_CONTROL_GAP))
            Spacer(modifier = Modifier.width(COMPOSER_CONTROL_ICON_SIZE))
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(
                    COMPOSER_CONTEXT_OCCLUSION_WIDTH +
                            COMPOSER_CONTEXT_INDICATOR_SIZE +
                            COMPOSER_CONTROL_GAP +
                            COMPOSER_CONTROL_ICON_SIZE,
                )
                .height(COMPOSER_CONTROL_ICON_SIZE)
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.65f to ComposerBackground.copy(alpha = 0.78f),
                                1f to ComposerBackground,
                            ),
                            endX = COMPOSER_CONTEXT_OCCLUSION_WIDTH.toPx(),
                        ),
                    )
                }
                .zIndex(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                contextIndicator()
                Spacer(modifier = Modifier.width(COMPOSER_CONTROL_GAP))
                primaryAction()
            }
        }
    }
}
