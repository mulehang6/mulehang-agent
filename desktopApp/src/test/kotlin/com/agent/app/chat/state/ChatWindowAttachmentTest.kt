package com.agent.app.chat.state

import com.agent.shared.agent.api.*
import com.agent.shared.agent.resource.AgentPromptCommand
import com.agent.shared.agent.resource.AgentPromptCommandKind
import com.agent.shared.agent.resource.AgentResourceOrigin
import com.agent.shared.agent.resource.AgentResourceSnapshot
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 验证ChatWindowAttachmentTest的状态转换。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowAttachmentTest : ChatWindowTestFixture() {
    /**
     * 附件选择结果应挂到当前活动会话的输入区，而不是进入消息正文。
     */
    @Test
    fun `should attach selected files to active conversation draft`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.attachFiles(listOf("D:\\tmp\\ChatScreen.kt", "D:\\tmp\\design.png"))

        assertEquals(
            listOf("ChatScreen.kt", "design.png"),
            state.activeDraftAttachments.map { it.name },
        )
    }

    /**
     * 用户应能从当前输入区移除误选附件。
     */
    @Test
    fun `should remove attachment from active conversation draft`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        state.attachFiles(listOf("D:\\tmp\\ChatScreen.kt", "D:\\tmp\\design.png"))

        state.removeAttachment("D:\\tmp\\ChatScreen.kt")

        assertEquals(listOf("design.png"), state.activeDraftAttachments.map { it.name })
    }

    /** prompt 命令先插入编辑器，用户再次发送才运行；`/reload` 则直接执行资源重载控制动作。 */
    @Test
    fun `should insert prompt command before send and reload resources without creating history`() = runTest(dispatcher) {
        val profile = profile()
        val commands = listOf(
            AgentPromptCommand(
                name = "review",
                description = "review",
                template = $$"请审查 $1",
                kind = AgentPromptCommandKind.PROMPT,
                origin = AgentResourceOrigin.USER_AUTO_DISCOVERY,
            ),
            AgentPromptCommand(
                name = "reload",
                description = "reload",
                kind = AgentPromptCommandKind.BUILTIN,
                origin = AgentResourceOrigin.BUILTIN,
            ),
        )
        val resourceSnapshot = AgentResourceSnapshot.empty().copy(version = 1, commands = commands)
        var reloadCount = 0
        var capturedPrompt: String? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedPrompt = request.prompt
                return flowOf(AgentStreamEvent.Started, AgentStreamEvent.Completed(""))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(profiles = listOf(profile), activeProfile = profile),
            projectPath = "E:\\commands",
            resourceSnapshotProvider = { resourceSnapshot },
            resourceReloader = {
                reloadCount += 1
                resourceSnapshot.copy(version = 2)
            },
        )

        state.refreshActiveResourceSnapshot()
        advanceUntilIdle()
        state.updateDraft("/review src/App.kt")
        state.sendDraft()
        assertEquals("请审查 src/App.kt", state.ui.draft)
        assertNull(state.ui.activeConversationOrNull)

        state.sendDraft()
        advanceUntilIdle()
        assertEquals("请审查 src/App.kt", capturedPrompt)

        state.updateDraft("/reload")
        state.sendDraft()
        advanceUntilIdle()
        assertEquals(1, reloadCount)
        assertEquals("", state.ui.draft)
        assertEquals(1, state.ui.activeConversation.history.count { it is AgentConversationHistoryMessage.User })
    }

    /** 资源重载占用期间，新的消息不能抢先使用将被替换的 MCP 连接。 */
    @Test
    fun `should block a new run while resource reload is pending`() = runTest(dispatcher) {
        val profile = profile()
        val reloadGate = CompletableDeferred<Unit>()
        var runCount = 0
        val resourceSnapshot = AgentResourceSnapshot.empty().copy(
            version = 1,
            commands = listOf(
                AgentPromptCommand(
                    name = "reload",
                    description = "reload",
                    kind = AgentPromptCommandKind.BUILTIN,
                    origin = AgentResourceOrigin.BUILTIN,
                ),
            ),
        )
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                runCount += 1
                return flowOf(AgentStreamEvent.Completed(""))
            }
        }
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(profiles = listOf(profile), activeProfile = profile),
            projectPath = "E:\\reload",
            resourceSnapshotProvider = { resourceSnapshot },
            resourceReloader = {
                reloadGate.await()
                resourceSnapshot.copy(version = 2)
            },
        )

        state.resourceSnapshot = resourceSnapshot
        state.updateDraft("/reload")
        state.sendDraft()

        assertFalse(state.canReloadAgentResources)
        state.updateDraft("next")
        state.sendDraft()
        assertEquals(0, runCount)

        reloadGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(state.canReloadAgentResources)
    }

    /** 异步刷新返回旧快照时，不能覆盖已经发布的更新版本。 */
    @Test
    fun `should keep newer resource snapshot after stale async refresh`() = runTest(dispatcher) {
        val stale = AgentResourceSnapshot.empty().copy(version = 1)
        val current = AgentResourceSnapshot.empty().copy(version = 2)
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\refresh",
            resourceSnapshotProvider = { stale },
        )
        state.resourceSnapshot = current

        state.refreshActiveResourceSnapshot()
        advanceUntilIdle()

        assertEquals(2, state.resourceVersion)
    }

    /** 取消后的协程尚未完成 finally 清理时，资源重载仍必须被拒绝。 */
    @Test
    fun `should wait for cancelled run cleanup before reload`() = runTest(dispatcher) {
        val profile = profile()
        val cleanupGate = CompletableDeferred<Unit>()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = kotlinx.coroutines.flow.flow {
                emit(AgentStreamEvent.Started)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupGate.await()
                    }
                }
            }
        }
        var reloadCount = 0
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(profiles = listOf(profile), activeProfile = profile),
            projectPath = "E:\\reload",
            resourceReloader = {
                reloadCount += 1
                AgentResourceSnapshot.empty().copy(version = 1)
            },
        )

        state.updateDraft("long")
        state.sendDraft()
        advanceUntilIdle()
        state.cancelActiveRun()

        assertFalse(state.canReloadAgentResources)
        assertFalse(state.reloadAgentResources())
        cleanupGate.complete(Unit)
        advanceUntilIdle()
        assertTrue(state.reloadAgentResources())
        assertEquals(1, reloadCount)
    }

    /** 未选择工作区时也应重载用户级资源，保证 `~/.agents/skills` 无需先打开项目。 */
    @Test
    fun `should reload user resources without an active workspace`() = runTest(dispatcher) {
        var reloadedWorkspace: String? = null
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = emptyList(), activeProfile = null),
            resourceReloader = { workspacePath ->
                reloadedWorkspace = workspacePath
                AgentResourceSnapshot.empty()
            },
        )

        assertTrue(state.reloadAgentResources())
        assertEquals("", reloadedWorkspace)
    }

}
