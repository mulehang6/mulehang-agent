package com.agent.app.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.agent.app.design.AppAccent
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 在工作区根层展示最新设置变更的短提示。
 *
 * 完整历史由独立通知页承载；该提示不再依赖工具栏坐标，也不会和通知页形成第二套历史界面。
 */
@Composable
internal fun BoxScope.SettingsChangeNotificationOverlay(
    notifications: SettingsChangeNotifications,
    notificationsPageVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val transientEntry = notifications.transientEntry
    LaunchedEffect(transientEntry?.id, notificationsPageVisible) {
        val entryId = transientEntry?.id ?: return@LaunchedEffect
        if (notificationsPageVisible) {
            notifications.markAllRead()
        } else {
            delay(SETTINGS_NOTIFICATION_AUTO_DISMISS_DURATION)
            notifications.dismissTransient(entryId)
        }
    }
    if (notificationsPageVisible || transientEntry == null) return

    SettingsNotificationToastCard(
        entry = transientEntry,
        onDismiss = notifications::dismissTransient,
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(top = 12.dp, end = 56.dp)
            .widthIn(max = 360.dp)
            .zIndex(20f),
    )
}

/** 新消息的轻量提示；关闭后只收起提示，不删除历史记录。 */
@Composable
private fun SettingsNotificationToastCard(
    entry: SettingsChangeNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    JewelSurface(
        role = JewelSurfaceRole.FLOATING,
        radius = 10.dp,
        solidColor = AppSidebarBackground,
        borderColor = AppLine,
        modifier = modifier.hoverable(interactionSource),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                Text(
                    text = "关闭",
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 3.dp, vertical = 2.dp),
                    style = JewelTheme.defaultTextStyle.copy(
                        color = AppAccent,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
            }
        }
    }
}

/** 将通知类别转为不含配置或敏感字段的 UI 标签。 */
internal val SettingsChangeNotificationCategory.label: String
    get() = when (this) {
        SettingsChangeNotificationCategory.EXTENSIONS -> "扩展设置"
        SettingsChangeNotificationCategory.AI_SERVICES -> "AI 服务"
    }
