package com.agent.runtime.agent

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
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
import ai.koog.prompt.streaming.emitEnd
import ai.koog.prompt.streaming.emitTextDelta
import ai.koog.prompt.streaming.emitToolCallComplete
import com.agent.runtime.capability.CapabilityDescriptor
import com.agent.runtime.capability.CapabilitySet
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
        emitEnd()
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
    conversationMemory: RuntimeConversationMemory = RuntimeConversationMemory(),
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
        systemPrompt = buildAgentSystemPrompt(CapabilitySet(adapters = emptyList())),
        toolRegistry = toolRegistry,
    ) {
        install(ChatMemory.Feature) {
            conversationMemory.applyTo(this)
        }
    }
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

internal fun buildRecordingAssembledAgent(
    capabilities: List<CapabilityDescriptor>,
    conversationMemory: RuntimeConversationMemory = RuntimeConversationMemory(),
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
        systemPrompt = buildAgentSystemPrompt(CapabilitySet(adapters = emptyList())),
        toolRegistry = toolRegistry,
    ) {
        install(ChatMemory.Feature) {
            conversationMemory.applyTo(this)
        }
    }
    return RecordingAssembledAgent(
        assembledAgent = AssembledAgent(
            binding = testProviderBinding(),
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            strategy = strategy,
            agent = agent,
            toolRegistry = toolRegistry,
            capabilities = capabilities,
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

internal class ScriptedToolLoopPromptExecutor(
    private val toolName: String,
    private val toolArgsJson: String,
    private val finalText: String,
) : PromptExecutor() {
    val capturedPrompts = mutableListOf<Prompt>()

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
    ): List<Message.Response> {
        capturedPrompts += prompt
        val sawToolResult = prompt.messages.any { message ->
            message is Message.Tool.Result && message.tool == toolName
        }
        if (!sawToolResult) {
            error("This fixture expects tool results before execute() is called.")
        }
        return listOf(
            Message.Assistant(
                content = finalText,
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
        val sawToolResult = prompt.messages.any { message ->
            message is Message.Tool.Result && message.tool == toolName
        }
        if (!sawToolResult) {
            emitToolCallComplete(
                id = "tool-call-1",
                name = toolName,
                content = toolArgsJson,
            )
            emitEnd()
            return@flow
        }

        emitTextDelta(finalText)
        emitEnd()
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = ModerationResult(
        isHarmful = false,
        categories = emptyMap(),
    )

    override fun close() = Unit
}

internal fun streamingToolLoopStrategy(): AIAgentGraphStrategy<String, String> = strategy("streaming_tool_loop") {
    val streamLlm by nodeLLMRequestStreamingAndSendResults<String>()
    val executeTools by nodeExecuteMultipleTools(parallelTools = false)
    val sendToolResults by nodeLLMSendMultipleToolResults()

    edge(nodeStart forwardTo streamLlm)
    edge(streamLlm forwardTo executeTools onMultipleToolCalls { true })
    edge(
        streamLlm forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { responses -> responses.joinToString("\n") { response -> response.content } }
    )
    edge(executeTools forwardTo sendToolResults)
    edge(sendToolResults forwardTo executeTools onMultipleToolCalls { true })
    edge(
        sendToolResults forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { responses -> responses.joinToString("\n") { response -> response.content } }
    )
}
