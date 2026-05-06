package com.agent.runtime.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

internal class RecordingPromptExecutor(
    private val responseForPrompt: (Prompt) -> String,
) : PromptExecutor() {
    val capturedPrompts = mutableListOf<Prompt>()

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
    ): List<Message.Response> {
        capturedPrompts += prompt
        return listOf(
            Message.Assistant(
                content = responseForPrompt(prompt),
                metaInfo = ResponseMetaInfo.create(Clock.System),
            ),
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
    ): Flow<StreamFrame> = flow {
        capturedPrompts += prompt
        emit(StreamFrame.TextDelta(responseForPrompt(prompt)))
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = ModerationResult(
        isHarmful = false,
        categories = emptyMap(),
    )

    override fun close() = Unit
}

internal data class RecordingAssembledAgent(
    val assembledAgent: AssembledAgent,
    val promptExecutor: RecordingPromptExecutor,
)

internal fun buildRecordingAssembledAgent(
    responseForPrompt: (Prompt) -> String,
): RecordingAssembledAgent {
    val promptExecutor = RecordingPromptExecutor(responseForPrompt)
    val llmModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = "fake-openai-model",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
    )
    val strategy = AgentStrategyFactory.singleRun()
    val toolRegistry = ToolRegistry { }
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = llmModel,
        strategy = strategy,
        systemPrompt = DEFAULT_AGENT_SYSTEM_PROMPT,
        toolRegistry = toolRegistry,
    )
    return RecordingAssembledAgent(
        assembledAgent = AssembledAgent(
            binding = testProviderBinding(),
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            strategy = strategy,
            agent = agent,
            toolRegistry = toolRegistry,
            capabilities = emptyList(),
        ),
        promptExecutor = promptExecutor,
    )
}

internal fun testProviderBinding(): ProviderBinding = ProviderBinding(
    providerId = "provider-openai",
    providerType = ProviderType.OPENAI_COMPATIBLE,
    baseUrl = "https://openrouter.ai/api/v1",
    apiKey = "test-key",
    modelId = "openai/gpt-oss-120b:free",
)
