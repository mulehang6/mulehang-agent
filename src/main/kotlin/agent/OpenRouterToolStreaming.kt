package agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.prompt.message.Message
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.kotlinx.KotlinxSerializer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.http.HttpResponse
import kotlin.time.Clock

/**
 * 描述从 OpenRouter 响应中提取出的单次工具调用。
 */
internal data class OpenRouterToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * 描述一次流式工具轮次的助手输出、工具调用和 thinking 状态。
 */
internal data class OpenRouterToolTurn(
    val taggedResponse: String,
    val toolCalls: List<OpenRouterToolCall>,
    val thinkingState: OpenRouterThinkingState = OpenRouterThinkingState()
)

/**
 * 描述工具循环下一步应继续还是结束。
 */
internal data class ToolLoopDecision(
    val shouldStop: Boolean,
    val hitIterationLimit: Boolean
)

private val toolEnvironmentLogger = KotlinLogging.logger("OpenRouterToolStreaming")

/**
 * 用于拼接流式返回中被拆分的工具调用字段。
 */
private data class OpenRouterStreamingToolCallPartial(
    val id: String = "",
    val name: String = "",
    val arguments: String = ""
)

/**
 * 从单个 OpenRouter SSE payload 中抽取完整的工具调用片段。
 */
internal fun extractOpenRouterToolCalls(payload: String): List<OpenRouterToolCall> {
    val root = openRouterJson.parseToJsonElement(payload).jsonObject
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return emptyList()
    val delta = choice["delta"]?.jsonObject ?: return emptyList()
    val toolCalls = delta["tool_calls"]?.jsonArray ?: return emptyList()

    return toolCalls.mapNotNull { element ->
        val toolCall = element.jsonObject
        val function = toolCall["function"]?.jsonObject ?: return@mapNotNull null
        val id = toolCall["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: ""
        OpenRouterToolCall(id = id, name = name, arguments = arguments)
    }
}

/**
 * 在终端流中输出工具结果块，并在必要时先关闭 thinking 标签。
 */
internal fun emitToolResultBlock(
    resultText: String,
    thinkingState: OpenRouterThinkingState,
    emitChunk: (String) -> Unit
): OpenRouterThinkingState {
    var nextState = thinkingState
    if (nextState.isThinkingOpen) {
        emitChunk("</thinking>\n")
        nextState = nextState.copy(
            isThinkingOpen = false,
            isInsideContentThinkingTag = false,
            pendingContent = "",
            shouldStripLeadingSeparator = false
        )
    }

    emitChunk("\n[工具结果]\n$resultText\n[/工具结果]\n")
    return nextState
}

/**
 * 根据本轮工具调用结果和最大轮次限制决定是否继续循环。
 */
internal fun decideNextToolLoopStep(
    toolCalls: List<OpenRouterToolCall>,
    iteration: Int,
    maxIterations: Int
): ToolLoopDecision {
    if (iteration >= maxIterations) {
        return ToolLoopDecision(shouldStop = true, hitIterationLimit = true)
    }

    return ToolLoopDecision(
        shouldStop = toolCalls.isEmpty(),
        hitIterationLimit = false
    )
}

private fun accumulateOpenRouterToolCalls(
    payload: String,
    partials: MutableMap<Int, OpenRouterStreamingToolCallPartial>
) {
    val root = openRouterJson.parseToJsonElement(payload).jsonObject
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return
    val delta = choice["delta"]?.jsonObject ?: return
    val toolCalls = delta["tool_calls"]?.jsonArray ?: return

    toolCalls.forEach { element ->
        val toolCall = element.jsonObject
        val index = toolCall["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: partials.size
        val function = toolCall["function"]?.jsonObject
        val current = partials[index] ?: OpenRouterStreamingToolCallPartial()

        partials[index] = current.copy(
            id = current.id + (toolCall["id"]?.jsonPrimitive?.contentOrNull ?: ""),
            name = current.name + (function?.get("name")?.jsonPrimitive?.contentOrNull ?: ""),
            arguments = current.arguments + (function?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "")
        )
    }
}

/**
 * 将按索引累积的工具调用碎片整理为可执行的完整调用列表。
 */
private fun finalizeOpenRouterToolCalls(
    partials: Map<Int, OpenRouterStreamingToolCallPartial>
): List<OpenRouterToolCall> {
    return partials.toSortedMap().values.mapNotNull { partial ->
        if (partial.id.isBlank() || partial.name.isBlank()) {
            null
        } else {
            OpenRouterToolCall(
                id = partial.id,
                name = partial.name,
                arguments = partial.arguments
            )
        }
    }
}

/**
 * 发起一轮带工具能力的流式请求，并返回本轮助手输出与工具调用集合。
 */
private suspend fun streamOpenRouterToolTurn(
    apiKey: String,
    toolRegistry: ToolRegistry,
    transcript: List<Message>,
    emitChunk: (String) -> Unit = ::printStreamingChunk
): OpenRouterToolTurn {
    val requestMessages = buildList {
        add(Message.System(SYSTEM_PROMPT, ai.koog.prompt.message.RequestMetaInfo(Clock.System.now())))
        addAll(transcript)
    }
    val requestBody = buildOpenRouterChatRequest(
        messages = requestMessages,
        stream = true,
        toolRegistry = toolRegistry
    )
    val reasoningSteps = mutableListOf<String>()
    val summarySteps = mutableListOf<String>()
    val responseBuilder = StringBuilder()
    val toolCallPartials = linkedMapOf<Int, OpenRouterStreamingToolCallPartial>()
    var thinkingState = OpenRouterThinkingState()

    val request = createOpenRouterRequest(apiKey, requestBody)
    val httpResponse = withContext(Dispatchers.IO) {
        openRouterHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    }

    if (httpResponse.statusCode() != 200) {
        val errorBody = httpResponse.body().bufferedReader().use { it.readText() }
        throw openRouterStatusError(httpResponse.statusCode(), errorBody)
    }

    httpResponse.body().bufferedReader().use { reader ->
        var eventPayload = StringBuilder()

        fun flushEventPayload() {
            val payload = eventPayload.toString().trim()
            if (payload.isBlank()) return
            if (payload == "[DONE]") {
                eventPayload = StringBuilder()
                return
            }

            thinkingState = processOpenRouterStreamPayload(
                payload = payload,
                responseBuilder = responseBuilder,
                reasoningSteps = reasoningSteps,
                summarySteps = summarySteps,
                state = thinkingState,
                emitChunk = emitChunk
            )
            accumulateOpenRouterToolCalls(payload, toolCallPartials)
            eventPayload = StringBuilder()
        }

        reader.forEachLine { line ->
            when {
                line.startsWith("data:") -> {
                    eventPayload.append(line.removePrefix("data:").trimStart())
                }

                line.isBlank() -> flushEventPayload()
            }
        }

        flushEventPayload()
    }

    val finalizedState = finalizeThinkingTaggedContentStream(
        responseBuilder = responseBuilder,
        state = thinkingState,
        emitChunk = emitChunk
    )
    return OpenRouterToolTurn(
        taggedResponse = buildTaggedAssistantResponse(
            reasoningTexts = reasoningSteps,
            summaryTexts = summarySteps,
            content = responseBuilder.toString()
        ),
        toolCalls = finalizeOpenRouterToolCalls(toolCallPartials),
        thinkingState = finalizedState
    )
}

internal suspend fun executeOpenRouterToolCalls(
    toolRegistry: ToolRegistry,
    toolCalls: List<OpenRouterToolCall>
): List<Message.Tool.Result> {
    val environment = GenericAgentEnvironment(
        "openrouter-tool-streaming",
        toolEnvironmentLogger,
        toolRegistry,
        KotlinxSerializer()
    )

    return environment.executeTools(
        toolCalls.map { toolCall ->
            Message.Tool.Call(
                toolCall.id,
                toolCall.name,
                toolCall.arguments,
                ai.koog.prompt.message.ResponseMetaInfo(Clock.System.now())
            )
        }
    ).map { it.toMessage(Clock.System) }
}

/**
 * 运行带工具调用能力的 OpenRouter 流式智能体主循环。
 */
internal suspend fun runStreamingAgent(
    apiKey: String,
    chatHistoryProvider: ChatHistoryProvider,
    toolRegistry: ToolRegistry,
    input: String,
    sessionId: String
): String {
    println("[事件] 发送请求到 LLM: ${input.take(30)}...")
    val response = runStreamingToolLoop(
        chatHistoryProvider = chatHistoryProvider,
        sessionId = sessionId,
        input = input,
        streamTurn = { transcript, emitChunk ->
            streamOpenRouterToolTurn(
                apiKey = apiKey,
                toolRegistry = toolRegistry,
                transcript = transcript,
                emitChunk = emitChunk
            )
        },
        executeToolCalls = { toolCalls ->
            executeOpenRouterToolCalls(
                toolRegistry = toolRegistry,
                toolCalls = toolCalls
            )
        }
    )
    println("[事件] LLM 响应成功，模型: $CHAT_MODEL")
    println("[事件] $CHAT_MODEL 智能体运行完成！")
    return response
}

/**
 * 驱动“助手输出 -> 工具调用 -> 工具结果回灌”的统一流式循环。
 */
internal suspend fun runStreamingToolLoop(
    chatHistoryProvider: ChatHistoryProvider,
    sessionId: String,
    input: String,
    streamTurn: suspend (List<Message>, (String) -> Unit) -> OpenRouterToolTurn,
    executeToolCalls: suspend (List<OpenRouterToolCall>) -> List<Message.Tool.Result>,
    emitChunk: (String) -> Unit = ::printStreamingChunk,
    maxIterations: Int = 4
): String {
    val history = chatHistoryProvider.load(sessionId).takeLast(CHAT_HISTORY_WINDOW_SIZE)
    val transcript = history.toMutableList<Message>()
    transcript += Message.User(input, ai.koog.prompt.message.RequestMetaInfo(Clock.System.now()))

    var iteration = 0
    var finalResponse = ""
    var nextThinkingState = OpenRouterThinkingState()

    while (true) {
        val turn = streamTurn(transcript.toList(), emitChunk)
        finalResponse = turn.taggedResponse
        nextThinkingState = turn.thinkingState
        iteration += 1

        val decision = decideNextToolLoopStep(turn.toolCalls, iteration, maxIterations)
        if (decision.hitIterationLimit) {
            error("工具调用达到最大循环次数：$maxIterations")
        }

        if (decision.shouldStop) {
            persistChatTurn(chatHistoryProvider, sessionId, history, input, finalResponse)
            return finalResponse
        }

        transcript += Message.Assistant(
            turn.taggedResponse,
            ai.koog.prompt.message.ResponseMetaInfo(Clock.System.now())
        )
        val toolCallMessages = turn.toolCalls.map { toolCall ->
            Message.Tool.Call(
                toolCall.id,
                toolCall.name,
                toolCall.arguments,
                ai.koog.prompt.message.ResponseMetaInfo(Clock.System.now())
            )
        }
        transcript += toolCallMessages

        val toolResults = executeToolCalls(turn.toolCalls)
        toolResults.forEach { toolResult ->
            nextThinkingState = emitToolResultBlock(
                resultText = toolResult.content,
                thinkingState = nextThinkingState,
                emitChunk = emitChunk
            )
        }
        transcript += toolResults
    }
}
