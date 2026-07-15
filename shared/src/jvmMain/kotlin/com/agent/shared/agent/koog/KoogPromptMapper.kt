package com.agent.shared.agent.koog

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.prompt.buildPromptParams
import com.agent.shared.settings.model.ConfigProfile

/**
 * 构建 agent 基础 prompt，只承载 provider 参数，不预写用户正文。
 */
internal fun buildAgentPrompt(
    profile: ConfigProfile,
    reasoningEffort: ReasoningEffort?,
): Prompt = Prompt.build(
    id = "mulehang-chat",
    params = buildPromptParams(profile, reasoningEffort),
) {}

/**
 * 将会话历史和当前用户输入映射为 Koog 可消费的消息序列。
 */
internal fun buildConversationMessages(
    history: List<AgentConversationHistoryMessage>,
    prompt: String,
    clock: KoogClock = KoogClock.System,
): List<Message> = history.flatMap { message ->
    message.toKoogMessages(clock)
} + Message.User(
    content = prompt,
    metaInfo = RequestMetaInfo.create(clock = clock),
)

/**
 * 将单条结构化历史消息映射为一段 Koog 消息序列。
 */
private fun AgentConversationHistoryMessage.toKoogMessages(clock: KoogClock): List<Message> = when (this) {
    is AgentConversationHistoryMessage.User -> listOf(
        Message.User(
            content = content,
            metaInfo = RequestMetaInfo.create(clock = clock),
        ),
    )

    is AgentConversationHistoryMessage.Assistant -> assistantHistoryToKoogMessages(parts, clock)
}

/**
 * 将 assistant 历史片段展开为 Koog 所需的 assistant/user/tool-result 消息序列。
 */
private fun assistantHistoryToKoogMessages(
    parts: List<AgentConversationHistoryPart>,
    clock: KoogClock,
): List<Message> {
    val messages = mutableListOf<Message>()
    val assistantParts = mutableListOf<MessagePart.ResponsePart>()
    val pendingToolCalls = linkedMapOf<String, PendingHistoricalToolCall>()

    fun flushAssistant() {
        if (assistantParts.isEmpty()) return
        messages += Message.Assistant(
            parts = assistantParts.toList(),
            metaInfo = ResponseMetaInfo.Empty,
        )
        assistantParts.clear()
    }

    fun appendMissingToolResults() {
        if (pendingToolCalls.isEmpty()) return
        pendingToolCalls.values.forEach { toolCall ->
            messages += Message.User(
                part = MessagePart.Tool.Result(
                    id = toolCall.id,
                    tool = toolCall.name,
                    output = ORPHANED_TOOL_CALL_RESULT,
                ),
                metaInfo = RequestMetaInfo.create(clock = clock),
            )
        }
        pendingToolCalls.clear()
    }

    fun beforeAssistantPart() {
        if (assistantParts.isEmpty()) {
            appendMissingToolResults()
        }
    }

    parts.forEach { part ->
        when (part) {
            is AgentConversationHistoryPart.Text -> {
                beforeAssistantPart()
                assistantParts += MessagePart.Text(part.text)
            }

            is AgentConversationHistoryPart.Reasoning -> {
                val content = part.rawText ?: part.summary.orEmpty()
                if (content.isNotBlank()) {
                    beforeAssistantPart()
                    assistantParts += MessagePart.Reasoning(
                        content = listOf(content),
                        summary = part.summary?.takeIf { it.isNotBlank() }?.let(::listOf),
                    )
                }
            }

            is AgentConversationHistoryPart.ToolCall -> {
                beforeAssistantPart()
                assistantParts += MessagePart.Tool.Call(
                    id = part.id,
                    tool = part.name,
                    args = part.argumentsPreview.orEmpty(),
                )
                pendingToolCalls[historicalToolCallKey(part.id, part.name)] = PendingHistoricalToolCall(
                    id = part.id,
                    name = part.name,
                )
            }

            is AgentConversationHistoryPart.ToolResult -> {
                flushAssistant()
                messages += Message.User(
                    part = MessagePart.Tool.Result(
                        id = part.id,
                        tool = part.name,
                        output = part.resultPreview.orEmpty(),
                    ),
                    metaInfo = RequestMetaInfo.create(clock = clock),
                )
                pendingToolCalls.removeHistoricalToolCall(part.id, part.name)
            }
        }
    }

    flushAssistant()
    appendMissingToolResults()
    return messages
}

/**
 * 记录已经进入历史 assistant/tool_calls、但尚未匹配到 tool result 的工具调用。
 */
private data class PendingHistoricalToolCall(
    val id: String?,
    val name: String,
)

/**
 * 为被中断或失败的历史工具调用补齐协议要求的工具结果文本。
 */
private const val ORPHANED_TOOL_CALL_RESULT = "工具调用未完成，未产生可用结果。"

/**
 * 生成历史工具调用匹配键；缺少 id 时退回工具名以匹配旧事件。
 */
private fun historicalToolCallKey(id: String?, name: String): String = id ?: name

/**
 * 从待匹配工具调用中移除已收到结果的项，优先按 id，其次按工具名兼容旧历史。
 */
private fun MutableMap<String, PendingHistoricalToolCall>.removeHistoricalToolCall(id: String?, name: String) {
    if (id != null && remove(id) != null) return
    val fallbackKey = entries.firstOrNull { (_, call) -> call.name == name }?.key ?: return
    remove(fallbackKey)
}
