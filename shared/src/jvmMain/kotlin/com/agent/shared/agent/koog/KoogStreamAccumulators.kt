package com.agent.shared.agent.koog

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * 将 Koog 的流式 frame 收敛为 assistant message，并同步发出 UI 所需的增量事件。
 */
internal suspend fun collectAssistantMessageFromStream(
    frames: Flow<StreamFrame>,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
): Message.Assistant {
    val textParts = linkedMapOf<String, TextAccumulator>()
    val reasoningParts = linkedMapOf<String, ReasoningAccumulator>()
    val toolCalls = linkedMapOf<String, ToolCallAccumulator>()
    val orderedPartKeys = mutableListOf<String>()
    var endFrame = StreamFrame.End()

    frames.collect { frame ->
        when (frame) {
            is StreamFrame.TextDelta -> {
                val key = "text:${frame.index ?: 0}"
                val accumulator = textParts.getOrPut(key) {
                    orderedPartKeys += key
                    TextAccumulator()
                }
                accumulator.deltaText.append(frame.text)
                emitEvent(AgentStreamEvent.TextDelta(frame.text))
            }

            is StreamFrame.TextComplete -> {
                val key = "text:${frame.index ?: 0}"
                val accumulator = textParts.getOrPut(key) {
                    orderedPartKeys += key
                    TextAccumulator()
                }
                accumulator.completeText = frame.text
            }

            is StreamFrame.ReasoningDelta -> {
                val key = reasoningKey(frame.id, frame.index)
                val accumulator = reasoningParts.getOrPut(key) {
                    orderedPartKeys += key
                    ReasoningAccumulator(id = frame.id)
                }
                frame.text?.let(accumulator.deltaContent::add)
                frame.summary?.let(accumulator.deltaSummary::add)
                emitEvent(
                    AgentStreamEvent.ReasoningDelta(
                        summary = frame.summary,
                        rawText = frame.text,
                    ),
                )
            }

            is StreamFrame.ReasoningComplete -> {
                val key = reasoningKey(frame.id, frame.index)
                val accumulator = reasoningParts.getOrPut(key) {
                    orderedPartKeys += key
                    ReasoningAccumulator(id = frame.id)
                }
                accumulator.completeContent = frame.content
                accumulator.completeSummary = frame.summary
                accumulator.encrypted = frame.encrypted
                emitEvent(
                    AgentStreamEvent.ReasoningCompleted(
                        summary = frame.summary?.joinToString(separator = ""),
                        rawText = frame.content.joinToString(separator = ""),
                    ),
                )
            }

            is StreamFrame.ToolCallDelta -> {
                val key = resolveToolCallAccumulatorKey(
                    toolCalls = toolCalls,
                    id = frame.id,
                    index = frame.index,
                )
                val accumulator = toolCalls.getOrPut(key) {
                    orderedPartKeys += key
                    ToolCallAccumulator(id = frame.id, index = frame.index)
                }
                if (accumulator.id == null && frame.id != null) {
                    accumulator.id = frame.id
                }
                if (accumulator.index == null && frame.index != null) {
                    accumulator.index = frame.index
                }
                if (frame.name != null) {
                    accumulator.name = frame.name
                }
                if (frame.content != null) {
                    accumulator.deltaArgs.append(frame.content)
                }
            }

            is StreamFrame.ToolCallComplete -> {
                val key = resolveToolCallAccumulatorKey(
                    toolCalls = toolCalls,
                    id = frame.id,
                    index = frame.index,
                )
                val accumulator = toolCalls.getOrPut(key) {
                    orderedPartKeys += key
                    ToolCallAccumulator(id = frame.id, index = frame.index)
                }
                if (accumulator.id == null && frame.id != null) {
                    accumulator.id = frame.id
                }
                if (accumulator.index == null && frame.index != null) {
                    accumulator.index = frame.index
                }
                accumulator.name = frame.name
                accumulator.completeArgs = frame.content
            }

            is StreamFrame.End -> endFrame = frame
        }
    }

    val parts = orderedPartKeys.mapNotNull { key ->
        when {
            textParts.containsKey(key) -> textParts.getValue(key).toMessagePart()
            reasoningParts.containsKey(key) -> reasoningParts.getValue(key).toMessagePart()
            toolCalls.containsKey(key) -> toolCalls.getValue(key).toMessagePart()
            else -> null
        }
    }

    return Message.Assistant(
        parts = parts,
        metaInfo = endFrame.metaInfo,
        finishReason = endFrame.finishReason,
    ).requireRoutableForStreamingGraph()
}

