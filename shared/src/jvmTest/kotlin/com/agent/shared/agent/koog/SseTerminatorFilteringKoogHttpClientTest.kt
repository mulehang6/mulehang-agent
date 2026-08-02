@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.koog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证通用 SSE 终止标记不会进入 JSON 事件解码器。
 */
class SseTerminatorFilteringKoogHttpClientTest {

    /**
     * OpenAI-compatible 端点可在任意流式协议中附加 legacy 终止标记。
     */
    @Test
    fun `should filter legacy done sentinel before json decoding`() {
        assertFalse(shouldDecodeSseData("[DONE]"))
        assertFalse(shouldDecodeSseData("  [DONE]  "))
        assertTrue(shouldDecodeSseData("{\"type\":\"response.completed\"}"))
    }

    /**
     * Koog 不消费 content-part 生命周期事件，必须在其多态 JSON 解码前过滤。
     */
    @Test
    fun `should filter responses content part lifecycle events before json decoding`() {
        assertFalse(
            shouldDecodeSseData(
                """{"type":"response.content_part.added","part":{"type":"reasoning_text","text":""}}""",
            ),
        )
        assertFalse(
            shouldDecodeSseData(
                """{"type":"response.content_part.done","part":{"type":"reasoning_text","text":"thinking"}}""",
            ),
        )
        assertTrue(shouldDecodeSseData("""{"type":"response.reasoning_text.delta","delta":"thinking"}"""))
    }

    /**
     * 服务端回显的推理配置不参与流帧映射，未知档位不能阻断 Responses 解码。
     */
    @Test
    fun `should remove echoed reasoning config before responses decoding`() {
        val normalizedData = normalizeSseDataForKoog(
            """{"type":"response.created","response":{"model":"deepseek-v4-flash","reasoning":{"effort":"xhigh"},"output":[]}}""",
        )

        assertFalse(normalizedData.contains("\"reasoning\""))
        assertTrue(normalizedData.contains("\"deepseek-v4-flash\""))
        assertTrue(normalizedData.contains("\"output\""))
    }

    /**
     * Koog 的 MessagePart 不携带 Responses output item 的完成状态；在工具结果续传时必须恢复
     * 该状态，才能让要求逐项回放 reasoning 的兼容服务端识别它为完整 item。
     */
    @Test
    fun `should restore completed status for replayed responses reasoning items`() {
        val normalizedBody = normalizeResponsesRequestBodyForKoog(
            """{"input":[{"type":"reasoning","id":"reasoning-1","content":[{"type":"reasoning_text","text":"think"}]}]}""",
        )

        assertEquals(
            """{"input":[{"type":"reasoning","id":"reasoning-1","content":[{"type":"reasoning_text","text":"think"}],"status":"completed"}]}""",
            normalizedBody,
        )
    }

    /** 已带完成状态的 reasoning item 必须保持不变，避免覆盖服务端的显式值。 */
    @Test
    fun `should preserve explicit responses reasoning status`() {
        val requestBody =
            """{"input":[{"type":"reasoning","status":"incomplete","content":[]}]}"""

        assertEquals(requestBody, normalizeResponsesRequestBodyForKoog(requestBody))
    }
}
