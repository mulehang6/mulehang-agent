@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.RightRailGlyph
import org.jetbrains.jewel.ui.component.Text

/** 会话树右侧工具页的两个互补视图。 */
private enum class ConversationTreePanelView {
    BRANCHES,
    ALL_ENTRIES,
}

/**
 * 在右侧工具区域展示完整会话图；路径切换只更新活动 leaf，不会关闭面板或移除图中的其他节点。
 */
@Composable
internal fun ConversationTreePanel(
    state: ChatWindowState,
    conversation: ChatConversationUiState?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDesktopPalette.current
    var view by remember(conversation?.id) { mutableStateOf(ConversationTreePanelView.BRANCHES) }
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 14.dp,
        solidColor = palette.panelBackground,
        borderColor = Color.Transparent,
        borderWidth = 0.dp,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            RightToolPageTitleTab(
                label = conversation?.let { "会话树 · ${it.title}" } ?: "会话树",
                glyph = RightRailGlyph.CONVERSATION_TREE,
                onClose = onClose,
            )
            if (conversation == null) {
                ConversationTreeEmptyState("请先选择一个会话。", Modifier.weight(1f))
            } else if (conversation.treeFormatVersion <= 0) {
                ConversationTreeEmptyState("旧线性会话不支持条目树。", Modifier.weight(1f))
            } else {
                ConversationTreeViewSwitcher(selected = view, onSelect = { view = it })
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
                when (view) {
                    ConversationTreePanelView.BRANCHES -> ConversationBranchPanelContent(
                        state = state,
                        conversation = conversation,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )

                    ConversationTreePanelView.ALL_ENTRIES -> ConversationEntryPanelContent(
                        state = state,
                        conversation = conversation,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 会话树页内的双层视图选择器。 */
@Composable
private fun ConversationTreeViewSwitcher(
    selected: ConversationTreePanelView,
    onSelect: (ConversationTreePanelView) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationTreeViewAction(
            label = "会话内分支",
            selected = selected == ConversationTreePanelView.BRANCHES,
            onClick = { onSelect(ConversationTreePanelView.BRANCHES) },
        )
        ConversationTreeViewAction(
            label = "全部条目",
            selected = selected == ConversationTreePanelView.ALL_ENTRIES,
            onClick = { onSelect(ConversationTreePanelView.ALL_ENTRIES) },
        )
    }
}

/** 绘制无额外动画的页内切换动作。 */
@Composable
private fun ConversationTreeViewAction(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(7.dp)
    val background = when {
        selected -> islandsTabSelectedFill(isDark = palette.isDark)
        hovered -> palette.hoverBackground.copy(alpha = 0.74f)
        else -> Color.Transparent
    }
    val border = if (selected) islandsTabSelectedBorder(isDark = palette.isDark) else Color.Transparent
    Text(
        text = label,
        color = if (selected || hovered) AppText else AppMuted,
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 会话树不可用时的稳定空状态。 */
@Composable
private fun ConversationTreeEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = AppMuted)
    }
}
