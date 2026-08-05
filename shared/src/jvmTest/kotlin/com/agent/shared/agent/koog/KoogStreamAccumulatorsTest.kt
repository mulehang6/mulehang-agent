package com.agent.shared.agent.koog

import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证流式 frame 收敛为 assistant message 的桌面侧规则。
 */
class KoogStreamAccumulatorsTest {

    /**
     * Koog 1.1.1 的 Anthropic 流式客户端不识别 signature_delta 增量，reasoning 完成帧的
     * encrypted 恒为 null；累积层必须兜底为非空，否则多轮请求回传 reasoning 时库内
     * `toAnthropicAssistantMessage` 会抛 "Encrypted signature is required"。
     */
    @Test
    fun `should fallback encrypted signature for reasoning missing signature delta`() = runTest {
        val frames = flowOf(
            StreamFrame.TextDelta(text = "你好", index = 0),
            StreamFrame.ReasoningComplete(
                id = null,
                content = listOf("思考文本"),
                summary = null,
                encrypted = null,
                index = 1,
            ),
            StreamFrame.End(),
        )

        val assistant = collectAssistantMessageFromStream(frames = frames, emitEvent = {})

        val reasoning = assistant.parts.filterIsInstance<MessagePart.Reasoning>().single()
        assertEquals("", reasoning.encrypted)
    }
}
