package com.agent.shared.agent.provider.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import com.agent.shared.settings.model.deepMerge

/**
 * DeepSeek chat-completions 的最小请求体。
 */
@Serializable(with = DeepSeekChatCompletionRequestSerializer::class)
internal data class DeepSeekChatCompletionRequest(
    val model: String,
    val messages: List<DeepSeekChatMessage>,
    val tools: List<DeepSeekToolDefinition>? = null,
    val stream: Boolean,
    @SerialName("stream_options")
    val streamOptions: DeepSeekStreamOptions,
    val thinking: DeepSeekThinking? = null,
    @SerialName("reasoning_effort")
    val reasoningEffort: String?,
    /** 由通用 Provider/模型设置生成，并在序列化时覆盖同名协议字段。 */
    val extraBody: JsonObject = JsonObject(emptyMap()),
)

/** 用于将固定协议字段和通用 JSON 覆盖扁平化到同一请求体的中间模型。 */
@Serializable
private data class DeepSeekChatCompletionRequestWire(
    val model: String,
    val messages: List<DeepSeekChatMessage>,
    val tools: List<DeepSeekToolDefinition>? = null,
    val stream: Boolean,
    @SerialName("stream_options")
    val streamOptions: DeepSeekStreamOptions,
    val thinking: DeepSeekThinking? = null,
    @SerialName("reasoning_effort")
    val reasoningEffort: String?,
)

/** 将 [DeepSeekChatCompletionRequest.extraBody] 深度合并为协议顶层字段的 JSON 序列化器。 */
internal object DeepSeekChatCompletionRequestSerializer : kotlinx.serialization.KSerializer<DeepSeekChatCompletionRequest> {
    override val descriptor: SerialDescriptor = DeepSeekChatCompletionRequestWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DeepSeekChatCompletionRequest) {
        val wire = DeepSeekChatCompletionRequestWire(
            model = value.model,
            messages = value.messages,
            tools = value.tools,
            stream = value.stream,
            streamOptions = value.streamOptions,
            thinking = value.thinking,
            reasoningEffort = value.reasoningEffort,
        )
        val json = Json((encoder as JsonEncoder).json) {
            encodeDefaults = true
            explicitNulls = false
        }
        val base = json.encodeToJsonElement(DeepSeekChatCompletionRequestWire.serializer(), wire).jsonObject
        encoder.encodeSerializableValue(JsonObject.serializer(), base.deepMerge(value.extraBody))
    }

    override fun deserialize(decoder: Decoder): DeepSeekChatCompletionRequest = throw SerializationException(
        "DeepSeek chat completion requests are outbound-only",
    )
}

/**
 * DeepSeek/OpenAI 兼容消息体。
 */
@Serializable
internal data class DeepSeekChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
)

/**
 * DeepSeek/OpenAI 兼容工具定义。
 */
@Serializable
internal data class DeepSeekToolDefinition(
    val type: String = "function",
    val function: DeepSeekToolFunctionDefinition,
)

/**
 * DeepSeek/OpenAI 兼容函数定义。
 */
@Serializable
internal data class DeepSeekToolFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * DeepSeek/OpenAI 兼容工具调用。
 */
@Serializable
internal data class DeepSeekToolCall(
    val id: String,
    val function: DeepSeekToolFunctionCall,
    val type: String = "function",
)

/**
 * DeepSeek/OpenAI 兼容函数调用体。
 */
@Serializable
internal data class DeepSeekToolFunctionCall(
    val name: String,
    val arguments: String = "",
)

/**
 * DeepSeek thinking 开关配置。
 */
@Serializable
internal data class DeepSeekThinking(
    val type: String,
)

/**
 * DeepSeek stream 选项。
 */
@Serializable
internal data class DeepSeekStreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean,
)

/**
 * DeepSeek chat-completions 的 SSE chunk。
 */
@Serializable
internal data class DeepSeekChatCompletionChunk(
    val id: String,
    val created: Long,
    val model: String,
    val choices: List<DeepSeekChatChoice> = emptyList(),
    val usage: DeepSeekUsage? = null,
)

/**
 * DeepSeek 流式 choice。
 */
@Serializable
internal data class DeepSeekChatChoice(
    val index: Int,
    val delta: DeepSeekChatDelta,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

/**
 * DeepSeek 流式 delta，额外包含 reasoning_content。
 */
@Serializable
internal data class DeepSeekChatDelta(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekStreamToolCall>? = null,
)

/**
 * DeepSeek chat-completions 流式工具调用 delta。
 */
@Serializable
internal data class DeepSeekStreamToolCall(
    val index: Int? = null,
    val id: String? = null,
    val function: DeepSeekStreamFunction? = null,
    val type: String? = "function",
)

/**
 * DeepSeek chat-completions 流式函数调用 delta。
 */
@Serializable
internal data class DeepSeekStreamFunction(
    val name: String? = null,
    val arguments: String? = null,
)

/**
 * DeepSeek 流式 usage 统计。
 */
@Serializable
internal data class DeepSeekUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
)
