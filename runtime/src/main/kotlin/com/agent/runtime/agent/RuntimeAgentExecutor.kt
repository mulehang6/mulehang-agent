package com.agent.runtime.agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.additionalPropertiesOf
import ai.koog.prompt.streaming.StreamFrame
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.core.RuntimeAgentExecutionFailure
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeCapabilityBridgeFailure
import com.agent.runtime.core.RuntimeFailed
import com.agent.runtime.core.RuntimeEvent
import com.agent.runtime.core.RuntimeInfoEvent
import com.agent.runtime.core.RuntimeProviderResolutionFailure
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeResult
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive

/**
 * 表示 agent 执行过程中可被 runtime CLI 逐条消费的更新。
 */
sealed interface RuntimeAgentExecutionUpdate

/**
 * 表示 agent streaming 中产生的一条 runtime event。
 */
data class RuntimeAgentEventUpdate(
    val event: RuntimeEvent,
) : RuntimeAgentExecutionUpdate

/**
 * 表示 agent 执行完成后的最终 runtime result。
 */
data class RuntimeAgentResultUpdate(
    val result: RuntimeResult,
) : RuntimeAgentExecutionUpdate

/**
 * 负责把 runtime 的 agent.run 请求翻译到真实 Koog agent 或 Koog streaming 执行。
 */
