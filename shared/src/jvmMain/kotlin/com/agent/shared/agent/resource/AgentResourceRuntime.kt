package com.agent.shared.agent.resource

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 资源快照生命周期。
 *
 * 同一工作区读取时始终返回当前快照；只有调用 [reload] 或切换工作区才会运行文件发现。这样
 * 正在运行的 Agent 可继续持有它在请求创建时得到的 [AgentResourceSnapshot]，不会被 GUI
 * 的重新加载或 Git 更新中途改变。
 */
class AgentResourceRuntime(
    private val loader: AgentResourceLoader = AgentResourceLoader(),
) {
    private val version = AtomicLong(0L)
    private val current = AtomicReference(AgentResourceSnapshot.empty())

    /** 返回当前工作区快照；首次进入或工作区变更时建立初始版本。 */
    fun snapshotFor(request: AgentResourceLoadRequest): AgentResourceSnapshot {
        val existing = current.get()
        val requestedWorkspace = request.workspacePath?.normalizedExistingOrAbsolute()
        return if (existing.version > 0L && existing.workspacePath == requestedWorkspace) {
            existing
        } else {
            reload(request)
        }
    }

    /** 显式重新发现资源并发布下一个版本，不主动拉取 Git 或启动 MCP 服务。 */
    @Synchronized
    fun reload(request: AgentResourceLoadRequest): AgentResourceSnapshot {
        val next = loader.load(request, version.incrementAndGet())
        current.set(next)
        return next
    }

    /** 获取最后一次发布的快照，供扩展中心显示诊断与版本号。 */
    fun currentSnapshot(): AgentResourceSnapshot = current.get()
}
