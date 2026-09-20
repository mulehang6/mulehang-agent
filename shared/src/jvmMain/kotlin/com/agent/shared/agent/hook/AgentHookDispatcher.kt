package com.agent.shared.agent.hook

import com.agent.shared.settings.model.AgentHookEvent
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Hook 可以要求调用方继续、自动放行、转人工确认或阻断本次动作。 */
enum class AgentHookDecision {
    CONTINUE,
    ALLOW,
    ASK,
    BLOCK,
}
/** 一次 Hook 调用的上下文；payload 会以 JSON 通过 stdin 传给命令。 */
data class AgentHookDispatchRequest(
    val event: AgentHookEvent,
    val sessionId: String,
    val workspacePath: String,
    val matcherValue: String? = null,
    val payload: JsonObject = buildJsonObject { },
    val traceId: String = "",
)

/** Hook 命令返回给运行时的可执行结果。 */
data class AgentHookDispatchResult(
    val decision: AgentHookDecision = AgentHookDecision.CONTINUE,
    val additionalContext: String? = null,
    val updatedInput: JsonObject? = null,
)

/** 隔离 Hook 运行时，便于本地工具与 Agent 生命周期共享同一套策略。 */
interface AgentHookDispatcher {
    /** 按配置顺序运行匹配的 Hook，并返回合并后的执行结果。 */
    suspend fun dispatch(request: AgentHookDispatchRequest): AgentHookDispatchResult

    /** 让每轮冻结的规则共享会话后台任务所有权。 */
    fun bindLifetime(lifetime: AgentHookLifetime) = Unit

    /** 释放独立 dispatcher 的后台任务。 */
    fun close() = Unit
}

/** 未配置 Hook 时的零开销实现。 */
object NoAgentHookDispatcher : AgentHookDispatcher {
    override suspend fun dispatch(request: AgentHookDispatchRequest): AgentHookDispatchResult = AgentHookDispatchResult()
}

/** 子进程执行结果，供 Hook 解析逻辑和单元测试共用。 */
data class AgentHookCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean,
)

/** 执行 Hook 命令的窄接口，避免测试依赖真实 Windows Shell。 */
fun interface AgentHookCommandExecutor {
    /** 在给定工作目录执行一条 `cmd.exe` 命令，并把 input 写入 stdin。 */
    fun execute(
        command: String,
        workingDirectory: File,
        input: String,
        timeoutMillis: Long,
    ): AgentHookCommandResult

    /** 旧测试执行器保持兼容；真实进程实现必须观察取消。 */
    fun executeCancellable(
        command: String,
        workingDirectory: File,
        input: String,
        timeoutMillis: Long,
        isCancelled: () -> Boolean,
    ): AgentHookCommandResult = execute(command, workingDirectory, input, timeoutMillis)
}
