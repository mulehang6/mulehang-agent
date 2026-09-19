package com.agent.shared.agent.hook

import com.agent.shared.settings.model.AgentHookEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 不含命令、输入和输出内容的 Hook 执行摘要。 */
data class AgentHookExecutionSummary(
    val sessionId: String,
    val event: AgentHookEvent,
    val asynchronous: Boolean,
    val durationMillis: Long,
    val outcome: String,
    val commandIndex: Int = 0,
)

/** 仅在当前进程保存最近执行结果，供设置页查看，不持久化敏感命令。 */
object AgentHookExecutionHistory {
    private val mutableRecent = MutableStateFlow<List<AgentHookExecutionSummary>>(emptyList())
    val recent = mutableRecent.asStateFlow()

    /** 限制记录数量，避免长期运行时无限积累。 */
    internal fun record(summary: AgentHookExecutionSummary) {
        mutableRecent.update { (listOf(summary) + it).take(50) }
    }
}
