package com.agent.shared.agent.provider.deepseek

import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.provider.ProviderKoogTransportAdapters
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ProviderType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 验证实际序列化结果和适配器选择，避免只测试内存请求对象。 */
class DeepSeekRequestSerializationTest {
    /** 即使调用方省略默认字段，工具声明与历史调用也必须保留协议必填 type。 */
    @Test
    fun `should encode required tool types and omit absent optional values`() {
        val request = DeepSeekChatCompletionRequest(
            model = "test",
            messages = listOf(DeepSeekChatMessage(
                role = "assistant",
                toolCalls = listOf(DeepSeekToolCall("call", DeepSeekToolFunctionCall("read"))),
            )),
            tools = listOf(DeepSeekToolDefinition(function = DeepSeekToolFunctionDefinition(
                "read", "Read file", JsonObject(emptyMap()),
            ))),
            stream = true,
            streamOptions = DeepSeekStreamOptions(true),
            reasoningEffort = null,
        )
        val body = Json.encodeToJsonElement(DeepSeekChatCompletionRequest.serializer(), request).jsonObject
        assertEquals("function", body.getValue("tools").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content)
        val message = body.getValue("messages").jsonArray.single().jsonObject
        assertEquals("function", message.getValue("tool_calls").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content)
        assertFalse("thinking" in body)
        assertFalse("reasoning_effort" in body)
    }

    /** 原始流携带显式输出限制；高级 JSON 可覆盖它，未配置时保持缺省。 */
    @Test
    fun `should serialize configured output limit with override precedence`() {
        fun body(config: ConfigProfile): JsonObject = Json.encodeToJsonElement(
            DeepSeekChatCompletionRequest.serializer(),
            buildDeepSeekRequest(AgentRunRequest(profile = config, prompt = "hello")),
        ).jsonObject
        assertFalse("max_completion_tokens" in body(profile()))
        assertEquals("512", body(profile().copy(limit = ModelLimit(output = 512)))["max_completion_tokens"].toString())
        val overridden = profile().copy(
            limit = ModelLimit(output = 512),
            requestBody = buildJsonObject { put("max_completion_tokens", 256) },
        )
        assertEquals("256", body(overridden)["max_completion_tokens"].toString())
    }

    /** DeepSeek Responses 继续安装回放修正，普通 Responses 不继承厂商修正。 */
    @Test
    fun `should retain deepseek responses normalization`() {
        val responses = profile().copy(providerType = ProviderType.OPENAI_RESPONSES, model = "deepseek-test")
        val adapter = assertNotNull(ProviderKoogTransportAdapters.forProfile(responses))
        val event = adapter.normalizeSseData("""{"response":{"reasoning":{"effort":"high"}}}""")
        assertFalse("reasoning" in Json.parseToJsonElement(event).jsonObject.getValue("response").jsonObject)
        assertNull(ProviderKoogTransportAdapters.forProfile(responses.copy(model = "ordinary")))
    }

    /** 创建无网络副作用的最小测试配置。 */
    private fun profile() = ConfigProfile(
        id = "test", providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://example.test/v1", apiKey = "placeholder",
        model = "test", enabled = true, layer = ConfigLayer.USER,
    )
}
