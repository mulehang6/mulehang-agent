package com.agent.server

import com.agent.agent.RuntimeAgentExecutor
import com.agent.capability.CapabilitySet
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderType
import com.agent.runtime.RuntimeAgentExecutionFailure
import com.agent.runtime.RuntimeAgentRunRequest
import com.agent.runtime.RuntimeCapabilityBridgeFailure
import com.agent.runtime.RuntimeFailed
import com.agent.runtime.RuntimeFailure
import com.agent.runtime.RuntimeProviderResolutionFailure
import com.agent.runtime.RuntimeRequestContext
import com.agent.runtime.RuntimeSession
import com.agent.runtime.RuntimeSuccess
import java.util.UUID

/**
 * 负责把 HTTP 请求翻译到现有 runtime 执行链。
 */
class DefaultRuntimeHttpService(
    private val runtimeAgentExecutor: RuntimeAgentExecutor = RuntimeAgentExecutor(),
    private val capabilitySetFactory: () -> CapabilitySet = { CapabilitySet(adapters = emptyList()) },
) : RuntimeHttpService {

    /**
     * 使用显式传入的 provider binding 和 prompt 执行一次 runtime 调用。
     */
    override suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload> {
        val sessionId = request.sessionId ?: UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val binding = request.provider.toDomainBinding()
            ?: return Result.fail(
                message = "Unsupported provider type '${request.provider.providerType}'.",
                data = RuntimeRunPayload(
                    sessionId = sessionId,
                    requestId = requestId,
                    events = listOf(
                        runtimeFailureEvent(
                            kind = "provider",
                            message = "Unsupported provider type '${request.provider.providerType}'.",
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
     * 把 HTTP 请求体里的 provider binding 转回领域模型。
     */
    private fun ProviderBindingHttpRequest.toDomainBinding(): ProviderBinding? {
        val providerType = runCatching { ProviderType.valueOf(providerType) }.getOrNull() ?: return null
        return ProviderBinding(
            providerId = providerId,
            providerType = providerType,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
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
private fun com.agent.runtime.RuntimeEvent.toPayload(): RuntimeEventPayload = RuntimeEventPayload(
    message = message,
    payload = payload,
)

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
