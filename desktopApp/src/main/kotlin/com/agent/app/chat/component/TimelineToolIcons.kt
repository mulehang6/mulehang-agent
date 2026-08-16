@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.agent.app.chat.presentation.*
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.*
import com.agent.app.tool.component.EditorDiffPreview
import com.agent.shared.chat.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
/** 绘制思考块标题前的闪光图标；流式思考中使用克制的呼吸反馈。 */
@Composable
internal fun TimelineReasoningGlyph(streaming: Boolean, tint: Color) {
    val transition = rememberInfiniteTransition(label = "timeline-reasoning-glyph-motion")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "timeline-reasoning-glyph-breathe",
    )
    Canvas(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(16.dp)
            .graphicsLayer {
                scaleX = if (streaming) scale else 1f
                scaleY = if (streaming) scale else 1f
            },
    ) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val stroke = 1.5.dp.toPx()
        drawLine(tint, Offset(center.x, size.height * 0.13f), Offset(center.x, size.height * 0.87f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.13f, center.y), Offset(size.width * 0.87f, center.y), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.24f, size.height * 0.24f), Offset(size.width * 0.76f, size.height * 0.76f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.76f, size.height * 0.24f), Offset(size.width * 0.24f, size.height * 0.76f), stroke, StrokeCap.Round)
    }
}

/** 绘制原型中的工具类型图标；运行时轻微摆动，强调执行仍在推进。 */
@Composable
internal fun TimelineToolGlyphIcon(
    glyph: TimelineToolGlyph,
    tint: Color,
    running: Boolean,
    iconSize: Dp = 18.dp,
) {
    val transition = rememberInfiniteTransition(label = "timeline-tool-glyph-motion")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing),
        ),
        label = "timeline-tool-glyph-progress",
    )
    val animatedProgress = if (running) progress else 0f
    Canvas(
        modifier = Modifier.size(iconSize),
    ) {
        val stroke = 1.5.dp.toPx()
        when (glyph) {
            TimelineToolGlyph.SEARCH -> {
                listOf(0.3f, 0.5f, 0.7f).forEach { yRatio ->
                    drawLine(
                        tint.copy(alpha = 0.48f),
                        Offset(size.width * 0.08f, size.height * yRatio),
                        Offset(size.width * 0.92f, size.height * yRatio),
                        stroke,
                        StrokeCap.Round,
                    )
                }
                val scanX = 0.24f + animatedProgress * 0.48f
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.25f,
                    center = Offset(size.width * scanX, size.height * 0.5f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawLine(
                    tint,
                    Offset(size.width * (scanX + 0.16f), size.height * 0.66f),
                    Offset(size.width * (scanX + 0.34f), size.height * 0.84f),
                    stroke,
                    StrokeCap.Round,
                )
            }

            TimelineToolGlyph.DIRECTORY -> {
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { index, yRatio ->
                    val nodeTint = tint.copy(alpha = if (!running || animatedProgress >= index / 3f) 1f else 0.35f)
                    drawCircle(nodeTint, radius = stroke * 0.45f, center = Offset(size.width * 0.2f, size.height * yRatio))
                    drawLine(
                        nodeTint,
                        Offset(size.width * 0.34f, size.height * yRatio),
                        Offset(size.width * (0.68f + index * 0.05f), size.height * yRatio),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }

            TimelineToolGlyph.TERMINAL -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.1f, size.height * 0.16f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawLine(tint, Offset(size.width * 0.27f, size.height * 0.38f), Offset(size.width * 0.42f, size.height * 0.5f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.42f, size.height * 0.5f), Offset(size.width * 0.27f, size.height * 0.62f), stroke, StrokeCap.Round)
                val cursorTint = tint.copy(alpha = if (!running || animatedProgress < 0.55f) 1f else 0.28f)
                drawLine(cursorTint, Offset(size.width * 0.52f, size.height * 0.63f), Offset(size.width * 0.72f, size.height * 0.63f), stroke, StrokeCap.Round)
            }

            TimelineToolGlyph.EDIT -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.12f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.54f, size.height * 0.74f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                val penOffset = (animatedProgress - 0.5f) * size.width * 0.16f
                drawLine(
                    tint,
                    Offset(size.width * 0.35f + penOffset, size.height * 0.69f),
                    Offset(size.width * 0.82f + penOffset, size.height * 0.22f),
                    stroke * 1.35f,
                    StrokeCap.Round,
                )
                drawLine(tint, Offset(size.width * 0.31f, size.height * 0.75f), Offset(size.width * 0.44f, size.height * 0.7f), stroke, StrokeCap.Round)
                val cursorTint = tint.copy(alpha = if (!running || animatedProgress < 0.5f) 1f else 0.28f)
                drawLine(cursorTint, Offset(size.width * 0.27f, size.height * 0.26f), Offset(size.width * 0.27f, size.height * 0.5f), stroke, StrokeCap.Round)
            }

            TimelineToolGlyph.READ -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.12f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.76f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawLine(tint, Offset(size.width * 0.34f, size.height * 0.42f), Offset(size.width * 0.66f, size.height * 0.42f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.34f, size.height * 0.6f), Offset(size.width * 0.66f, size.height * 0.6f), stroke, StrokeCap.Round)
                val scanY = size.height * (0.26f + animatedProgress * 0.48f)
                drawLine(tint.copy(alpha = 0.78f), Offset(size.width * 0.28f, scanY), Offset(size.width * 0.72f, scanY), stroke, StrokeCap.Round)
            }

            TimelineToolGlyph.NETWORK -> {
                drawLine(tint, Offset(size.width * 0.18f, size.height * 0.38f), Offset(size.width * 0.72f, size.height * 0.38f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.72f, size.height * 0.38f), Offset(size.width * 0.57f, size.height * 0.24f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.72f, size.height * 0.38f), Offset(size.width * 0.57f, size.height * 0.52f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.82f, size.height * 0.65f), Offset(size.width * 0.28f, size.height * 0.65f), stroke, StrokeCap.Round)
                drawCircle(tint, radius = stroke * 0.55f, center = Offset(size.width * (0.28f + animatedProgress * 0.45f), size.height * 0.65f))
            }

            TimelineToolGlyph.GENERIC -> {
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { index, yRatio ->
                    drawLine(
                        color = tint,
                        start = Offset(size.width * 0.14f, size.height * yRatio),
                        end = Offset(size.width * 0.86f, size.height * yRatio),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = tint,
                        radius = stroke * 0.72f,
                        center = Offset(
                            x = size.width * listOf(0.34f, 0.68f, 0.46f)[index],
                            y = size.height * yRatio,
                        ),
                    )
                }
            }

        }
    }
}
