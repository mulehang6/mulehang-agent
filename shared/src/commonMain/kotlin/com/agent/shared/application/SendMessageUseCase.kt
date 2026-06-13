package com.agent.shared.application

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentRunRequest
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.config.ConfigProfile
import com.agent.shared.exceptions.IllegalConfigExceptions
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 发送消息用例骨架。
 */
class SendMessageUseCase(
    private val agentGateway: AgentGateway,
) {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    /**
     * 执行一次消息发送。
     */
    operator fun invoke(prompt: String, profile: ConfigProfile): Flow<AgentStreamEvent> {
        return invoke(
            AgentRunRequest(
                prompt = prompt,
                profile = profile,
            ),
        )
    }

    /**
     * 执行一次带运行参数的消息发送。
     */
    operator fun invoke(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
        if (request.prompt.isBlank()) {
            log.warn { "提示词为空" }
            throw IllegalArgumentException("提示词不能为空")
        }

        if (request.profile.id.isBlank()) {
            log.warn { "profile id 为空" }
            throw IllegalConfigExceptions { "profile id 不能为空" }
        }
        log.info { buildAgentRunRequestDiagnostic(request) }
        agentGateway.run(request).collect(::emit)
    }

}

/**
 * 构造不包含 prompt、apiKey 的发送请求诊断摘要。
 */
internal fun buildAgentRunRequestDiagnostic(request: AgentRunRequest): String =
    "Agent request: provider=${request.profile.providerId} " +
        "model=${request.profile.model} " +
        "reasoning_effort=${request.reasoningEffort?.wireValue ?: "null"} " +
        "context=${request.profile.limit?.context ?: "null"} " +
        "output=${request.profile.limit?.output ?: "null"}"
