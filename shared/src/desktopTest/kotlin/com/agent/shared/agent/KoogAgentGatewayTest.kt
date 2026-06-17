package com.agent.shared.agent

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证 Koog gateway 的本地错误分支。
 */
class KoogAgentGatewayTest {

    /**
     * 自定义流式节点应把文本与思考 frame 同时转换为 UI 事件和 assistant message。
     */
    @Test
    fun `should collect streaming text and reasoning into assistant message`() = runTest {
        val emittedEvents = mutableListOf<AgentStreamEvent>()

        val message = collectAssistantMessageFromStream(
            frames = flow {
                emit(StreamFrame.ReasoningDelta(id = "r1", text = "raw", summary = "summary"))
                emit(StreamFrame.TextDelta("hel"))
                emit(StreamFrame.TextDelta("lo"))
                emit(
                    StreamFrame.ReasoningComplete(
                        id = "r1",
                        content = listOf("raw", " detail"),
                        summary = listOf("summary", " done"),
                    ),
                )
                emit(StreamFrame.End(finishReason = "stop"))
            },
            emitEvent = { event: AgentStreamEvent -> emittedEvents.add(event) },
        )

        assertEquals(
            listOf(
                AgentStreamEvent.ReasoningDelta(summary = "summary", rawText = "raw"),
                AgentStreamEvent.TextDelta("hel"),
                AgentStreamEvent.TextDelta("lo"),
                AgentStreamEvent.ReasoningCompleted(
                    summary = "summary done",
                    rawText = "raw detail",
                ),
            ),
            emittedEvents,
        )
        assertEquals("stop", message.finishReason)
        assertEquals(
            listOf(
                MessagePart.Reasoning(
                    id = "r1",
                    content = listOf("raw", " detail"),
                    summary = listOf("summary", " done"),
                ),
                MessagePart.Text("hello"),
            ),
            message.parts,
        )
    }

    /**
     * 自定义流式节点应把工具调用 frame 还原为可继续执行的 assistant message。
     */
    @Test
    fun `should collect streaming tool calls into assistant message`() = runTest {
        val message = collectAssistantMessageFromStream(
            frames = flowOf(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    name = "read_file",
                    content = "{\"path\":\"REA",
                ),
                StreamFrame.ToolCallComplete(
                    id = "call-1",
                    name = "read_file",
                    content = "{\"path\":\"README.md\"}",
                ),
                StreamFrame.End(finishReason = "tool_calls"),
            ),
            emitEvent = {},
        )

