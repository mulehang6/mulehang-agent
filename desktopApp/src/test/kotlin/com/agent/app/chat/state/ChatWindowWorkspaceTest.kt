package com.agent.app.chat.state

import com.agent.shared.agent.api.*
import com.agent.shared.chat.model.*
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.tool.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 验证ChatWindowWorkspaceTest的状态转换。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowWorkspaceTest : ChatWindowTestFixture() {
    /**
     * 首次启动且没有选择工作区时，不应创建占位工作区。
     */
    @Test
    fun `should start without workspace when project path is blank`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
        )

        assertEquals(emptyList(), state.ui.workspaceGroups)
        assertNull(state.ui.activeConversationOrNull)
        assertNull(state.errorMessage)
    }

    /**
     * 新建对话时如果当前会话仍是空白默认会话，应直接复用它而不是留下两个“新对话”。
     */
    @Test
    fun `should reuse current empty conversation when creating a new workspace conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        val originalConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\def")

        assertEquals("def", state.ui.workspaceGroups.single().label)
        assertEquals(1, state.ui.workspaceGroups.single().conversations.size)
        assertEquals(originalConversationId, state.ui.activeConversationId)
        assertEquals(emptyList(), state.ui.activeConversation.attachments)
    }

    /**
     * 当前会话已有内容时，新建对话应保留历史线程并切换到新的空线程。
     */
    @Test
    fun `should keep existing non-empty conversation when creating a new one`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        val originalConversationId = state.ui.activeConversationId

        state.send("hello")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")

        assertEquals(2, state.ui.workspaceGroups.single().conversations.size)
        assertNotEquals(originalConversationId, state.ui.activeConversationId)
        assertEquals(2, state.findConversation(originalConversationId).items.size)
        assertEquals(emptyList(), state.ui.activeConversation.items)
    }

    /**
     * 历史会话旁已有同工作区空白会话时，新建操作应直接切换到该会话。
     */
    @Test
    fun `should reuse existing empty conversation when creating from a historical conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        val historicalConversationId = state.ui.activeConversationId
        state.send("hello")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")
        val emptyConversationId = state.ui.activeConversationId

        state.selectConversation(historicalConversationId)
        state.updateDraft("未发送草稿")
        state.createConversationForWorkspace("E:\\abc\\def")

        assertEquals(emptyConversationId, state.ui.activeConversationId)
        assertEquals(2, state.ui.workspaceGroups.single().conversations.size)
        assertEquals("", state.ui.draft)
    }

    /**
     * task-first 侧栏应把仍在进行中的线程和已完成线程拆到两个分组。
     */
    @Test
    fun `should expose running and done task sections from task list`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("first")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")

        val sections = state.ui.taskSections.associateBy { it.group }
        assertEquals(1, sections[ChatTaskGroup.RUNNING]?.tasks?.size)
        assertEquals(1, sections[ChatTaskGroup.DONE]?.tasks?.size)
        assertEquals("新建对话", sections[ChatTaskGroup.RUNNING]?.tasks?.single()?.title)
        assertEquals("first", sections[ChatTaskGroup.DONE]?.tasks?.single()?.title)
    }

    /**
     * 等待用户输入和等待审批仍属于进行中的任务，不应被归入 Done。
     */
    @Test
    fun `should keep waiting tasks in running section`() {
        assertEquals(
            ChatTaskGroup.RUNNING,
            taskGroupFor(
                ChatConversationUiState(
                    id = "waiting-question",
                    title = "Question",
                    workspacePath = "E:\\abc\\def",
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "pending"))),
                    executionState = ExecutionState.WaitingForUserInput,
                ),
            ),
        )
        assertEquals(
            ChatTaskGroup.RUNNING,
            taskGroupFor(
                ChatConversationUiState(
                    id = "waiting-approval",
                    title = "Approval",
                    workspacePath = "E:\\abc\\def",
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "pending"))),
                    executionState = ExecutionState.WaitingForApproval,
                ),
            ),
        )
    }

    /**
     * 空线程但执行已失败时应归入 Done，而非 Running。
     * 复现：sendDraft 的并发保护会在无 item 时直接设为 Failed。
     */
    @Test
    fun `should classify empty failed conversation as done`() {
        assertEquals(
            ChatTaskGroup.DONE,
            taskGroupFor(
                ChatConversationUiState(
                    id = "failed-empty",
                    title = "Failed",
                    workspacePath = "E:\\abc\\def",
                    items = emptyList(),
                    executionState = ExecutionState.Failed(AppError("已有任务在执行", "请等待")),
                ),
            ),
        )
    }

    /**
     * 空线程且空闲时应归入 Running，表示可接收输入。
     */
    @Test
    fun `should classify empty idle conversation as running`() {
        assertEquals(
            ChatTaskGroup.RUNNING,
            taskGroupFor(
                ChatConversationUiState(
                    id = "empty-idle",
                    title = "新对话",
                    workspacePath = "E:\\abc\\def",
                    items = emptyList(),
                    executionState = ExecutionState.Idle,
                ),
            ),
        )
    }

    /**
     * task-first 主导航切换时，应同步更新活动 task 与活动工作区标签。
     */
    @Test
    fun `should switch active task when selecting another conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("seed")
        advanceUntilIdle()
        val firstConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val secondConversationId = state.ui.activeConversationId

        state.selectConversation(firstConversationId)

        assertEquals(firstConversationId, state.ui.activeTaskId)
        assertEquals("def", state.ui.activeWorkspaceLabel)
        state.selectConversation(secondConversationId)
        assertEquals("ghi", state.ui.activeWorkspaceLabel)
    }

    /**
     * 刷新配置快照不应清空已经打开的多个工作区会话。
     */
    @Test
    fun `should keep workspace groups when session snapshot updates`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val activeConversationId = state.ui.activeConversationId

        state.updateSessionSnapshot(
            AppSessionSnapshot(
                profiles = listOf(profile(model = "gpt-4.1-mini")),
                activeProfile = profile(model = "gpt-4.1-mini"),
            ),
        )

        assertEquals(listOf("ghi", "def"), state.ui.workspaceGroups.map { it.label })
        assertEquals(activeConversationId, state.ui.activeConversationId)
        assertEquals("openai:gpt-4.1-mini", state.activeProfile?.id)
    }

    /**
     * 发送动作只能影响当前激活的会话，不应回写到其他线程。
     */
    @Test
    fun `should append messages only to the active conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        val firstConversationId = state.ui.activeConversationId
        state.send("seed")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")
        val secondConversationId = state.ui.activeConversationId

        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        val firstConversation = state.findConversation(firstConversationId)
        val secondConversation = state.findConversation(secondConversationId)
        assertEquals(2, firstConversation.items.size)
        assertEquals(2, secondConversation.items.filterIsInstance<ChatMessageItem>().size)
    }

    /**
     * 当前会话的工作区和权限档位必须进入 agent 运行请求。
     */
    @Test
    fun `should send workspace path and permission preset from active conversation`() = runTest(dispatcher) {
        var capturedRequest: AgentRunRequest? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequest = request
                return flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.Completed("ok"),
                )
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

        state.updatePermission(PermissionPreset.BRAVE)
        state.send("hello")
        advanceUntilIdle()

        assertEquals("E:\\abc\\def", capturedRequest?.workspacePath)
        assertEquals(PermissionPreset.BRAVE, capturedRequest?.permissionPreset)
    }

    /**
     * 侧栏重命名和删除应只影响被选中的对话，并在删除当前项后切换到剩余对话。
     */
    @Test
    fun `should rename and delete a conversation from sidebar actions`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        val renamedConversationId = state.ui.activeConversationId

        state.renameConversation(renamedConversationId, "改名后的任务")
        assertEquals("改名后的任务", state.findConversation(renamedConversationId).title)
        state.createConversationForWorkspace("E:\\abc\\def")
        val survivingConversationId = state.ui.activeConversationId
        state.deleteConversation(renamedConversationId)

        assertEquals(listOf(survivingConversationId), state.ui.tasks.map { it.id })
        assertEquals(survivingConversationId, state.ui.activeConversationId)
    }

    /** 删除当前历史会话时必须复用已有的新建对话，不能再次生成空白占位项。 */
    @Test
    fun `should reuse existing new conversation when deleting active historical conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\abc\\def",
        )
        val historicalId = state.ui.activeConversationId
        state.send("历史会话")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")
        val newConversationId = state.ui.activeConversationId

        state.selectConversation(historicalId)
        state.deleteConversation(historicalId)

        assertEquals(listOf(newConversationId), state.ui.tasks.map { it.id })
        assertEquals(newConversationId, state.ui.activeConversationId)
    }

    /**
     * 重启后加载历史任务时，当前焦点仍应停留在新的空白对话。
     */
    @Test
    fun `should keep a new conversation active when restoring history`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        val newConversationId = state.ui.activeConversationId
        val historicalConversation = newConversation(
            workspacePath = "E:\\abc\\def",
            contextWindow = null,
            reasoningEffort = ReasoningEffort.MEDIUM,
        ).copy(title = "历史任务")

        state.restoreTasks(listOf(historicalConversation))

        assertEquals(newConversationId, state.ui.activeConversationId)
        assertEquals(
            listOf(newConversationId, historicalConversation.id),
            state.ui.tasks.map(ChatConversationUiState::id),
        )
    }

    /**
     * 会话每次状态变更都应刷新最后操作时间戳，侧栏"已完成"分组依赖它排序。
     */
    @Test
    fun `should refresh updated at on conversation mutation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            clock = { 1_000L },
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals(1_000L, state.ui.activeConversation.updatedAt)
    }

    /** 已移动的工作目录必须在进入 Agent 前拦截，并保留用户草稿。 */
    @Test
    fun `should keep draft and avoid agent call when workspace directory is unavailable`() = runTest(dispatcher) {
        var calls = 0
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(object : AgentGateway {
                override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                    calls += 1
                    return flowOf(AgentStreamEvent.Completed("unexpected"))
                }
            }),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\moved",
            workspaceDirectoryExists = { false },
        )

        state.updateDraft("保留这条草稿")
        state.sendDraft()

        assertEquals(0, calls)
        assertEquals("保留这条草稿", state.ui.draft)
        assertEquals("工作目录不可用", (state.ui.activeConversation.executionState as ExecutionState.Failed).error.title)
        assertTrue(state.ui.activeConversation.items.isEmpty())
    }

    /** 编辑工作区应将旧组迁入目标目录，并以显式名称覆盖合并后的分组。 */
    @Test
    fun `should merge workspace histories when editing into an existing target`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\old",
            workspaceDirectoryExists = { true },
        )

        state.send("旧目录历史")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\new")
        state.send("目标目录历史")
        advanceUntilIdle()
        assertEquals(null, state.editWorkspace("E:\\old", "合并后的工作区", "E:\\new"))

        assertTrue(state.ui.tasks.all { it.workspacePath == "E:\\new" })
        assertTrue(state.ui.tasks.all { it.workspaceName == "合并后的工作区" })
        assertEquals(1, state.ui.workspaceTaskSections.size)
        assertEquals("E:\\new", state.ui.workspaceTaskSections.single().workspacePath)
    }

    /** 修复卡片迁移到已有目录时采用目标工作区现有名称。 */
    @Test
    fun `should preserve target workspace name when relinking workspace`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\old",
            workspaceDirectoryExists = { true },
        )

        state.createConversationForWorkspace("E:\\new")
        assertEquals(null, state.editWorkspace("E:\\new", "目标工作区", "E:\\new"))
        assertEquals(null, state.relinkWorkspace("E:\\old", "E:\\new"))

        assertTrue(state.ui.tasks.all { it.workspacePath == "E:\\new" })
        assertTrue(state.ui.tasks.all { it.workspaceName == "目标工作区" })
    }

    /** 无有效回退目录时应保留历史并回到欢迎页。 */
    @Test
    fun `should return to welcome state when no available workspace remains`() = runTest(dispatcher) {
        var now = 100L
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\current",
            clock = { now },
            workspaceDirectoryExists = { it == "E:\\current" },
        )
        val currentConversationId = state.ui.activeConversationId
        state.send("保留当前历史")
        advanceUntilIdle()
        now = 200L
        state.createConversationForWorkspace("E:\\unavailable")
        val unavailableConversationId = state.ui.activeConversationId
        state.send("不可用工作区历史")
        advanceUntilIdle()
        state.selectConversation(currentConversationId)
        state.updateDraft("删除前草稿")

        state.disconnectWorkspace("E:\\current")

        assertEquals("", state.ui.activeTaskId)
        assertEquals(null, state.ui.activeConversationOrNull)
        assertEquals("", state.ui.draft)
        assertEquals("", state.findConversation(currentConversationId).workspacePath)
        assertTrue(state.ui.tasks.any { it.id == unavailableConversationId })
    }

}
