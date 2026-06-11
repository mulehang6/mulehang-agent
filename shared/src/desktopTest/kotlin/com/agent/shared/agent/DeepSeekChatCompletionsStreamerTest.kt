package com.agent.shared.agent

import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 验证 DeepSeek chat-completions 专用流式适配。
 */
class DeepSeekChatCompletionsStreamerTest {

    /**
     * 请求体应显式开启 thinking，并带上 reasoning effort。
     */
    @Test
    fun `should enable thinking mode in deepseek request`() = runTest {
        var capturedRequest: DeepSeekChatCompletionRequest? = null
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { request, _ ->
                capturedRequest = request
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-1",
                        created = 1L,
                        model = "deepseek-v4-flash",
                        choices = emptyList(),
                        usage = DeepSeekUsage(totalTokens = 4, promptTokens = 1, completionTokens = 3),
                    ),
                )
            },
        )

        streamer.stream(prompt = "你好", config = deepSeekProfile()).toList()
        val request = requireNotNull(capturedRequest)

        assertEquals("deepseek-v4-flash", request.model)
        assertEquals(true, request.stream)
        assertEquals(DeepSeekThinking(type = "enabled"), request.thinking)
        assertEquals("high", request.reasoningEffort)
        assertEquals(listOf(DeepSeekChatMessage(role = "user", content = "你好")), request.messages)
    }

    /**
     * reasoning_content 与 content 应分别映射成 reasoning/text frame。
     */
    @Test
    fun `should map reasoning content chunks into stream frames`() = runTest {
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { _, _ ->
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-1",
                        created = 1L,
                        model = "deepseek-v4-flash",
                        choices = listOf(
                            DeepSeekChatChoice(
                                index = 0,
                                delta = DeepSeekChatDelta(reasoningContent = "先判断问题"),
                            ),
                        ),
                    ),
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-1",
                        created = 2L,
                        model = "deepseek-v4-flash",
                        choices = listOf(
                            DeepSeekChatChoice(
                                index = 0,
                                delta = DeepSeekChatDelta(content = "这是答案"),
                                finishReason = "stop",
                            ),
                        ),
                        usage = DeepSeekUsage(totalTokens = 12, promptTokens = 4, completionTokens = 8),
                    ),
                )
            },
        )

        val frames = streamer.stream(prompt = "你好", config = deepSeekProfile()).toList()

        assertEquals(3, frames.size)
        assertEquals(
            StreamFrame.ReasoningDelta(
                text = "先判断问题",
                index = 0,
            ),
            frames[0],
        )
        assertEquals(StreamFrame.TextDelta(text = "这是答案", index = 0), frames[1])
        val end = assertIs<StreamFrame.End>(frames[2])
        assertEquals("stop", end.finishReason)
        assertEquals(12, end.metaInfo.totalTokensCount)
        assertEquals(4, end.metaInfo.inputTokensCount)
        assertEquals(8, end.metaInfo.outputTokensCount)
    }

    private fun deepSeekProfile(): ConfigProfile = ConfigProfile(
        id = "deepseek",
        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "key",
        model = "deepseek-v4-flash",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