        assertEquals("tool_calls", message.finishReason)
        assertEquals(
            listOf(
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = "{\"path\":\"README.md\"}",
                ),
            ),
            message.parts,
        )
    }

    /**
     * 工具调用增量在不同 chunk 中只保留 index 或 id 时，仍应合并成同一个 tool call。
     */
    @Test
    fun `should merge tool call deltas by index when later chunks omit id`() = runTest {
        val message = collectAssistantMessageFromStream(
            frames = flowOf(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    index = 0,
                    name = "read_file",
                    content = "{\"path\":\"REA",
                ),
                StreamFrame.ToolCallDelta(
                    id = null,
                    name = null,
                    index = 0,
                    content = "DME.md\"}",
                ),
                StreamFrame.End(finishReason = "tool_calls"),
            ),
            emitEvent = {},
        )

        assertEquals("tool_calls", message.finishReason)
        assertEquals(
            listOf(
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = "{\"path\":\"README.md\"}",
                ),
            ),
            message.parts,
        )
    }

    /**
     * `tool_calls` 结束态若没有真正的工具调用 part，应尽早转成明确异常而不是让图节点卡死。
     */
    @Test
    fun `should reject tool call finish without tool call parts`() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            collectAssistantMessageFromStream(
                frames = flowOf(
                    StreamFrame.ReasoningDelta(id = "r1", text = "先判断问题"),
                    StreamFrame.End(finishReason = "tool_calls"),
                ),
                emitEvent = {},
            )
        }

        assertTrue(error.message.orEmpty().contains("tool_calls"))
    }

    /**
     * 只有 reasoning、没有文本或工具调用的响应同样无法命中策略图边，应该直接失败。
     */
    @Test
    fun `should reject reasoning only assistant message`() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            collectAssistantMessageFromStream(
                frames = flowOf(
                    StreamFrame.ReasoningDelta(id = "r1", text = "先判断问题"),
                    StreamFrame.ReasoningComplete(
                        id = "r1",
                        content = listOf("先判断问题"),
                    ),
                    StreamFrame.End(finishReason = "stop"),
                ),
                emitEvent = {},
            )
        }

        assertTrue(error.message.orEmpty().contains("思考内容"))
    }

    /**
     * 参考 paicli 的 ReAct 主循环，只要存在 tool call，就算同时带有文本也不能直接结束。
     */
    @Test
    fun `should keep looping when assistant message contains both text and tool calls`() {
        val assistant = Message.Assistant(
            listOf(
                MessagePart.Text("我先去读取文件。"),
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = "{\"path\":\"README.md\"}",
                ),
            ),
            ResponseMetaInfo.Empty,
            finishReason = "tool_calls",
        )

        assertFalse(assistant.shouldFinishReactLoop())
        assertEquals(null, assistant.finalTextForReactLoop())
    }

    /**
     * 参考 paicli 的 ReAct 主循环，只有纯文本且无工具调用时才结束当前轮次。
     */
    @Test
    fun `should finish react loop only for assistant text without tool calls`() {
        val assistant = Message.Assistant(
            listOf(
                MessagePart.Text("最终答案"),
            ),
            ResponseMetaInfo.Empty,
            finishReason = "stop",
        )

        assertTrue(assistant.shouldFinishReactLoop())
        assertEquals("最终答案", assistant.finalTextForReactLoop())
    }

    /**
     * 当前阶段不支持的 provider 应转换为 UI 可消费的失败事件。
     */
    @Test
    fun `should emit failed event for unsupported provider`() = runTest {
        val events = KoogAgentGateway().run(
            AgentRunRequest(
                prompt = "hello",
                profile = ConfigProfile(
                    id = "google-main",
                    providerType = ProviderType.GOOGLE,
                    baseUrl = "https://generativelanguage.googleapis.com",
                    apiKey = "key",
                    model = "gemini-2.5-pro",
                    enabled = true,
                    layer = ConfigLayer.PROJECT,
                ),
            ),
        ).toList()

        assertEquals(2, events.size)
        assertEquals(AgentStreamEvent.Started, events.first())
        val failed = assertIs<AgentStreamEvent.Failed>(events.last())
        assertTrue(failed.reason.contains("暂不支持"))
    }

    /**
     * 流式文本与工具调用 frame 应被转换为应用层事件流。
     */
    @Test
    fun `should map stream frames into text and tool events`() = runTest {
        val gateway = KoogAgentGateway(
            streamRunner = { _ ->
                flowOf(
                    StreamFrame.TextDelta("hel"),
                    StreamFrame.ToolCallComplete(
                        id = "call-1",
                        name = "read_file",
                        content = """{"path":"README.md"}""",
                    ),
                    StreamFrame.TextDelta("lo"),
                    StreamFrame.End(),
                )
            },
        )

        val events = gateway.run(
            AgentRunRequest(
                prompt = "hello",
                profile = openAiProfile(),
            ),
        ).toList()

        assertEquals(6, events.size)
        assertEquals(AgentStreamEvent.Started, events[0])
        assertEquals(AgentStreamEvent.TextDelta("hel"), events[1])
        assertEquals(
            AgentStreamEvent.ToolCallStarted(
                name = "read_file",
                argumentsPreview = """{"path":"README.md"}""",
            ),
            events[2],
        )
        assertEquals(
            AgentStreamEvent.ToolCallFinished(
                name = "read_file",
                resultPreview = """{"path":"README.md"}""",
            ),
            events[3],
        )
        assertEquals(AgentStreamEvent.TextDelta("lo"), events[4])
        assertEquals(AgentStreamEvent.Completed("hello"), events[5])
    }

    /**
     * reasoning frame 应映射为 summary 优先的思考流事件。
     */
    @Test
    fun `should map reasoning frames into reasoning events`() = runTest {
        val gateway = KoogAgentGateway(
            streamRunner = { _ ->
                flowOf(
                    StreamFrame.ReasoningDelta(
                        id = "r1",
                        text = "raw-1",
                        summary = "summary-1",
                    ),
                    StreamFrame.ReasoningComplete(
                        id = "r1",
                        content = listOf("raw-1", "raw-2"),
                        summary = listOf("summary-1", "summary-2"),
                    ),
                    StreamFrame.End(),
                )
            },
        )

        val events = gateway.run(
            AgentRunRequest(
                prompt = "hello",
                profile = openAiProfile(),
            ),
        ).toList()

        assertEquals(4, events.size)
        assertEquals(AgentStreamEvent.Started, events[0])
        assertEquals(
            AgentStreamEvent.ReasoningDelta(
                summary = "summary-1",
                rawText = "raw-1",
            ),
            events[1],
        )
        assertEquals(
            AgentStreamEvent.ReasoningCompleted(
                summary = "summary-1summary-2",
                rawText = "raw-1raw-2",
            ),
            events[2],
        )
        assertEquals(AgentStreamEvent.Completed(""), events[3])
    }

    /**
     * 扩展请求携带结构化历史后，原有 frame 到事件的映射不应退化。
     */
    @Test
    fun `should keep mapping stream frames when request carries history`() = runTest {
        val gateway = KoogAgentGateway(
            streamRunner = { _ ->
                flowOf(
                    StreamFrame.TextDelta("hel"),
                    StreamFrame.TextDelta("lo"),
                    StreamFrame.End(),
                )
            },
        )

        val events = gateway.run(
            AgentRunRequest(
                prompt = "hello",
                profile = openAiProfile(),
                history = listOf(
                    AgentConversationHistoryMessage.User("previous turn"),
                ),
            ),
        ).toList()

        assertEquals(AgentStreamEvent.Started, events[0])
        assertEquals(AgentStreamEvent.TextDelta("hel"), events[1])
        assertEquals(AgentStreamEvent.TextDelta("lo"), events[2])
        assertEquals(AgentStreamEvent.Completed("hello"), events[3])
    }

    /**
     * ask_user 通过交互桥恢复时，应先发出问题事件，再完成当前轮次。
     */
    @Test
    fun `should emit question requested event when bridge resumes ask user`() = runTest {
        val gateway = KoogAgentGateway(
            interactionBridge = object : DesktopToolInteractionBridge {
                override suspend fun requestQuestion(request: QuestionRequest): String = "Option B"

                override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
            },
            agentRunner = { _, _, bridge, _ ->
                bridge.requestQuestion(
                    QuestionRequest(
                        requestId = "q1",
                        toolCallId = "call-1",
                        question = "Pick one",
                        options = listOf("Option A", "Option B"),
                    ),
                )
            },
        )

        val events = gateway.run(
            AgentRunRequest(
                prompt = "hello",
                profile = openAiProfile(),
                workspacePath = "D:\\repo",
            ),
        ).toList()

        assertEquals(3, events.size)
        assertEquals(AgentStreamEvent.Started, events[0])
        val questionEvent = assertIs<AgentStreamEvent.QuestionRequested>(events[1])
        assertEquals("Pick one", questionEvent.request.question)
        assertEquals(AgentStreamEvent.Completed("Option B"), events[2])
    }

    /**
     * 工具内部通过 runBlocking 等待审批时，审批事件也应先被 UI 侧消费到，而不是卡死主线程。
     */
    @Test
    @Suppress("RunBlocking")
    fun `should emit approval requested before blocking tool resumes`() = runTest {
        val interactionBridge = object : DesktopToolInteractionBridge {
            private val approvalDeferred = CompletableDeferred<Boolean>()

            override suspend fun requestQuestion(request: QuestionRequest): String = error("unexpected question")

            override suspend fun requestApproval(request: ApprovalRequest): Boolean = approvalDeferred.await()

            fun submitApproval(approved: Boolean): Boolean = approvalDeferred.complete(approved)
        }
        val events = mutableListOf<AgentStreamEvent>()
        val gateway = KoogAgentGateway(
            interactionBridge = interactionBridge,
            executionDispatcher = Dispatchers.Default,
            agentRunner = { _, _, bridge, _ ->
                val approved = runBlocking {
                    bridge.requestApproval(
                        ApprovalRequest(
                            requestId = "approval-1",
                            toolName = "run_powershell",
                            summary = "执行 PowerShell 7 脚本",
                            payloadPreview = "Get-Location",
                        ),
                    )
                }
                if (approved) "approved" else "rejected"
            },
        )

        val collectJob = launch {
            gateway.run(
                AgentRunRequest(
                    prompt = "hello",
                    profile = openAiProfile(),
                    workspacePath = "D:\\repo",
                ),
            ).collect { event ->
                events += event
            }
        }

        withTimeout(5.seconds) {
            while (events.none { it is AgentStreamEvent.ApprovalRequested }) {
                yield()
            }
        }

        assertEquals(2, events.size)
        assertEquals(AgentStreamEvent.Started, events[0])
        val approvalEvent = assertIs<AgentStreamEvent.ApprovalRequested>(events[1])
        assertEquals("run_powershell", approvalEvent.request.toolName)

        assertTrue(interactionBridge.submitApproval(true))
        collectJob.join()

        assertEquals(3, events.size)
        assertEquals(AgentStreamEvent.Completed("approved"), events[2])
    }

    private fun openAiProfile(): ConfigProfile = ConfigProfile(
        id = "openai-main",
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "key",
        model = "gpt-4.1",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
