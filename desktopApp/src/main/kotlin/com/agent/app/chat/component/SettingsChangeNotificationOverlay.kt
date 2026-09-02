@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.agent.app.design.AppAccent
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

internal val SETTINGS_NOTIFICATION_CARD_MAX_WIDTH = 360.dp
private val SETTINGS_NOTIFICATION_CARD_EDGE = 12.dp
private val SETTINGS_NOTIFICATION_CARD_GAP = 8.dp

/** 通知浮层的像素布局结果；卡片始终向通知按钮的左侧和上方生长。 */
internal data class SettingsNotificationCardPlacement(
    val leftPx: Int,
    val bottomPx: Int,
    val maxHeightPx: Int,
)

/** 根据右侧工具栏通知按钮的位置，将卡片限制在窗口可用范围内。 */
internal fun settingsNotificationCardPlacement(
    rootSize: IntSize,
    anchor: Rect,
    cardWidthPx: Int,
    edgePx: Int,
    gapPx: Int,
): SettingsNotificationCardPlacement {
    val maxLeft = (rootSize.width - cardWidthPx - edgePx).coerceAtLeast(edgePx)
    val preferredLeft = anchor.left.roundToInt() - cardWidthPx - gapPx
    return SettingsNotificationCardPlacement(
        leftPx = preferredLeft.coerceIn(edgePx, maxLeft),
        bottomPx = (rootSize.height - anchor.bottom.roundToInt()).coerceAtLeast(edgePx),
        maxHeightPx = (anchor.bottom.roundToInt() - edgePx).coerceAtLeast(1),
    )
}

/** 计算浮层可用宽度，避免窄窗口时超出窗口边缘。 */
internal fun settingsNotificationCardWidth(availableWidth: Dp): Dp =
    minOf(SETTINGS_NOTIFICATION_CARD_MAX_WIDTH, (availableWidth - SETTINGS_NOTIFICATION_CARD_EDGE * 2).coerceAtLeast(1.dp))

/**
 * 渲染在 ChatScreen 根容器的通知覆盖层。
 *
 * 它不属于任何 Island，使用根坐标锚定右侧通知图标，因此不会受到设置、终端或工作区的裁剪。
 */
@Composable
internal fun BoxScope.SettingsChangeNotificationOverlay(
    notifications: SettingsChangeNotifications,
    anchor: Rect?,
    modifier: Modifier = Modifier,
) {
    val transientEntry = notifications.transientEntry
    if (anchor == null || (!notifications.historyVisible && transientEntry == null)) return

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().matchParentSize().zIndex(20f),
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cardWidth = settingsNotificationCardWidth(maxWidth)
        val placement = with(density) {
            settingsNotificationCardPlacement(
                rootSize = IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()),
                anchor = anchor,
                cardWidthPx = cardWidth.roundToPx(),
                edgePx = SETTINGS_NOTIFICATION_CARD_EDGE.roundToPx(),
                gapPx = SETTINGS_NOTIFICATION_CARD_GAP.roundToPx(),
            )
        }
        val left = with(density) { placement.leftPx.toDp() }
        val bottom = with(density) { placement.bottomPx.toDp() }
        val maxCardHeight = with(density) { placement.maxHeightPx.toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = left, bottom = bottom)
                .width(cardWidth)
                .heightIn(max = maxCardHeight),
        ) {
            if (notifications.historyVisible) {
                SettingsNotificationHistoryCard(
                    entries = notifications.entries.asReversed(),
                    maxHeight = maxCardHeight,
                    onRemove = notifications::remove,
                    onClear = notifications::clear,
                )
            } else {
                transientEntry?.let { entry ->
                    SettingsNotificationToastCard(
                        entry = entry,
                        onDismiss = notifications::dismissTransient,
                    )
                }
            }
        }
    }
}

/** 新消息的轻量提示；关闭后只收起提示，不会删除历史记录。 */
@Composable
private fun SettingsNotificationToastCard(
    entry: SettingsChangeNotification,
    onDismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    FloatingNotificationSurface(
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.category.label,
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
                Text(
                    text = entry.message,
                    style = JewelTheme.defaultTextStyle.copy(color = AppText),
                )
            }
            if (hovered) {
                NotificationTextAction(text = "关闭", onClick = onDismiss)
            }
        }
    }
}

/** 点击通知图标后显示的本次会话变更记录。 */
@Composable
private fun SettingsNotificationHistoryCard(
    entries: List<SettingsChangeNotification>,
    maxHeight: Dp,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    FloatingNotificationSurface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).hoverable(interactionSource),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "设置变更历史",
                    modifier = Modifier.weight(1f),
                    style = JewelTheme.defaultTextStyle.copy(color = AppText),
                    fontWeight = FontWeight.SemiBold,
                )
                if (hovered && entries.isNotEmpty()) {
                    NotificationTextAction(text = "清空全部", onClick = onClear)
                }
            }
            if (entries.isEmpty()) {
                Text("本次会话尚无设置变更。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            } else {
                Column(
                    modifier = Modifier.heightIn(max = (maxHeight - 64.dp).coerceAtLeast(1.dp))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entries.forEach { entry ->
                        SettingsNotificationHistoryItem(entry = entry, onRemove = { onRemove(entry.id) })
                    }
                }
            }
        }
    }
}

/** 历史单项；删除动作仅在指针位于对应消息上时出现。 */
@Composable
private fun SettingsNotificationHistoryItem(
    entry: SettingsChangeNotification,
    onRemove: () -> Unit,
) {
    val interactionSource = remember(entry.id) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.category.label, style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            Text(entry.message, style = JewelTheme.defaultTextStyle.copy(color = AppText))
        }
        if (hovered) {
            NotificationTextAction(text = "删除", onClick = onRemove)
        }
    }
}

/** 所有通知卡片共用的根层浮动表面。 */
@Composable
private fun FloatingNotificationSurface(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    JewelSurface(
        role = JewelSurfaceRole.FLOATING,
        radius = 10.dp,
        solidColor = AppSidebarBackground,
        borderColor = AppLine,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** 仅用于悬停时出现的通知操作，保持卡片正文的视觉优先级。 */
@Composable
private fun NotificationTextAction(text: String, onClick: () -> Unit) {
    val interactionSource = remember(text) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Text(
        text = text,
        modifier = Modifier
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        style = JewelTheme.defaultTextStyle.copy(
            color = AppAccent,
            textDecoration = if (hovered) TextDecoration.Underline else null,
        ),
    )
}

/** 将通知类别转为不含配置或敏感字段的 UI 标签。 */
private val SettingsChangeNotificationCategory.label: String
    get() = when (this) {
        SettingsChangeNotificationCategory.EXTENSIONS -> "扩展设置"
        SettingsChangeNotificationCategory.AI_SERVICES -> "AI 服务"
    }
