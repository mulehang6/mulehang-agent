package com.agent.app.chat.component

/**
 * 表示一个绑定到固定工作区路径的内嵌终端标签页。
 */
internal data class TerminalTab(
    val id: Long,
    val workspacePath: String,
    val title: String,
)

/**
 * 保存内嵌终端标签页及当前选中页的纯展示状态。
 */
internal data class TerminalTabsState(
    val tabs: List<TerminalTab> = emptyList(),
    val activeTabId: Long? = null,
    val nextTabId: Long = 1,
)

/**
 * 为 [workspacePath] 新增并选中一个终端标签页。
 */
internal fun TerminalTabsState.addTab(workspacePath: String): TerminalTabsState {
    val tab = TerminalTab(
        id = nextTabId,
        workspacePath = workspacePath,
        title = if (nextTabId == 1L) "终端" else "终端 $nextTabId",
    )
    return copy(
        tabs = tabs + tab,
        activeTabId = tab.id,
        nextTabId = nextTabId + 1,
    )
}

/**
 * 选中已有的 [tabId]，未知标签页不改变状态。
 */
internal fun TerminalTabsState.selectTab(tabId: Long): TerminalTabsState =
    if (tabs.any { it.id == tabId }) copy(activeTabId = tabId) else this

/**
 * 关闭 [tabId]，当前页被关闭时优先选中其左侧相邻页。
 */
internal fun TerminalTabsState.closeTab(tabId: Long): TerminalTabsState {
    val removedIndex = tabs.indexOfFirst { it.id == tabId }
    if (removedIndex < 0) return this

    val remainingTabs = tabs.filterNot { it.id == tabId }
    val nextActiveTabId = when {
        activeTabId != tabId -> activeTabId
        remainingTabs.isEmpty() -> null
        else -> remainingTabs.getOrNull(removedIndex - 1)?.id ?: remainingTabs[removedIndex].id
    }
    return copy(tabs = remainingTabs, activeTabId = nextActiveTabId)
}

/**
 * 仅保留 [tabId] 对应的终端标签页，并将它设为活动标签。
 */
internal fun TerminalTabsState.retainOnly(tabId: Long): TerminalTabsState {
    val retainedTab = tabs.firstOrNull { it.id == tabId } ?: return this
    return copy(tabs = listOf(retainedTab), activeTabId = retainedTab.id)
}

/**
 * 判断当前状态是否存在可显示的活动终端页。
 */
internal fun TerminalTabsState.hasActiveTab(): Boolean = activeTabId != null && tabs.any { it.id == activeTabId }

/** 描述右侧终端按钮对面板可见性及标签状态的唯一操作。 */
internal enum class TerminalRailAction {
    CREATE_AND_SHOW,
    SHOW,
    HIDE,
}

/**
 * 根据面板当前可见性与已有标签解析终端按钮行为；收起不影响终端会话。
 */
internal fun terminalRailAction(
    panelVisible: Boolean,
    hasActiveTab: Boolean,
): TerminalRailAction = when {
    panelVisible -> TerminalRailAction.HIDE
    hasActiveTab -> TerminalRailAction.SHOW
    else -> TerminalRailAction.CREATE_AND_SHOW
}

/** 仅关闭最后一个标签时需等待终端面板退出动画，避免窗口内容突然清空。 */
internal fun shouldDeferTerminalTabClose(tabs: TerminalTabsState): Boolean = tabs.tabs.size == 1

/** 在最后一个标签关闭后清空窗口状态，令下一次打开创建全新的首个终端。 */
internal fun TerminalTabsState.resetAfterTerminalWindowClosed(): TerminalTabsState = TerminalTabsState()

/**
 * 判断是否需要向终端发送新的 Swing 焦点请求。
 */
internal fun shouldRequestTerminalFocus(isTerminalFocused: Boolean): Boolean = !isTerminalFocused
