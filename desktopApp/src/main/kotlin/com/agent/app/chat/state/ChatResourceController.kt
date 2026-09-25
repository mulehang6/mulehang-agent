package com.agent.app.chat.state

import com.agent.shared.agent.resource.AgentResourceSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 管理工作区资源快照及手动重载与 Agent 运行的互斥。 */
internal class ChatResourceController(private val window: ChatWindowState) {
    var reloadInProgress = false
        private set

    /** 当前没有 Agent 任务占用 MCP 连接时才允许重载资源。 */
    val canReload: Boolean
        get() = window.activeRunJob == null && window.activeRunConversationId == null && !reloadInProgress

    /** 运行期间拒绝重载，避免中途断开 MCP 工具。 */
    suspend fun reload(): Boolean {
        if (!canReload) return false
        reloadInProgress = true
        return try {
            reloadInternal()
        } finally {
            reloadInProgress = false
        }
    }

    /** 先占重载槽位，再异步读取资源。 */
    fun startReload(onSuccess: () -> Unit): Boolean {
        if (!canReload) return false
        reloadInProgress = true
        window.scope.launch {
            try {
                if (reloadInternal()) onSuccess()
            } finally {
                reloadInProgress = false
            }
        }
        return true
    }

    /** 从已发布的资源版本替换当前快照。 */
    fun refreshActiveSnapshot() {
        val workspacePath = window.ui.activeConversationOrNull?.workspacePath ?: window.ui.newWorkspacePath
        window.scope.launch {
            val next = withContext(window.resourceDispatcher) {
                window.resourceSnapshotProvider(workspacePath)
            } ?: AgentResourceSnapshot.empty()
            if ((window.ui.activeConversationOrNull?.workspacePath ?: window.ui.newWorkspacePath) == workspacePath) {
                val current = window.resourceSnapshot
                if (next.version >= current.version) {
                    if (current.version != next.version || current.workspacePath != next.workspacePath) {
                        window.runtimeResourceDiagnostics = emptyList()
                    }
                    window.resourceSnapshot = next
                }
            }
        }
    }

    /** provider 缺失时返回空快照，避免注入另一个工作区的旧资源。 */
    fun refreshFor(workspacePath: String): AgentResourceSnapshot =
        window.resourceSnapshotProvider(workspacePath)?.also { next ->
            if (window.resourceSnapshot.version != next.version ||
                window.resourceSnapshot.workspacePath != next.workspacePath
            ) window.runtimeResourceDiagnostics = emptyList()
            window.resourceSnapshot = next
        } ?: AgentResourceSnapshot.empty()

    /** 发送后的读取离开 UI 线程，只发布当前工作区对应的结果。 */
    suspend fun loadForRun(workspacePath: String): AgentResourceSnapshot {
        val next = withContext(window.resourceDispatcher) {
            window.resourceSnapshotProvider(workspacePath) ?: AgentResourceSnapshot.empty()
        }
        if ((window.ui.activeConversationOrNull?.workspacePath ?: window.ui.newWorkspacePath) == workspacePath) {
            window.resourceSnapshot = next
        }
        return next
    }

    /** 执行已占用槽位的资源重载。 */
    private suspend fun reloadInternal(): Boolean {
        val workspacePath = window.ui.activeConversationOrNull?.workspacePath ?: window.ui.newWorkspacePath
        val next = withContext(window.resourceDispatcher) {
            window.resourceReloader(workspacePath)
        } ?: return false
        window.resourceSnapshot = next
        window.runtimeResourceDiagnostics = emptyList()
        return true
    }
}
