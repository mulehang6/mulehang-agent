package com.agent.shared.agent.koog

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.QuestionRequest
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
     * reasoning 文本已通过增量事件到达时，空的完成事件不能清除该文本；否则工具结果续传会丢失
     * Responses 协议要求回放的 reasoning_text。
     */
    @Test
    fun `should retain reasoning delta when completion content is empty`() = runTest {
        val message = collectAssistantMessageFromStream(
            frames = flowOf(
                StreamFrame.ReasoningDelta(
                    id = "reasoning-1",
                    text = "需要先读取文件。",
                ),
                StreamFrame.ReasoningComplete(
                    id = "reasoning-1",
                    content = emptyList(),
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

        assertEquals(
            listOf("需要先读取文件。"),
            message.parts.filterIsInstance<MessagePart.Reasoning>().single().content,
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
     * 工具调用参数完成时只公告调用开始，不应把参数伪装成执行结果。
     */
    @Test
    fun `should map tool call arguments without reporting a finished result`() = runTest {
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

        assertEquals(5, events.size)
        assertEquals(AgentStreamEvent.Started, events[0])
        assertEquals(AgentStreamEvent.TextDelta("hel"), events[1])
        assertEquals(
            AgentStreamEvent.ToolCallStarted(
                toolCallId = "call-1",
                name = "read_file",
                argumentsPreview = """{"path":"README.md"}""",
            ),
            events[2],
        )
        assertEquals(AgentStreamEvent.TextDelta("lo"), events[3])
        assertEquals(AgentStreamEvent.Completed("hello"), events[4])
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
     * 首轮 Koog 请求也必须把已有结构化历史映射回 prompt，而不是只发送当前 prompt。
     */
    @Test
    fun `should build koog prompt messages from structured conversation history`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.Reasoning(
                            summary = "先分析",
                            rawText = "先分析原始思考",
                        ),
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                        AgentConversationHistoryPart.ToolResult(
                            id = "call-1",
                            name = "read_file",
                            resultPreview = "file-content",
                        ),
                        AgentConversationHistoryPart.Text("done"),
                    ),
                ),
            ),
            prompt = "second",
        )

        assertEquals(5, messages.size)
        assertEquals(listOf(MessagePart.Text("first")), assertIs<Message.User>(messages[0]).parts)
        assertEquals(
            listOf(
                MessagePart.Reasoning(content = listOf("先分析原始思考"), summary = listOf("先分析")),
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = """{"path":"README.md"}""",
                ),
            ),
            assertIs<Message.Assistant>(messages[1]).parts,
        )
        assertEquals(
            listOf(
                MessagePart.Tool.Result(
                    id = "call-1",
                    tool = "read_file",
                    output = "file-content",
                ),
            ),
            assertIs<Message.User>(messages[2]).parts,
        )
        assertEquals(listOf(MessagePart.Text("done")), assertIs<Message.Assistant>(messages[3]).parts)
        assertEquals(listOf(MessagePart.Text("second")), assertIs<Message.User>(messages[4]).parts)
    }

    /**
     * 中断或失败的上一轮可能只留下 tool call；恢复历史时必须补齐 tool result，避免兼容 API 拒绝请求。
     */
    @Test
    fun `should synthesize tool result for orphaned historical tool call`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                    ),
                ),
            ),
            prompt = "second",
        )

        assertEquals(4, messages.size)
        assertEquals(
            listOf(
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = """{"path":"README.md"}""",
                ),
            ),
            assertIs<Message.Assistant>(messages[1]).parts,
        )
        assertEquals(
            listOf(
                MessagePart.Tool.Result(
                    id = "call-1",
                    tool = "read_file",
                    output = "工具调用未完成，未产生可用结果。",
                ),
            ),
            assertIs<Message.User>(messages[2]).parts,
        )
        assertEquals(listOf(MessagePart.Text("second")), assertIs<Message.User>(messages[3]).parts)
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
     * 工具在后台线程产生输出时，网关必须在工具结束前把增量转为 UI 事件。
     */
    @Test
    fun `should forward tool output chunks before the agent completes`() = runTest {
        val gateway = KoogAgentGateway(
            interactionBridge = object : DesktopToolInteractionBridge {
                override suspend fun requestQuestion(request: QuestionRequest): String = "answer"

                override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
            },
            agentRunner = { _, _, bridge, _ ->
                bridge.onToolOutputChunk(
                    toolName = "run_powershell",
                    text = "> Task :shared:compileKotlin\n",
                    isErrorStream = false,
                )
                "done"
            },
        )

        val events = gateway.run(
            AgentRunRequest(
                prompt = "hello",
                profile = openAiProfile(),
                workspacePath = "D:\\repo",
            ),
        ).toList()

        assertEquals(AgentStreamEvent.Started, events[0])
        assertEquals(
            AgentStreamEvent.ToolOutputDelta(
                toolCallId = null,
                name = "run_powershell",
                text = "> Task :shared:compileKotlin\n",
                stream = AgentStreamEvent.ToolOutputStream.Stdout,
            ),
            events[1],
        )
        assertEquals(AgentStreamEvent.Completed("done"), events[2])
    }

    /**
     * 工具内部通过 runBlocking 等待审批时，审批事件也应先被 UI 侧消费到，而不是卡死主线程。
     */
    @Test
    @Suppress("RunBlockingInSuspendFunction")
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

    /**
     * 终端工具事件应从 Koog 的参数预览中抽取模型传入的操作意图。
     */
    @Test
    fun `should extract terminal operation intent from tool arguments`() {
        assertEquals(
            "列出当前目录内容",
            extractToolOperationIntent(
                toolName = "run_powershell",
                argumentsPreview = "{script=Get-ChildItem, operation_intent=列出当前目录内容}",
            ),
        )
        assertEquals(
            null,
            extractToolOperationIntent(
                toolName = "read_file",
                argumentsPreview = "{path=README.md}",
            ),
        )
    }

    /**
     * 长终端参数必须先按结构读取字段再生成预览，字段顺序不应影响卡片的命令和操作意图。
     */
    @Test
    fun `should build terminal card fields before truncating long arguments`() {
        val script =
            """1..5000 | ForEach-Object { Write-Output ("out-" + ${'$'}_); """ +
                    """[Console]::Error.WriteLine("err-" + ${'$'}_) }"""
        val intent = "循环输出 1 到 5000，每次迭代向标准输出写入 out-N，向标准错误写入 err-N"
        val argumentOrders = listOf(
            linkedMapOf(
                "script" to JSONPrimitive(script),
                "operation_intent" to JSONPrimitive(intent),
            ),
            linkedMapOf(
                "operation_intent" to JSONPrimitive(intent),
                "script" to JSONPrimitive(script),
            ),
        )

        argumentOrders.forEach { entries ->
            val event = buildToolCallStartedEvent(
                toolCallId = "call-terminal",
                toolName = "run_powershell",
                arguments = JSONObject(entries),
            )

            assertEquals(script.take(120), event.argumentsPreview)
            assertEquals(intent, event.operationIntent)
        }
    }

    /**
     * 工具事件需同时提供给 UI 的完整输出和给后续模型上下文的紧凑预览。
     */
    @Test
    fun `should retain complete tool result separately from model preview`() {
        val result = "out-1\nout-2\nout-3"

        val event = buildToolCallFinishedEvent(
            toolCallId = "call-terminal",
            toolName = "run_powershell",
            result = result,
        )

        assertEquals("out-1 out-2 out-3", event.resultPreview)
        assertEquals(result, event.resultDisplay)
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
