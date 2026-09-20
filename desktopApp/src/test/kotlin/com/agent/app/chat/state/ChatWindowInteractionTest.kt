package com.agent.app.chat.state

import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.shared.agent.api.*
import com.agent.shared.chat.model.*
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.tool.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 验证ChatWindowInteractionTest的状态转换。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowInteractionTest : ChatWindowTestFixture() {
    /**
     * 执行中再次触发主按钮时应取消当前轮次，并恢复到空闲态。
     */
    @Test
    fun `should cancel active run and return idle when requested`() = runTest(dispatcher) {
        val cancelled = CompletableDeferred<Unit>()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                try {
                    emit(AgentStreamEvent.Status("正在等待模型响应…"))
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("long running")
        advanceUntilIdle()
        assertEquals(ExecutionState.Running, state.ui.activeConversation.executionState)

        assertEquals("正在等待模型响应…", state.ui.activeConversation.progressMessage)
        state.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertNull(state.ui.activeConversation.progressMessage)
        assertTrue(cancelled.isCompleted)
    }

    /**
     * 当前交互桥仍是单轮挂起模型时，应明确禁止第二个 task 并发发送。
     */
    @Test
    fun `should reject concurrent send from another task while one run is active`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val capturedRequests = mutableListOf<AgentRunRequest>()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                capturedRequests += request
                emit(AgentStreamEvent.Started)
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("task-a")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\ghi")
        state.send("task-b")
        advanceUntilIdle()

        assertEquals(1, capturedRequests.size)
        assertEquals("task-a", capturedRequests.single().prompt)
        assertEquals(
            "已有任务在执行: 请等待当前任务完成，或先停止当前任务再启动新的 task。",
            state.errorMessage,
        )
        assertEquals(ExecutionState.Running, state.findConversation(state.ui.tasks.last().id).executionState)
        assertTrue(started.isCompleted)
    }

    /**
     * ask_user 期间应保持同一轮次挂起，回答后继续执行而不是新开一轮。
     */
    @Test
    fun `should keep same turn running while waiting for ask user response`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val question = QuestionRequest(
                    requestId = "q1",
                    toolCallId = "call-1",
                    question = "Pick one",
                    options = listOf("Option A", "Option B"),
                )
                emit(AgentStreamEvent.QuestionRequested(question))
                val answer = coordinator.requestQuestion(question)
                emit(AgentStreamEvent.Completed("selected: $answer"))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("start")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForUserInput, state.ui.activeConversation.executionState)
        assertEquals("Pick one", state.ui.activeConversation.pendingQuestion?.question)

        state.answerPendingQuestion("Option A")
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingQuestion)
        val assistantItem = state.state.items.last() as ChatMessageItem
        assertEquals("selected: Option A", assistantItem.message.content)
    }

    /**
     * 回答挂起问题时，应定向恢复原会话，而不是当前选中的其他 task。
     */
    @Test
    fun `should resume question on owning conversation even after switching tasks`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val question = QuestionRequest(
                    requestId = "q1",
                    toolCallId = "call-1",
                    question = "Pick one",
                    options = listOf("Option A", "Option B"),
                )
                emit(AgentStreamEvent.QuestionRequested(question))
                val answer = coordinator.requestQuestion(question)
                emit(AgentStreamEvent.Completed("selected: $answer"))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("start")
        advanceUntilIdle()
        val ownerConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val otherConversationId = state.ui.activeConversationId

        state.answerPendingQuestion("Option A")
        advanceUntilIdle()

        val ownerConversation = state.findConversation(ownerConversationId)
        val otherConversation = state.findConversation(otherConversationId)
        assertEquals(ExecutionState.Idle, ownerConversation.executionState)
        assertEquals(null, ownerConversation.pendingQuestion)
        assertEquals("selected: Option A", (ownerConversation.items.last() as ChatMessageItem).message.content)
        assertEquals(ExecutionState.Idle, otherConversation.executionState)
        assertEquals(null, otherConversation.pendingQuestion)
    }

    /**
     * 审批请求应进入等待态，提交结果后继续当前轮次。
     */
    @Test
    fun `should keep same turn running while waiting for approval response`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val approval = ApprovalRequest(
                    requestId = "a1",
                    toolName = "run_powershell",
                    summary = "执行 PowerShell 7 脚本",
                    payloadPreview = "Get-Location",
                )
                emit(AgentStreamEvent.ApprovalRequested(approval))
                val approved = coordinator.requestApproval(approval)
                emit(AgentStreamEvent.Completed("approved: $approved"))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("run command")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForApproval, state.ui.activeConversation.executionState)
        assertEquals("run_powershell", state.ui.activeConversation.pendingApproval?.toolName)

        state.answerPendingApproval(true)
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingApproval)
        val assistantItem = state.state.items.last() as ChatMessageItem
        assertEquals("approved: true", assistantItem.message.content)
    }

    /**
     * 拒绝审批后应停止当前 agent 轮次，并让用户可以从 composer 开始下一轮。
     */
    @Test
    fun `should stop the agent run after rejecting an approval`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        var requests = 0
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                if (requests++ == 0) {
                    val approval = ApprovalRequest(
                        requestId = "a-stop",
                        toolName = "run_powershell",
                        summary = "执行 PowerShell 脚本",
                    )
                    emit(AgentStreamEvent.ApprovalRequested(approval))
                    coordinator.requestApproval(approval)
                }
                emit(AgentStreamEvent.Completed("done"))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("run command")
        advanceUntilIdle()
        state.answerPendingApproval(ApprovalResponse.REJECT_AND_STOP)
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingApproval)

        state.send("next instruction")
        advanceUntilIdle()
        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals("done", (state.ui.activeConversation.items.last() as ChatMessageItem).message.content)
    }

    /**
     * 提交审批时，应只恢复发起审批请求的会话。
     */
    @Test
    fun `should resume approval on owning conversation even after switching tasks`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val approval = ApprovalRequest(
                    requestId = "a1",
                    toolName = "run_powershell",
                    summary = "执行 PowerShell 7 脚本",
                    payloadPreview = "Get-Location",
                )
                emit(AgentStreamEvent.ApprovalRequested(approval))
                val approved = coordinator.requestApproval(approval)
                emit(AgentStreamEvent.Completed("approved: $approved"))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("run command")
        advanceUntilIdle()
        val ownerConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val otherConversationId = state.ui.activeConversationId

        state.answerPendingApproval(true)
        advanceUntilIdle()

        val ownerConversation = state.findConversation(ownerConversationId)
        val otherConversation = state.findConversation(otherConversationId)
        assertEquals(ExecutionState.Idle, ownerConversation.executionState)
        assertEquals(null, ownerConversation.pendingApproval)
        assertEquals("approved: true", (ownerConversation.items.last() as ChatMessageItem).message.content)
        assertEquals(ExecutionState.Idle, otherConversation.executionState)
        assertEquals(null, otherConversation.pendingApproval)
    }

    /**
     * 取消正在等待用户输入的轮次时，应清理挂起状态、释放运行槽位并恢复空闲态。
     */
    @Test
    fun `should cancel waiting for user input and clear pending state`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                emit(
                    AgentStreamEvent.QuestionRequested(
                        QuestionRequest(
                            requestId = "q1",
                            toolCallId = "call-1",
                            question = "Pick one",
                            options = listOf("Option A", "Option B"),
                        )
                    )
                )
                awaitCancellation()
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("start")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForUserInput, state.ui.activeConversation.executionState)
        assertEquals("Pick one", state.ui.activeConversation.pendingQuestion?.question)

        state.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingQuestion)

        state.send("after cancel")
        advanceUntilIdle()

        val lastItem = state.ui.activeConversation.items.last() as ChatMessageItem
        assertEquals(ChatRole.User, lastItem.message.role)
        assertEquals("after cancel", lastItem.message.content)
    }

    /**
     * 取消正在等待审批的轮次时，应清理挂起状态、释放运行槽位并恢复空闲态。
     */
    @Test
    fun `should cancel waiting for approval and clear pending state`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                emit(
                    AgentStreamEvent.ApprovalRequested(
                        ApprovalRequest(
                            requestId = "a1",
                            toolName = "run_powershell",
                            summary = "执行 PowerShell 脚本",
                            payloadPreview = "Get-Location",
                        )
                    )
                )
                awaitCancellation()
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("run command")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForApproval, state.ui.activeConversation.executionState)
        assertEquals("run_powershell", state.ui.activeConversation.pendingApproval?.toolName)

        state.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingApproval)
    }

    /** 批量问题应在一次提交后记录 Answers，并只恢复当前轮次一次。 */
    @Test
    fun `should resume once and append answers after every batch question is answered`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val batchRequest = QuestionRequest(
            requestId = "q-batch",
            toolCallId = "call-batch",
            questions = listOf(
                QuestionPrompt("目标", listOf("UI", "Bug")),
                QuestionPrompt("语言", listOf("中文", "English")),
            ),
        )
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                emit(AgentStreamEvent.QuestionRequested(batchRequest))
                emit(AgentStreamEvent.Completed(coordinator.requestQuestion(batchRequest)))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("start")
        advanceUntilIdle()
        state.answerPendingQuestions(
            listOf(QuestionAnswer("目标", "UI"), QuestionAnswer("语言", "中文")),
        )
        advanceUntilIdle()

        val answers = state.ui.activeConversation.items.filterIsInstance<AnsweredQuestionsItem>().single()
        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(listOf("UI", "中文"), answers.answers.map(QuestionAnswer::answer))
    }

}
