package com.agent.shared.agent.prompt

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
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
     * 配置了推理档位的 Responses 模型必须声明 Thinking capability，Koog 才会在工具调用续传时
     * 将已收集的 reasoning item 编码回 `input`。
     */
    @Test
    fun `should preserve reasoning items for any responses profile with configured efforts`() {
        val model = buildLlmModel(
            responsesProfile().copy(reasoningEfforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MAX)),
        )

        assertTrue(model.supports(LLMCapability.Thinking))
    }

    /**
     * 未声明 reasoningEfforts 的模型不应被推断为思考模型，避免向普通模型续传不受支持的 item。
     */
    @Test
    fun `should not infer thinking capability without configured efforts`() {
        val model = buildLlmModel(responsesProfile())

        assertTrue(!model.supports(LLMCapability.Thinking))
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
     * 未带版本路径的 baseUrl 是服务端声明的精确前缀，Responses 路径不得臆测追加 v1。
     */
    @Test
    fun `should preserve root base url for responses endpoint`() {
        val settings = buildOpenAIClientSettings(
            responsesProfile().copy(baseUrl = "https://api.deepseek.com"),
        )

        assertEquals("https://api.deepseek.com", settings.baseUrl)
        assertEquals("responses", settings.responsesAPIPath)
    }

    /**
     * OpenAI chat/completions 路径应把推理强度写入 legacy 请求字段。
     */
    @Test
    fun `should build openai chat params with legacy reasoning effort`() {
        val params = buildPromptParams(
            config = chatCompletionsProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
        )

        val openAIParams = params as? OpenAIChatParams
        assertEquals(null, openAIParams?.reasoningEffort)
        assertEquals("\"high\"", openAIParams?.additionalProperties?.get("reasoning_effort").toString())
    }

    /**
     * OpenAI responses 路径应把推理强度写入统一 reasoning 请求字段。
     */
    @Test
    @Suppress("UnstableApiUsage")
    fun `should build openai responses params with unified reasoning config`() {
        val params = buildPromptParams(
            config = responsesProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
        )

        val openAIParams = params as? OpenAIResponsesParams
        assertEquals(null, openAIParams?.reasoning)
        assertEquals("{\"effort\":\"high\"}", openAIParams?.additionalProperties?.get("reasoning").toString())
    }

    /**
     * MAX 档位不能被 Koog 静默降级，必须通过 legacy 请求字段原样发送。
     */
    @Test
    fun `should pass max reasoning effort without koog downgrade`() {
        val params = buildPromptParams(
            config = chatCompletionsProfile(),
            reasoningEffort = ReasoningEffort.MAX,
        )

        val openAIParams = params as? OpenAIChatParams
        assertEquals(null, openAIParams?.reasoningEffort)
        assertEquals("\"max\"", openAIParams?.additionalProperties?.get("reasoning_effort").toString())
    }

    /**
     * Responses 端点必须自动通过统一 reasoning 字段原样透传。
     */
    @Test
    @Suppress("UnstableApiUsage")
    fun `should pass xhigh reasoning effort through responses params`() {
        val params = buildPromptParams(
            config = responsesProfile(),
            reasoningEffort = ReasoningEffort.XHIGH,
        ) as OpenAIResponsesParams

        assertEquals(null, params.reasoning)
        assertEquals("{\"effort\":\"xhigh\"}", params.additionalProperties?.get("reasoning").toString())
    }

    /**
     * Responses 服务可通过 `reasoning.effort=none` 显式关闭思考模式，不能在客户端丢失该 wire value。
     */
    @Test
    @Suppress("UnstableApiUsage")
    fun `should pass none reasoning effort through responses params`() {
        val params = buildPromptParams(
            config = responsesProfile(),
            reasoningEffort = ReasoningEffort.NONE,
        ) as OpenAIResponsesParams

        assertEquals(null, params.reasoning)
        assertEquals("{\"effort\":\"none\"}", params.additionalProperties?.get("reasoning").toString())
    }

    /**
     * Chat Completions 端点必须自动通过 legacy reasoning_effort 字段原样透传。
     */
    @Test
    fun `should pass max reasoning effort through chat completions params`() {
        val params = buildPromptParams(
            config = chatCompletionsProfile(),
            reasoningEffort = ReasoningEffort.MAX,
        ) as OpenAIChatParams

        assertEquals(null, params.reasoningEffort)
        assertEquals("\"max\"", params.additionalProperties?.get("reasoning_effort").toString())
    }

    /**
     * 直连 Anthropic 的推理档位必须自动写入其原生 output_config.effort 字段。
     */
    @Test
    fun `should pass max reasoning effort through anthropic output config params`() {
        val params = buildPromptParams(
            config = anthropicProfile(),
            reasoningEffort = ReasoningEffort.MAX,
        ) as AnthropicParams

        assertEquals("{\"effort\":\"max\"}", params.additionalProperties?.get("output_config").toString())
    }

    private fun deepSeekProfile(): ConfigProfile = ConfigProfile(
        id = "deepseek",
        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "key",
        model = "deepseek-v4-flash",
        enabled = true,
        layer = ConfigLayer.PROJECT,
        reasoningEfforts = listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX),
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

    /** 创建自定义 Chat Completions 测试 profile。 */
    private fun chatCompletionsProfile(): ConfigProfile = ConfigProfile(
        id = "gateway-chat-completions",
        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://gateway.example/v1",
        apiKey = "key",
        model = "openai/gpt-5.6-luna",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )

    /** 创建直连 Anthropic 测试 profile。 */
    private fun anthropicProfile(): ConfigProfile = ConfigProfile(
        id = "anthropic-direct",
        providerType = ProviderType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        apiKey = "key",
        model = "claude-sonnet-4-6",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
