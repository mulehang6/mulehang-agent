package com.agent.runtime.cli

import com.agent.runtime.agent.RuntimeAgentExecutor
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.core.RuntimeAgentExecutionFailure
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeCapabilityBridgeFailure
import com.agent.runtime.core.RuntimeEvent
import com.agent.runtime.core.RuntimeFailed
import com.agent.runtime.core.RuntimeFailure
import com.agent.runtime.core.RuntimeProviderResolutionFailure
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive

/**
 * 负责把 CLI 的最小 `stdio` 请求翻译成 runtime 事件流。
 */
class DefaultRuntimeCliService(
    private val runtimeAgentExecutor: RuntimeAgentExecutor = RuntimeAgentExecutor(),
    private val capabilitySetFactory: () -> CapabilitySet = { CapabilitySet(adapters = emptyList()) },
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * 按顺序产出一次 CLI 运行请求对应的状态、事件和最终结果。
     */
    fun stream(request: RuntimeCliRunRequest): Flow<RuntimeCliOutboundMessage> = flow {
        val sessionId = request.sessionId ?: sessionIdFactory()
        val requestId = requestIdFactory()
        emit(
            RuntimeCliStatusMessage(
                status = "session.started",
                sessionId = sessionId,
                requestId = requestId,
                mode = request.mode(),
            ),
        )

        if (request.provider == null) {
            emit(
                RuntimeCliStatusMessage(
                    status = "run.started",
                    sessionId = sessionId,
                    requestId = requestId,
                    mode = DEMO_MODE,
                ),
            )
            emit(
                RuntimeCliEventMessage(
                    sessionId = sessionId,
                    requestId = requestId,
                    event = RuntimeCliEventPayload(
                        message = "runtime.cli.demo",
                        payload = JsonPrimitive(request.prompt),
                    ),
                ),
            )
            emit(
                RuntimeCliResultMessage(
                    sessionId = sessionId,
                    requestId = requestId,
                    output = JsonPrimitive("echo:${request.prompt}"),
                    mode = DEMO_MODE,
                ),
            )
            return@flow
        }

        val binding = request.provider.toDomainBinding()
            ?: run {
                emit(
                    RuntimeCliFailureMessage(
                        sessionId = sessionId,
                        requestId = requestId,
                        kind = "provider",
                        message = "Unsupported provider type '${request.provider.providerType}'.",
                    ),
                )
                return@flow
            }

        emit(
            RuntimeCliStatusMessage(
                status = "run.started",
                sessionId = sessionId,
                requestId = requestId,
                mode = AGENT_MODE,
            ),
        )

        when (
            val result = runtimeAgentExecutor.execute(
                session = RuntimeSession(id = sessionId),
                context = RuntimeRequestContext(sessionId = sessionId, requestId = requestId),
                request = RuntimeAgentRunRequest(prompt = request.prompt),
                binding = binding,
                capabilitySet = capabilitySetFactory(),
            )
        ) {
            is RuntimeSuccess -> {
                result.events.forEach { event ->
                    emit(
                        RuntimeCliEventMessage(
                            sessionId = sessionId,
                            requestId = requestId,
                            event = event.toPayload(),
                        ),
                    )
                }
                emit(
                    RuntimeCliResultMessage(
                        sessionId = sessionId,
                        requestId = requestId,
                        output = result.output,
                        mode = AGENT_MODE,
                    ),
                )
            }

            is RuntimeFailed -> {
                result.events.forEach { event ->
                    emit(
                        RuntimeCliEventMessage(
                            sessionId = sessionId,
                            requestId = requestId,
                            event = event.toPayload(),
                        ),
                    )
                }
                emit(
                    RuntimeCliFailureMessage(
                        sessionId = sessionId,
                        requestId = requestId,
                        kind = result.failure.toFailureKind(),
                        message = result.failure.message,
                    ),
                )
            }
        }
    }

    /**
     * 把 CLI 协议中的 provider 载荷翻译为现有 runtime 领域 binding。
     */
    private fun RuntimeCliProviderBinding.toDomainBinding(): ProviderBinding? {
        val providerType = runCatching { ProviderType.valueOf(providerType) }.getOrNull() ?: return null
        return ProviderBinding(
            providerId = providerId,
            providerType = providerType,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
        )
    }

    /**
     * 把 runtime 事件翻译为 CLI 可消费的稳定事件载荷。
     */
    private fun RuntimeEvent.toPayload(): RuntimeCliEventPayload = RuntimeCliEventPayload(
        message = message,
        payload = payload,
    )

    /**
     * 把 runtime 失败对象压平为 CLI 协议层的稳定失败类别。
     */
    private fun RuntimeFailure.toFailureKind(): String = when (this) {
        is RuntimeProviderResolutionFailure -> "provider"
        is RuntimeCapabilityBridgeFailure -> "capability"
        is RuntimeAgentExecutionFailure -> "agent"
        else -> "runtime"
    }

    /**
     * 根据是否提供 provider binding 判断当前运行模式。
     */
    private fun RuntimeCliRunRequest.mode(): String = if (provider == null) DEMO_MODE else AGENT_MODE

    private companion object {
        private const val AGENT_MODE = "agent"
        private const val DEMO_MODE = "demo"
    }
}
