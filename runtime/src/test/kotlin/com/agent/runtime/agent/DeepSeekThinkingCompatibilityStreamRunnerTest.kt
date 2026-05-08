package com.agent.runtime.agent

import ai.koog.prompt.streaming.StreamFrame
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 DeepSeek thinking SSE 兼容层能把原始 `reasoning_content`/`content` 增量翻译为标准 StreamFrame。
 */
class DeepSeekThinkingCompatibilityStreamRunnerTest {

    @Test
    fun `should translate DeepSeek reasoning_content stream payloads into Koog frames`() = runTest {
        val runner = DeepSeekThinkingCompatibilityStreamRunner(
            transport = object : DeepSeekThinkingTransport {
                override fun openEventStream(binding: ProviderBinding, prompt: ai.koog.prompt.dsl.Prompt) = flowOf(
                    """{"choices":[{"delta":{"reasoning_content":"step "}}]}""",
                    """{"choices":[{"delta":{"reasoning_content":"one"}}]}""",
                    """{"choices":[{"delta":{"content":"done"}}]}""",
                    "[DONE]",
                )
            },
        )

        val frames = runner.stream(
            binding = deepSeekBinding(),
            prompt = buildRuntimePrompt(userPrompt = "hello", binding = deepSeekBinding()),
        ).toList()

        assertEquals(
            listOf(
                StreamFrame.ReasoningDelta(text = "step "),
                StreamFrame.ReasoningDelta(text = "one"),
                StreamFrame.TextDelta("done"),
            ),
            frames,
        )
    }

    @Test
    fun `should only enable compatibility runner for DeepSeek thinking requests without tools`() {
        val runner = DeepSeekThinkingCompatibilityStreamRunner(
            transport = object : DeepSeekThinkingTransport {
                override fun openEventStream(binding: ProviderBinding, prompt: ai.koog.prompt.dsl.Prompt) = flowOf("[DONE]")
            },
        )

        assertEquals(true, runner.supports(binding = deepSeekBinding(), hasTools = false))
        assertEquals(false, runner.supports(binding = deepSeekBinding(enableThinking = false), hasTools = false))
        assertEquals(false, runner.supports(binding = deepSeekBinding(), hasTools = true))
        assertEquals(false, runner.supports(binding = openAiBinding(), hasTools = false))
    }

    private fun deepSeekBinding(enableThinking: Boolean = true) = ProviderBinding(
        providerId = "DeepSeek",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.deepseek.com",
        apiKey = "test-key",
        modelId = "deepseek-v4-flash",
        enableThinking = enableThinking,
    )

    private fun openAiBinding() = ProviderBinding(
        providerId = "OpenAI",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.openai.com",
        apiKey = "test-key",
        modelId = "gpt-4.1",
        enableThinking = true,
    )
}
