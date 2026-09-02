@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.koog

import com.agent.shared.agent.provider.deepseek.DeepSeekKoogTransportAdapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
     * Koog 1.1.1 的 Anthropic 流式实现不会将 thinking signature 写入 reasoning frame；
     * 过滤该元数据事件可避免客户端产生无效警告，同时必须保留真正的思考文本增量。
     */
    @Test
    fun `should filter unsupported anthropic signature delta before json decoding`() {
        assertFalse(
            shouldDecodeSseData(
                """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig"}}""",
            ),
        )
        assertTrue(
            shouldDecodeSseData(
                """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"reasoning"}}""",
            ),
        )
    }

    /**
     * 服务端回显的推理配置不参与流帧映射，未知档位不能阻断 Responses 解码。
     */
    @Test
    fun `should remove echoed reasoning config before responses decoding`() {
        val normalizedData = DeepSeekKoogTransportAdapter.normalizeSseData(
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
        val normalizedBody = DeepSeekKoogTransportAdapter.normalizeRequestBody(
            """{"input":[{"type":"reasoning","id":"reasoning-1","content":[{"type":"reasoning_text","text":"think"}]}]}""",
        )

        assertEquals(
            """{"input":[{"type":"reasoning","id":"reasoning-1","content":[{"type":"reasoning_text","text":"think"}],"status":"completed"}]}""",
            normalizedBody,
        )
    }

    /** 已带完成状态与推理文本的 reasoning item 必须保持不变，避免覆盖服务端的显式值。 */
    @Test
    fun `should preserve explicit responses reasoning status`() {
        val requestBody =
            """{"input":[{"type":"reasoning","status":"incomplete","content":[{"type":"reasoning_text","text":"think"}]}]}"""

        assertEquals(requestBody, DeepSeekKoogTransportAdapter.normalizeRequestBody(requestBody))
    }

    /**
     * DeepSeek thinking mode 的工具续传轮可产出空 reasoning item（无 summary 与 content）；
     * Koog 1.1.1 会整体丢弃该 item，导致后续请求缺失必须回传的 reasoning_text。
     * 此处给空 item 补充空文本 content，让 Koog 生成 ReasoningComplete 帧以保留回放结构。
     */
    @Test
    fun `should fill empty reasoning item content before koog decoding`() {
        val normalizedData = DeepSeekKoogTransportAdapter.normalizeSseData(
            """{"type":"response.output_item.done","output_index":0,"item":{"id":"rs_1","type":"reasoning","summary":[]}}""",
        )

        assertTrue(
            normalizedData.contains(
                """"content":[{"type":"reasoning_text","text":""}]""",
            ),
        )
    }

    /**
     * Koog 序列化空 reasoning 时可能省略 content 字段；请求规范化必须补齐空文本 content，
     * 满足要求回放 reasoning_text 的兼容服务端校验。
     */
    @Test
    fun `should fill missing reasoning content in request body`() {
        val normalizedBody = DeepSeekKoogTransportAdapter.normalizeRequestBody(
            """{"input":[{"type":"reasoning","id":"rs_1","summary":[],"status":"completed"}]}""",
        )

        assertTrue(
            normalizedBody.contains(
                """"content":[{"type":"reasoning_text","text":""}]""",
            ),
        )
    }

    /**
     * DeepSeek thinking mode 下 input 以 function_call_output 结尾且该工具轮次没有
     * reasoning_text 时会被 400 拒绝；请求规范化必须在末尾追加空文本 user 消息闭合工具轮次。
     */
    @Test
    fun `should append trailing empty user message after tool outputs`() {
        val normalizedBody = DeepSeekKoogTransportAdapter.normalizeRequestBody(
            """{"input":[{"type":"function_call","call_id":"c1","name":"list_dir","arguments":"{}"},{"type":"function_call_output","call_id":"c1","output":"[]"}]}""",
        )

        val parsedInput = Json.parseToJsonElement(normalizedBody).jsonObject["input"]!!.jsonArray
        val lastItem = parsedInput.last().jsonObject
        assertEquals("message", lastItem["type"]?.jsonPrimitive?.content)
        assertEquals("user", lastItem["role"]?.jsonPrimitive?.content)
        val content = lastItem["content"]?.jsonArray?.single()?.jsonObject
        assertEquals("input_text", content?.get("type")?.jsonPrimitive?.content)
        assertEquals("", content?.get("text")?.jsonPrimitive?.content)
    }

    /**
     * input 不以 function_call_output 结尾时不应追加空消息，避免改变正常请求。
     */
    @Test
    fun `should not append trailing empty user message for other endings`() {
        val requestBody =
            """{"input":[{"type":"message","role":"user","content":[{"type":"input_text","text":"hello"}]}]}"""

        assertEquals(requestBody, DeepSeekKoogTransportAdapter.normalizeRequestBody(requestBody))
    }
}