class RuntimeAgentExecutor(
    private val assembleAgent: suspend (ProviderBinding, CapabilitySet) -> AssembledAgent = { binding, capabilitySet ->
        AgentAssembly().assemble(binding, capabilitySet)
    },
    private val runner: (suspend (AssembledAgent, String) -> String)? = null,
    private val streamRunner: ((AssembledAgent, String) -> Flow<StreamFrame>)? = null,
    private val compatibilityStreamRunner: CompatibilityStreamRunner = DeepSeekThinkingCompatibilityStreamRunner(),
) {

    private val koogStreamRunner: (AssembledAgent, String) -> Flow<StreamFrame> = { assembled, userPrompt ->
        assembled.promptExecutor.executeStreaming(
            prompt = buildRuntimePrompt(userPrompt, assembled.binding),
            model = assembled.llmModel,
            tools = assembled.toolRegistry.tools.map { it.descriptor },
        )
    }

    private val fallbackNonStreamingRunner: suspend (AssembledAgent, String) -> String = { assembled, prompt ->
        assembled.agent.run(prompt)
    }

    /**
     * 执行一次 runtime agent 请求，并把 streaming 更新聚合为统一 runtime result。
     */
    suspend fun execute(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: RuntimeAgentRunRequest,
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): RuntimeResult {
        val events = mutableListOf<RuntimeEvent>()
        var finalResult: RuntimeResult? = null

        stream(session, context, request, binding, capabilitySet).collect { update ->
            when (update) {
                is RuntimeAgentEventUpdate -> events.add(update.event)
                is RuntimeAgentResultUpdate -> finalResult = update.result
            }
        }

        return when (val result = finalResult) {
            is RuntimeSuccess -> result.copy(events = events)
            is RuntimeFailed -> result.copy(events = events)
            null -> RuntimeFailed(
                failure = RuntimeAgentExecutionFailure(message = "agent execution produced no result"),
                events = events,
            )
        }
    }

    /**
     * 执行一次 runtime agent 请求，并在 Koog streaming frame 到达时立即产出 runtime event。
     */
    fun stream(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: RuntimeAgentRunRequest,
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): Flow<RuntimeAgentExecutionUpdate> = flow {
        try {
            val assembledAgent = assembleAgent(binding, capabilitySet)
            if (runner != null && streamRunner == null) {
                val result = executeLegacyRunner(
                    session = session,
                    context = context,
                    prompt = request.prompt,
                    assembledAgent = assembledAgent,
                    runner = runner,
                )
                when (result) {
                    is RuntimeSuccess -> result.events.forEach { event -> emit(RuntimeAgentEventUpdate(event)) }
                    is RuntimeFailed -> result.events.forEach { event -> emit(RuntimeAgentEventUpdate(event)) }
                }
                emit(RuntimeAgentResultUpdate(result))
                return@flow
            }

            val textOutput = StringBuilder()
            var sawTextDelta = false

            emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.started", payload = JsonPrimitive(context.requestId))))
            try {
                val selectedStreamRunner = when {
                    streamRunner != null -> streamRunner
                    compatibilityStreamRunner.supports(
                        binding = assembledAgent.binding,
                        hasTools = assembledAgent.toolRegistry.tools.isNotEmpty(),
                    ) -> { _, prompt -> compatibilityStreamRunner.stream(assembledAgent.binding, prompt) }
                    else -> koogStreamRunner
                }
                selectedStreamRunner(assembledAgent, request.prompt).collect { frame ->
                    when (frame) {
                        is StreamFrame.TextDelta -> {
                            sawTextDelta = true
                            textOutput.append(frame.text)
                            emitTextDelta(frame.text)
                        }

                        is StreamFrame.TextComplete -> {
                            if (!sawTextDelta) {
                                textOutput.append(frame.text)
                                emitTextDelta(frame.text)
                            }
                        }

                        is StreamFrame.ReasoningDelta -> {
                            val delta = frame.text ?: frame.summary
                            if (delta != null) {
                                emitReasoningDelta(delta)
                            }
                        }

                        is StreamFrame.ToolCallDelta -> {
                            val delta = frame.content ?: frame.name ?: frame.id
                            if (delta != null) {
                                emitToolDelta("agent.tool.delta", delta)
                            }
                        }

                        is StreamFrame.ToolCallComplete -> emitToolDelta("agent.tool.completed", frame.name)

                        is StreamFrame.End,
                        is StreamFrame.ReasoningComplete,
                        -> Unit
                    }
                }
            } catch (error: Throwable) {
                if (!error.looksLikeUnsupportedStreamingReasoning()) {
                    throw error
                }
                val fallbackOutput = (runner ?: fallbackNonStreamingRunner)(assembledAgent, request.prompt)
                textOutput.clear()
                textOutput.append(fallbackOutput)
                emitTextDelta(fallbackOutput)
            }
            emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.completed", payload = JsonPrimitive(session.id))))
            emit(RuntimeAgentResultUpdate(RuntimeSuccess(output = JsonPrimitive(textOutput.toString()))))
        } catch (error: IllegalArgumentException) {
            emit(
                RuntimeAgentResultUpdate(
                    RuntimeFailed(
                        failure = RuntimeProviderResolutionFailure(
                            message = error.message ?: "provider resolution failed",
                            cause = error,
                        ),
                    ),
                ),
            )
        } catch (error: IllegalStateException) {
            emit(
                RuntimeAgentResultUpdate(
                    RuntimeFailed(
                        failure = RuntimeCapabilityBridgeFailure(
                            message = error.message ?: "capability bridge failed",
                            cause = error,
                        ),
                    ),
                ),
            )
        } catch (error: Throwable) {
            emit(
                RuntimeAgentResultUpdate(
                    RuntimeFailed(
                        failure = RuntimeAgentExecutionFailure(
                            message = binding.agentExecutionFailureMessage(error),
                            cause = error,
                        ),
                    ),
                ),
            )
        }
    }

    /**
     * 保留单元测试和非 streaming runner 的旧执行路径。
     */
    private suspend fun executeLegacyRunner(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        prompt: String,
        assembledAgent: AssembledAgent,
        runner: suspend (AssembledAgent, String) -> String,
    ): RuntimeResult {
        val output = runner(assembledAgent, prompt)
        return RuntimeSuccess(
            events = listOf(
                RuntimeInfoEvent(message = "agent.run.started", payload = JsonPrimitive(context.requestId)),
                RuntimeInfoEvent(message = "agent.run.completed", payload = JsonPrimitive(session.id)),
            ),
            output = JsonPrimitive(output),
        )
    }

    /**
     * 发出普通回答文本增量。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<RuntimeAgentExecutionUpdate>.emitTextDelta(delta: String) {
        emit(
            RuntimeAgentEventUpdate(
                RuntimeInfoEvent(
                    message = "agent.text.delta",
                    channel = "text",
                    delta = delta,
                ),
            ),
        )
    }

    /**
     * 发出模型 reasoning/thinking 增量。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<RuntimeAgentExecutionUpdate>.emitReasoningDelta(delta: String) {
        emit(
            RuntimeAgentEventUpdate(
                RuntimeInfoEvent(
                    message = "agent.reasoning.delta",
                    channel = "thinking",
                    delta = delta,
                ),
            ),
        )
    }

    /**
     * 发出工具调用相关增量。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<RuntimeAgentExecutionUpdate>.emitToolDelta(
        message: String,
        delta: String,
    ) {
        emit(
            RuntimeAgentEventUpdate(
                RuntimeInfoEvent(
                    message = message,
                    channel = "tool",
                    delta = delta,
                ),
            ),
        )
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
    private fun ProviderBinding.usesOpenAIResponsesEndpoint(): Boolean = providerType == ProviderType.OPENAI_RESPONSES

    /**
     * 保守识别 Responses endpoint 不存在或不支持的常见错误文本。
     */
    private fun String.looksLikeUnsupportedResponsesEndpoint(): Boolean {
        val normalized = lowercase()
        return "responses" in normalized &&
            ("404" in normalized || "not found" in normalized || "unsupported" in normalized)
    }

    /**
     * 识别 Koog 0.8.0 Responses streaming 对 reasoning_text subtype 的已知反序列化缺口。
     */
    private fun Throwable.looksLikeUnsupportedStreamingReasoning(): Boolean {
        val message = message ?: return false
        return "reasoning_text" in message && "OutputContent" in message
    }
}

