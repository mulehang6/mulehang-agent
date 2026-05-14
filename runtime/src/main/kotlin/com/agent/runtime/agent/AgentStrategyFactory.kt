@file:OptIn(ai.koog.agents.core.annotation.InternalAgentsApi::class)

package com.agent.runtime.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import kotlinx.coroutines.flow.toList

/**
 * 负责提供仓库当前阶段可用的真实 Koog strategy。
 */
object AgentStrategyFactory {

    /**
     * 返回阶段 03 默认使用的 Koog single-run strategy。
     */
    fun singleRun(): AIAgentGraphStrategy<String, String> = strategy("runtime_streaming_single_run") {
        val streamLlm by nodeRuntimeCompatibleStreamingAndSendResults<String>()
        val executeTools by nodeExecuteMultipleTools(parallelTools = false)
        val sendToolResults by nodeRuntimeCompatibleStreamingToolResults()

        edge(nodeStart forwardTo streamLlm)
        edge(streamLlm forwardTo executeTools onMultipleToolCalls { true })
        edge(
            streamLlm forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { responses -> responses.joinToString("\n") { response -> response.content } }
        )
        edge(executeTools forwardTo sendToolResults)
        edge(sendToolResults forwardTo executeTools onMultipleToolCalls { true })
        edge(
            sendToolResults forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { responses -> responses.joinToString("\n") { response -> response.content } }
        )
    }
}

/**
 * 兼容部分 OpenAI-compatible provider 只发 reasoning delta、不发 reasoning complete 的 streaming 缺口。
 */
private inline fun <reified T> nodeRuntimeCompatibleStreamingAndSendResults(
    name: String? = null,
): AIAgentNodeDelegate<T, List<Message.Response>> = node(name) { input ->
    llm.writeSession {
        appendPrompt {
            user(input.toString())
        }
        requestLLMStreaming()
            .toList()
            .normalizeFramesForPromptReplay()
            .toMessageResponses()
            .also { responses -> appendPrompt { messages(responses) } }
    }
}

/**
 * 把多个工具结果追加回 prompt 后继续走 streaming，确保工具后的最终回答仍然按增量事件发出。
 */
private fun nodeRuntimeCompatibleStreamingToolResults(
    name: String? = null,
): AIAgentNodeDelegate<List<ReceivedToolResult>, List<Message.Response>> = node(name) { results ->
    llm.writeSession {
        appendPrompt {
            tool {
                results.forEach { result(it) }
            }
        }

        requestLLMStreaming()
            .toList()
            .normalizeFramesForPromptReplay()
            .toMessageResponses()
            .also { appendPrompt { messages(it) } }
    }
}

/**
 * 给只有 reasoning delta 的流补一条 reasoning complete，确保后续 prompt replay 不会丢失 reasoning。
 */
internal fun List<StreamFrame>.normalizeFramesForPromptReplay(): List<StreamFrame> {
    val reasoningByKey = linkedMapOf<ReasoningFrameKey, MutableReasoningFrame>()
    val completedKeys = mutableSetOf<ReasoningFrameKey>()

    forEach { frame ->
        when (frame) {
            is StreamFrame.ReasoningDelta -> {
                val key = ReasoningFrameKey(frame.id, frame.index)
                val entry = reasoningByKey.getOrPut(key) { MutableReasoningFrame(frame.id, frame.index) }
                frame.text?.takeIf { it.isNotEmpty() }?.let(entry.textParts::add)
                frame.summary?.takeIf { it.isNotEmpty() }?.let(entry.summaryParts::add)
            }

            is StreamFrame.ReasoningComplete -> completedKeys += ReasoningFrameKey(frame.id, frame.index)
            else -> Unit
        }
    }

    val syntheticCompletes = reasoningByKey
        .filterKeys { it !in completedKeys }
        .values
        .filter { it.textParts.isNotEmpty() || it.summaryParts.isNotEmpty() }
        .map { entry ->
            StreamFrame.ReasoningComplete(
                id = entry.id,
                text = entry.textParts.toList(),
                summary = entry.summaryParts.toList().ifEmpty { null },
                encrypted = null,
                index = entry.index,
            )
        }

    if (syntheticCompletes.isEmpty()) {
        return this
    }

    val end = lastOrNull()
    return if (end is StreamFrame.End) {
        dropLast(1) + syntheticCompletes + end
    } else {
        this + syntheticCompletes
    }
}

/**
 * 标识同一段 reasoning stream。
 */
private data class ReasoningFrameKey(
    val id: String?,
    val index: Int?,
)

/**
 * 在补 complete 之前暂存 reasoning delta。
 */
private data class MutableReasoningFrame(
    val id: String?,
    val index: Int?,
    val textParts: MutableList<String> = mutableListOf(),
    val summaryParts: MutableList<String> = mutableListOf(),
)
