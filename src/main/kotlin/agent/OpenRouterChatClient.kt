package agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 构造 OpenRouter 的 reasoning 配置对象。
 */
internal fun buildOpenRouterReasoningConfig(
    effort: String = OPENROUTER_REASONING_EFFORT,
    exclude: Boolean = false
): JsonObject {
    return buildJsonObject {
        put("effort", effort)
        put("exclude", exclude)
    }
}

/**
 * 将通用消息对象转换为 OpenRouter 兼容的消息结构。
 */
private fun toOpenRouterMessage(message: Message): JsonObject? {
    return when (message) {
        is Message.System -> buildJsonObject {
            put("role", "system")
            put("content", message.content)
        }

        is Message.User -> buildJsonObject {
            put("role", "user")
            put("content", message.content)
        }

        is Message.Assistant -> buildJsonObject {
            put("role", "assistant")
            put("content", message.content)
        }

        is Message.Tool.Call -> buildJsonObject {
            put("role", "assistant")
            put("content", JsonNull)
            put(
                "tool_calls",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", message.id)
                            put("type", "function")
                            put(
                                "function",
                                buildJsonObject {
                                    put("name", message.tool)
                                    put("arguments", message.content)
                                }
                            )
                        }
                    )
                }
            )
        }

        is Message.Tool.Result -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", message.id)
            put("content", message.content)
        }

        else -> null
    }
}

/**
 * 跟踪 OpenRouter 推理流式输出的 thinking 标签状态。
 */
internal data class OpenRouterThinkingState(
    val isThinkingOpen: Boolean = false,
    val isInsideContentThinkingTag: Boolean = false,
    val pendingContent: String = "",
    val shouldStripLeadingSeparator: Boolean = false
)

private val thinkingTagPattern = Regex("(?s)<thinking>(.*?)</thinking>")
private const val THINKING_OPEN_TAG = "<thinking>"
private const val THINKING_CLOSE_TAG = "</thinking>"
private val openRouterExcludedToolNames = setOf(
    "__say_to_user__",
    "__ask_user__",
    "__exit__"
)

/**
 * 去掉 thinking 块结束后正文前可能附带的首个空行。
 */
private fun stripLeadingThinkingSeparator(text: String): String {
    return text
        .removePrefix("\r\n")
        .removePrefix("\n")
}

/**
 * 从完整助手消息中拆出 thinking 片段和最终可展示正文。
 */
private fun extractThinkingTaggedContent(content: String): Pair<String, String> {
    val matches = thinkingTagPattern.findAll(content).toList()
    if (matches.isEmpty()) return "" to content

    val thinkingContent = matches.joinToString(separator = "") { it.groupValues[1] }
    val plainContent = stripLeadingThinkingSeparator(
        content.replace(thinkingTagPattern, "")
    )
    return thinkingContent to plainContent
}

/**
 * 在流式分片尾部查找一个标签的“部分命中”起点，处理跨 chunk 标签。
 */
private fun findPartialTagStart(text: String, tag: String): Int {
    val searchStart = (text.length - tag.length + 1).coerceAtLeast(0)
    for (index in searchStart until text.length) {
        if (tag.startsWith(text.substring(index))) {
            return index
        }
    }
    return -1
}

/**
 * 把普通正文追加到最终回复缓冲区，并在需要时裁掉首个空行。
 */
private fun appendPlainStreamingContent(
    text: String,
    responseBuilder: StringBuilder,
    state: OpenRouterThinkingState,
    emitChunk: (String) -> Unit
): OpenRouterThinkingState {
    val normalizedText = if (state.shouldStripLeadingSeparator) {
        stripLeadingThinkingSeparator(text)
    } else {
        text
    }

    if (normalizedText.isNotEmpty()) {
        responseBuilder.append(normalizedText)
        emitChunk(normalizedText)
    }

    return state.copy(shouldStripLeadingSeparator = false)
}

/**
 * 逐块解析带 `<thinking>` 标签的流式内容，维护 thinking 与正文的切换状态。
 */
