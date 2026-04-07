package agent

import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ai.koog.rag.base.files.JVMFileSystemProvider
import java.io.File
import mulehang.config.AppConfig
import mulehang.config.ProviderConfig
import mulehang.provider.ExecutorFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证 agent 包拆分后的关键行为保持不变。
 */
class MySimpleAgentTest {
    @Test
    fun `should build default provider binding from config`() {
        val cfg = AppConfig(
            defaultProvider = "openrouter",
            defaultModel = "openrouter.gpt4o",
            providers = mapOf(
                "openrouter" to ProviderConfig(
                    apiKey = "abc",
                    baseUrl = "https://openrouter.ai/api/v1"
                )
            )
        )

        val binding = ExecutorFactory(cfg).defaultBinding()

        assertEquals("openrouter", binding.providerId)
        assertEquals("openrouter.gpt4o", binding.modelId)
        assertEquals("abc", binding.apiKey)
    }

    /**
     * 校验聊天历史存储只会保留可复用的用户与助手消息。
     */
    @Test
    fun `should store and load only reusable chat messages`() = runBlocking {
        val storageDir = File("build/tmp/test-chat-history").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val provider = FileChatHistoryProvider(storageDir.absolutePath)
            val storedMessages = prompt("chat-history-store") {
                system("系统提示")
                user("你好")
                assistant("你好，请问有什么可以帮你？")
            }.messages
            val expectedMessages = retainChatMessages(storedMessages)

            provider.store(
                conversationId = "session-1",
                messages = storedMessages
            )

            assertEquals(
                expectedMessages,
                provider.load("session-1")
            )
        } finally {
            storageDir.deleteRecursively()
        }
    }

    /**
     * 校验 OpenRouter 请求体包含预期的 reasoning 配置。
     */
    @Test
    fun `should include reasoning config in OpenRouter request body`() {
        val requestBody = buildOpenRouterChatRequest(
            messages = prompt("chat-session") {
                system("系统提示")
                user("你好")
            }.messages,
            stream = true,
            toolRegistry = ToolRegistryBuilder().build()
        )
        val reasoning = requestBody["reasoning"]?.jsonObject

        assertEquals(CHAT_MODEL_ID, requestBody["model"]?.jsonPrimitive?.content)
        assertEquals(true, requestBody["stream"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertEquals(0.7, requestBody["temperature"]?.jsonPrimitive?.content?.toDouble())
        assertEquals("high", reasoning?.get("effort")?.jsonPrimitive?.content)
        assertEquals(false, reasoning?.get("exclude")?.jsonPrimitive?.content?.toBooleanStrict())
    }

    @Test
    fun `should include tools schema in OpenRouter request body`() {
        val toolRegistry = ToolRegistryBuilder()
            .tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            .build()

        val requestBody = buildOpenRouterChatRequest(
            messages = prompt("chat-session") {
                system("系统提示")
                user("读取 build.gradle.kts")
            }.messages,
            stream = true,
            toolRegistry = toolRegistry
        )
        val tools = requestBody["tools"]?.jsonArray ?: error("missing tools")
        val function = tools.single().jsonObject["function"]?.jsonObject ?: error("missing function")

        assertEquals("function", tools.single().jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(toolRegistry.tools.single().name, function["name"]?.jsonPrimitive?.content)
        assertTrue(function["description"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
        assertTrue(function["parameters"] != null)
    }

    /**
     * 校验流式事件负载中的推理与最终文本都能被正确提取。
     */
    @Test
    fun `should extract reasoning and content from OpenRouter stream payload`() {
        val responseBuilder = StringBuilder()
        val reasoningSteps = mutableListOf<String>()
        val summarySteps = mutableListOf<String>()
        val emittedChunks = mutableListOf<String>()
        var state = OpenRouterThinkingState()

        state = processOpenRouterStreamPayload(
            payload = """
                {
                  "choices": [
                    {
                      "delta": {
                        "reasoning_details": [
                          {"type": "reasoning.text", "text": "先分析"},
                          {"type": "reasoning.summary", "summary": "分析中"}
                        ],
                        "content": "最终答案"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            responseBuilder = responseBuilder,
            reasoningSteps = reasoningSteps,
            summarySteps = summarySteps,
            state = state,
            emitChunk = emittedChunks::add
        )

        assertEquals(listOf("先分析"), reasoningSteps)
        assertEquals(listOf("分析中"), summarySteps)
        assertEquals("最终答案", responseBuilder.toString())
        assertEquals(listOf("<thinking>", "先分析", "分析中", "</thinking>\n", "最终答案"), emittedChunks)
        assertFalse(state.isThinkingOpen)
    }

    /**
     * 校验非流式响应中的推理信息和内容提取逻辑。
     */
    @Test
    fun `should extract reasoning details from non streaming OpenRouter response`() {
        val (reasoningTexts, summaryTexts, content) = extractOpenRouterResponse(
            """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "最终回答",
                        "reasoning_details": [
                          {"type": "reasoning.text", "text": "先列条件"},
                          {"type": "reasoning.summary", "summary": "得到结论"}
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent()
        )

        assertEquals(listOf("先列条件"), reasoningTexts)
        assertEquals(listOf("得到结论"), summaryTexts)
        assertEquals("最终回答", content)
    }

    /**
     * 校验推理与正文会被组装为带 thinking 标签的助手回复。
     */
    @Test
    fun `should wrap reasoning into thinking tags for assistant response`() {
        val response = buildTaggedAssistantResponse(
            reasoningTexts = listOf("先列条件"),
            summaryTexts = listOf("再推导"),
            content = "最终回答"
        )

        assertEquals("<thinking>先列条件再推导</thinking>\n最终回答", response)
    }

    /**
     * 校验模型若直接在 content 中输出多段 thinking 标签，仍会被合并为一对标签。
     */
    @Test
    fun `should collapse repeated thinking tags from content`() {
        val response = buildTaggedAssistantResponse(
            reasoningTexts = emptyList(),
            summaryTexts = emptyList(),
            content = "<thinking>Here</thinking><thinking>'s a thinking process</thinking>\n我是助手"
        )

        assertEquals("<thinking>Here's a thinking process</thinking>\n我是助手", response)
    }

    /**
     * 校验持久化后的助手消息在下一轮请求体中仍保留 thinking 标签。
     */
    @Test
    fun `should keep thinking tags when assistant history is sent back to openrouter`() = runBlocking {
        val storageDir = File("build/tmp/test-openrouter-thinking-history").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val provider = FileChatHistoryProvider(storageDir.absolutePath)
            val wrappedResponse = "<thinking>先分析再推导</thinking>\n最终答案"

            persistChatTurn(
                chatHistoryProvider = provider,
                sessionId = "session-1",
                history = emptyList(),
                input = "帮我计算",
                response = wrappedResponse
            )

            val requestBody = buildOpenRouterChatRequest(
                messages = provider.load("session-1"),
                stream = true,
                toolRegistry = ToolRegistryBuilder().build()
            )
            val requestMessages = requestBody["messages"]?.jsonArray ?: error("missing messages")

            assertEquals("帮我计算", requestMessages[0].jsonObject["content"]?.jsonPrimitive?.content)
            assertEquals(wrappedResponse, requestMessages[1].jsonObject["content"]?.jsonPrimitive?.content)
        } finally {
            storageDir.deleteRecursively()
        }
    }

    /**
     * 校验跨多个 content chunk 的 repeated thinking 标签只输出一对包裹。
     */
    @Test
    fun `should stream repeated thinking tagged content with one wrapper pair`() {
        val responseBuilder = StringBuilder()
        val reasoningSteps = mutableListOf<String>()
        val summarySteps = mutableListOf<String>()
        val emittedChunks = mutableListOf<String>()
        var state = OpenRouterThinkingState()

        state = processOpenRouterStreamPayload(
            payload = """
                {
                  "choices": [
                    {
                      "delta": {
                        "content": "<thinking>Here</thinking>"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            responseBuilder = responseBuilder,
            reasoningSteps = reasoningSteps,
            summarySteps = summarySteps,
            state = state,
            emitChunk = emittedChunks::add
        )

        state = processOpenRouterStreamPayload(
            payload = """
                {
                  "choices": [
                    {
                      "delta": {
                        "content": "<thinking>'s a thinking process</thinking>\n我是助手"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            responseBuilder = responseBuilder,
            reasoningSteps = reasoningSteps,
            summarySteps = summarySteps,
            state = state,
            emitChunk = emittedChunks::add
        )

        assertEquals(
            listOf("<thinking>", "Here", "'s a thinking process", "</thinking>\n", "我是助手"),
            emittedChunks
        )
        assertEquals("我是助手", responseBuilder.toString())
        assertFalse(state.isThinkingOpen)
    }

    /**
     * 校验拆散到多个 chunk 的 thinking 标签仍只输出一对包裹。
     */
    @Test
    fun `should stream split thinking tags with one wrapper pair`() {
        val responseBuilder = StringBuilder()
        val reasoningSteps = mutableListOf<String>()
        val summarySteps = mutableListOf<String>()
        val emittedChunks = mutableListOf<String>()
        var state = OpenRouterThinkingState()

        state = processOpenRouterStreamPayload(
            payload = """
                {
                  "choices": [
                    {
                      "delta": {
                        "content": "<thin"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            responseBuilder = responseBuilder,
            reasoningSteps = reasoningSteps,
            summarySteps = summarySteps,
            state = state,
            emitChunk = emittedChunks::add
        )

        state = processOpenRouterStreamPayload(
            payload = """
                {
                  "choices": [
                    {
                      "delta": {
                        "content": "king>1. 思考中\n2. 思考中\n</thi"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            responseBuilder = responseBuilder,
            reasoningSteps = reasoningSteps,
            summarySteps = summarySteps,
            state = state,
            emitChunk = emittedChunks::add
        )

        state = processOpenRouterStreamPayload(
            payload = """
                {
                  "choices": [
                    {
                      "delta": {
                        "content": "nking>\n我是..."
                      }
                    }
                  ]
                }
            """.trimIndent(),
            responseBuilder = responseBuilder,
            reasoningSteps = reasoningSteps,
            summarySteps = summarySteps,
            state = state,
            emitChunk = emittedChunks::add
        )

        assertEquals(
            listOf("<thinking>", "1. 思考中\n2. 思考中\n", "</thinking>\n", "我是..."),
            emittedChunks
        )
        assertEquals("我是...", responseBuilder.toString())
        assertFalse(state.isThinkingOpen)
    }

    /**
     * 校验普通聊天请求会直接进入流式执行分支。
     */
    @Test
    fun `should route simple chat to streaming runner directly`() = runBlocking {
        val notices = mutableListOf<String>()

        val result = runAgentByMode(
            input = "你好",
            sessionId = "session-1",
            streamingRunner = { input, _ -> "stream:$input" },
            toolRunner = { _, _ -> error("tool runner should not be used") },
            onToolModeSelected = notices::add
        )

        assertEquals("stream:你好", result.response)
        assertFalse(result.shouldPrintResponse)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `should route file questions to streaming runner directly`() = runBlocking {
        val notices = mutableListOf<String>()

        val result = runAgentByMode(
            input = "请读取 build.gradle.kts",
            sessionId = "session-1",
            streamingRunner = { input, _ -> "stream:$input" },
            toolRunner = { _, _ -> error("tool runner should not be used") },
            onToolModeSelected = notices::add
        )

        assertEquals("stream:请读取 build.gradle.kts", result.response)
        assertFalse(result.shouldPrintResponse)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `should always use unified streaming runner for file questions`() = runBlocking {
        val notices = mutableListOf<String>()
        var streamingCalls = 0
        var toolCalls = 0

        val result = runAgentByMode(
            input = "读取 build.gradle.kts",
            sessionId = "session-1",
            streamingRunner = { input, _ ->
                streamingCalls += 1
                "stream:$input"
            },
            toolRunner = { input, _ ->
                toolCalls += 1
                "tool:$input"
            },
            onToolModeSelected = notices::add
        )

        assertEquals("stream:读取 build.gradle.kts", result.response)
        assertFalse(result.shouldPrintResponse)
        assertEquals(1, streamingCalls)
        assertEquals(0, toolCalls)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `should include current working directory in system prompt`() {
        assertTrue(SYSTEM_PROMPT.contains(CURRENT_WORKING_DIRECTORY))
    }

    /**
     * 校验 400 错误会被识别为可回退的流式失败。
     */
    @Test
    fun `should fallback when streaming request gets http 400`() {
        val error = IllegalStateException(
            "Error from client: OpenRouterLLMClient\nStatus code: 400\nMessage: Expected status code 200 but was 400"
        )

        assertTrue(shouldFallbackToNonStreaming(error))
    }

    /**
     * 校验带错误响应体的 400 异常同样会触发回退判断。
     */
    @Test
    fun `should fallback for provider specific 400 error body`() {
        val error = IllegalStateException(
            """
            Error from client: OpenRouterLLMClient Status code: 400 Error body:
            {"error":{"message":"Upstream provider rejected the request.","type":"invalid_request_error"}}
            """.trimIndent()
        )

        assertTrue(shouldFallbackToNonStreaming(error))
    }

    /**
     * 校验 429 限流错误同样会触发非流式回退。
     */
    @Test
    fun `should fallback when streaming request gets http 429`() {
        val error = IllegalStateException(
            """
            Error from client: OpenRouterRawClient
            Message: Expected status code 200 but was 429
            Status code: 429
            Error body: {"error":{"message":"Provider returned error","code":429}}
            """.trimIndent()
        )

        assertTrue(shouldFallbackToNonStreaming(error))
    }

    /**
     * 校验非 400 异常不会误触发非流式回退。
     */
    @Test
    fun `should not fallback for unrelated errors`() {
        val error = IllegalStateException("Status code: 401")

        assertFalse(shouldFallbackToNonStreaming(error))
    }

    /**
     * 校验流式失败后会改用兜底执行器并返回可打印结果。
     */
    @Test
    fun `should retry with fallback runner after streaming 400 failure`() = runBlocking {
        val notices = mutableListOf<String>()

        val result = runAgentWithFallback(
            input = "你好",
            sessionId = "session-1",
            streamingRunner = { _, _ ->
                throw IllegalStateException("Expected status code 200 but was 400")
            },
            fallbackRunner = { input, sessionId -> "fallback:$sessionId:$input" },
            onFallback = notices::add
        )

        assertEquals("fallback:session-1:你好", result.response)
        assertTrue(result.shouldPrintResponse)
        assertEquals(listOf(STREAMING_FALLBACK_NOTICE), notices)
    }

    /**
     * 校验 429 流式失败后也会改用兜底执行器并返回可打印结果。
     */
    @Test
    fun `should retry with fallback runner after streaming 429 failure`() = runBlocking {
        val notices = mutableListOf<String>()

        val result = runAgentWithFallback(
            input = "解释项目",
            sessionId = "session-1",
            streamingRunner = { _, _ ->
                throw IllegalStateException("Expected status code 200 but was 429")
            },
            fallbackRunner = { input, sessionId -> "fallback:$sessionId:$input" },
            onFallback = notices::add
        )

        assertEquals("fallback:session-1:解释项目", result.response)
        assertTrue(result.shouldPrintResponse)
        assertEquals(listOf(STREAMING_FALLBACK_NOTICE), notices)
    }

    @Test
    fun `should swallow recoverable cli failure after fallback also fails with 429`() = runBlocking {
        val notices = mutableListOf<String>()

        val result = runAgentCliTurn(
            execute = {
                runAgentWithFallback(
                    input = "解释这个项目",
                    sessionId = "session-1",
                    streamingRunner = { _, _ ->
                        throw IllegalStateException("Expected status code 200 but was 429")
                    },
                    fallbackRunner = { _, _ ->
                        throw IllegalStateException(
                            """
                            Error from client: OpenRouterRawClient
                            Message: Expected status code 200 but was 429
                            Status code: 429
                            Error body: {"error":{"message":"Provider returned error","code":429}}
                            """.trimIndent()
                        )
                    },
                    onFallback = notices::add
                )
            },
            onError = notices::add
        )

        assertEquals(null, result)
        assertEquals(
            listOf(
                STREAMING_FALLBACK_NOTICE,
                "[系统] OpenRouter 当前被上游限流（HTTP 429），本次请求未完成，请稍后重试。"
            ),
            notices
        )
    }

    /**
     * 校验普通聊天在回退时会使用专门的聊天兜底执行器。
     */
    @Test
    fun `should use dedicated fallback runner for non tool chat`() = runBlocking {
        val notices = mutableListOf<String>()

        val result = runAgentByMode(
            input = "你好",
            sessionId = "session-1",
            streamingRunner = { _, _ ->
                throw IllegalStateException("Error from client: OpenRouterLLMClient Status code: 400")
            },
            toolRunner = { _, _ -> error("tool runner should not be used as chat fallback") },
            fallbackRunner = { input, sessionId -> "fallback:$sessionId:$input" },
            onFallback = notices::add
        )

        assertEquals("fallback:session-1:你好", result.response)
        assertTrue(result.shouldPrintResponse)
        assertEquals(listOf(STREAMING_FALLBACK_NOTICE), notices)
    }

    /**
     * 校验不可回退的异常会继续向外抛出。
     */
    @Test
    fun `should rethrow non fallback errors`() {
        assertFailsWith<IllegalStateException> {
            runBlocking {
                runAgentWithFallback(
                    input = "你好",
                    sessionId = "session-1",
                    streamingRunner = { _, _ -> throw IllegalStateException("Status code: 401") },
                    fallbackRunner = { _, _ -> "ok" }
                )
            }
        }
    }

    @Test
    fun `should rethrow non recoverable cli failures`() {
        assertFailsWith<IllegalStateException> {
            runBlocking {
                runAgentCliTurn(
                    execute = { throw IllegalStateException("Status code: 401") }
                )
            }
        }
    }

    /**
     * 校验推理增量输出不会污染最终回复正文。
     */
    @Test
    fun `should print reasoning deltas without polluting final response`() {
        val responseBuilder = StringBuilder()
        val reasoningSteps = mutableListOf<String>()
        val summarySteps = mutableListOf<String>()
        val emittedChunks = mutableListOf<String>()
        val emittedLines = mutableListOf<String>()

        val state = processStreamingFrame(
            frame = StreamFrame.ReasoningDelta(text = "先分析题意", summary = "分析中"),
            responseBuilder = responseBuilder,
            reasoningSteps = reasoningSteps,
            summarySteps = summarySteps,
            state = StreamingFrameState(),
            emitChunk = emittedChunks::add,
            emitLine = emittedLines::add
        )

        assertEquals(listOf("先分析题意"), reasoningSteps)
        assertEquals(listOf("分析中"), summarySteps)
        assertEquals(listOf("先分析题意", "分析中"), emittedChunks)
        assertTrue(emittedLines.isEmpty())
        assertEquals("", responseBuilder.toString())
        assertTrue(state.hasReasoningTextDelta)
        assertTrue(state.hasReasoningSummaryDelta)
    }

    /**
     * 校验未收到增量时仍会输出完整推理内容。
     */
    @Test
    fun `should print completed reasoning when no delta arrived`() {
        val emittedChunks = mutableListOf<String>()
        val emittedLines = mutableListOf<String>()

        processStreamingFrame(
            frame = StreamFrame.ReasoningComplete(
                text = listOf("先列条件", "再做推导"),
                summary = listOf("得到结论")
            ),
            responseBuilder = StringBuilder(),
            reasoningSteps = mutableListOf(),
            summarySteps = mutableListOf(),
            state = StreamingFrameState(),
            emitChunk = emittedChunks::add,
            emitLine = emittedLines::add
        )

        assertTrue(emittedChunks.isEmpty())
        assertEquals(listOf("先列条件再做推导", "得到结论"), emittedLines)
    }

    /**
     * 校验推理增量帧的诊断描述包含关键字段。
     */
    @Test
    fun `should describe reasoning delta frame for diagnostics`() {
        val description = describeStreamingFrame(
            StreamFrame.ReasoningDelta(text = "先分析", summary = "分析中")
        )

        assertTrue(description.contains("ReasoningDelta"))
        assertTrue(description.contains("先分析"))
        assertTrue(description.contains("分析中"))
    }
}
