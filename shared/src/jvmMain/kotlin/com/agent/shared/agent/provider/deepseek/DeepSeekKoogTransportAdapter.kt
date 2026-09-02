package com.agent.shared.agent.provider.deepseek

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSessionCommon
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.provider.KoogProviderTransportAdapter
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DeepSeek 的 Koog 传输适配器。
 *
 * 该对象是 DeepSeek SSE、Responses replay 和请求补丁的唯一接入点。通用 Koog 图只向
 * provider 注册表询问是否有适配器，因而其他 OpenAI-compatible 服务不会得到这些修补。
 */
internal object DeepSeekKoogTransportAdapter : KoogProviderTransportAdapter {
    /** DeepSeek 使用 OpenAI 兼容的 chat-completions 或 Responses wire-format。 */
    override fun supports(profile: ConfigProfile): Boolean = profile.isDeepSeekOpenAiProfile()

    /**
     * 仅 chat-completions 需要自行读取 reasoning_content；Responses 继续使用 Koog 默认流。
     */
    override suspend fun streamFrames(
        session: AIAgentLLMWriteSessionCommon,
        request: AgentRunRequest,
    ): Flow<StreamFrame>? {
        if (!request.profile.isDeepSeekChatCompletionsProfile()) return null
        // 专用 DeepSeek SSE 协议仅建模文本和工具调用。含图片时退回 Koog 标准 OpenAI
        // serializer，由其将 Attachment 编码为 provider 支持的多模态 content part。
        if (session.prompt.messages.any { message ->
                message.parts.any { part -> part is MessagePart.Attachment }
            }
        ) {
            return null
        }
        return DeepSeekChatCompletionsStreamer().stream(
            prompt = session.prompt,
            config = request.profile,
            reasoningEffort = request.reasoningEffort,
            tools = session.tools,
        )
    }

    /**
     * 删除 Koog 1.1.1 尚未建模的 Responses 推理回显，并补全空 reasoning item。
     */
    override fun normalizeSseData(data: String): String = runCatching {
        val event = Json.parseToJsonElement(data).jsonObject
        val response = event["response"]?.jsonObject
        val withoutEchoedReasoning = if (response != null && "reasoning" in response) {
            JsonObject(event + ("response" to JsonObject(response - "reasoning")))
        } else {
            event
        }
        val withFilledReasoning = if (
            withoutEchoedReasoning["type"]?.jsonPrimitive?.content == "response.output_item.done"
        ) {
            JsonObject(
                withoutEchoedReasoning +
                    ("item" to fillEmptyReasoningItemContent(withoutEchoedReasoning["item"])),
            )
        } else {
            withoutEchoedReasoning
        }
        withFilledReasoning.toString()
    }.getOrDefault(data)

    /**
     * 补齐 stateless replay 的 reasoning 状态和空文本，并闭合以 tool result 结尾的轮次。
     */
    override fun normalizeRequestBody(data: String): String = runCatching {
        val request = Json.parseToJsonElement(data).jsonObject
        val input = request["input"]?.jsonArray ?: return@runCatching data
        var changed = false
        val normalizedInput = input.map { item ->
            val itemObject = item as? JsonObject ?: return@map item
            if (itemObject["type"]?.jsonPrimitive?.content != "reasoning") {
                item
            } else {
                var updated = itemObject
                if ("status" !in updated) {
                    changed = true
                    updated = JsonObject(updated + ("status" to JsonPrimitive("completed")))
                }
                val filled = fillEmptyReasoningItemContent(updated)
                if (filled !== updated) changed = true
                filled
            }
        }.toMutableList()
        appendTrailingEmptyUserMessage(normalizedInput)?.let { trailingMessage ->
            normalizedInput += trailingMessage
            changed = true
        }
        if (changed) {
            JsonObject(request + ("input" to JsonArray(normalizedInput))).toString()
        } else {
            data
        }
    }.getOrDefault(data)

    /** 为缺失文本的 reasoning item 注入空的 reasoning_text，保留其回放结构。 */
    private fun fillEmptyReasoningItemContent(item: JsonElement?): JsonElement {
        val itemObject = item as? JsonObject ?: return item ?: JsonNull
        if (itemObject["type"]?.jsonPrimitive?.content != "reasoning") return itemObject
        val content = itemObject["content"]
        val hasReasoningText = (content as? JsonArray)?.any { part ->
            (part as? JsonObject)?.get("type")?.jsonPrimitive?.content == "reasoning_text"
        } == true
        if (hasReasoningText) return itemObject
        return JsonObject(
            itemObject +
                ("content" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("reasoning_text"),
                                "text" to JsonPrimitive(""),
                            ),
                        ),
                    ),
                )),
        )
    }

    /** 在工具输出后追加空 user 消息，满足 DeepSeek thinking 服务端的轮次闭合要求。 */
    private fun appendTrailingEmptyUserMessage(input: List<JsonElement>): JsonObject? {
        val last = input.lastOrNull() as? JsonObject ?: return null
        if (last["type"]?.jsonPrimitive?.content != "function_call_output") return null
        return JsonObject(
            mapOf(
                "type" to JsonPrimitive("message"),
                "role" to JsonPrimitive("user"),
                "content" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("input_text"),
                                "text" to JsonPrimitive(""),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

}

/** 判断是否使用 DeepSeek 的 OpenAI 兼容端点；仅传输适配器需要了解此 wire-format 边界。 */
private fun ConfigProfile.isDeepSeekOpenAiProfile(): Boolean =
    providerType in DEEPSEEK_OPENAI_PROVIDER_TYPES &&
        (baseUrl.contains("deepseek.com", ignoreCase = true) || model.startsWith("deepseek", ignoreCase = true))

/** DeepSeek chat-completions 的 reasoning 流式帧需要专用解析器。 */
private fun ConfigProfile.isDeepSeekChatCompletionsProfile(): Boolean =
    providerType == ProviderType.OPENAI_CHAT_COMPLETIONS && isDeepSeekOpenAiProfile()

/** DeepSeek 适配的 OpenAI endpoint 类型。 */
private val DEEPSEEK_OPENAI_PROVIDER_TYPES = setOf(
    ProviderType.OPENAI_CHAT_COMPLETIONS,
    ProviderType.OPENAI_RESPONSES,
)
