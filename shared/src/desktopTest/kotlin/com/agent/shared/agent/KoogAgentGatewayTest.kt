package com.agent.shared.agent

import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证 Koog gateway 的本地错误分支。
 */
class KoogAgentGatewayTest {

    /**
     * 当前阶段不支持的 provider 应转换为 UI 可消费的失败事件。
     */
    @Test
    fun `should emit failed event for unsupported provider`() = runTest {
        val events = KoogAgentGateway().run(
            prompt = "hello",
            config = ConfigProfile(
                id = "google-main",
                providerType = ProviderType.GOOGLE,
                baseUrl = "https://generativelanguage.googleapis.com",
                apiKey = "key",
                model = "gemini-2.5-pro",
                enabled = true,
                layer = ConfigLayer.PROJECT,
            ),
        ).toList()

        assertEquals(2, events.size)
        assertEquals(AgentStreamEvent.Started, events.first())
        val failed = assertIs<AgentStreamEvent.Failed>(events.last())
        assertTrue(failed.reason.contains("暂不支持"))
    }
}
