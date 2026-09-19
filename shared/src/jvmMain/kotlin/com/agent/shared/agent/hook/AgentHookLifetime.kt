package com.agent.shared.agent.hook

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren

/** 会话持有后台 Hook；单轮完成不取消，关闭会话时统一回收。 */
class AgentHookLifetime : AutoCloseable {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 结束事件执行前先取消此前仍在运行的维护任务。 */
    fun cancelPending() = scope.coroutineContext.cancelChildren()

    /** 会话结束事件也可配置后台命令；在关闭所有权前等待其受限执行结束。 */
    suspend fun awaitPending() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() }
    }

    /** 关闭操作幂等，已关闭作用域不会再启动命令。 */
    override fun close() = scope.cancel()
}
