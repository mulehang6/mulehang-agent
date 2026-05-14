@file:Suppress("UnstableApiUsage")

package com.agent.runtime.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.typeToken
import com.agent.runtime.capability.BuiltInFileToolCapability
import com.agent.runtime.capability.CapabilityDescriptor
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.capability.ToolCapabilityAdapter
import com.agent.runtime.capability.ToolRiskLevel
import com.agent.runtime.core.RuntimeAgentExecutionFailure
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeCapabilityBridgeFailure
import com.agent.runtime.core.RuntimeFailed
import com.agent.runtime.core.RuntimeProviderResolutionFailure
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证 runtime 到 agent.run 的结果翻译与错误分层。
 */
class RuntimeAgentExecutorTest {

    @Test
    fun `should translate successful agent run into runtime success`() = runTest {
        val executor = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            runner = { _, prompt -> "echo:$prompt" },
        )

        val result = executor.execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("echo:hello"), result.output)
    }

    @Test
    fun `should translate provider capability and agent failures separately`() = runTest {
        val request = RuntimeAgentRunRequest(prompt = "hello")
        val session = RuntimeSession(id = "session-1")
        val context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1")
        val capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo")))

        val providerFailure = RuntimeAgentExecutor(
            assembleAgent = { _, _ -> throw IllegalArgumentException("provider failed") },
            runner = { _, _ -> "unused" },
        ).execute(session, context, request, openAiBinding(), capabilitySet)
        val capabilityFailure = RuntimeAgentExecutor(
            assembleAgent = { _, _ -> throw IllegalStateException("capability failed") },
            runner = { _, _ -> "unused" },
        ).execute(session, context, request, openAiBinding(), capabilitySet)
        val agentFailure = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            runner = { _, _ -> throw RuntimeException("agent failed") },
        ).execute(session, context, request, openAiBinding(), capabilitySet)

        assertIs<RuntimeFailed>(providerFailure)
        assertIs<RuntimeProviderResolutionFailure>(providerFailure.failure)

        assertIs<RuntimeFailed>(capabilityFailure)
        assertIs<RuntimeCapabilityBridgeFailure>(capabilityFailure.failure)

        assertIs<RuntimeFailed>(agentFailure)
        assertIs<RuntimeAgentExecutionFailure>(agentFailure.failure)
    }

    @Test
    fun `should suggest chat completions when default responses endpoint execution is unsupported`() = runTest {
        val result = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            runner = { _, _ -> throw RuntimeException("404 from /v1/responses") },
        ).execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(providerType = ProviderType.OPENAI_RESPONSES),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeFailed>(result)
        val failure = assertIs<RuntimeAgentExecutionFailure>(result.failure)
        assertContains(failure.message, "Responses")
        assertContains(failure.message, "chat/completions")
    }

    @Test
    fun `should translate streaming text and reasoning frames into runtime events`() = runTest {
        val result = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            streamRunner = { _, _ ->
                flowOf(
                    StreamFrame.ReasoningDelta(text = "thinking "),
                    StreamFrame.TextDelta("hello "),
                    StreamFrame.TextDelta("world"),
                )
            },
        ).execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("hello world"), result.output)
        assertEquals(
            listOf("status", "thinking", "text", "text", "status"),
            result.events.map { it.channel ?: "status" },
        )
        assertEquals("thinking ", result.events[1].delta)
        assertEquals("hello ", result.events[2].delta)
        assertEquals("world", result.events[3].delta)
    }

    @Test
    fun `should not emit duplicate text when streaming completes after deltas`() = runTest {
        val result = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            streamRunner = { _, _ ->
                flowOf(
                    StreamFrame.TextDelta("你好"),
                    StreamFrame.TextComplete("你好"),
                    StreamFrame.End(finishReason = null, metaInfo = ResponseMetaInfo.Empty),
                )
            },
        ).execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "你好"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("你好"), result.output)
        val textEvents = result.events.filter { it.channel == "text" }
        assertEquals(listOf("你好"), textEvents.mapNotNull { it.delta })
    }

    @Test
    fun `should inject available tool catalog into runtime system prompt`() = runTest {
        val root = "D:\\JetBrains\\projects\\idea_projects\\mulehang-agent"
        val systemMessage = buildAgentSystemPrompt(
            CapabilitySet(
                adapters = emptyList(),
                builtInFileTools = listOf(
                    BuiltInFileToolCapability.readFile(root),
                    BuiltInFileToolCapability.writeFile(root),
                ),
            ),
        )

        assertContains(systemMessage, "__read_file__")
        assertContains(systemMessage, "__write_file__")
        assertContains(systemMessage, root)
        assertContains(systemMessage, "Reply in the same language as the user's latest message")
        assertContains(systemMessage, "Default to answering directly without tools")
        assertContains(systemMessage, "Do not call workspace tools for greetings, small talk")
        assertContains(systemMessage, "when the user explicitly asks not to inspect the project")
        assertContains(systemMessage, "Only inspect the workspace when the answer depends on repository facts")
        assertContains(systemMessage, "If the user asks what tools you have")
    }

    @Test
    fun `should apply thinking params when binding defaults wrap an empty prompt`() = runTest {
        val promptExecutor = RecordingPromptExecutor { "project summary" }
        val executor = BindingDefaultsPromptExecutor(
            delegate = promptExecutor,
            defaultParams = openAiBinding(
                providerType = ProviderType.OPENAI_RESPONSES,
                enableThinking = true,
            ).toThinkingParams() ?: error("expected thinking params"),
        )
        val llmModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "fake-openai-model",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
        )

        executor.execute(
            prompt = buildRuntimePrompt(
                userPrompt = "这个项目是做什么的",
                binding = openAiBinding(),
            ),
            model = llmModel,
        )

        val params = assertIs<OpenAIChatParams>(promptExecutor.capturedPrompts.single().params)
        assertEquals(ReasoningEffort.MEDIUM, params.reasoningEffort)
    }

    @Test
    fun `should preserve streamed reasoning before tool results are sent back to llm`() = runTest {
        val promptExecutor = ScriptedDeepSeekReasoningToolLoopPromptExecutor(
            toolName = "tool.echo",
            toolArgsJson = """{"payload":"hello"}""",
            finalText = "tool result: hello",
        )
        val llmModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "fake-openai-model",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
        )
        val toolRegistry = ai.koog.agents.core.tools.ToolRegistry {
            tool(RuntimeExecutorEchoTool())
        }
        val assembledAgent = AssembledAgent(
            binding = openAiBinding(
                baseUrl = "https://api.deepseek.com",
                modelId = "deepseek-chat",
                enableThinking = true,
            ),
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            strategy = AgentStrategyFactory.singleRun(),
            agent = ai.koog.agents.core.agent.AIAgent(
                promptExecutor = promptExecutor,
                llmModel = llmModel,
                strategy = AgentStrategyFactory.singleRun(),
                systemPrompt = DEFAULT_AGENT_SYSTEM_PROMPT,
                toolRegistry = toolRegistry,
            ),
            toolRegistry = toolRegistry,
            capabilities = listOf(CapabilityDescriptor(id = "tool.echo", kind = "tool", riskLevel = ToolRiskLevel.MID)),
        )

        val result = RuntimeAgentExecutor(
            assembleAgent = { _, _ -> assembledAgent },
            assembleStreamingAgent = { _, _, _ -> assembledAgent },
        ).execute(
            session = RuntimeSession(id = "session-deepseek-thinking"),
            context = RuntimeRequestContext(
                sessionId = "session-deepseek-thinking",
                requestId = "request-deepseek-thinking",
            ),
            request = RuntimeAgentRunRequest(prompt = "这个项目是做什么的"),
            binding = assembledAgent.binding,
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("tool result: hello"), result.output)
        val secondPromptMessages = promptExecutor.capturedPrompts.last().messages
        assertTrue(secondPromptMessages.any { it is Message.Reasoning })
    }

    @Test
    fun `should execute tool calls instead of stopping after tool call frames`() = runTest {
        val promptExecutor = ScriptedToolLoopPromptExecutor(
            toolName = "tool.echo",
            toolArgsJson = """{"payload":"hello"}""",
            finalText = "tool result: hello",
        )
        val llmModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "fake-openai-model",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
        )
        val toolRegistry = ai.koog.agents.core.tools.ToolRegistry {
            tool(RuntimeExecutorEchoTool())
        }
        val assembledAgent = AssembledAgent(
            binding = openAiBinding(),
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            strategy = AgentStrategyFactory.singleRun(),
            agent = ai.koog.agents.core.agent.AIAgent(
                promptExecutor = promptExecutor,
                llmModel = llmModel,
                strategy = AgentStrategyFactory.singleRun(),
                systemPrompt = DEFAULT_AGENT_SYSTEM_PROMPT,
                toolRegistry = toolRegistry,
            ),
            toolRegistry = toolRegistry,
            capabilities = listOf(CapabilityDescriptor(id = "tool.echo", kind = "tool", riskLevel = ToolRiskLevel.MID)),
        )

        val result = RuntimeAgentExecutor(
            assembleAgent = { _, _ -> assembledAgent },
            assembleStreamingAgent = { _, _, emitRuntimeEvent ->
                var sawStreamingTextDelta = false
                assembledAgent.copy(
                    agent = ai.koog.agents.core.agent.AIAgent(
                        promptExecutor = assembledAgent.promptExecutor,
                        llmModel = assembledAgent.llmModel,
                        strategy = assembledAgent.strategy,
                        systemPrompt = DEFAULT_AGENT_SYSTEM_PROMPT,
                        toolRegistry = assembledAgent.toolRegistry,
                    ) {
                        handleEvents {
                            onLLMStreamingFrameReceived { eventContext ->
                                when (val frame = eventContext.streamFrame) {
                                    is StreamFrame.TextDelta -> {
                                        sawStreamingTextDelta = true
                                        emitRuntimeEvent(
                                            com.agent.runtime.core.RuntimeInfoEvent(
                                                message = "agent.text.delta",
                                                channel = "text",
                                                delta = frame.text,
                                            ),
                                        )
                                    }

                                    is StreamFrame.TextComplete -> {
                                        if (!sawStreamingTextDelta) {
                                            emitRuntimeEvent(
                                                com.agent.runtime.core.RuntimeInfoEvent(
                                                    message = "agent.text.delta",
                                                    channel = "text",
                                                    delta = frame.text,
                                                ),
                                            )
                                        }
                                        sawStreamingTextDelta = false
                                    }

                                    is StreamFrame.ReasoningDelta -> {
                                        val delta = frame.text ?: frame.summary
                                        if (delta != null) {
                                            emitRuntimeEvent(
                                                com.agent.runtime.core.RuntimeInfoEvent(
                                                    message = "agent.reasoning.delta",
                                                    channel = "thinking",
                                                    delta = delta,
                                                ),
                                            )
                                        }
                                    }

                                    is StreamFrame.End -> sawStreamingTextDelta = false
                                    else -> Unit
                                }
                            }
                            onToolCallStarting { eventContext ->
                                emitRuntimeEvent(
                                    eventContext.toRuntimeToolEvent(
                                        status = "running",
                                        message = "agent.tool.started",
                                    ),
                                )
                            }
                            onToolCallCompleted { eventContext ->
                                emitRuntimeEvent(
                                    eventContext.toRuntimeToolEvent(
                                        status = "completed",
                                        message = "agent.tool.completed",
                                        output = eventContext.toolResult,
                                    ),
                                )
                            }
                            onToolCallFailed { eventContext ->
                                emitRuntimeEvent(
                                    eventContext.toRuntimeToolEvent(
                                        status = "error",
                                        message = "agent.tool.failed",
                                        error = eventContext.message,
                                    ),
                                )
                            }
                        }
                    },
                )
            },
        ).execute(
            session = RuntimeSession(id = "session-tool-loop"),
            context = RuntimeRequestContext(sessionId = "session-tool-loop", requestId = "request-tool-loop"),
            request = RuntimeAgentRunRequest(prompt = "say hello via tool"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("tool result: hello"), result.output)
        assertTrue(
            promptExecutor.capturedPrompts.first().messages.any {
                it.role == Message.Role.User && it.content == "say hello via tool"
            },
        )
        val toolEvents = result.events.filter { it.channel == "tool" }
        assertEquals(listOf("agent.tool.started", "agent.tool.completed"), toolEvents.map { it.message })
        val startedPayload = toolEvents.first().payload!!.jsonObject
        assertEquals("tool-call-1", startedPayload.getValue("toolCallId").jsonPrimitive.contentOrNull)
        assertEquals("tool.echo", startedPayload.getValue("toolName").jsonPrimitive.contentOrNull)
        assertEquals("running", startedPayload.getValue("status").jsonPrimitive.contentOrNull)
        assertEquals("hello", startedPayload.getValue("input").jsonObject.getValue("payload").jsonPrimitive.contentOrNull)
        val completedPayload = toolEvents.last().payload!!.jsonObject
        assertEquals("completed", completedPayload.getValue("status").jsonPrimitive.contentOrNull)
        assertEquals("hello", completedPayload.getValue("output").jsonPrimitive.contentOrNull)
        val textEvents = result.events.filter { it.channel == "text" }
        assertEquals(listOf("tool result: hello"), textEvents.mapNotNull { it.delta })
    }

    @Test
    fun `should append current user input on every tool-enabled turn`() = runTest {
        val promptExecutor = RecordingPromptExecutor { prompt ->
            prompt.messages.last { it.role == Message.Role.User }.content
        }
        val llmModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "fake-openai-model",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
        )
        val toolRegistry = ai.koog.agents.core.tools.ToolRegistry {
            tool(RuntimeExecutorEchoTool())
        }
        val assembledAgent = AssembledAgent(
            binding = openAiBinding(),
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            strategy = AgentStrategyFactory.singleRun(),
            agent = ai.koog.agents.core.agent.AIAgent(
                promptExecutor = promptExecutor,
                llmModel = llmModel,
                strategy = AgentStrategyFactory.singleRun(),
                systemPrompt = DEFAULT_AGENT_SYSTEM_PROMPT,
                toolRegistry = toolRegistry,
            ),
            toolRegistry = toolRegistry,
            capabilities = listOf(CapabilityDescriptor(id = "tool.echo", kind = "tool", riskLevel = ToolRiskLevel.MID)),
        )
        val executor = RuntimeAgentExecutor(
            assembleAgent = { _, _ -> assembledAgent },
            assembleStreamingAgent = { _, _, _ -> assembledAgent },
        )

        executor.execute(
            session = RuntimeSession(id = "session-current-input"),
            context = RuntimeRequestContext(sessionId = "session-current-input", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "探索项目"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )
        val secondResult = executor.execute(
            session = RuntimeSession(id = "session-current-input"),
            context = RuntimeRequestContext(sessionId = "session-current-input", requestId = "request-2"),
            request = RuntimeAgentRunRequest(prompt = "这个项目是做什么的？"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(secondResult)
        assertEquals(JsonPrimitive("这个项目是做什么的？"), secondResult.output)
        assertEquals(
            "这个项目是做什么的？",
            promptExecutor.capturedPrompts.last().messages.last { it.role == Message.Role.User }.content,
        )
    }

    @Test
    fun `should use DeepSeek thinking compatibility stream when chat completions reasoning is hidden from Koog`() = runTest {
        val result = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            compatibilityStreamRunner = object : CompatibilityStreamRunner {
                override fun supports(binding: ProviderBinding, hasTools: Boolean): Boolean = true

                override fun stream(binding: ProviderBinding, prompt: ai.koog.prompt.dsl.Prompt) = flowOf(
                    StreamFrame.ReasoningDelta(text = "inspect "),
                    StreamFrame.TextDelta("answer"),
                )
            },
        ).execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(
                baseUrl = "https://api.deepseek.com",
                modelId = "deepseek-v4-flash",
                enableThinking = true,
            ),
            capabilitySet = CapabilitySet(adapters = emptyList()),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("answer"), result.output)
        assertEquals(
            listOf("status", "thinking", "text", "status"),
            result.events.map { it.channel ?: "status" },
        )
        assertEquals("inspect ", result.events[1].delta)
        assertEquals("answer", result.events[2].delta)
    }

    @Test
    fun `should load previous conversation history into next streaming prompt for same session`() = runTest {
        val conversationMemory = RuntimeConversationMemory()
        val recordings = mutableListOf<RecordingAssembledAgent>()
        val executor = RuntimeAgentExecutor(
            conversationMemory = conversationMemory,
            assembleAgent = { _, _ ->
                buildRecordingAssembledAgent(conversationMemory = conversationMemory) { prompt ->
                    val previousUserMessages = prompt.messages
                        .filter { it.role == Message.Role.User }
                        .dropLast(1)
                        .map { it.content }
                    if ("hello" in previousUserMessages) "you said hello" else "no memory"
                }.also { recordings += it }.assembledAgent
            },
            assembleStreamingAgent = { _, _, _ -> recordings.last().assembledAgent },
        )

        executor.execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = emptyList()),
        )
        val secondResult = executor.execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-2"),
            request = RuntimeAgentRunRequest(prompt = "what did I say?"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = emptyList()),
        )

        assertIs<RuntimeSuccess>(secondResult)
        assertEquals(JsonPrimitive("you said hello"), secondResult.output)
        val secondPromptMessages = recordings.last().promptExecutor.capturedPrompts.last().messages
        assertTrue(secondPromptMessages.any { it.role == Message.Role.User && it.content == "hello" })
        assertTrue(secondPromptMessages.any { it.role == Message.Role.Assistant && it.content == "no memory" })
        assertTrue(secondPromptMessages.any { it.role == Message.Role.User && it.content == "what did I say?" })
    }

    @Test
    fun `should store streamed reasoning in conversation history for next DeepSeek turn`() = runTest {
        val recordedPrompts = mutableListOf<ai.koog.prompt.dsl.Prompt>()
        val compatibilityRunner = object : CompatibilityStreamRunner {
            override fun supports(binding: ProviderBinding, hasTools: Boolean): Boolean = true

            override fun stream(binding: ProviderBinding, prompt: ai.koog.prompt.dsl.Prompt) = if (recordedPrompts.isEmpty()) {
                recordedPrompts += prompt
                flowOf(
                    StreamFrame.ReasoningDelta(text = "think "),
                    StreamFrame.TextDelta("hello"),
                )
            } else {
                recordedPrompts += prompt
                flowOf(StreamFrame.TextDelta("follow-up"))
            }
        }
        val executor = RuntimeAgentExecutor(
            conversationMemory = RuntimeConversationMemory(),
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            compatibilityStreamRunner = compatibilityRunner,
        )

        executor.execute(
            session = RuntimeSession(id = "session-deepseek-history"),
            context = RuntimeRequestContext(sessionId = "session-deepseek-history", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(
                baseUrl = "https://api.deepseek.com",
                modelId = "deepseek-v4-flash",
                enableThinking = true,
            ),
            capabilitySet = CapabilitySet(adapters = emptyList()),
        )
        executor.execute(
            session = RuntimeSession(id = "session-deepseek-history"),
            context = RuntimeRequestContext(sessionId = "session-deepseek-history", requestId = "request-2"),
            request = RuntimeAgentRunRequest(prompt = "what did I say?"),
            binding = openAiBinding(
                baseUrl = "https://api.deepseek.com",
                modelId = "deepseek-v4-flash",
                enableThinking = true,
            ),
            capabilitySet = CapabilitySet(adapters = emptyList()),
        )

        val secondPromptMessages = recordedPrompts.last().messages
        assertTrue(secondPromptMessages.any { it is Message.Reasoning && it.content == "think " })
        assertTrue(secondPromptMessages.any { it is Message.Assistant && it.content == "hello" })
    }

    @Test
    fun `should trim stored conversation history to configured window size`() = runTest {
        val conversationMemory = RuntimeConversationMemory(windowSize = 4)
        val recordings = mutableListOf<RecordingAssembledAgent>()
        val executor = RuntimeAgentExecutor(
            conversationMemory = conversationMemory,
            assembleAgent = { _, _ ->
                buildRecordingAssembledAgent(conversationMemory = conversationMemory) { prompt ->
                    prompt.messages.last { it.role == Message.Role.User }.content.uppercase()
                }.also { recordings += it }.assembledAgent
            },
            assembleStreamingAgent = { _, _, _ -> recordings.last().assembledAgent },
        )

        listOf("turn-1", "turn-2", "turn-3", "turn-4").forEachIndexed { index, prompt ->
            executor.execute(
                session = RuntimeSession(id = "session-1"),
                context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-${index + 1}"),
                request = RuntimeAgentRunRequest(prompt = prompt),
                binding = openAiBinding(),
                capabilitySet = CapabilitySet(adapters = emptyList()),
            )
        }

        val latestPrompt = recordings.last().promptExecutor.capturedPrompts.last()
        val userMessages = latestPrompt.messages.filter { it.role == Message.Role.User }.map { it.content }
        val assistantMessages = latestPrompt.messages.filter { it.role == Message.Role.Assistant }.map { it.content }
        assertEquals(listOf("turn-2", "turn-3", "turn-4"), userMessages)
        assertEquals(listOf("TURN-2", "TURN-3"), assistantMessages)
    }

    @Test
    fun `should inject deepseek thinking payload into prompt params when enabled`() {
        val prompt = buildRuntimePrompt(
            userPrompt = "hello",
            binding = openAiBinding(
                baseUrl = "https://api.deepseek.com",
                modelId = "deepseek-v4-flash",
                enableThinking = true,
            ),
        )

        val params = assertIs<OpenAIChatParams>(prompt.params)
        assertEquals("{\"type\":\"enabled\"}", params.additionalProperties?.get("thinking")?.toString())
    }

    private fun openAiBinding(
        providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl: String = "https://openrouter.ai/api/v1",
        modelId: String = "openai/gpt-oss-120b:free",
        enableThinking: Boolean = false,
    ) = ProviderBinding(
        providerId = "provider-openai",
        providerType = providerType,
        baseUrl = baseUrl,
        apiKey = "test-key",
        modelId = modelId,
        enableThinking = enableThinking,
    )

    @Serializable
    private data class RuntimeExecutorEchoArgs(
        @property:LLMDescription("Payload returned directly by the test echo tool.")
        val payload: String,
    )

    private class RuntimeExecutorEchoTool : SimpleTool<RuntimeExecutorEchoArgs>(
        argsType = typeToken<RuntimeExecutorEchoArgs>(),
        name = "tool.echo",
        description = "Returns the provided payload for runtime tool-loop tests.",
    ) {
        override suspend fun execute(args: RuntimeExecutorEchoArgs): String = args.payload
    }

    private class ScriptedDeepSeekReasoningToolLoopPromptExecutor(
        private val toolName: String,
        private val toolArgsJson: String,
        private val finalText: String,
    ) : ai.koog.prompt.executor.model.PromptExecutor() {
        val capturedPrompts = mutableListOf<ai.koog.prompt.dsl.Prompt>()

        override suspend fun execute(
            prompt: ai.koog.prompt.dsl.Prompt,
            model: LLModel,
            tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
        ): List<Message.Response> {
            capturedPrompts += prompt
            check(prompt.messages.any { message -> message is Message.Reasoning }) {
                "expected reasoning replay before sending tool results back to llm"
            }
            return listOf(
                Message.Assistant(
                    content = finalText,
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )
        }

        override fun executeStreaming(
            prompt: ai.koog.prompt.dsl.Prompt,
            model: LLModel,
            tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
        ) = kotlinx.coroutines.flow.flow {
            capturedPrompts += prompt
            val sawToolResult = prompt.messages.any { message ->
                message is Message.Tool.Result && message.tool == toolName
            }
            if (!sawToolResult) {
                emit(StreamFrame.ReasoningDelta(id = "reason-1", text = "inspect ", index = 0))
                emit(StreamFrame.ReasoningDelta(id = "reason-1", text = "workspace", index = 0))
                emit(StreamFrame.ToolCallComplete(id = "tool-call-1", name = toolName, content = toolArgsJson, index = 0))
                emit(StreamFrame.End(finishReason = null, metaInfo = ResponseMetaInfo.Empty))
                return@flow
            }

            emit(StreamFrame.TextDelta(finalText, index = 0))
            emit(StreamFrame.End(finishReason = null, metaInfo = ResponseMetaInfo.Empty))
        }

        override suspend fun moderate(
            prompt: ai.koog.prompt.dsl.Prompt,
            model: LLModel,
        ) = ai.koog.prompt.dsl.ModerationResult(
            isHarmful = false,
            categories = emptyMap(),
        )

        override fun close() = Unit
    }
}
