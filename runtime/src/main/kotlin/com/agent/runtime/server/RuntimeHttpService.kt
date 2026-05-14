package com.agent.runtime.server

import com.agent.runtime.agent.RuntimeAgentEventUpdate
import com.agent.runtime.agent.RuntimeAgentExecutor
import com.agent.runtime.agent.RuntimeAgentResultUpdate
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 负责把 HTTP 请求翻译到现有 runtime 执行链。
 */
class DefaultRuntimeHttpService(
    private val runtimeAgentExecutor: RuntimeAgentExecutor = RuntimeAgentExecutor(),
    private val capabilitySetFactory: () -> CapabilitySet = { CapabilitySet(adapters = emptyList()) },
    private val defaultBindingResolver: () -> RuntimeHttpProviderResolution = { resolveDefaultHttpProviderBinding() },
) : RuntimeHttpService {

    /**
     * 使用显式传入的 provider binding 和 prompt 执行一次 runtime 调用。
     */
    override suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload> {
        val sessionId = request.sessionId ?: UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val bindingResolution = request.provider?.toDomainBinding()
            ?: defaultBindingResolver()
        val binding = bindingResolution.binding
            ?: return Result.fail(
                message = bindingResolution.failureMessage,
                data = RuntimeRunPayload(
                    sessionId = sessionId,
                    requestId = requestId,
                    events = listOf(
                        runtimeFailureEvent(
                            kind = "provider",
                            message = bindingResolution.failureMessage,
                        ),
                    ),
                ),
            )

        return when (
            val result = runtimeAgentExecutor.execute(
                session = RuntimeSession(id = sessionId),
                context = RuntimeRequestContext(sessionId = sessionId, requestId = requestId),
                request = RuntimeAgentRunRequest(prompt = request.prompt),
                binding = binding,
                capabilitySet = capabilitySetFactory(),
            )
        ) {
            is RuntimeSuccess -> Result.success(
                data = RuntimeRunPayload(
                    sessionId = sessionId,
                    requestId = requestId,
                    events = result.events.map { it.toPayload() },
                    output = result.output,
                ),
            )

            is RuntimeFailed -> Result.fail(
                message = result.failure.message,
                data = RuntimeRunPayload(
                    sessionId = sessionId,
                    requestId = requestId,
                    events = result.events.map { it.toPayload() } +
                        runtimeFailureEvent(
                            kind = result.failure.toFailureKind(),
                            message = result.failure.message,
                        ),
                ),
            )
        }
    }

    /**
     * 使用同一条 runtime 执行链生成可流式消费的 SSE 事件。
     */
    override fun stream(request: RuntimeRunHttpRequest): Flow<RuntimeSseEvent> = flow {
        val sessionId = request.sessionId ?: UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val bindingResolution = request.provider?.toDomainBinding()
            ?: defaultBindingResolver()
        val binding = bindingResolution.binding
        if (binding == null) {
            emit(
                RuntimeSseEvent(
                    event = "run.failed",
                    sessionId = sessionId,
                    requestId = requestId,
                    failureKind = "provider",
                    message = bindingResolution.failureMessage,
                ),
            )
            return@flow
        }

        emit(
            RuntimeSseEvent(
                event = "status",
                sessionId = sessionId,
                requestId = requestId,
                message = "run.started",
            ),
        )
        emit(binding.toMetadataSseEvent(sessionId = sessionId, requestId = requestId))

        runtimeAgentExecutor.stream(
            session = RuntimeSession(id = sessionId),
            context = RuntimeRequestContext(sessionId = sessionId, requestId = requestId),
            request = RuntimeAgentRunRequest(prompt = request.prompt),
            binding = binding,
            capabilitySet = capabilitySetFactory(),
        ).collect { update ->
            when (update) {
                is RuntimeAgentEventUpdate -> {
                    val event = update.event.toSseOrNull(sessionId = sessionId, requestId = requestId)
                    if (event != null) {
                        emit(event)
                    }
                }

                is RuntimeAgentResultUpdate -> when (val result = update.result) {
                    is RuntimeSuccess -> {
                        emit(
                            RuntimeSseEvent(
                                event = "run.completed",
                                sessionId = sessionId,
                                requestId = requestId,
                                output = result.output,
                            ),
                        )
                    }

                    is RuntimeFailed -> {
                        emit(
                            RuntimeSseEvent(
                                event = "run.failed",
                                sessionId = sessionId,
                                requestId = requestId,
                                failureKind = result.failure.toFailureKind(),
                                message = result.failure.message,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * 把 HTTP 请求体里的 provider binding 转回领域模型。
     */
    private fun ProviderBindingHttpRequest.toDomainBinding(): RuntimeHttpProviderResolution {
        val providerType = runCatching { ProviderType.valueOf(providerType) }.getOrNull()
            ?: return RuntimeHttpProviderResolution(
                binding = null,
                failureMessage = "Unsupported provider type '${this.providerType}'.",
            )
        return RuntimeHttpProviderResolution(
            binding = ProviderBinding(
                providerId = providerId,
                providerType = providerType,
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = modelId,
            ),
            failureMessage = "",
        )
    }
}

/**
 * 把 runtime 失败对象压平成 HTTP 宿主侧的稳定错误类型。
 */
private fun RuntimeFailure.toFailureKind(): String = when (this) {
    is RuntimeProviderResolutionFailure -> "provider"
    is RuntimeCapabilityBridgeFailure -> "capability"
    is RuntimeAgentExecutionFailure -> "agent"
    else -> "runtime"
}

/**
 * 把 runtime 普通事件转换为 HTTP 载荷事件。
 */
private fun RuntimeEvent.toPayload(): RuntimeEventPayload = RuntimeEventPayload(
    message = message,
    payload = payload,
)

/**
 * 把 runtime 普通事件转换为最小 SSE 事件；仅转发真正面向 UI 的 channel/delta 事件。
 */
private fun RuntimeEvent.toSseOrNull(
    sessionId: String,
    requestId: String,
): RuntimeSseEvent? = when (channel) {
    "thinking" -> RuntimeSseEvent(
        event = "thinking.delta",
        sessionId = sessionId,
        requestId = requestId,
        channel = channel,
        message = message,
        delta = delta ?: payload.toDeltaText(),
    )

    "text" -> RuntimeSseEvent(
        event = "text.delta",
        sessionId = sessionId,
        requestId = requestId,
        channel = channel,
        message = message,
        delta = delta ?: payload.toDeltaText(),
    )

    "tool" -> RuntimeSseEvent(
        event = "tool.delta",
        sessionId = sessionId,
        requestId = requestId,
        channel = channel,
        message = message,
        delta = delta ?: payload.toDeltaText(),
    )

    "status" -> RuntimeSseEvent(
        event = "status.delta",
        sessionId = sessionId,
        requestId = requestId,
        channel = channel,
        message = message,
        delta = delta ?: payload.toDeltaText(),
    )

    else -> null
}

/**
 * 仅把文本类 JsonElement 压平为 SSE delta，其他结构化载荷保持为空。
 */
private fun JsonElement?.toDeltaText(): String? = this?.jsonPrimitive?.contentOrNull

/**
 * 构造统一的 runtime 失败事件载荷。
 */
private fun runtimeFailureEvent(
    kind: String,
    message: String,
): RuntimeEventPayload = RuntimeEventPayload(
    message = "runtime.run.failed",
    failureKind = kind,
    failureMessage = message,
)

/**
 * 把当前 provider binding 压平成一条供 CLI 展示输入框元信息的 SSE 事件。
 */
private fun ProviderBinding.toMetadataSseEvent(
    sessionId: String,
    requestId: String,
): RuntimeSseEvent = RuntimeSseEvent(
    event = "run.metadata",
    sessionId = sessionId,
    requestId = requestId,
    providerLabel = providerId,
    modelLabel = modelId,
    reasoningEffort = reasoningEffortLabelOrNull(),
)

/**
 * 推导当前 provider 在 CLI 中可展示的思考等级；只有明确分级时才返回文本。
 */
private fun ProviderBinding.reasoningEffortLabelOrNull(): String? {
    if (!enableThinking) {
        return null
    }

    return when (providerType) {
        ProviderType.OPENAI_RESPONSES -> "medium"
        else -> null
    }
}
