package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * 集中管理嵌入式终端的标签、可见性和延迟关闭生命周期。
 *
 * 该控制器只保存界面生命周期状态，不改变终端创建或关闭时机。
 */
internal class TerminalPanelController {
    var tabs by mutableStateOf(TerminalTabsState())
        private set
    var visible by mutableStateOf(false)
        private set
    private var pendingTabCloseId by mutableStateOf<Long?>(null)
    val sessions = TerminalSessionStore()

    /** 选择现有终端标签。 */
    fun select(tabId: Long) {
        tabs = tabs.selectTab(tabId)
    }

    /** 为工作区新建并显示终端标签。 */
    fun add(workspacePath: String) {
        pendingTabCloseId = null
        tabs = tabs.addTab(workspacePath)
        sessions.create(tabs.tabs.last())
        visible = true
    }

    /** 关闭标签；最后一个可见标签沿用原来的延迟关闭动画。 */
    fun close(tabId: Long) {
        if (visible && shouldDeferTerminalTabClose(tabs)) {
            pendingTabCloseId = tabId
            visible = false
        } else {
            sessions.close(tabId)
            tabs = tabs.closeTab(tabId)
            visible = tabs.hasActiveTab()
        }
    }

    /** 关闭除保留标签外的全部终端。 */
    fun closeOthers(keptTabId: Long) {
        sessions.closeAllExcept(keptTabId)
        tabs = tabs.retainOnly(keptTabId)
    }

    /** 收起终端而不结束会话。 */
    fun hide() {
        visible = false
    }

    /** 根据右侧工具栏操作显示、隐藏或创建终端。 */
    fun toggleFromRail(workspacePath: String) {
        when (terminalRailAction(panelVisible = visible, hasActiveTab = tabs.hasActiveTab())) {
            TerminalRailAction.CREATE_AND_SHOW -> add(workspacePath)
            TerminalRailAction.SHOW -> {
                pendingTabCloseId = null
                visible = true
            }
            TerminalRailAction.HIDE -> hide()
        }
    }

    /** 处理终端面板退出动画完成后的延迟资源释放。 */
    suspend fun closePendingTabAfterExit() {
        val tabId = pendingTabCloseId
        if (tabId != null && !visible) {
            delay((TERMINAL_PANEL_EXIT_DURATION_MILLIS.toLong() + TERMINAL_PANEL_CLOSE_DELAY_MILLIS).milliseconds)
            if (pendingTabCloseId == tabId && !visible) {
                sessions.close(tabId)
                tabs = tabs.resetAfterTerminalWindowClosed()
                pendingTabCloseId = null
            }
        }
    }

    /** 释放所有关联终端会话。 */
    fun closeAll() {
        sessions.closeAll()
    }
}

/** 创建并绑定组合生命周期的终端面板控制器。 */
@Composable
internal fun rememberTerminalPanelController(): TerminalPanelController {
    val controller = remember { TerminalPanelController() }
    DisposableEffect(controller) {
        onDispose(controller::closeAll)
    }
    return controller
}
