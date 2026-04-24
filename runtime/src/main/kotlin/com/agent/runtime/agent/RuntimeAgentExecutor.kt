package com.agent.runtime.agent

import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.provider.OpenAIEndpointMode
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import com.agent.runtime.core.RuntimeAgentExecutionFailure
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeCapabilityBridgeFailure
import com.agent.runtime.core.RuntimeFailed
import com.agent.runtime.core.RuntimeInfoEvent
import com.agent.runtime.core.RuntimeProviderResolutionFailure
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeResult
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
import kotlinx.serialization.json.JsonPrimitive

/**
 * 负责把 runtime 的 agent.run 请求翻译到真实 Koog agent 执行。
 */
class RuntimeAgentExecutor(
    private val assembleAgent: suspend (ProviderBinding, CapabilitySet) -> AssembledAgent = { binding, capabilitySet ->
        AgentAssembly().assemble(binding, capabilitySet)
    },
    private val runner: suspend (AssembledAgent, String) -> String = { assembled, prompt ->
        assembled.agent.run(prompt)
    },
) {

    /**
     * 执行一次 runtime agent 请求，并把结果翻译为统一 runtime result。
     */
    suspend fun execute(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: RuntimeAgentRunRequest,
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): RuntimeResult {
        return try {
            val assembledAgent = assembleAgent(binding, capabilitySet)
            val output = runner(assembledAgent, request.prompt)

            RuntimeSuccess(
                events = listOf(
                    RuntimeInfoEvent(message = "agent.run.started", payload = JsonPrimitive(context.requestId)),
                    RuntimeInfoEvent(message = "agent.run.completed", payload = JsonPrimitive(session.id)),
                ),
                output = JsonPrimitive(output),
            )
        } catch (error: IllegalArgumentException) {
            RuntimeFailed(
                failure = RuntimeProviderResolutionFailure(
                    message = error.message ?: "provider resolution failed",
                    cause = error,
                ),
            )
        } catch (error: IllegalStateException) {
            RuntimeFailed(
                failure = RuntimeCapabilityBridgeFailure(
                    message = error.message ?: "capability bridge failed",
                    cause = error,
                ),
            )
        } catch (error: Throwable) {
            RuntimeFailed(
                failure = RuntimeAgentExecutionFailure(
                    message = binding.agentExecutionFailureMessage(error),
                    cause = error,
                ),
            )
        }
    }

    /**
     * 在 Responses API 明确不可用时，给出可操作的 chat/completions 切换提示。
     */
    private fun ProviderBinding.agentExecutionFailureMessage(error: Throwable): String {
        val message = error.message ?: "agent execution failed"
        if (usesOpenAIResponsesEndpoint() && message.looksLikeUnsupportedResponsesEndpoint()) {
            return "OpenAI Responses API request failed. Switch this provider to chat/completions if it does not support Responses. Cause: $message"
        }
        return message
    }

    /**
     * 判断当前 binding 是否会按 OpenAI Responses endpoint 执行。
     */
    private fun ProviderBinding.usesOpenAIResponsesEndpoint(): Boolean =
        providerType in OPENAI_PROTOCOL_PROVIDER_TYPES &&
            (options.openAIEndpointMode ?: OpenAIEndpointMode.RESPONSES) == OpenAIEndpointMode.RESPONSES

    /**
     * 保守识别 Responses endpoint 不存在或不支持的常见错误文本。
     */
    private fun String.looksLikeUnsupportedResponsesEndpoint(): Boolean {
        val normalized = lowercase()
        return "responses" in normalized &&
            ("404" in normalized || "not found" in normalized || "unsupported" in normalized)
    }

    private companion object {
        private val OPENAI_PROTOCOL_PROVIDER_TYPES = setOf(
            ProviderType.OPENAI,
            ProviderType.OPENAI_COMPATIBLE,
        )
    }
}
