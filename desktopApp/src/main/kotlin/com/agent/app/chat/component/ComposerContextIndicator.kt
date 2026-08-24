@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppLine
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/** 以业务语义圆环展示上下文占用，并由 Jewel Tooltip 承载精确值。 */
@Composable
internal fun ComposerContextIndicator(
    sweepAngle: Float,
    tooltip: String,
    modifier: Modifier = Modifier,
) {
    Tooltip(tooltip = { Text(tooltip) }) {
        var hovered by remember { mutableStateOf(false) }
        Box(
            modifier = modifier
                .size(34.dp)
                .background(
                    color = if (hovered) AppHoverBackground else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val strokeWidth = 2.dp.toPx()
                drawCircle(color = AppLine, style = Stroke(width = strokeWidth))
                drawArc(
                    color = AppAccent,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
    }
}
