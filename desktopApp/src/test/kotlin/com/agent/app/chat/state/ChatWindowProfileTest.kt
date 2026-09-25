package com.agent.app.chat.state

import com.agent.app.chat.media.SessionMediaStore
import com.agent.app.chat.media.StoredSessionImage
import com.agent.app.platform.ClipboardPngImage
import com.agent.shared.agent.api.*
import com.agent.shared.chat.model.*
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.settings.model.ModelLimit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*
import java.nio.file.Path

/** 验证ChatWindowProfileTest的状态转换。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowProfileTest : ChatWindowTestFixture() {
    /**
     * snapshot 刷新后如果 profile context limit 变化，会话占比应立即重算。
     */
    @Test
    fun `should recalculate context usage when session snapshot reloads current profile`() = runTest(dispatcher) {
        val initialProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(initialProfile),
                activeProfile = initialProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("a".repeat(80))
        advanceUntilIdle()
        state.updateSessionSnapshot(
            AppSessionSnapshot(
                profiles = listOf(
                    initialProfile.copy(limit = ModelLimit(context = 200, output = 20)),
                ),
                activeProfile = initialProfile.copy(limit = ModelLimit(context = 200, output = 20)),
            ),
        )

        assertEquals(
            estimateContextUsage(state.ui.activeConversation.items, 0, 200),
            state.ui.activeConversation.contextUsageFraction,
        )
    }

    /** 不支持视觉输入的 profile 必须在发送前失败，不能悄悄丢掉用户粘贴的图片。 */
    @Test
    fun `should reject pasted image before sending to profile without vision capability`() = runTest(dispatcher) {
        val mediaStore = object : SessionMediaStore {
            override fun storePng(conversationId: String, bytes: ByteArray): StoredSessionImage = StoredSessionImage(
                mediaId = "image-1",
                path = Path.of("C:\\session-media\\image-1.png"),
            )
        }
        val textOnlyProfile = profile(model = "text-only").copy(supportsVision = false)
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(textOnlyProfile), activeProfile = textOnlyProfile),
            projectPath = "E:\\abc\\def",
            sessionMediaStore = mediaStore,
        )

        assertEquals(null, state.addClipboardImage(ClipboardPngImage(byteArrayOf(1), 1, 1)))
        state.sendDraft()

        assertTrue(state.state.executionState is ExecutionState.Failed)
        assertTrue(state.errorMessage.orEmpty().contains("不支持图片输入"))
        assertNull(state.ui.activeConversationOrNull)
    }

    /**
     * 当前会话的 thinking level 应进入发送请求，避免只停留在 hover 展示层。
     */
    @Test
    fun `should send active conversation reasoning effort with draft`() = runTest(dispatcher) {
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
                profiles = listOf(profile(model = "deepseek-r1")),
                activeProfile = profile(model = "deepseek-r1"),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.updateReasoningEffort(ReasoningEffort.HIGH)
        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        assertEquals(ReasoningEffort.HIGH, state.ui.activeConversation.reasoningEffort)
        assertEquals(ReasoningEffort.HIGH, capturedRequest?.reasoningEffort)
    }

    /**
     * 当前会话默认档位不在模型 variants 内时，应使用 profile 能力默认档位发送。
     */
    @Test
    fun `should send profile default reasoning effort when conversation effort is unsupported`() = runTest(dispatcher) {
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
                profiles = listOf(profile(model = "deepseek-v4-flash")),
                activeProfile = profile(model = "deepseek-v4-flash"),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        assertEquals(ReasoningEffort.HIGH, capturedRequest?.reasoningEffort)
    }

    /**
     * DeepSeek 仅支持 high/max 时，初始会话应直接显示其默认的 high 档位。
     */
    @Test
    fun `should initialize deepseek conversation with supported default reasoning effort`() = runTest(dispatcher) {
        val deepSeekProfile = profile(model = "deepseek-v4-flash")
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(deepSeekProfile),
                activeProfile = deepSeekProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        assertEquals(ReasoningEffort.HIGH, state.ui.newReasoningEffort)
    }

    /**
     * 配置在初始空快照之后加载时，已有会话也应采用 DeepSeek 的默认档位。
     */
    @Test
    fun `should normalize existing conversation reasoning effort when deepseek snapshot loads`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = emptyList(), activeProfile = null),
            projectPath = "E:\\abc\\def",
        )
        val deepSeekProfile = profile(model = "deepseek-v4-flash")

        state.updateSessionSnapshot(
            AppSessionSnapshot(
                profiles = listOf(deepSeekProfile),
                activeProfile = deepSeekProfile,
            ),
        )

        assertEquals(ReasoningEffort.HIGH, state.ui.newReasoningEffort)
    }

    /**
     * 切换到不支持当前档位的模型时，应立即回退到该模型默认档位。
     */
    @Test
    fun `should reset unsupported reasoning effort when switching to deepseek`() = runTest(dispatcher) {
        val openAiProfile = profile(model = "gpt-4.1")
        val deepSeekProfile = profile(model = "deepseek-v4-flash")
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(openAiProfile, deepSeekProfile),
                activeProfile = openAiProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.selectProfile(deepSeekProfile.id)

        assertEquals(ReasoningEffort.HIGH, state.ui.newReasoningEffort)
    }

    /**
     * 当前模型不支持 thinking 时，发送请求不应携带 reasoning effort。
     */
    @Test
    fun `should omit reasoning effort for unsupported active profile`() = runTest(dispatcher) {
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
        val unsupportedProfile = profile(model = "claude-sonnet-4")
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(unsupportedProfile),
                activeProfile = unsupportedProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.updateReasoningEffort(ReasoningEffort.HIGH)
        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        assertEquals(null, capturedRequest?.reasoningEffort)
    }

    /**
     * 上下文圆环应使用当前 profile 的 context limit 作为分母，而不是固定展示占比。
     */
    @Test
    fun `should estimate context usage from active profile context limit`() = runTest(dispatcher) {
        val limitedProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(limitedProfile),
                activeProfile = limitedProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("a".repeat(80))
        advanceUntilIdle()

        assertEquals(
            estimateContextUsage(state.ui.activeConversation.items, 0, 100),
            state.activeContextUsageFraction,
        )
    }

    /**
     * 空会话的上下文占用应按 profile limit 计入固定系统提示词。
     */
    @Test
    fun `should initialize context usage from active profile context limit`() = runTest(dispatcher) {
        val limitedProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(limitedProfile),
                activeProfile = limitedProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        assertEquals(
            estimateContextUsage(emptyList(), 0, 100),
            state.activeContextUsageFraction,
        )
    }

    /**
     * 切换 profile 后应使用新模型的 context limit 重算已有会话占比。
     */
    @Test
    fun `should recalculate context usage when active profile changes`() = runTest(dispatcher) {
        val smallContextProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val largeContextProfile = profile(
            model = "deepseek-v4-flash",
            limit = ModelLimit(context = 200, output = 20),
        )
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(smallContextProfile, largeContextProfile),
                activeProfile = smallContextProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("a".repeat(80))
        advanceUntilIdle()
        state.selectProfile(largeContextProfile.id)

        assertEquals(
            estimateContextUsage(state.ui.activeConversation.items, 0, 200),
            state.ui.activeConversation.contextUsageFraction,
        )
    }

}
