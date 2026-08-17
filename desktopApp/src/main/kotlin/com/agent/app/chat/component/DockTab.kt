@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.PANEL_TAB_ICON_SIZE
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Dock 标签与设置标签共享的固定高度。 */
internal val DOCK_TAB_HEIGHT = 32.dp

/** Dock 标签在 hover 和选中状态下使用的统一圆角。 */
internal val DOCK_TAB_CORNER_RADIUS = 7.dp

/**
 * 绘制设置与终端共用的 Dock 标签外观。
 *
 * 调用方可在 [modifier] 中附加快捷键、右键菜单定位等交互；基础的选择、hover 与关闭行为保持一致。
 */
@Composable
internal fun DockTab(
    label: String,
    iconKey: IconKey,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedBackground: Color = AppSelectedBackground,
    hoverBackground: Color = AppHoverBackground,
    selectedBorder: Color = AppAccent,
) {
    var hovered by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(DOCK_TAB_CORNER_RADIUS)
    val background = when {
        selected -> selectedBackground
        hovered -> hoverBackground
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .height(DOCK_TAB_HEIGHT)
            .widthIn(max = 220.dp)
            .clip(shape)
            .background(background)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) selectedBorder else Color.Transparent,
                shape = shape,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = if (onClose == null) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            key = iconKey,
            contentDescription = label,
            modifier = Modifier.size(PANEL_TAB_ICON_SIZE),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        onClose?.let { close ->
            IconActionButton(
                key = AllIconsKeys.Actions.Cancel,
                contentDescription = "关闭 $label",
                onClick = close,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
