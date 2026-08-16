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
/**
 * 在后台生成 PlantUML SVG，完成后由 Skia 直接绘制；失败时保留原始源码供用户检查。
 */
@Composable
internal fun PlantUmlDiagram(source: String) {
    var showSource by remember(source) { mutableStateOf(false) }
    var copied by remember(source) { mutableStateOf(false) }
    var copyNoticeVersion by remember(source) { mutableIntStateOf(0) }
    var renderedSvg by remember(source) { mutableStateOf<Result<String>?>(null) }
    LaunchedEffect(source) {
        renderedSvg = runCatching {
            withContext(Dispatchers.Default) {
                renderPlantUmlToSvg(source)
            }
        }
    }
    LaunchedEffect(copyNoticeVersion) {
        if (copyNoticeVersion > 0) {
            delay(1_500)
            copied = false
        }
    }
    when (val result = renderedSvg) {
        null -> Text(
            text = "正在渲染 PlantUML 图表…",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )

        else -> {
            val svg = result.getOrNull()
            if (svg != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (showSource) {
                        PlantUmlSource(
                            source = source,
                            onShowRendered = { showSource = false },
                            onCopied = {
                                copied = true
                                copyNoticeVersion += 1
                            },
                        )
                    } else {
                        PlantUmlSvg(
                            svg = svg,
                            source = source,
                            onShowSource = { showSource = true },
                            onCopied = {
                                copied = true
                                copyNoticeVersion += 1
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = copied,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        enter = fadeIn(tween(durationMillis = 120)),
                        exit = fadeOut(tween(durationMillis = 160)),
                    ) {
                        JewelSurface(
                            role = JewelSurfaceRole.FLOATING,
                            radius = 6.dp,
                            solidColor = AppHoverBackground,
                            borderColor = AppLine.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = "已复制 UML 源码",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = JewelTheme.defaultTextStyle.copy(color = AppText),
                            )
                        }
                    }
                }
            } else {
                AssistantCodeBlock(language = "plantuml", source = source)
            }
        }
    }
}

/** 在与图表一致的容器中展示 PlantUML 原始源码，并保留返回渲染视图的入口。 */
@Composable
private fun PlantUmlSource(
    source: String,
    onShowRendered: () -> Unit,
    onCopied: () -> Unit,
) {
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 8.dp,
        solidColor = Color(0xFF24272E),
        borderColor = AppLine.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLANT_UML_CONTROL_BAR_HEIGHT_DP.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlantUmlControlButton(text = "渲染", onClick = onShowRendered)
                PlantUmlCopyControl(source = source, onCopied = onCopied)
            }
            AssistantCodeBlock(language = "plantuml", source = source)
        }
    }
}

/** UML 图形可视画布的高度；控制栏占用独立的顶部层，不与图形共用空间。 */
internal const val PLANT_UML_CANVAS_HEIGHT_DP = 460

/** UML 图表容器最大宽度，保留会话页可用的滚动留白。 */
internal const val PLANT_UML_MAX_WIDTH_DP = 720

/** UML 控制栏固定高度，保证其作为独立且可点击的顶层。 */
internal const val PLANT_UML_CONTROL_BAR_HEIGHT_DP = 48

/** PlantUML 图形缩放的上限，避免极端放大导致渲染开销失控。 */
private const val PLANT_UML_MAX_SCALE = 3f

/** PlantUML 图像的原始像素尺寸。 */
internal data class PlantUmlIntrinsicSize(
    val width: Float,
    val height: Float,
)

/** 从 PlantUML 的 SVG 根节点读取图表原始尺寸，供适配、缩放和拖拽计算共用。 */
internal fun svgIntrinsicSize(svg: String): PlantUmlIntrinsicSize {
    val viewBox = SVG_VIEW_BOX_ATTRIBUTE.find(svg)
    if (viewBox != null) {
        return PlantUmlIntrinsicSize(
            width = viewBox.groupValues[1].toFloat(),
            height = viewBox.groupValues[2].toFloat(),
        )
    }
    val width = SVG_WIDTH_ATTRIBUTE.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    val height = SVG_HEIGHT_ATTRIBUTE.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    return PlantUmlIntrinsicSize(width = width ?: 1f, height = height ?: 1f)
}

/** SVG 的 viewBox 是最可靠的绘制尺寸来源。 */
private val SVG_VIEW_BOX_ATTRIBUTE = Regex(
    """\bviewBox\s*=\s*[\"']\s*[-+]?\d+(?:\.\d+)?[\s,]+[-+]?\d+(?:\.\d+)?[\s,]+([-+]?\d+(?:\.\d+)?)[\s,]+([-+]?\d+(?:\.\d+)?)\s*[\"']""",
    RegexOption.IGNORE_CASE,
)

/** 当 SVG 未提供 viewBox 时，退回使用根节点的宽高属性。 */
private val SVG_WIDTH_ATTRIBUTE = Regex(
    """\bwidth\s*=\s*[\"']\s*([-+]?\d+(?:\.\d+)?)(?:px)?\s*[\"']""",
    RegexOption.IGNORE_CASE,
)

/** 当 SVG 未提供 viewBox 时，退回使用根节点的宽高属性。 */
private val SVG_HEIGHT_ATTRIBUTE = Regex(
    """\bheight\s*=\s*[\"']\s*([-+]?\d+(?:\.\d+)?)(?:px)?\s*[\"']""",
    RegexOption.IGNORE_CASE,
)

