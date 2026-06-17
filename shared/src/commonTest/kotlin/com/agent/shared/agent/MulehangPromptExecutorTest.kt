package com.agent.shared.agent

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort as KoogReasoningEffort
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证运行时 profile 到 Koog 配置的映射。
 */
class MulehangPromptExecutorTest {

    /**
     * OpenAI-compatible profile 应把 JSON 中的 model 映射为 Koog 模型 id。
     */
    @Test
    fun `should build koog model from configured profile model`() {
        val model = buildLlmModel(deepSeekProfile())

        assertEquals("deepseek-v4-flash", model.id)
        assertEquals(LLMProvider.OpenAI, model.provider)
        assertTrue(model.supports(LLMCapability.Completion))
        assertTrue(model.supports(LLMCapability.Thinking))
        assertTrue(model.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    /**
     * baseUrl 已包含 v1 时不应再追加一次 v1。
     */
    @Test
    fun `should build openai settings from configured base url`() {
        val settings = buildOpenAIClientSettings(deepSeekProfile())

        assertEquals("https://api.deepseek.com/v1", settings.baseUrl)
        assertEquals("chat/completions", settings.chatCompletionsPath)
    }

    /**
     * OpenAI chat/completions 路径应把推理强度映射到 Koog 专用参数。
     */
    @Test
    fun `should build openai chat params with reasoning effort`() {
        val params = buildPromptParams(
            config = deepSeekProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
        )

        val openAIParams = params as? OpenAIChatParams
        assertEquals(KoogReasoningEffort.HIGH, openAIParams?.reasoningEffort)
    }

    /**
     * OpenAI responses 路径应把推理强度写入 reasoning config。
     */
    @Test
    @Suppress("UnstableApiUsage")
    fun `should build openai responses params with reasoning config`() {
        val params = buildPromptParams(
            config = responsesProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
        )

        val openAIParams = params as? OpenAIResponsesParams
        assertEquals(KoogReasoningEffort.HIGH, openAIParams?.reasoning?.effort)
        assertEquals(null, openAIParams?.reasoning?.summary)
    }

    /**
     * MAX 档位在 Koog OpenAI 兼容参数中应回退到最高支持值。
     */
    @Test
    fun `should map max reasoning effort to highest koog level`() {
        val params = buildPromptParams(
            config = deepSeekProfile(),
            reasoningEffort = ReasoningEffort.MAX,
        )

        val openAIParams = params as? OpenAIChatParams
        assertEquals(KoogReasoningEffort.HIGH, openAIParams?.reasoningEffort)
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

    private fun responsesProfile(): ConfigProfile = ConfigProfile(
        id = "openai-responses",
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "key",
        model = "gpt-5-mini",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
