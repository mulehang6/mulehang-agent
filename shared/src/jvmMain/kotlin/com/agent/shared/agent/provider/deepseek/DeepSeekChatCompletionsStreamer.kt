@file:Suppress("UnstableApiUsage")

package com.agent.shared.agent.provider.deepseek

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.prompt.buildOpenAIClientSettings
import com.agent.shared.settings.model.ConfigProfile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DeepSeek chat-completions 的专用流式适配器。
 *
 * Koog 1.0 的 chat-completions 流模型不会解析 DeepSeek 的 `reasoning_content`，
 * 这里直接读取原始 SSE chunk，并将思考增量映射回 Koog 的 `StreamFrame`。
 */
internal class DeepSeekChatCompletionsStreamer(
    private val chunkRunner: (DeepSeekChatCompletionRequest, ConfigProfile) -> Flow<DeepSeekChatCompletionChunk> =
        { request, config ->
            openDeepSeekSseChunks(
                request = request,
                settings = buildOpenAIClientSettings(config),
                apiKey = config.apiKey,
            )
        },
) {
    /**
     * 执行一次 DeepSeek chat-completions 流式请求。
     */
    fun stream(request: AgentRunRequest): Flow<StreamFrame> = flow {
        emitAllFrames(
            deepSeekRequest = buildDeepSeekRequest(request),
            config = request.profile,
        )
    }

    /**
     * 基于当前 Koog prompt 与可用工具执行一次 DeepSeek chat-completions 流式请求。
     */
    fun stream(
        prompt: Prompt,
        config: ConfigProfile,
        reasoningEffort: ReasoningEffort?,
        tools: List<ToolDescriptor> = emptyList(),
    ): Flow<StreamFrame> = flow {
        emitAllFrames(
            deepSeekRequest = buildDeepSeekRequest(
                prompt = prompt,
                config = config,
                reasoningEffort = reasoningEffort,
                tools = tools,
            ),
            config = config,
        )
    }

    /**
     * 真正执行 DeepSeek SSE 读取并映射成 Koog frame。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamFrame>.emitAllFrames(
        deepSeekRequest: DeepSeekChatCompletionRequest,
        config: ConfigProfile,
    ) {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null
        val reasoningBuffers = linkedMapOf<Int, StringBuilder>()
        log.info { buildDeepSeekRequestDiagnostic(deepSeekRequest) }

        chunkRunner(deepSeekRequest, config).collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let { reasoning ->
                    reasoningBuffers.getOrPut(choice.index) { StringBuilder() }.append(reasoning)
                    emit(StreamFrame.ReasoningDelta(text = reasoning, index = choice.index))
                }
                choice.delta.content?.takeIf { it.isNotEmpty() }?.let { content ->
                    emit(StreamFrame.TextDelta(text = content, index = choice.index))
                }
                choice.delta.toolCalls.orEmpty().forEachIndexed { toolIndex, toolCall ->
                    emit(
                        StreamFrame.ToolCallDelta(
                            id = toolCall.id,
                            index = toolCall.index ?: toolIndex,
                            name = toolCall.function?.name,
                            content = unwrapDeepSeekToolArguments(toolCall.function?.arguments),
                        ),
                    )
                }
                choice.finishReason?.let { finishReason = it }
            }

            chunk.usage?.let { usage ->
                metaInfo = ResponseMetaInfo.create(
                    clock = KoogClock.System,
                    totalTokensCount = usage.totalTokens,
                    inputTokensCount = usage.promptTokens,
                    outputTokensCount = usage.completionTokens,
                    modelId = chunk.model,
                )
            }
        }

        reasoningBuffers.forEach { (index, text) ->
            if (text.isNotEmpty()) {
                emit(
                    StreamFrame.ReasoningComplete(
                        id = null,
                        content = listOf(text.toString()),
                        index = index,
                    ),
                )
            }
        }
        emit(StreamFrame.End(finishReason = finishReason, metaInfo = metaInfo ?: ResponseMetaInfo.Empty))
    }

    /**
     * 解开 DeepSeek 偶发的 `{"arguments":"{...}"}` 工具参数包装，只接受内层 JSON 对象。
     */
    private fun unwrapDeepSeekToolArguments(arguments: String?): String? {
        val wrappedArguments = runCatching {
            Json.parseToJsonElement(arguments ?: return null)
                .jsonObject["arguments"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull() ?: return arguments
        return wrappedArguments.takeIf {
            runCatching { Json.parseToJsonElement(it) is JsonObject }.getOrDefault(false)
        } ?: arguments
    }

    /**
     * 兼容旧调用方式，默认使用请求对象中的默认推理强度。
     */
    fun stream(prompt: String, config: ConfigProfile): Flow<StreamFrame> = stream(
        AgentRunRequest(
            prompt = prompt,
            profile = config,
        ),
    )

    private companion object {
        private val log = KotlinLogging.logger { }
    }
}