private fun processThinkingTaggedContentChunk(
    contentChunk: String,
    responseBuilder: StringBuilder,
    state: OpenRouterThinkingState,
    emitChunk: (String) -> Unit
): OpenRouterThinkingState {
    var nextState = state
    var remaining = state.pendingContent + contentChunk
    nextState = nextState.copy(pendingContent = "")

    while (remaining.isNotEmpty()) {
        if (nextState.isInsideContentThinkingTag) {
            val closeIndex = remaining.indexOf(THINKING_CLOSE_TAG)
            if (closeIndex >= 0) {
                val thinkingText = remaining.substring(0, closeIndex)
                if (thinkingText.isNotEmpty()) {
                    emitChunk(thinkingText)
                }
                remaining = remaining.substring(closeIndex + THINKING_CLOSE_TAG.length)
                nextState = nextState.copy(
                    isInsideContentThinkingTag = false
                )
                continue
            }

            val partialCloseIndex = findPartialTagStart(remaining, THINKING_CLOSE_TAG)
            if (partialCloseIndex >= 0) {
                val thinkingText = remaining.substring(0, partialCloseIndex)
                if (thinkingText.isNotEmpty()) {
                    emitChunk(thinkingText)
                }
                nextState = nextState.copy(pendingContent = remaining.substring(partialCloseIndex))
                return nextState
            }

            emitChunk(remaining)
            return nextState
        }

        val openIndex = remaining.indexOf(THINKING_OPEN_TAG)
        if (openIndex >= 0) {
            val plainPrefix = remaining.substring(0, openIndex)
            if (plainPrefix.isNotEmpty() && nextState.isThinkingOpen) {
                emitChunk("$THINKING_CLOSE_TAG\n")
                nextState = nextState.copy(
                    isThinkingOpen = false,
                    shouldStripLeadingSeparator = true
                )
            }
            nextState = appendPlainStreamingContent(
                text = plainPrefix,
                responseBuilder = responseBuilder,
                state = nextState,
                emitChunk = emitChunk
            )
            if (!nextState.isThinkingOpen) {
                emitChunk(THINKING_OPEN_TAG)
            }
            remaining = remaining.substring(openIndex + THINKING_OPEN_TAG.length)
            nextState = nextState.copy(
                isThinkingOpen = true,
                isInsideContentThinkingTag = true
            )
            continue
        }

        val partialOpenIndex = findPartialTagStart(remaining, THINKING_OPEN_TAG)
        if (partialOpenIndex >= 0) {
            val plainPrefix = remaining.substring(0, partialOpenIndex)
            if (plainPrefix.isNotEmpty() && nextState.isThinkingOpen) {
                emitChunk("$THINKING_CLOSE_TAG\n")
                nextState = nextState.copy(
                    isThinkingOpen = false,
                    shouldStripLeadingSeparator = true
                )
            }
            nextState = appendPlainStreamingContent(
                text = plainPrefix,
                responseBuilder = responseBuilder,
                state = nextState,
                emitChunk = emitChunk
            )
            nextState = nextState.copy(pendingContent = remaining.substring(partialOpenIndex))
            return nextState
        }

        if (nextState.isThinkingOpen) {
            emitChunk("$THINKING_CLOSE_TAG\n")
            nextState = nextState.copy(
                isThinkingOpen = false,
                shouldStripLeadingSeparator = true
            )
        }
        nextState = appendPlainStreamingContent(
            text = remaining,
            responseBuilder = responseBuilder,
            state = nextState,
            emitChunk = emitChunk
        )
        return nextState
    }

    return nextState.copy(pendingContent = "")
}

/**
 * 在流结束时清空残留的 pending 内容，并补齐未关闭的 thinking 标签。
 */
