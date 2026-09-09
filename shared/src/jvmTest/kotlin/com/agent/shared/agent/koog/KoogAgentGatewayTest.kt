package com.agent.shared.agent.koog

import ai.koog.prompt.streaming.StreamFrame
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.AgentIterationLimit
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.QuestionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 验证网关事件、交互转发与失败提示。 */
class KoogAgentGatewayTest {

    /**
     * Koog 的循环上限异常必须成为可继续操作的提示，而不是使会话停在技术异常状态。
     */
    @Test
    fun `should turn iteration limit exception into a recoverable message`() {
        val configuredLimit = agentFailureReason(AIAgentMaxNumberOfIterationsReachedException(75), 75)
        val unlimitedLimit = agentFailureReason(
            AIAgentMaxNumberOfIterationsReachedException(AgentIterationLimit.KOOG_MAXIMUM),
            AgentIterationLimit.KOOG_MAXIMUM,
        )

        assertTrue(configuredLimit.contains("最大迭代次数（75）"))
        assertTrue(configuredLimit.contains("当前会话仍可继续"))
        assertTrue(unlimitedLimit.contains("运行时允许的最大迭代次数"))
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
        val script = $$"""1..5000 | ForEach-Object { Write-Output ("out-" + $_); [Console]::Error.WriteLine("err-" + $_) }"""
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
