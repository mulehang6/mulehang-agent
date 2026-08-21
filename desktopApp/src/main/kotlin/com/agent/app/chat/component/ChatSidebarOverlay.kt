@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * 渲染左侧任务 Island 的白色表面和明确的收起入口。
 *
 * 分栏占位、滑入和拖动由 [TaskSidebarSplitLayout] 管理；此组件不再拥有覆盖层行为，
 * 因此点击主工作区或右侧 Island 不会令它收起。
 */
@Composable
internal fun TaskSidebarIsland(
    state: ChatWindowState,
    compact: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JewelSurface(
        role = JewelSurfaceRole.CHROME,
        radius = 12.dp,
        solidColor = AppWorkspaceBackground,
        borderWidth = 0.dp,
        modifier = modifier
            .padding(
                top = ISLANDS_LAYOUT_GAP,
                bottom = ISLANDS_LAYOUT_GAP,
            )
            .fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TaskSidebar(
                state = state,
                compact = compact,
                modifier = Modifier.fillMaxSize().padding(bottom = 44.dp),
            )
            ActionButton(
                onClick = onCollapse,
                tooltip = { Text("收起任务侧栏") },
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Icon(
                    key = AllIconsKeys.General.HideToolWindow,
                    contentDescription = "收起任务侧栏",
                    tint = AppMuted,
                )
            }
        }
    }
}