internal fun finalizeThinkingTaggedContentStream(
    responseBuilder: StringBuilder,
    state: OpenRouterThinkingState,
    emitChunk: (String) -> Unit = ::printStreamingChunk
): OpenRouterThinkingState {
    var nextState = state
    if (state.pendingContent.isNotEmpty()) {
        if (state.isInsideContentThinkingTag) {
            emitChunk(state.pendingContent)
            nextState = nextState.copy(
                pendingContent = "",
                isInsideContentThinkingTag = false
            )
        } else {
            if (state.isThinkingOpen) {
                emitChunk(THINKING_CLOSE_TAG)
                nextState = nextState.copy(
                    isThinkingOpen = false,
                    shouldStripLeadingSeparator = false,
                    pendingContent = ""
                )
            }
            nextState = appendPlainStreamingContent(
                text = state.pendingContent,
                responseBuilder = responseBuilder,
                state = nextState.copy(pendingContent = ""),
                emitChunk = emitChunk
            )
        }
    }

    if (nextState.isThinkingOpen) {
        emitChunk(THINKING_CLOSE_TAG)
        nextState = nextState.copy(
            isThinkingOpen = false,
            isInsideContentThinkingTag = false,
            shouldStripLeadingSeparator = false
        )
    }

    return nextState.copy(pendingContent = "")
}

/**
 * 将推理文本与最终回答组装为带 thinking 标签的助手消息。
 */
internal fun buildTaggedAssistantResponse(
    reasoningTexts: List<String>,
    summaryTexts: List<String>,
    content: String
): String {
    val (thinkingFromContent, plainContent) = extractThinkingTaggedContent(content)
    val thinkingContent = (reasoningTexts + summaryTexts).joinToString(separator = "")
        .ifBlank { thinkingFromContent }

    return when {
        thinkingContent.isBlank() -> plainContent
        plainContent.isBlank() -> "<thinking>$thinkingContent</thinking>"
        else -> "<thinking>$thinkingContent</thinking>\n$plainContent"
    }
}

/**
 * 将单个工具参数描述转换为 OpenRouter 所需的 JSON Schema。
 */
private fun buildOpenRouterParameterSchema(parameter: ToolParameterDescriptor): JsonObject {
    /**
     * 把 Koog 的工具参数类型映射为 OpenRouter 兼容的 JSON Schema 片段。
     */
    fun buildTypeSchema(type: ToolParameterType): JsonObject {
        return when (type) {
            is ToolParameterType.String -> buildJsonObject { put("type", "string") }
            is ToolParameterType.Integer -> buildJsonObject { put("type", "integer") }
            is ToolParameterType.Float -> buildJsonObject { put("type", "number") }
            is ToolParameterType.Boolean -> buildJsonObject { put("type", "boolean") }
            is ToolParameterType.Null -> buildJsonObject { put("type", "null") }
            is ToolParameterType.Enum -> buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    type.entries.forEach { add(JsonPrimitive(it)) }
                }
            }

            is ToolParameterType.List -> buildJsonObject {
                put("type", "array")
                put("items", buildTypeSchema(type.itemsType))
            }

            is ToolParameterType.Object -> buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    type.properties.forEach { property ->
                        put(property.name, buildOpenRouterParameterSchema(property))
                    }
                }
                if (type.requiredProperties.isNotEmpty()) {
                    putJsonArray("required") {
                        type.requiredProperties.forEach { add(JsonPrimitive(it)) }
                    }
                }
                type.additionalProperties?.let { additionalProperties ->
                    put("additionalProperties", additionalProperties)
                }
                type.additionalPropertiesType?.let { additionalType ->
                    put("additionalProperties", buildTypeSchema(additionalType))
                }
            }

            is ToolParameterType.AnyOf -> buildJsonObject {
                putJsonArray("anyOf") {
                    type.types.forEach { anyOfType ->
                        add(buildOpenRouterParameterSchema(anyOfType))
                    }
                }
            }

            else -> buildJsonObject { put("type", type.name.lowercase()) }
        }
    }

    return buildTypeSchema(parameter.type).let { schema ->
        buildJsonObject {
            schema.forEach { (key, value) -> put(key, value) }
            if (parameter.description.isNotBlank()) {
                put("description", parameter.description)
            }
        }
    }
}

/**
 * 构造 OpenRouter 所需的工具列表 Schema，并过滤掉会话控制类工具。
 */
