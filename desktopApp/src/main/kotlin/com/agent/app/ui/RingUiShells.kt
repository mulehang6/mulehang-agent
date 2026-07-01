package com.agent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 顶部 header 与局部控制按钮使用的图标集合。
 */
internal enum class HeaderGlyph {
    MENU,
    SHARE,
    SETTINGS,
    HELP,
    ADD,
    CODE,
    SEARCH,
}

/**
 * 顶部 header 按钮模型。
 */
internal data class HeaderAction(
    val glyph: HeaderGlyph,
)

/**
 * 顶部 header 固定动作布局。
 */
internal data class HeaderActions(
    val left: HeaderAction,
    val right: List<HeaderAction>,
)

/**
 * 返回原型顶部 header 的动作定义。
 */
internal fun buildHeaderActions(): HeaderActions = HeaderActions(
    left = HeaderAction(glyph = HeaderGlyph.MENU),
    right = listOf(
        HeaderAction(glyph = HeaderGlyph.SHARE),
        HeaderAction(glyph = HeaderGlyph.SETTINGS),
        HeaderAction(glyph = HeaderGlyph.HELP),
    ),
)

/**
 * 右侧 rail 的图标类型。
 */
internal enum class RightRailGlyph {
    CODE,
    TERMINAL,
    DOWNLOAD,
    UPLOAD,
    HISTORY,
    COPY,
    FILTER,
}

/**
 * 右侧 rail 的按钮展示模型。
 */
internal data class RightRailButtonModel(
    val glyph: RightRailGlyph,
    val active: Boolean = false,
)

/**
 * 右侧 rail 的固定分组结构。
 */
internal fun buildRightRailGroups(): List<List<RightRailButtonModel>> = listOf(
    listOf(
        RightRailButtonModel(glyph = RightRailGlyph.CODE, active = true),
        RightRailButtonModel(glyph = RightRailGlyph.TERMINAL),
        RightRailButtonModel(glyph = RightRailGlyph.DOWNLOAD),
    ),
    listOf(
        RightRailButtonModel(glyph = RightRailGlyph.UPLOAD),
        RightRailButtonModel(glyph = RightRailGlyph.HISTORY),
    ),
    listOf(
        RightRailButtonModel(glyph = RightRailGlyph.COPY),
        RightRailButtonModel(glyph = RightRailGlyph.FILTER),
    ),
)

/**
 * 顶部 header 的小图标按钮。
 */
@Composable
internal fun RingHeaderActionButton(
    glyph: HeaderGlyph,
    onClick: (() -> Unit)? = null,
    inline: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(if (inline) 6.dp else 10.dp),
        color = if (inline) Color.Transparent else AppChipBackground,
        border = if (inline) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, AppLine)
        },
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
        Box(
            modifier = Modifier.size(if (inline) 28.dp else 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            HeaderGlyphIcon(glyph = glyph, tint = AppText)
        }
    }
}

/**
 * 原型搜索输入壳。
 */
@Composable
internal fun RingInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    iconGlyph: HeaderGlyph? = null,
    borderless: Boolean = false,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(8.dp),
        leadingIcon = iconGlyph?.let { glyph ->
            {
                RingGlyphIcon(
                    glyph = glyph,
                    tint = AppMuted,
                    size = 14.dp,
                )
            }
        },
        colors = if (borderless) {
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
    )
}

/**
 * 原型主按钮壳。
 */
@Composable
internal fun RingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppAccent,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
    ) {
        Text(text)
    }
}

/**
 * 原型 Island 风格容器壳。
 */
@Composable
internal fun RingIsland(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    color: Color = AppSidebarBackground,
    borderColor: Color = AppLine,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        content()
    }
}

/**
 * 原型 Select button 壳。
 */
@Composable
internal fun RingSelectChip(
    label: String,
    expanded: Boolean,
    tone: Color = AppChipBackground,
    onExpandedChange: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = tone,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = modifier.clickable(onClick = onExpandedChange),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(color = AppText),
                )
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onExpandedChange,
        ) {
            content()
        }
    }
}

/**
 * 右侧 rail 的按钮壳。
 */
