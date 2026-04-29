package com.agent.runtime.cli

import com.agent.runtime.agent.RuntimeAgentEventUpdate
import com.agent.runtime.agent.RuntimeAgentExecutor
import com.agent.runtime.agent.RuntimeAgentResultUpdate
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
import com.agent.runtime.utils.readDotEnv
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 负责把 CLI 的最小 `stdio` 请求翻译成 runtime 事件流。
 */
class DefaultRuntimeCliService(
    private val runtimeAgentExecutor: RuntimeAgentExecutor = RuntimeAgentExecutor(),
    private val capabilitySetFactory: () -> CapabilitySet = { CapabilitySet(adapters = emptyList()) },
    private val defaultBindingResolver: () -> CliProviderResolution = { resolveDefaultBinding() },
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
                mode = AGENT_MODE,
            ),
        )

        val bindingResolution = if (request.provider != null) {
            request.provider.toDomainBinding()
        } else {
            defaultBindingResolver()
        }
        val binding = bindingResolution.binding

        if (binding == null) {
            emit(
                RuntimeCliFailureMessage(
                    sessionId = sessionId,
                    requestId = requestId,
                    kind = "provider",
                    message = bindingResolution.failureMessage,
                    details = bindingResolution.details,
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

        runtimeAgentExecutor.stream(
            session = RuntimeSession(id = sessionId),
            context = RuntimeRequestContext(sessionId = sessionId, requestId = requestId),
            request = RuntimeAgentRunRequest(prompt = request.prompt),
            binding = binding,
            capabilitySet = capabilitySetFactory(),
        ).collect { update ->
            when (update) {
                is RuntimeAgentEventUpdate -> emit(
                    RuntimeCliEventMessage(
                        sessionId = sessionId,
                        requestId = requestId,
                        event = update.event.toPayload(),
                    ),
                )

                is RuntimeAgentResultUpdate -> when (val result = update.result) {
                    is RuntimeSuccess -> emit(
                        RuntimeCliResultMessage(
                            sessionId = sessionId,
                            requestId = requestId,
                            output = result.output,
                            mode = AGENT_MODE,
                        ),
                    )

                    is RuntimeFailed -> emit(
                        RuntimeCliFailureMessage(
                            sessionId = sessionId,
                            requestId = requestId,
                            kind = result.failure.toFailureKind(),
                            message = result.failure.message,
                            details = binding.toFailureDetails(source = bindingResolution.details.source),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 把 CLI 协议中的 provider 载荷翻译为现有 runtime 领域 binding。
     */
    private fun RuntimeCliProviderBinding.toDomainBinding(): CliProviderResolution {
        val providerType = runCatching { ProviderType.valueOf(providerType) }.getOrNull()
        val details = RuntimeCliFailureDetails(
            source = REQUEST_SOURCE,
            providerId = providerId,
            providerType = this.providerType,
            baseUrl = baseUrl,
            modelId = modelId,
            apiKeyPresent = apiKey.isNotBlank(),
        )
        if (providerType == null) {
            return CliProviderResolution(
                binding = null,
                details = details,
                failureMessage = "Invalid CLI provider type: ${this.providerType}",
            )
        }

        return CliProviderResolution(
            binding = ProviderBinding(
                providerId = providerId,
                providerType = providerType,
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = modelId,
            ),
            details = details,
        )
    }

    /**
     * 把 runtime 事件翻译为 CLI 可消费的稳定事件载荷。
     */
    private fun RuntimeEvent.toPayload(): RuntimeCliEventPayload = RuntimeCliEventPayload(
        message = message,
        channel = channel,
        delta = delta,
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

    private companion object {
        private const val AGENT_MODE = "agent"
        private const val REQUEST_SOURCE = "cli-request"
    }
}

/**
 * 从 runtime 进程自己的配置源解析默认 provider binding。
 */
private fun resolveDefaultBinding(): CliProviderResolution {
    val values = readDotEnv() + System.getenv()
    val providerId = values[PROVIDER_ID_ENV]?.trim().orEmpty()
    val providerTypeText = values[PROVIDER_TYPE_ENV]?.trim().orEmpty()
    val baseUrl = values[PROVIDER_BASE_URL_ENV]?.trim().orEmpty()
    val apiKey = values[PROVIDER_API_KEY_ENV]?.trim().orEmpty()
    val modelId = values[PROVIDER_MODEL_ID_ENV]?.trim().orEmpty()
    val details = RuntimeCliFailureDetails(
        source = DEFAULT_SOURCE,
        providerId = providerId.ifBlank { null },
        providerType = providerTypeText.ifBlank { null },
        baseUrl = baseUrl.ifBlank { null },
        modelId = modelId.ifBlank { null },
        apiKeyPresent = apiKey.isNotBlank(),
    )
    val missingKeys = buildList {
        if (providerId.isBlank()) add(PROVIDER_ID_ENV)
        if (providerTypeText.isBlank()) add(PROVIDER_TYPE_ENV)
        if (baseUrl.isBlank()) add(PROVIDER_BASE_URL_ENV)
        if (apiKey.isBlank()) add(PROVIDER_API_KEY_ENV)
        if (modelId.isBlank()) add(PROVIDER_MODEL_ID_ENV)
    }

    if (missingKeys.isNotEmpty()) {
        return CliProviderResolution(
            binding = null,
            details = details,
            failureMessage = "Missing runtime provider configuration for CLI request. Missing keys: ${missingKeys.joinToString()}",
        )
    }

    val providerType = runCatching { ProviderType.valueOf(providerTypeText) }.getOrNull()
    if (providerType == null) {
        return CliProviderResolution(
            binding = null,
            details = details,
            failureMessage = "Invalid runtime provider type for CLI request: $providerTypeText",
        )
    }

    return CliProviderResolution(
        binding = ProviderBinding(
            providerId = providerId,
            providerType = providerType,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            enableThinking = values[PROVIDER_ENABLE_THINKING_ENV]?.trim()?.lowercase() == "true",
        ),
        details = details,
    )
}

/**
 * 表示一次 CLI provider 解析后的结构化结果。
 */
data class CliProviderResolution(
    val binding: ProviderBinding?,
    val details: RuntimeCliFailureDetails,
    val failureMessage: String = "Missing runtime provider configuration for CLI request.",
)

/**
 * 把领域层 binding 转成适合协议层显示的安全诊断摘要。
 */
private fun ProviderBinding.toFailureDetails(source: String?): RuntimeCliFailureDetails = RuntimeCliFailureDetails(
    source = source,
    providerId = providerId,
    providerType = providerType.name,
    baseUrl = baseUrl,
    modelId = modelId,
    apiKeyPresent = apiKey.isNotBlank(),
)

private const val PROVIDER_ID_ENV = "MULEHANG_PROVIDER_ID"
private const val PROVIDER_TYPE_ENV = "MULEHANG_PROVIDER_TYPE"
private const val PROVIDER_BASE_URL_ENV = "MULEHANG_PROVIDER_BASE_URL"
private const val PROVIDER_API_KEY_ENV = "MULEHANG_PROVIDER_API_KEY"
private const val PROVIDER_MODEL_ID_ENV = "MULEHANG_PROVIDER_MODEL_ID"
private const val PROVIDER_ENABLE_THINKING_ENV = "MULEHANG_PROVIDER_ENABLE_THINKING"
private const val DEFAULT_SOURCE = "runtime-default"
