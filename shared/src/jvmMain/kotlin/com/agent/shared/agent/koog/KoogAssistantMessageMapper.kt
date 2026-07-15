package com.agent.shared.agent.koog

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.utils.time.KoogClock

/**
 * 将已经收敛完成的 assistant message 追加回 prompt，和 Koog 默认非流式语义保持一致。
 */
internal fun appendAssistantMessageToPrompt(
    currentPrompt: Prompt,
    response: Message.Assistant,
    clock: KoogClock,
): Prompt = prompt(currentPrompt, clock) {
    message(response)
}

/**
 * 验证当前 assistant message 至少能命中单轮流式策略图的一条边，避免 Koog 子图卡死。
 */
internal fun Message.Assistant.requireRoutableForStreamingGraph(): Message.Assistant {
    val hasToolCalls = parts.any { it is MessagePart.Tool.Call }
    if (finishReason == "tool_calls" && !hasToolCalls) {
        error("模型返回 finishReason=tool_calls，但未提供可执行的工具调用。")
    }

    val hasText = parts.any { part ->
        part is MessagePart.Text && part.text.isNotBlank()
    }
    if (!hasToolCalls && !hasText) {
        val message = if (parts.any { it is MessagePart.Reasoning }) {
            "模型仅返回了思考内容，未返回文本或工具调用。"
        } else {
            "模型未返回文本或工具调用。"
        }
        error(message)
    }
    return this
}

/**
 * 参考 paicli 的 ReAct 循环：只要 assistant 同时携带工具调用，就继续走工具链；
 * 只有不存在工具调用且存在正文文本时，当前轮次才应结束。
 */
internal fun Message.Assistant.shouldFinishReactLoop(): Boolean =
    parts.none { it is MessagePart.Tool.Call } &&
            parts.any { part -> part is MessagePart.Text && part.text.isNotBlank() }

/**
 * 提取当前 ReAct 轮次可作为最终输出的文本；若仍需继续调用工具则返回 null。
 */
internal fun Message.Assistant.finalTextForReactLoop(): String? {
    if (!shouldFinishReactLoop()) {
        return null
    }
    return parts
        .filterIsInstance<MessagePart.Text>()
        .map { it.text }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")
        .takeIf { it.isNotBlank() }
}