@Composable
internal fun RingRailActionButton(
    glyph: RightRailGlyph,
    active: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(26.dp)
            .background(
                color = if (active) AppSelectedBackground else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        RightRailGlyphIcon(
            glyph = glyph,
            tint = if (active) Color.White else AppText.copy(alpha = 0.72f),
        )
    }
}

/**
 * 纯图标渲染，匹配原型中非按钮式 glyph 的位置。
 */
@Composable
internal fun RingGlyphIcon(
    glyph: HeaderGlyph,
    tint: Color = AppText,
    size: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        HeaderGlyphIcon(glyph = glyph, tint = tint)
    }
}

/**
 * 绘制顶部 header 的图标。
 */
@Composable
private fun HeaderGlyphIcon(
    glyph: HeaderGlyph,
    tint: Color,
) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        when (glyph) {
            HeaderGlyph.MENU -> {
                drawLine(tint, Offset(size.width * 0.18f, size.height * 0.28f), Offset(size.width * 0.82f, size.height * 0.28f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.18f, size.height * 0.5f), Offset(size.width * 0.82f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.18f, size.height * 0.72f), Offset(size.width * 0.82f, size.height * 0.72f), strokeWidth, StrokeCap.Round)
            }

            HeaderGlyph.SHARE -> {
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.72f), Offset(size.width * 0.5f, size.height * 0.24f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.34f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.22f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.66f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.22f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.26f, size.height * 0.78f), Offset(size.width * 0.74f, size.height * 0.78f), strokeWidth, StrokeCap.Round)
            }

            HeaderGlyph.SETTINGS -> {
                drawCircle(tint, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.5f, size.height * 0.5f), style = Stroke(width = strokeWidth))
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.1f), Offset(size.width * 0.5f, size.height * 0.24f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.76f), Offset(size.width * 0.5f, size.height * 0.9f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.24f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.76f, size.height * 0.5f), Offset(size.width * 0.9f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.3f, size.height * 0.3f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.7f, size.height * 0.7f), Offset(size.width * 0.8f, size.height * 0.8f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.3f, size.height * 0.7f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.7f, size.height * 0.3f), Offset(size.width * 0.8f, size.height * 0.2f), strokeWidth, StrokeCap.Round)
            }

            HeaderGlyph.HELP -> {
                drawLine(tint, Offset(size.width * 0.34f, size.height * 0.34f), Offset(size.width * 0.5f, size.height * 0.22f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.22f), Offset(size.width * 0.66f, size.height * 0.34f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.66f, size.height * 0.34f), Offset(size.width * 0.58f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.58f, size.height * 0.5f), Offset(size.width * 0.5f, size.height * 0.58f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.68f), Offset(size.width * 0.5f, size.height * 0.7f), strokeWidth, StrokeCap.Round)
                drawCircle(tint, radius = strokeWidth / 2f, center = Offset(size.width * 0.5f, size.height * 0.82f))
            }

            HeaderGlyph.ADD -> {
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.2f), Offset(size.width * 0.5f, size.height * 0.8f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.8f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
            }

            HeaderGlyph.CODE -> {
                drawLine(tint, Offset(size.width * 0.42f, size.height * 0.22f), Offset(size.width * 0.24f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.24f, size.height * 0.5f), Offset(size.width * 0.42f, size.height * 0.78f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.58f, size.height * 0.22f), Offset(size.width * 0.76f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.76f, size.height * 0.5f), Offset(size.width * 0.58f, size.height * 0.78f), strokeWidth, StrokeCap.Round)
            }

            HeaderGlyph.SEARCH -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.22f,
                    center = Offset(size.width * 0.45f, size.height * 0.45f),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.6f, size.height * 0.6f),
                    Offset(size.width * 0.8f, size.height * 0.8f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 用 Compose Canvas 绘制右侧 rail 图标。
 */
@Composable
private fun RightRailGlyphIcon(
    glyph: RightRailGlyph,
    tint: Color,
) {
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val width = size.width
            val height = size.height
            when (glyph) {
                RightRailGlyph.CODE -> {
                    drawLine(tint, Offset(width * 0.42f, height * 0.24f), Offset(width * 0.24f, height * 0.5f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.24f, height * 0.5f), Offset(width * 0.42f, height * 0.76f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.58f, height * 0.24f), Offset(width * 0.76f, height * 0.5f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.76f, height * 0.5f), Offset(width * 0.58f, height * 0.76f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.53f, height * 0.18f), Offset(width * 0.47f, height * 0.82f), stroke.width, StrokeCap.Round)
                }

                RightRailGlyph.TERMINAL -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(width * 0.15f, height * 0.2f),
                        size = Size(width * 0.7f, height * 0.55f),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = stroke,
                    )
                    drawLine(tint, Offset(width * 0.3f, height * 0.36f), Offset(width * 0.42f, height * 0.48f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.42f, height * 0.48f), Offset(width * 0.3f, height * 0.6f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.5f, height * 0.61f), Offset(width * 0.66f, height * 0.61f), stroke.width, StrokeCap.Round)
                }

                RightRailGlyph.DOWNLOAD -> {
                    drawLine(tint, Offset(width * 0.5f, height * 0.2f), Offset(width * 0.5f, height * 0.58f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.34f, height * 0.45f), Offset(width * 0.5f, height * 0.62f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.66f, height * 0.45f), Offset(width * 0.5f, height * 0.62f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.28f, height * 0.8f), Offset(width * 0.72f, height * 0.8f), stroke.width, StrokeCap.Round)
                }

                RightRailGlyph.UPLOAD -> {
                    drawLine(tint, Offset(width * 0.26f, height * 0.75f), Offset(width * 0.72f, height * 0.29f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.5f, height * 0.18f), Offset(width * 0.5f, height * 0.62f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.34f, height * 0.34f), Offset(width * 0.5f, height * 0.18f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.66f, height * 0.34f), Offset(width * 0.5f, height * 0.18f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.28f, height * 0.78f), Offset(width * 0.72f, height * 0.78f), stroke.width, StrokeCap.Round)
                }

                RightRailGlyph.HISTORY -> {
                    drawArc(
                        color = tint,
                        startAngle = -30f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(width * 0.2f, height * 0.2f),
                        size = Size(width * 0.6f, height * 0.6f),
                        style = stroke,
                    )
                    drawLine(tint, Offset(width * 0.64f, height * 0.2f), Offset(width * 0.78f, height * 0.18f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.64f, height * 0.2f), Offset(width * 0.69f, height * 0.33f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.5f, height * 0.35f), Offset(width * 0.5f, height * 0.5f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.5f, height * 0.5f), Offset(width * 0.62f, height * 0.56f), stroke.width, StrokeCap.Round)
                }

                RightRailGlyph.COPY -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(width * 0.34f, height * 0.24f),
                        size = Size(width * 0.42f, height * 0.5f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = stroke,
                    )
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(width * 0.2f, height * 0.36f),
                        size = Size(width * 0.42f, height * 0.5f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = stroke,
                    )
                }

                RightRailGlyph.FILTER -> {
                    drawLine(tint, Offset(width * 0.2f, height * 0.26f), Offset(width * 0.8f, height * 0.26f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.2f, height * 0.26f), Offset(width * 0.56f, height * 0.56f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.8f, height * 0.26f), Offset(width * 0.56f, height * 0.56f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(width * 0.56f, height * 0.56f), Offset(width * 0.56f, height * 0.8f), stroke.width, StrokeCap.Round)
                }
            }
        }
    }
}

internal val AppBackground = Color(0xFF1E1F22)
internal val AppHeaderBackground = Color(0xFF1E1F22)
internal val AppSidebarBackground = Color(0xFF2B2D30)
internal val AppPanelBackground = Color(0xFF1E1F22)
internal val AppSelectedBackground = Color(0xFF2E436E)
internal val AppUserCardBackground = Color(0xFF43454A)
internal val AppChipBackground = Color(0xFF43454A)
internal val ComposerBackground = Color(0xFF2B2D30)
internal val AppRailBackground = Color(0xFF24262B)
internal val AppLine = Color(0xFF393B40)
internal val AppText = Color(0xFFFFFFFF)
internal val AppMuted = Color(0xFF9DA0A8)
internal val AppAccent = Color(0xFF548AF7)
internal val AppSuccess = Color(0xFF5FAD65)
internal val AppDanger = Color(0xFFE37774)