/** 计算完整显示 PlantUML 图像所需的等比缩放，不放大小于视口的图形。 */
internal fun plantUmlFitScale(
    intrinsicSize: PlantUmlIntrinsicSize,
    viewportWidth: Float,
    viewportHeight: Float,
): Float =
    if (viewportWidth > 0f && viewportHeight > 0f) {
        minOf(viewportWidth / intrinsicSize.width, viewportHeight / intrinsicSize.height).coerceAtMost(1f)
    } else {
        1f
    }

/** 将滚轮或按钮的倍率变更限制在当前查看器支持的范围内。 */
internal fun plantUmlZoomedScale(
    scale: Float,
    multiplier: Float,
    minimumScale: Float,
): Float = (scale * multiplier).coerceIn(minimumScale, PLANT_UML_MAX_SCALE)

/**
 * 使用 PlantUML 在 JVM 内生成的 SVG 以矢量方式绘制图表，缩放时保持文字与连线清晰。
 *
 * 控制栏始终位于裁切画布之上；画布仅占用控制栏下方的独立区域。
 */
@Composable
private fun PlantUmlSvg(
    svg: String,
    source: String,
    onShowSource: () -> Unit,
    onCopied: () -> Unit,
) {
    val document = remember(svg) { SVGDOM(Data.makeFromBytes(svg.encodeToByteArray())) }
    val intrinsicSize = remember(svg) { svgIntrinsicSize(svg) }
    DisposableEffect(document) {
        onDispose(document::close)
    }
    val density = LocalDensity.current
    val canvasHeight = PLANT_UML_CANVAS_HEIGHT_DP.dp
    val controlBarHeight = PLANT_UML_CONTROL_BAR_HEIGHT_DP.dp
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        JewelSurface(
            role = JewelSurfaceRole.PANEL,
            radius = 8.dp,
            solidColor = Color(0xFF24272E),
            borderColor = AppLine.copy(alpha = 0.65f),
            modifier = Modifier
                .widthIn(max = PLANT_UML_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight + controlBarHeight)
                .clipToBounds(),
        ) {
            val viewportWidth = with(density) { maxWidth.toPx() }
            val viewportHeight = with(density) { canvasHeight.toPx() }
            val fitScale = plantUmlFitScale(intrinsicSize, viewportWidth, viewportHeight)
            val minimumScale = (fitScale * 0.5f).coerceAtLeast(0.05f)
            var scale by remember(svg) { mutableFloatStateOf(fitScale) }
            var offset by remember(svg) { mutableStateOf(Offset.Zero) }
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .clipToBounds()
                    .onPointerEvent(
                        eventType = PointerEventType.Scroll,
                        pass = PointerEventPass.Initial,
                    ) { event ->
                        val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (scrollY != 0f) {
                            event.changes.forEach { it.consume() }
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = if (scrollY < 0f) 1.12f else 0.9f,
                                minimumScale = minimumScale,
                            )
                        }
                    }
                    .pointerInput(svg, minimumScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = zoom,
                                minimumScale = minimumScale,
                            )
                            offset += pan
                        }
                    },
            ) {
                val scaledWidth = intrinsicSize.width * scale
                val scaledHeight = intrinsicSize.height * scale
                val centeredPosition = Offset(
                    x = (size.width - scaledWidth) / 2f,
                    y = (size.height - scaledHeight) / 2f,
                )
                withTransform({
                    translate(
                        left = centeredPosition.x + offset.x,
                        top = centeredPosition.y + offset.y,
                    )
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                }) {
                    document.setContainerSize(intrinsicSize.width, intrinsicSize.height)
                    drawIntoCanvas { canvas ->
                        document.render(canvas.skiaCanvas)
                    }
                }
            }
            JewelSurface(
                role = JewelSurfaceRole.CHROME,
                radius = 0.dp,
                solidColor = Color(0xFF24272E),
                borderColor = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(controlBarHeight)
                    .zIndex(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlantUmlControlButton(
                        text = "−",
                        onClick = {
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = 1f / 1.2f,
                                minimumScale = minimumScale,
                            )
                        },
                    )
                    PlantUmlControlButton(
                        text = "${(scale * 100).roundToInt()}%",
                        onClick = { scale = 1f; offset = Offset.Zero },
                    )
                    PlantUmlControlButton(
                        text = "+",
                        onClick = {
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = 1.2f,
                                minimumScale = minimumScale,
                            )
                        },
                    )
                    PlantUmlControlButton(text = "适配", onClick = { scale = fitScale; offset = Offset.Zero })
                    PlantUmlControlButton(text = "源码", onClick = onShowSource)
                    PlantUmlCopyControl(source = source, onCopied = onCopied)
                }
            }
        }
        }
    }
}

/**
 * 仅在 Agent 完成回复后启用图表渲染，避免不完整的流式围栏触发布局抖动。
 */
internal fun shouldRenderMarkdownDiagram(isStreaming: Boolean): Boolean = !isStreaming

/** UML 工具栏使用轻量桌面控件，避免 Material 文本按钮的移动端视觉反馈。 */
@Composable
private fun PlantUmlControlButton(
    text: String,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) AppLine.copy(alpha = 0.72f) else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = JewelTheme.defaultTextStyle.copy(
                color = if (hovered) AppText else AppMuted,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** 将 UML 原始源码复制到系统剪贴板，并通知图表容器展示短暂反馈。 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PlantUmlCopyControl(
    source: String,
    onCopied: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    PlantUmlControlButton(
        text = "复制",
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(java.awt.datatransfer.StringSelection(source)))
                onCopied()
            }
        },
    )
}
