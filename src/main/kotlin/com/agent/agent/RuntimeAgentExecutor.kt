package com.agent.agent

import com.agent.capability.CapabilitySet
import com.agent.provider.ProviderBinding
import com.agent.runtime.RuntimeAgentExecutionFailure
import com.agent.runtime.RuntimeAgentRunRequest
import com.agent.runtime.RuntimeCapabilityBridgeFailure
import com.agent.runtime.RuntimeFailed
import com.agent.runtime.RuntimeInfoEvent
import com.agent.runtime.RuntimeProviderResolutionFailure
import com.agent.runtime.RuntimeRequestContext
import com.agent.runtime.RuntimeResult
import com.agent.runtime.RuntimeSession
import com.agent.runtime.RuntimeSuccess
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
                    message = error.message ?: "agent execution failed",
                    cause = error,
                ),
            )
        }
    }
}
