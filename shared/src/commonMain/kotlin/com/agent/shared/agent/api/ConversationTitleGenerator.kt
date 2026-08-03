package com.agent.shared.agent.api

import com.agent.shared.settings.model.ConfigProfile

/**
 * 生成会话标题所需的最小输入，避免标题任务获得正常聊天的历史和工具能力。
 */
data class ConversationTitleRequest(
    val firstUserMessage: String,
    val profile: ConfigProfile,
)

/**
 * 独立于常规 Agent 对话的无工具会话标题生成入口。
 */
interface ConversationTitleGenerator {
    /**
     * 根据首条用户消息生成简短的会话标题。
     */
    suspend fun generate(request: ConversationTitleRequest): String
}
