package com.agent.app.chat.state

import com.agent.shared.agent.api.*
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 验证ChatWindowTitleTest的状态转换。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowTitleTest : ChatWindowTestFixture() {
    /** 显式重新生成使用 head 路径首条用户消息，且不会把任务切成运行态。 */
    @Test
    fun `should regenerate title without changing execution state`() = runTest(dispatcher) {
        val regeneratedTitle = CompletableDeferred<String>()
        var callCount = 0
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\abc\\def",
            conversationTitleGenerator = object : ConversationTitleGenerator {
                override suspend fun generate(request: ConversationTitleRequest): String {
                    callCount += 1
                    return if (callCount == 1) "初始标题" else regeneratedTitle.await()
                }
            },
        )
        state.updateDraft("首条用户消息")
        state.sendDraft()
        advanceUntilIdle()

        assertTrue(state.regenerateConversationTitle(state.ui.activeConversationId))
        assertEquals("初始标题", state.ui.activeConversation.title)
        assertTrue(state.ui.activeConversation.titleRegenerationInProgress)
        assertEquals(com.agent.shared.chat.model.ExecutionState.Idle, state.ui.activeConversation.executionState)

        regeneratedTitle.complete("重新生成标题")
        advanceUntilIdle()

        assertEquals("重新生成标题", state.ui.activeConversation.title)
        assertFalse(state.ui.activeConversation.titleRegenerationInProgress)
        assertEquals(com.agent.shared.chat.model.ExecutionState.Idle, state.ui.activeConversation.executionState)
    }

    /** 显式重新生成失败时保留稳定原标题并清除独立生成状态。 */
    @Test
    fun `failed title regeneration keeps previous title`() = runTest(dispatcher) {
        var callCount = 0
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\abc\\def",
            conversationTitleGenerator = object : ConversationTitleGenerator {
                override suspend fun generate(request: ConversationTitleRequest): String {
                    callCount += 1
                    if (callCount > 1) error("标题服务不可用")
                    return "稳定标题"
                }
            },
        )
        state.updateDraft("首条用户消息")
        state.sendDraft()
        advanceUntilIdle()

        state.regenerateConversationTitle(state.ui.activeConversationId)
        advanceUntilIdle()

        assertEquals("稳定标题", state.ui.activeConversation.title)
        assertFalse(state.ui.activeConversation.titleRegenerationInProgress)
        assertEquals(ConversationTitleState.GENERATED, state.ui.activeConversation.titleState)
    }

    /**
     * 对话标题应从首条消息生成一个短标题，去掉多行和冗余空白。
     */
    @Test
    fun `should build compact conversation title from first user prompt`() {
        assertEquals(
            "重构 ChatWindowState",
            buildConversationTitle("  重构 ChatWindowState\n避免重复的新对话  "),
        )
        assertEquals("新建对话", buildConversationTitle("   "))
    }

    /**
     * 首条消息应立即保留本地回退标题，同时独立异步请求 AI 标题。
     */
    @Test
    fun `should generate the first conversation title asynchronously`() = runTest(dispatcher) {
        val generatedTitle = CompletableDeferred<String>()
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            conversationTitleGenerator = object : ConversationTitleGenerator {
                override suspend fun generate(request: ConversationTitleRequest): String = generatedTitle.await()
            },
        )

        state.updateDraft("请帮我梳理这个项目的实时工具输出方案")
        state.sendDraft()

        assertEquals(ConversationTitleState.GENERATING, state.ui.activeConversation.titleState)
        assertEquals("请帮我梳理这个项目的实时工具输出方案", state.ui.activeConversation.title)

        generatedTitle.complete("**实时工具输出方案**")
        advanceUntilIdle()

        assertEquals(ConversationTitleState.GENERATED, state.ui.activeConversation.titleState)
        assertEquals("实时工具输出方案", state.ui.activeConversation.title)
    }

    /**
     * 用户手动重命名后，迟到的 AI 标题结果不能覆盖用户输入。
     */
    @Test
    fun `should not overwrite a manually renamed title with a stale generated result`() = runTest(dispatcher) {
        val generatedTitle = CompletableDeferred<String>()
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            conversationTitleGenerator = object : ConversationTitleGenerator {
                override suspend fun generate(request: ConversationTitleRequest): String = generatedTitle.await()
            },
        )

        state.updateDraft("分析聊天界面的过渡动画")
        state.sendDraft()
        state.renameConversation(state.ui.activeConversationId, "我手动命名的会话")
        generatedTitle.complete("AI 生成的旧标题")
        advanceUntilIdle()

        assertEquals("我手动命名的会话", state.ui.activeConversation.title)
        assertEquals(ConversationTitleState.GENERATED, state.ui.activeConversation.titleState)
    }

    /**
     * 标题生成失败时应结束 loading 状态，但保留发送时已写入的本地回退标题。
     */
    @Test
    fun `should mark title state failed and keep local fallback when generation throws`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            conversationTitleGenerator = object : ConversationTitleGenerator {
                override suspend fun generate(request: ConversationTitleRequest): String =
                    error("network timeout")
            },
        )

        state.updateDraft("梳理侧栏动效计划")
        state.sendDraft()
        advanceUntilIdle()

        assertEquals(ConversationTitleState.FAILED, state.ui.activeConversation.titleState)
        assertEquals("梳理侧栏动效计划", state.ui.activeConversation.title)
    }

    /**
     * 删除持有在途标题生成的会话后，迟到结果不应影响替换出的新会话。
     */
    @Test
    fun `should ignore stale title result after deleting its owning conversation`() = runTest(dispatcher) {
        val generatedTitle = CompletableDeferred<String>()
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            conversationTitleGenerator = object : ConversationTitleGenerator {
                override suspend fun generate(request: ConversationTitleRequest): String = generatedTitle.await()
            },
        )

        state.updateDraft("清理待办标题任务")
        state.sendDraft()
        val deletedConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\def")
        state.deleteConversation(deletedConversationId)
        val replacementConversationId = state.ui.activeConversationId

        generatedTitle.complete("旧会话的新标题")
        advanceUntilIdle()

        assertNotEquals(deletedConversationId, replacementConversationId)
        assertEquals("", replacementConversationId)
        assertNull(state.ui.activeConversationOrNull)
        assertTrue(state.ui.tasks.isEmpty())
    }

}