internal fun buildOpenRouterToolsSchema(toolRegistry: ToolRegistry): JsonArray {
    return buildJsonArray {
        toolRegistry.tools
            .filterNot { tool -> tool.name in openRouterExcludedToolNames }
            .forEach { tool ->
            val descriptor = tool.descriptor
            val parameters = descriptor.requiredParameters + descriptor.optionalParameters
            add(
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", descriptor.description)
                        putJsonObject("parameters") {
                            put("type", "object")
                            putJsonObject("properties") {
                                parameters.forEach { parameter ->
                                    put(parameter.name, buildOpenRouterParameterSchema(parameter))
                                }
                            }
                            if (descriptor.requiredParameters.isNotEmpty()) {
                                putJsonArray("required") {
                                    descriptor.requiredParameters.forEach { add(JsonPrimitive(it.name)) }
                                }
                            }
                            put("additionalProperties", false)
                        }
                    }
                }
            )
        }
    }
}

/**
 * 构造 OpenRouter 聊天请求体。
 */
internal fun buildOpenRouterChatRequest(
    messages: List<Message>,
    stream: Boolean,
    toolRegistry: ToolRegistry
): JsonObject {
    return buildJsonObject {
        put("model", CHAT_MODEL.id)
        put("temperature", JsonPrimitive(CHAT_TEMPERATURE))
        put("stream", JsonPrimitive(stream))
        put("reasoning", buildOpenRouterReasoningConfig())
        put("tools", buildOpenRouterToolsSchema(toolRegistry))
        put(
            "messages",
            buildJsonArray {
                messages.mapNotNull(::toOpenRouterMessage).forEach(::add)
            }
        )
    }
}

/**
 * 从 reasoning_details 中拆出推理正文与摘要。
 */
