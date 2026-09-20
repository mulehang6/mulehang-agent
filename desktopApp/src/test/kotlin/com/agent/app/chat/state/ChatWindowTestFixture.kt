package com.agent.app.chat.state

import com.agent.shared.agent.api.*
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 聊天状态测试共享受控的主线程与资源调度器。 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ChatWindowTestFixture {
    protected val dispatcher = StandardTestDispatcher()

    /**
     * 将 Main dispatcher 替换为测试 dispatcher。
     */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /**
     * 恢复 Main dispatcher，避免污染其他测试。
     */
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun profile(
        model: String = "gpt-4.1",
        limit: ModelLimit? = null,
    ): ConfigProfile = ConfigProfile(
        id = if (model.startsWith("deepseek", ignoreCase = true)) {
            "deepseek:$model"
        } else {
            "openai:$model"
        },
        providerId = if (model.startsWith("deepseek", ignoreCase = true)) "deepseek" else "openai",
        providerLabel = if (model.startsWith("deepseek", ignoreCase = true)) "DeepSeek" else "OpenAI",
        providerType = if (model.startsWith("deepseek", ignoreCase = true)) {
            ProviderType.OPENAI_CHAT_COMPLETIONS
        } else {
            ProviderType.OPENAI_RESPONSES
        },
        baseUrl = if (model.startsWith("deepseek", ignoreCase = true)) {
            "https://api.deepseek.com/v1"
        } else {
            "https://api.openai.com/v1"
        },
        apiKey = "key",
        model = model,
        enabled = true,
        layer = ConfigLayer.PROJECT,
        limit = limit,
    )

    protected fun idleGateway(): AgentGateway = object : AgentGateway {
        override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
            AgentStreamEvent.Started,
            AgentStreamEvent.Completed(""),
        )
    }

    protected fun streamingGateway(): AgentGateway = object : AgentGateway {
        override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
            AgentStreamEvent.Started,
            AgentStreamEvent.TextDelta("hel"),
            AgentStreamEvent.TextDelta("lo"),
            AgentStreamEvent.Completed("hello"),
        )
    }
}