/**
 * 构造 runtime 的单轮 prompt；当 provider 显式开启 thinking 时，同时附带 provider 级参数。
 */
internal fun buildRuntimePrompt(
    userPrompt: String,
    binding: ProviderBinding,
): Prompt {
    val basePrompt = prompt("runtime-agent") {
        system(DEFAULT_AGENT_SYSTEM_PROMPT)
        user(userPrompt)
    }

    val params = binding.toThinkingParams() ?: return basePrompt
    return basePrompt.withParams(params)
}

/**
 * 把仓库侧的 thinking 开关翻译为各 provider 真正理解的请求参数。
 */
private fun ProviderBinding.toThinkingParams(): LLMParams? {
    if (!enableThinking) {
        return null
    }

    return when (providerType) {
        ProviderType.OPENAI_COMPATIBLE -> OpenAIChatParams(
            additionalProperties = if (usesDeepSeekThinkingMode()) {
                additionalPropertiesOf("thinking" to mapOf("type" to "enabled"))
            } else {
                null
            },
        )

        ProviderType.OPENAI_RESPONSES -> OpenAIChatParams(
            reasoningEffort = ReasoningEffort.MEDIUM,
        )

        ProviderType.ANTHROPIC -> AnthropicParams(
            thinking = AnthropicThinking.Enabled(budgetTokens = 1_024),
        )

        ProviderType.GEMINI -> GoogleParams(
            thinkingConfig = GoogleThinkingConfig(includeThoughts = true),
        )
    }
}

/**
 * DeepSeek 的 OpenAI-compatible thinking mode 需要通过 `thinking={type=enabled}` 透传到请求体。
 */
internal fun ProviderBinding.usesDeepSeekThinkingMode(): Boolean {
    val normalizedBaseUrl = baseUrl.lowercase()
    val normalizedProviderId = providerId.lowercase()
    val normalizedModelId = modelId.lowercase()
    return "deepseek" in normalizedBaseUrl ||
        "deepseek" in normalizedProviderId ||
        normalizedModelId.startsWith("deepseek")
}

/**
 * 统一复用当前 runtime agent 的系统提示，避免兼容层和 Koog 主链路出现语义漂移。
 */
internal const val DEFAULT_AGENT_SYSTEM_PROMPT = "You are a helpful assistant."
