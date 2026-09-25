package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest

/** 仅在完整用户轮次边界切分历史，保留最近原文。 */
internal data class ContextCompactionPlan(
    val prefixCount: Int,
    val sourceTokens: Int,
    val retainedTokens: Int,
)

/** 使用本轮窗口和阈值计算压缩边界，绝不从工具调用与结果之间截断。 */
internal fun planContextCompaction(request: AgentRunRequest): ContextCompactionPlan? {
    val window = request.contextWindow?.takeIf { it > 0 } ?: return null
    val threshold = request.contextCompactionThresholdPercent
    require(threshold in 50..95 && threshold % 5 == 0) { "上下文压缩阈值必须介于 50–95%，步长为 5。" }
    if ((request.contextUsageFraction ?: 0f) * 100 < threshold) return null
    val history = request.history
    val userStarts = history.indices.filter { history[it] is AgentConversationHistoryMessage.User }
    if (userStarts.size < 2) return null
    val tailBudget = minOf(20_000, (window * 0.3).toInt().coerceAtLeast(512))
    var boundary = userStarts.last()
    for (candidate in userStarts.dropLast(1).asReversed()) {
        if (estimateHistoryTokens(history.drop(candidate)) > tailBudget) break
        boundary = candidate
    }
    if (boundary <= 0) return null
    return ContextCompactionPlan(
        prefixCount = boundary,
        sourceTokens = estimateHistoryTokens(history),
        retainedTokens = estimateHistoryTokens(history.drop(boundary)),
    )
}

/** 文本近似按四字符一个 token，结构片段另计固定开销。 */
internal fun estimateHistoryTokens(history: List<AgentConversationHistoryMessage>): Int = history.sumOf { message ->
    when (message) {
        is AgentConversationHistoryMessage.User -> (message.content.length + 3) / 4 + 8
        is AgentConversationHistoryMessage.Assistant -> message.parts.sumOf { part ->
            (part.toString().length + 3) / 4 + 8
        }
    }
}
