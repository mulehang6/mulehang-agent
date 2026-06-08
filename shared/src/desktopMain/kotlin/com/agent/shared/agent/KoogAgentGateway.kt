package com.agent.shared.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import com.agent.shared.config.ConfigProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Koog 1.0.0 接入点，负责执行单轮消息并转换为应用事件。
 */
class KoogAgentGateway : AgentGateway {
    /**
     * 运行一次消息请求。
     */
    override fun run(prompt: String, config: ConfigProfile): Flow<AgentStreamEvent> = flow {
        emit(AgentStreamEvent.Started)

        try {
            val agent = AIAgent(
                promptExecutor = buildPromptExecutor(config),
                llmModel = OpenAIModels.Chat.GPT5_4Mini,
            )
            val result = agent.run(prompt)
            emit(AgentStreamEvent.Delta(result))
            emit(AgentStreamEvent.Completed(result))
        } catch (e: Exception) {
            emit(AgentStreamEvent.Failed(e.message ?: "执行错误"))
        }
    }
}
