package com.agent.shared.agent.koog

import com.agent.shared.agent.api.ConversationTitleRequest
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * 验证标题生成器与底层 Koog 执行完全解耦，且异常/结果不被吞掉或篡改。
 */
class KoogConversationTitleGeneratorTest {

    /**
     * 生成结果应原样透传底层 runner 的返回值，不做额外加工。
     */
    @Test
    fun `should delegate to injected runner and return its result`() = runTest {
        var capturedRequest: ConversationTitleRequest? = null
        val generator = KoogConversationTitleGenerator(
            agentRunner = { request ->
                capturedRequest = request
                "生成的标题"
            },
        )
        val request = ConversationTitleRequest(
            firstUserMessage = "帮我梳理这个项目的实时工具输出方案",
            profile = openAiProfile(),
        )

        val title = generator.generate(request)

        assertEquals("生成的标题", title)
        assertSame(request, capturedRequest)
    }

    /**
     * runner 失败时异常应原样向调用方传播，由调用方决定回退文案，而不是在此吞掉。
     */
    @Test
    fun `should propagate runner failure without swallowing it`() = runTest {
        val generator = KoogConversationTitleGenerator(
            agentRunner = { error("invalid api key") },
        )

        val error = assertFailsWith<IllegalStateException> {
            generator.generate(
                ConversationTitleRequest(
                    firstUserMessage = "hello",
                    profile = openAiProfile(),
                ),
            )
        }
        assertEquals("invalid api key", error.message)
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
