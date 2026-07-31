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
        title = "终端 $nextTabId",
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

/**
 * 描述终端图标点击后的状态意图，避免重复点击反转面板可见性。
 */
internal enum class TerminalIconAction {
    CREATE_TAB,
    FOCUS_ACTIVE_TAB,
}

/**
 * 根据是否已有终端页，解析终端图标的唯一状态意图。
 */
internal fun terminalIconAction(hasActiveTab: Boolean): TerminalIconAction =
    if (hasActiveTab) TerminalIconAction.FOCUS_ACTIVE_TAB else TerminalIconAction.CREATE_TAB

/**
 * 判断是否需要向终端发送新的 Swing 焦点请求。
 */
internal fun shouldRequestTerminalFocus(isTerminalFocused: Boolean): Boolean = !isTerminalFocused