internal fun extractOpenRouterReasoningDetails(reasoningDetails: JsonArray?): Pair<List<String>, List<String>> {
    if (reasoningDetails == null) return emptyList<String>() to emptyList()

    val reasoningTexts = mutableListOf<String>()
    val summaryTexts = mutableListOf<String>()

    reasoningDetails.forEach { detail ->
        val detailObject = detail.jsonObject
        when (detailObject["type"]?.jsonPrimitive?.contentOrNull) {
            "reasoning.text" -> {
                detailObject["text"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?.let(reasoningTexts::add)
            }

            "reasoning.summary" -> {
                detailObject["summary"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?.let(summaryTexts::add)
            }
        }
    }

    return reasoningTexts to summaryTexts
}

/**
 * 解析单个 OpenRouter SSE 事件负载并追加到输出缓存。
 */
internal fun processOpenRouterStreamPayload(
    payload: String,
    responseBuilder: StringBuilder,
    reasoningSteps: MutableList<String>,
    summarySteps: MutableList<String>,
    state: OpenRouterThinkingState,
    emitChunk: (String) -> Unit = ::printStreamingChunk
): OpenRouterThinkingState {
    val root = openRouterJson.parseToJsonElement(payload).jsonObject
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return state
    val delta = choice["delta"]?.jsonObject ?: return state
    val (reasoningTexts, summaryTexts) = extractOpenRouterReasoningDetails(delta["reasoning_details"]?.jsonArray)
    var nextState = state

    if ((reasoningTexts.isNotEmpty() || summaryTexts.isNotEmpty()) && !nextState.isThinkingOpen) {
        emitChunk("<thinking>")
        nextState = nextState.copy(isThinkingOpen = true)
    }

    reasoningTexts.forEach {
        reasoningSteps.add(it)
        emitChunk(it)
    }

    summaryTexts.forEach {
        summarySteps.add(it)
        emitChunk(it)
    }

    delta["content"]?.jsonPrimitive?.contentOrNull?.let {
        nextState = processThinkingTaggedContentChunk(
            contentChunk = it,
            responseBuilder = responseBuilder,
            state = nextState,
            emitChunk = emitChunk
        )
    }

    return nextState
}

/**
 * 解析非流式 OpenRouter 响应中的推理信息和最终文本。
 */
internal fun extractOpenRouterResponse(responseBody: String): Triple<List<String>, List<String>, String> {
    val root = openRouterJson.parseToJsonElement(responseBody).jsonObject
    val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        ?: return Triple(emptyList(), emptyList(), "")
    val (reasoningTexts, summaryTexts) = extractOpenRouterReasoningDetails(message["reasoning_details"]?.jsonArray)
    val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return Triple(reasoningTexts, summaryTexts, content)
}

/**
 * 根据 API Key 和请求体创建 OpenRouter HTTP 请求。
 */
internal fun createOpenRouterRequest(apiKey: String, requestBody: JsonObject): HttpRequest {
    return HttpRequest.newBuilder(OPENROUTER_CHAT_COMPLETIONS_URI)
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(90))
        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
        .build()
}

/**
 * 构造包含状态码和响应体的统一异常对象。
 */
internal fun openRouterStatusError(statusCode: Int, body: String): IllegalStateException {
    return IllegalStateException(
        "Error from client: OpenRouterRawClient\n" +
            "Message: Expected status code 200 but was $statusCode\n" +
            "Status code: $statusCode\n" +
            "Error body: $body"
    )
}

/**
 * 将历史消息与当前输入组装成一轮普通聊天提示词。
 */
private fun buildChatPrompt(history: List<Message>, input: String) = prompt("chat-session") {
    system(SYSTEM_PROMPT)

    history.forEach { message ->
        when (message) {
            is Message.User -> user(message.content)
            is Message.Assistant -> assistant(message.content)
            else -> Unit
        }
    }

    user(input)
}

/**
 * 通过 OpenRouter 流式接口执行普通聊天请求。
 */
internal suspend fun runStreamingChat(
    apiKey: String,
    chatHistoryProvider: ChatHistoryProvider,
    toolRegistry: ToolRegistry,
    input: String,
    sessionId: String
): String {
    val history = chatHistoryProvider.load(sessionId).takeLast(CHAT_HISTORY_WINDOW_SIZE)
    val prompt = buildChatPrompt(history, input)
    val requestBody = buildOpenRouterChatRequest(prompt.messages, stream = true, toolRegistry = toolRegistry)
    val reasoningSteps = mutableListOf<String>()
    val summarySteps = mutableListOf<String>()
    val responseBuilder = StringBuilder()
    var thinkingState = OpenRouterThinkingState()

    println("[事件] 发送请求到 LLM: ${input.take(30)}...")

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
                state = thinkingState
            )
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

    thinkingState = finalizeThinkingTaggedContentStream(
        responseBuilder = responseBuilder,
        state = thinkingState
    )
    val response = responseBuilder.toString()
    val taggedResponse = buildTaggedAssistantResponse(reasoningSteps, summarySteps, response)
    println("[事件] LLM 响应成功，模型: $CHAT_MODEL")
    println("[事件] $CHAT_MODEL 智能体运行完成！")
    persistChatTurn(chatHistoryProvider, sessionId, history, input, taggedResponse)
    return taggedResponse
}

/**
 * 通过 OpenRouter 非流式接口执行普通聊天请求。
 */
internal suspend fun runNonStreamingChat(
    apiKey: String,
    chatHistoryProvider: ChatHistoryProvider,
    toolRegistry: ToolRegistry,
    input: String,
    sessionId: String
): String {
    val history = chatHistoryProvider.load(sessionId).takeLast(CHAT_HISTORY_WINDOW_SIZE)
    val prompt = buildChatPrompt(history, input)
    val requestBody = buildOpenRouterChatRequest(prompt.messages, stream = false, toolRegistry = toolRegistry)

    println("[事件] 发送请求到 LLM: ${input.take(30)}...")
    val request = createOpenRouterRequest(apiKey, requestBody)
    val httpResponse = withContext(Dispatchers.IO) {
        openRouterHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    if (httpResponse.statusCode() != 200) {
        throw openRouterStatusError(httpResponse.statusCode(), httpResponse.body())
    }

    val (reasoningTexts, summaryTexts, response) = extractOpenRouterResponse(httpResponse.body())
    val taggedResponse = buildTaggedAssistantResponse(reasoningTexts, summaryTexts, response)
    println("[事件] LLM 响应成功，模型: $CHAT_MODEL")
    println("[事件] $CHAT_MODEL 智能体运行完成！")

    persistChatTurn(chatHistoryProvider, sessionId, history, input, taggedResponse)
    return taggedResponse
}
