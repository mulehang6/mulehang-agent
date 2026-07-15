@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.provider.deepseek

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 将应用运行请求映射为 DeepSeek chat-completions 请求。
 */
internal fun buildDeepSeekRequest(request: AgentRunRequest): DeepSeekChatCompletionRequest =
    DeepSeekChatCompletionRequest(
        model = request.profile.model,
        messages = request.history.map(::toDeepSeekHistoryMessage) +
                DeepSeekChatMessage(role = "user", content = request.prompt),
        stream = true,
        streamOptions = DeepSeekStreamOptions(includeUsage = true),
        thinking = DeepSeekThinking(type = "enabled"),
        reasoningEffort = request.reasoningEffort?.wireValue,
    )

/**
 * 将 Koog prompt、配置与工具描述映射为 DeepSeek chat-completions 请求。
 */
internal fun buildDeepSeekRequest(
    prompt: Prompt,
    config: ConfigProfile,
    reasoningEffort: ReasoningEffort?,
    tools: List<ToolDescriptor> = emptyList(),
): DeepSeekChatCompletionRequest =
    DeepSeekChatCompletionRequest(
        model = config.model,
        messages = prompt.messages.flatMap(::toDeepSeekPromptMessages),
        tools = tools.takeIf { it.isNotEmpty() }?.map(::toDeepSeekToolDefinition),
        stream = true,
        streamOptions = DeepSeekStreamOptions(includeUsage = true),
        thinking = DeepSeekThinking(type = "enabled"),
        reasoningEffort = reasoningEffort?.wireValue,
    )

/**
 * 构造不包含 prompt、messages、apiKey 的 DeepSeek 请求诊断摘要。
 */
internal fun buildDeepSeekRequestDiagnostic(request: DeepSeekChatCompletionRequest): String =
    "DeepSeek request: model=${request.model} " +
            "thinking=${request.thinking.type} " +
            "reasoning_effort=${request.reasoningEffort ?: "null"} " +
            "tools=${request.tools?.size ?: 0} " +
            "stream=${request.stream}"

/**
 * 将结构化历史消息映射为 DeepSeek/OpenAI 兼容消息。
 */
private fun toDeepSeekHistoryMessage(message: AgentConversationHistoryMessage): DeepSeekChatMessage =
    when (message) {
        is AgentConversationHistoryMessage.User -> DeepSeekChatMessage(
            role = "user",
            content = message.content,
        )

        is AgentConversationHistoryMessage.Assistant -> DeepSeekChatMessage(
            role = "assistant",
            content = serializeAssistantParts(message.parts),
        )
    }

/**
 * 将助手结构化片段压平成当前 DeepSeek 兼容消息文本。
 */
private fun serializeAssistantParts(parts: List<AgentConversationHistoryPart>): String =
    parts.joinToString(separator = "\n\n") { part ->
        when (part) {
            is AgentConversationHistoryPart.Text -> part.text

            is AgentConversationHistoryPart.Reasoning ->
                "[reasoning]\n${part.rawText ?: part.summary.orEmpty()}\n[/reasoning]"

            is AgentConversationHistoryPart.ToolCall ->
                "[tool_call:${part.name}]\n${part.argumentsPreview.orEmpty()}\n[/tool_call]"

            is AgentConversationHistoryPart.ToolResult ->
                "[tool_result:${part.name}]\n${part.resultPreview.orEmpty()}\n[/tool_result]"
        }
    }.trim()

/**
 * 将 Koog prompt 中的消息展平为 DeepSeek/OpenAI 兼容消息。
 */
private fun toDeepSeekPromptMessages(message: Message): List<DeepSeekChatMessage> = when (message) {
    is Message.System -> listOf(
        DeepSeekChatMessage(
            role = "system",
            content = message.textContent().takeIf { it.isNotBlank() },
        ),
    )

    is Message.User -> message.parts.toDeepSeekUserMessages()
    is Message.Assistant -> listOfNotNull(message.toDeepSeekAssistantMessage())
}

/**
 * 将用户消息拆成文本消息与 tool result 消息，保留 part 顺序。
 */
