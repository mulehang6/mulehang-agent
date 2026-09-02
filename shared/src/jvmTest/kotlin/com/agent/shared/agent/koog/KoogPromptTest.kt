package com.agent.shared.agent.koog

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlin.test.Test
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import java.nio.file.Files

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

    /** 文件 XML 与图片标签/附件必须保留同一个用户输入序列，避免“图1、图2”错位。 */
    @Test
    fun `should map ordered file and image input parts without reordering`() {
        val imagePath = Files.createTempFile("mulehang-image", ".png")
        Files.write(imagePath, byteArrayOf(1, 2, 3))

        val messages = buildConversationMessages(
            history = emptyList(),
            prompt = "ignored fallback",
            inputParts = listOf(
                UserInputPart.Text("先读 "),
                UserInputPart.FileSnapshot("src/App.kt", "fun main() = Unit", "text/x-kotlin"),
                UserInputPart.Text("，再看 "),
                UserInputPart.Image("image-1", imagePath.toString(), "image/png", "图1"),
                UserInputPart.Text("，最后继续"),
            ),
        )

        val parts = assertIs<Message.User>(messages.single()).parts
        assertEquals(MessagePart.Text("先读 "), parts[0])
        assertEquals(
            MessagePart.Text("<file name=\"src/App.kt\">\nfun main() = Unit\n</file>"),
            parts[1],
        )
        assertEquals(MessagePart.Text("，再看 "), parts[2])
        assertEquals(MessagePart.Text("图1："), parts[3])
        assertIs<MessagePart.Attachment>(parts[4])
        assertEquals(MessagePart.Text("，最后继续"), parts[5])
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
