package com.agent.runtime.agent

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.base.models.Content
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 验证 DeepSeek interleaved reasoning 转换与 Kilo 的行为一致。
 */
class DeepSeekInterleavedOpenAILLMClientTest {

    @Test
    fun `should merge reasoning and tool calls into one assistant message`() {
        val prompt = prompt("runtime-agent") {
            messages(
                listOf(
                    Message.Reasoning(content = "Let me inspect first.", metaInfo = ResponseMetaInfo.Empty),
                    Message.Tool.Call(
                        id = "tool-call-1",
                        tool = "tool.echo",
                        content = """{"payload":"hello"}""",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                ),
            )
        }

        val messages = convertPromptToOpenAIMessagesPreservingInterleavedReasoning(prompt) { user ->
            Content.Text(user.content)
        }

        assertEquals(1, messages.size)
        val assistant = assertIs<OpenAIMessage.Assistant>(messages.single())
        assertEquals("Let me inspect first.", assistant.reasoningContent)
        assertEquals(1, assistant.toolCalls?.size)
        assertEquals("tool.echo", assistant.toolCalls?.single()?.function?.name)
    }

    @Test
    fun `should preserve empty reasoning content for tool call turns`() {
        val prompt = prompt("runtime-agent") {
            messages(
                listOf(
                    Message.Tool.Call(
                        id = "tool-call-1",
                        tool = "tool.echo",
                        content = """{"payload":"hello"}""",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                ),
            )
        }

        val messages = convertPromptToOpenAIMessagesPreservingInterleavedReasoning(prompt) { user ->
            Content.Text(user.content)
        }

        assertEquals(1, messages.size)
        val assistant = assertIs<OpenAIMessage.Assistant>(messages.single())
        assertEquals("", assistant.reasoningContent)
        assertEquals(1, assistant.toolCalls?.size)
    }

    @Test
    fun `should merge reasoning and assistant text into one assistant message`() {
        val prompt = prompt("runtime-agent") {
            messages(
                listOf(
                    Message.Reasoning(content = "Let me think first.", metaInfo = ResponseMetaInfo.Empty),
                    Message.Assistant(content = "Final answer", metaInfo = ResponseMetaInfo.Empty),
                ),
            )
        }

        val messages = convertPromptToOpenAIMessagesPreservingInterleavedReasoning(prompt) { user ->
            Content.Text(user.content)
        }

        assertEquals(1, messages.size)
        val assistant = assertIs<OpenAIMessage.Assistant>(messages.single())
        assertEquals("Let me think first.", assistant.reasoningContent)
        assertEquals("Final answer", (assistant.content as? Content.Text)?.value)
    }

    @Test
    fun `should add empty reasoning content for plain assistant messages`() {
        val prompt = prompt("runtime-agent") {
            messages(
                listOf(
                    Message.Assistant(content = "Final answer", metaInfo = ResponseMetaInfo.Empty),
                ),
            )
        }

        val messages = convertPromptToOpenAIMessagesPreservingInterleavedReasoning(prompt) { user ->
            Content.Text(user.content)
        }

        assertEquals(1, messages.size)
        val assistant = assertIs<OpenAIMessage.Assistant>(messages.single())
        assertEquals("", assistant.reasoningContent)
        assertEquals("Final answer", (assistant.content as? Content.Text)?.value)
    }
}