/**
 * 文本 part 的临时收集器。
 */
private data class TextAccumulator(
    val deltaText: StringBuilder = StringBuilder(),
    var completeText: String? = null,
) {
    /**
     * 生成最终文本 part；空文本会被丢弃。
     */
    fun toMessagePart(): MessagePart.Text? {
        val text = if (deltaText.isNotEmpty()) {
            deltaText.toString()
        } else {
            completeText
        }
        return text?.takeIf { it.isNotEmpty() }?.let(MessagePart::Text)
    }
}

/**
 * 思考 part 的临时收集器。
 */
private data class ReasoningAccumulator(
    val id: String? = null,
    val deltaContent: MutableList<String> = mutableListOf(),
    val deltaSummary: MutableList<String> = mutableListOf(),
    var completeContent: List<String>? = null,
    var completeSummary: List<String>? = null,
    var encrypted: String? = null,
) {
    /**
     * 生成最终 reasoning part；没有内容时返回 null。
     */
    fun toMessagePart(): MessagePart.Reasoning? {
        val content = completeContent ?: deltaContent.takeIf { it.isNotEmpty() }
        if (content.isNullOrEmpty()) {
            return null
        }
        val summary = completeSummary ?: deltaSummary.takeIf { it.isNotEmpty() }
        return MessagePart.Reasoning(
            id = id,
            content = content,
            summary = summary,
            encrypted = encrypted,
        )
    }
}

/**
 * 工具调用 part 的临时收集器。
 */
private data class ToolCallAccumulator(
    var id: String? = null,
    var index: Int? = null,
    var name: String? = null,
    val deltaArgs: StringBuilder = StringBuilder(),
    var completeArgs: String? = null,
) {
    /**
     * 生成最终工具调用 part；名称或参数缺失时返回 null。
     */
    fun toMessagePart(): MessagePart.Tool.Call? {
        val resolvedName = name ?: return null
        val resolvedArgs = completeArgs ?: deltaArgs.toString().takeIf { it.isNotEmpty() } ?: return null
        return MessagePart.Tool.Call(
            id = id,
            tool = resolvedName,
            args = resolvedArgs,
        )
    }
}

/**
 * 解析工具调用增量对应的稳定 accumulator key，优先复用已有 index / id 对应项。
 */
private fun resolveToolCallAccumulatorKey(
    toolCalls: Map<String, ToolCallAccumulator>,
    id: String?,
    index: Int?,
): String {
    toolCalls.entries.firstOrNull { (_, accumulator) ->
        index != null && accumulator.index == index
    }?.let { return it.key }

    toolCalls.entries.firstOrNull { (_, accumulator) ->
        id != null && accumulator.id == id
    }?.let { return it.key }

    return toolCallKey(id = id, index = index)
}

/**
 * 生成 reasoning part 的稳定 key。
 */
private fun reasoningKey(id: String?, index: Int?): String = "reasoning:${id ?: index ?: 0}"

/**
 * 生成工具调用 part 的稳定 key。
 */
private fun toolCallKey(id: String?, index: Int?): String = "tool:${index ?: id ?: 0}"

/**
 * 生成适合 UI 最小展示的事件预览。
 */
internal fun String.toPreview(limit: Int = 120): String = replace("\n", " ").trim().take(limit)
