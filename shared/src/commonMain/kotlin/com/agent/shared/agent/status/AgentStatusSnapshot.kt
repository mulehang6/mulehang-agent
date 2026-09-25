package com.agent.shared.agent.status

import kotlinx.serialization.Serializable

/** 每次模型请求前由程序生成的确定性运行状态。 */
@Serializable
data class AgentStatusSnapshot(
    val timestampMillis: Long,
    val turnElapsedMillis: Long,
    val toolCallCount: Int,
    val errorCount: Int,
    val errors: List<String>,
    val todos: List<AgentTodoItem>,
    val workspacePath: String,
    val gitStatus: String,
    val modelId: String,
    val contextUsageFraction: Float?,
    val contextWindow: Int?,
    val recoveryState: String,
)

/** 将状态快照格式化成模型实际接收、详情界面也可逐字显示的内部消息。 */
fun AgentStatusSnapshot.toModelMessage(): String = buildString {
    appendLine("[内部 Agent 状态]")
    appendLine("时间戳：$timestampMillis")
    appendLine("当前轮耗时：${turnElapsedMillis}ms")
    appendLine("工具调用：$toolCallCount；错误：$errorCount")
    if (errors.isNotEmpty()) appendLine("最近错误：${errors.joinToString("；")}")
    appendLine("TODO：${todos.count { it.status == AgentTodoStatus.COMPLETED }}/${todos.size} 完成")
    todos.forEach { appendLine("- [${it.status}] ${it.id}: ${it.content}") }
    appendLine("工作目录：$workspacePath")
    appendLine("Git：$gitStatus")
    appendLine("模型：$modelId")
    appendLine("上下文：${contextUsageFraction?.let { "${(it * 100).toInt()}%" } ?: "未知"} / ${contextWindow ?: "未知"} tokens")
    appendLine("恢复状态：$recoveryState")
    append("[/内部 Agent 状态]")
}
