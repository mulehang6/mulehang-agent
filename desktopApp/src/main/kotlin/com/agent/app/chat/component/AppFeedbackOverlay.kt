package com.agent.app.chat.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 全局反馈默认与窗口底部保持的间距。 */
internal const val APP_FEEDBACK_BOTTOM_PADDING_DP = 24
private const val APP_FEEDBACK_POINTER_OFFSET_DP = 12

/** 应用级反馈及其可选的鼠标锚点。 */
internal data class AppFeedbackState(
    val message: String,
    val anchor: Offset?,
    val token: Long = 0L,
)

/** 保留可用的鼠标位置；为空时由全局 toast 使用默认底部位置。 */
internal fun feedbackToastAnchor(pointerPosition: Offset?): Offset? = pointerPosition

/** 为每次反馈分配递增标识，确保重复文案也会重新开始展示计时。 */
internal fun nextAppFeedbackToken(currentToken: Long): Long = currentToken + 1L

/** 在窗口范围内按鼠标锚点或默认底部位置展示应用反馈。 */
@Composable
internal fun BoxScope.AppFeedbackOverlay(feedback: AppFeedbackState) {
    val anchor = feedbackToastAnchor(feedback.anchor)
    val pointerOffsetPx = with(LocalDensity.current) { APP_FEEDBACK_POINTER_OFFSET_DP.dp.toPx() }
    AnimatedContent(
        targetState = feedback.message,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        modifier = if (anchor == null) {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = APP_FEEDBACK_BOTTOM_PADDING_DP.dp)
        } else {
            Modifier.align(Alignment.TopStart)
        },
    ) { message ->
        AppFeedbackToast(
            message = message,
            modifier = if (anchor == null) {
                Modifier
            } else {
                Modifier.graphicsLayer {
                    translationX = anchor.x + pointerOffsetPx
                    translationY = anchor.y + pointerOffsetPx
                }
            },
        )
    }
}
