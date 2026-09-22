@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.iconKey
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 独立展示本次应用会话中的设置变更历史。 */
@Composable
internal fun NotificationsPanel(
    notifications: SettingsChangeNotifications,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDesktopPalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(notifications.entries.lastOrNull()?.id) {
        notifications.markAllRead()
    }
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 14.dp,
        solidColor = palette.panelBackground,
        borderColor = androidx.compose.ui.graphics.Color.Transparent,
        borderWidth = 0.dp,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            RightToolPageTitleTab(
                label = "通知",
                glyph = RightRailGlyph.NOTIFICATIONS,
                onClose = onClose,
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "本次会话的设置变更",
                    modifier = Modifier.weight(1f),
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                )
                if (notifications.entries.isNotEmpty()) {
                    NotificationPanelAction("清空全部", notifications::clear)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (notifications.entries.isEmpty()) {
                    Text("本次会话尚无通知。", color = AppMuted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    ) {
                        items(
                            items = notifications.entries.asReversed(),
                            key = SettingsChangeNotification::id,
                        ) { entry ->
                            NotificationPanelItem(entry = entry, onRemove = { notifications.remove(entry.id) })
                        }
                    }
                    VerticalScrollbar(
                        scrollState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** 通知页中的单条记录；删除操作仅在对应记录悬浮时出现。 */
@Composable
private fun NotificationPanelItem(
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.category.label, style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            Text(entry.message, style = JewelTheme.defaultTextStyle.copy(color = AppText))
        }
        if (hovered) NotificationPanelAction("删除", onRemove)
    }
}

/** 通知页使用的弱化文字动作。 */
@Composable
private fun NotificationPanelAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 3.dp),
        color = AppAccent,
    )
}

/** 右侧工具页面共用的单页 Islands 标签。 */
@Composable
internal fun RightToolPageTitleTab(
    label: String,
    glyph: RightRailGlyph,
    onClose: () -> Unit,
) {
    IslandsTabStrip(
        tabs = listOf(
            IslandsTab(
                label = label,
                selected = true,
                iconKey = glyph.iconKey,
                closable = true,
                onClick = {},
                onClose = onClose,
            ),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
