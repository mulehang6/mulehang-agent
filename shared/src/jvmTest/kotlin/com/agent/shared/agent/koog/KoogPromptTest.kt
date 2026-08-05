package com.agent.shared.agent.koog

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlin.test.Test
import ai.koog.prompt.message.Message
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 Koog agent prompt 的桌面侧构造规则。
 */
class KoogPromptTest {

    /**
     * agent 基础 prompt 应包含统一系统约束，但不应提前写入用户正文。
     */
    @Test
    fun `should build agent prompt without duplicating user message`() {
        val prompt = buildAgentPrompt(
            profile = deepSeekProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
        )

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages.single() is Message.System)
        val params = prompt.params as? OpenAIChatParams
        assertEquals(null, params?.reasoningEffort)
        assertEquals("\"high\"", params?.additionalProperties?.get("reasoning_effort").toString())
    }

    /**
     * 标题 prompt 也只应包含系统约束，且不能提前写入首条用户消息。
     */
    @Test
    fun `should build conversation title prompt without duplicating user message`() {
        val prompt = buildConversationTitlePrompt(profile = deepSeekProfile())

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages.single() is Message.System)
    }

    /**
     * 标题系统约束必须明确禁止工具、Markdown 与引号，避免污染侧栏展示。
     */
    @Test
    fun `should forbid tools markdown and quotes in title system prompt`() {
        val prompt = conversationTitleSystemPrompt()

        assertTrue(prompt.contains("不能调用工具"))
        assertTrue(prompt.contains("不要使用引号、Markdown"))
    }

    /**
     * Anthropic 兼容 profile 的 client settings 应保留 baseUrl，并把运行时构造的
     * LLModel 映射回配置的模型 ID，绕开 Koog 内置 Claude 白名单。
     */
    @Test
    fun `should map anthropic compatible model to configured model id`() {
        val profile = anthropicCompatibleProfile()
        val settings = buildAnthropicClientSettings(profile)

        assertEquals(profile.baseUrl, settings.baseUrl)
        assertEquals(profile.model, settings.modelVersionsMap[buildLlmModel(profile)])
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

    private fun anthropicCompatibleProfile(): ConfigProfile = ConfigProfile(
        id = "deepseek-anthropic",
        providerType = ProviderType.ANTHROPIC,
        baseUrl = "https://api.deepseek.com/anthropic",
        apiKey = "key",
        model = "deepseek-v4-flash",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
