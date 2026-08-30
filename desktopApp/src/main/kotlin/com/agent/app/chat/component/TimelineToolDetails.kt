package com.agent.app.chat.component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.design.*
import com.agent.shared.chat.model.ToolEventItem
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * 以固定最大高度显示工具详情的原始代码文本，并仅在内容溢出时展示滚动条。
 */
@Composable
internal fun ToolEventOutputPane(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    val scrollState = rememberScrollState()
    val codeFontFamily = LocalDesktopTypography.current.codeFontFamily
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TOOL_EVENT_OUTPUT_MAX_HEIGHT)
            .clip(RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp))
            .background(backgroundColor)
            .border(
                1.dp,
                AppLine.copy(alpha = 0.5f),
                RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp),
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(10.dp),
            style = JewelTheme.defaultTextStyle.copy(
                color = textColor,
                fontFamily = codeFontFamily,
                lineHeight = 20.sp,
            ),
        )
        if (shouldShowToolOutputScrollbar(maxScrollValue = scrollState.maxValue)) {
            VerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 3.dp),
            )
        }
    }
}

/** 渲染 Answers 详情中的单个内层岛屿，保持问答内容在同一视觉语法内。 */
@Composable
internal fun DetailIsland(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp))
            .background(DetailIslandBackground)
            .border(
                1.dp,
                AppLine.copy(alpha = 0.5f),
                RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp),
            )
            .padding(10.dp),
        content = { content() },
    )
}

/** 工具行仅在鼠标悬浮时展示展开方向，避免静态时间线产生视觉噪声。 */
internal fun shouldShowTimelineToolChevron(hovered: Boolean): Boolean = hovered

/** 保留固定箭头槽位，避免鼠标进出时工具名称和摘要发生位移。 */
@Composable
internal fun TimelineToolChevronSlot(
    visible: Boolean,
    rotation: Float,
) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { alpha = if (visible) 1f else 0f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = AllIconsKeys.General.ArrowDown,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * 返回工具详情箭头的旋转角度，展开时朝上，收起时朝下。
 */
internal fun toolEventChevronRotation(expanded: Boolean): Float = if (expanded) 90f else 0f

/**
 * 返回决定工具卡片展开状态归属的稳定字段，结果文本更新不应重置用户的展开选择。
 */
internal fun toolEventExpansionIdentity(item: ToolEventItem): List<Any?> = listOf(
    item.toolCallId,
    item.toolName,
    item.preview,
)

/**
 * 工具输出列表存在可滚动内容时显示滚动条，避免短输出产生无意义的视觉噪声。
 */
internal fun shouldShowToolOutputScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0