private fun List<MessagePart.RequestPart>.toDeepSeekUserMessages(): List<DeepSeekChatMessage> {
    val messages = mutableListOf<DeepSeekChatMessage>()
    val textBuffer = StringBuilder()

    fun flushTextBuffer() {
        if (textBuffer.isNotEmpty()) {
            messages += DeepSeekChatMessage(role = "user", content = textBuffer.toString())
            textBuffer.setLength(0)
        }
    }

    forEach { part ->
        when (part) {
            is MessagePart.Text -> {
                if (textBuffer.isNotEmpty()) {
                    textBuffer.append('\n')
                }
                textBuffer.append(part.text)
            }

            is MessagePart.Tool.Result -> {
                flushTextBuffer()
                messages += DeepSeekChatMessage(
                    role = "tool",
                    content = part.output,
                    toolCallId = part.id ?: part.tool,
                )
            }

            else -> Unit
        }
    }

    flushTextBuffer()
    return messages
}

/**
 * 将助手消息映射为单条 assistant role 消息，保留 reasoning 与 tool call。
 */
private fun Message.Assistant.toDeepSeekAssistantMessage(): DeepSeekChatMessage? {
    val textContent = parts
        .filterIsInstance<MessagePart.Text>()
        .joinToString(separator = "\n") { it.text }
        .takeIf { it.isNotBlank() }
    val reasoningContent = parts
        .filterIsInstance<MessagePart.Reasoning>()
        .joinToString(separator = "\n") { it.content.joinToString(separator = "") }
        .takeIf { it.isNotBlank() }
    val toolCalls = parts
        .filterIsInstance<MessagePart.Tool.Call>()
        .map { part ->
            DeepSeekToolCall(
                id = part.id ?: part.tool,
                function = DeepSeekToolFunctionCall(
                    name = part.tool,
                    arguments = part.args,
                ),
            )
        }
        .takeIf { it.isNotEmpty() }
    if (textContent == null && reasoningContent == null && toolCalls.isNullOrEmpty()) {
        return null
    }
    return DeepSeekChatMessage(
        role = "assistant",
        content = textContent,
        reasoningContent = reasoningContent,
        toolCalls = toolCalls,
    )
}

/**
 * 将 ToolDescriptor 转成 DeepSeek/OpenAI 兼容工具 schema。
 */
private fun toDeepSeekToolDefinition(tool: ToolDescriptor): DeepSeekToolDefinition = DeepSeekToolDefinition(
    function = DeepSeekToolFunctionDefinition(
        name = tool.name,
        description = tool.description,
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                (tool.requiredParameters + tool.optionalParameters).forEach { parameter ->
                    put(parameter.name, parameter.toDeepSeekJsonSchema())
                }
            }
            putJsonArray("required") {
                tool.requiredParameters.forEach { parameter -> add(parameter.name) }
            }
        },
    ),
)

/**
 * 将工具参数描述映射为兼容 OpenAI/DeepSeek 的 JSON schema。
 */
private fun ToolParameterDescriptor.toDeepSeekJsonSchema(): JsonObject = buildJsonObject {
    put("description", description)
    fillDeepSeekJsonSchema(type)
}

/**
 * 递归填充 JSON schema 类型定义。
 */
private fun JsonObjectBuilder.fillDeepSeekJsonSchema(type: ToolParameterType) {
    when (type) {
        ToolParameterType.Boolean -> put("type", "boolean")
        ToolParameterType.Float -> put("type", "number")
        ToolParameterType.Integer -> put("type", "integer")
        ToolParameterType.String -> put("type", "string")
        ToolParameterType.Null -> put("type", "null")

        is ToolParameterType.Enum -> {
            put("type", "string")
            putJsonArray("enum") {
                type.entries.forEach(::add)
            }
        }

        is ToolParameterType.List -> {
            put("type", "array")
            putJsonObject("items") {
                fillDeepSeekJsonSchema(type.itemsType)
            }
        }

        is ToolParameterType.Object -> {
            put("type", "object")
            type.additionalProperties?.let { put("additionalProperties", it) }
            putJsonObject("properties") {
                type.properties.forEach { property ->
                    putJsonObject(property.name) {
                        fillDeepSeekJsonSchema(property.type)
                        put("description", property.description)
                    }
                }
            }
            putJsonArray("required") {
                type.requiredProperties.forEach(::add)
            }
        }

        is ToolParameterType.AnyOf -> {
            putJsonArray("anyOf") {
                type.types.forEach { parameter ->
                    add(parameter.toDeepSeekJsonSchema())
                }
            }
        }
    }
}
