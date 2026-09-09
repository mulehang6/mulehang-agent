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
 * OpenAI Chat Completions 的 reasoning-content 传输适配器。
 *
 * 不依据服务商域名或模型名前缀选择实现：任何采用 OpenAI Chat Completions 的自定义服务都通过
 * 同一流读取路径解析可选的 `reasoning_content`，而没有该字段的标准流保持原样。
 */
internal object DeepSeekKoogTransportAdapter : KoogProviderTransportAdapter {
    /** Chat Completions 共用增量解析；DeepSeek Responses 仍需要回放兼容修正。 */
    override fun supports(profile: ConfigProfile): Boolean =
        profile.providerType == ProviderType.OPENAI_CHAT_COMPLETIONS ||
            (profile.providerType == ProviderType.OPENAI_RESPONSES &&
                (profile.baseUrl.contains("deepseek.com", ignoreCase = true) ||
                    profile.model.startsWith("deepseek", ignoreCase = true)))

    /**
     * Chat Completions 需要自行读取可选 reasoning_content；Responses 继续使用 Koog 默认流。
     */
    override suspend fun streamFrames(
        session: AIAgentLLMWriteSessionCommon,
        request: AgentRunRequest,
    ): Flow<StreamFrame>? {
        if (request.profile.providerType != ProviderType.OPENAI_CHAT_COMPLETIONS) return null
        // 原始 SSE 路径仅建模文本和工具调用。含图片时退回 Koog 标准 OpenAI
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
