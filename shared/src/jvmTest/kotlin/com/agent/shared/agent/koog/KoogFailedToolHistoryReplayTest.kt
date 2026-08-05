package com.agent.shared.agent.koog

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.time.KoogClock
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.agent.prompt.buildPromptParams
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 回归测试：全部工具调用失败的回合结束后，下一轮请求回放历史时必须为每个
 * function_call 补齐 function_call_output，否则 DeepSeek 会以
 * "No tool output found for tool call X" 400 拒绝。
 */
class KoogFailedToolHistoryReplayTest {

    private val createdEvent = """{"type":"response.created","response":{"id":"r1","object":"response","created_at":1785771440,"status":"in_progress","background":false,"completed_at":null,"content_filters":null,"error":null,"frequency_penalty":0.0,"incomplete_details":null,"instructions":null,"max_output_tokens":null,"max_tool_calls":null,"model":"deepseek-v4-flash","moderation":null,"output":[],"parallel_tool_calls":true,"presence_penalty":0.0,"previous_response_id":null,"prompt_cache_key":null,"prompt_cache_retention":null,"reasoning":{"effort":null,"summary":null},"safety_identifier":null,"service_tier":"default","store":false,"temperature":1.0,"text":{"format":{"type":"text"},"verbosity":null},"tool_choice":"auto","tools":[],"top_logprobs":0,"top_p":1.0,"truncation":"disabled","usage":null,"user":null,"metadata":{}},"sequence_number":0}"""

    private val completedEvent = """{"type":"response.completed","response":{"id":"r1","object":"response","created_at":1785771440,"status":"completed","background":false,"completed_at":1785771441,"content_filters":null,"error":null,"frequency_penalty":0.0,"incomplete_details":null,"instructions":null,"max_output_tokens":null,"max_tool_calls":null,"model":"deepseek-v4-flash","moderation":null,"output":[],"parallel_tool_calls":true,"presence_penalty":0.0,"previous_response_id":null,"prompt_cache_key":null,"prompt_cache_retention":null,"reasoning":{"effort":null,"summary":null},"safety_identifier":null,"service_tier":"default","store":false,"temperature":1.0,"text":{"format":{"type":"text"},"verbosity":null},"tool_choice":"auto","tools":[],"top_logprobs":0,"top_p":1.0,"truncation":"disabled","usage":{"input_tokens":10,"input_tokens_details":{"cached_tokens":0},"output_tokens":10,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":20},"user":null,"metadata":{}},"sequence_number":1}"""

    private val roundEvents = listOf(
        createdEvent,
        """{"type":"response.output_text.delta","content_index":0,"delta":"ok","item_id":"item-1","output_index":0,"sequence_number":1}""",
        """{"type":"response.output_item.done","item":{"type":"message","id":"item-1","status":"completed","content":[{"type":"output_text","annotations":[],"text":"ok"}],"role":"assistant"},"output_index":0,"sequence_number":2}""",
        completedEvent,
    )

    /**
     * 捕获每次 sse 请求体的录制型 HTTP client。
     */
    private class RecordingKoogHttpClient(
        private val rounds: List<List<String>>,
        val capturedBodies: MutableList<String> = mutableListOf(),
    ) : KoogHttpClient {
        override val clientName: String = "recording"
        private var callCount = 0

        override fun <T : Any, R : Any, O : Any> sse(
            path: String,
            requestBody: T,
            requestBodyType: KClass<T>,
            dataFilter: (String?) -> Boolean,
            decodeStreamingResponse: (String) -> R,
            processStreamingChunk: (R) -> O?,
            parameters: Map<String, String>,
            headers: Map<String, String>,
        ): Flow<O> = flow {
            capturedBodies += requestBody.toString()
            val events = rounds[minOf(callCount++, rounds.lastIndex)]
            events.forEach { raw ->
                val data = raw.trim()
                if (dataFilter(data)) {
                    val decoded = decodeStreamingResponse(data)
                    processStreamingChunk(decoded)?.let { emit(it) }
                }
            }
        }

        override suspend fun <R : Any> get(
            path: String,
            responseType: KClass<R>,
            parameters: Map<String, String>,
            headers: Map<String, String>,
        ): R = error("get is not used in this test")

        override suspend fun <T : Any, R : Any> post(
            path: String,
            requestBody: T,
            requestBodyType: KClass<T>,
            responseType: KClass<R>,
            parameters: Map<String, String>,
            headers: Map<String, String>,
        ): R = error("post is not used in this test")

        override fun <T : Any> lines(
            path: String,
            requestBody: T,
            requestBodyType: KClass<T>,
            parameters: Map<String, String>,
            headers: Map<String, String>,
        ): Flow<String> = error("lines is not used in this test")

        override fun close() = Unit
    }

    /**
     * 模拟 reducer 在全部工具失败后的历史：assistant 消息含 5 个 ToolCall part
     * （失败不产生 ToolResult part）与最终文本。
     */
    private fun failedToolsHistory(): List<AgentConversationHistoryMessage> = listOf(
        AgentConversationHistoryMessage.User("故意调用失败几个工具"),
        AgentConversationHistoryMessage.Assistant(
            parts = listOf(
                AgentConversationHistoryPart.Reasoning(summary = "先思考", rawText = "先思考一下"),
                AgentConversationHistoryPart.ToolCall(
                    id = "call-1",
                    name = "read_file",
                    argumentsPreview = """{"path":"/definitely/not/exist/file.txt"}""",
                ),
                AgentConversationHistoryPart.ToolCall(
                    id = "call-2",
                    name = "list_dir",
                    argumentsPreview = """{"path":"/no/such/directory"}""",
                ),
                AgentConversationHistoryPart.ToolCall(
                    id = "call-3",
                    name = "glob_files",
                    argumentsPreview = """{"pattern":"*.xyz", "path":"/nonexistent/root", "max_results":10}""",
                ),
                AgentConversationHistoryPart.ToolCall(
                    id = "call-4",
                    name = "grep_code",
                    argumentsPreview = """{"pattern":"nothing_matches_this", "path":"/missing/path"}""",
                ),
                AgentConversationHistoryPart.ToolCall(
                    id = "call-5",
                    name = "edit_file",
                    argumentsPreview = """{"path":"/not/there/readme.md"}""",
                ),
                AgentConversationHistoryPart.Text("已按你的要求故意调用失败的工具。"),
            ),
        ),
    )

    @Test
    fun `failed tool call history replays with a function_call_output for every function_call`() = runTest {
        val recording = RecordingKoogHttpClient(listOf(roundEvents))
        val factory = object : KoogHttpClient.Factory {
            override fun create(
                clientName: String,
                baseUrl: String,
                headers: Map<String, String>,
                queryParameters: Map<String, String>,
                requestTimeoutMillis: Long,
                connectTimeoutMillis: Long,
                socketTimeoutMillis: Long,
                json: Json,
            ): KoogHttpClient = SseTerminatorFilteringKoogHttpClient(recording)
        }
        val client = OpenAILLMClient(
            apiKey = "test-key",
            settings = OpenAIClientSettings(baseUrl = "https://api.deepseek.com"),
            httpClientFactory = factory,
        )
        val profile = ConfigProfile(
            id = "deepseek-main",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://api.deepseek.com",
            apiKey = "test-key",
            model = "deepseek-v4-flash",
            enabled = true,
            layer = ConfigLayer.PROJECT,
        )
        val model = buildLlmModel(profile)
        val params = buildPromptParams(profile, reasoningEffort = ReasoningEffort.HIGH)

        // 与 KoogStreamingStrategy.nodeCallLlm 一致：历史 + 新用户消息。
        val messages = buildConversationMessages(failedToolsHistory(), "总结一下刚才发生了什么")
        val prompt = Prompt.build(id = "replay", params = params) {
            system("You are a helpful assistant.")
            messages.forEach(::message)
        }

        collectAssistantMessageFromStream(
            frames = client.executeStreaming(prompt, model, tools = emptyList()),
            emitEvent = {},
        )

        val body = checkNotNull(recording.capturedBodies.singleOrNull())
        val input = Json.parseToJsonElement(body).jsonObject["input"]!!.jsonArray

        val functionCalls = input.filter { item ->
            item.jsonObject["type"]?.jsonPrimitive?.content == "function_call"
        }
        val outputs = input.filter { item ->
            item.jsonObject["type"]?.jsonPrimitive?.content == "function_call_output"
        }

        // 请求体必须保留 5 个 function_call，并为每个 call 提供匹配 call_id 的输出。
        assertEquals(5, functionCalls.size)
        assertEquals(5, outputs.size)
        functionCalls.forEach { callItem ->
            val callId = callItem.jsonObject["call_id"]?.jsonPrimitive?.content
            assertNotNull(callId, "function_call 必须带 call_id")
            val matchingOutput = outputs.any { outputItem ->
                outputItem.jsonObject["call_id"]?.jsonPrimitive?.content == callId
            }
            assertEquals(true, matchingOutput, "function_call $callId 缺少对应的 function_call_output")
        }

        // 工具轮次必须闭合在 assistant 文本（output_message）之前：
        // function_call 与 function_call_output 之间不允许插入其他 assistant 内容，
        // 否则 DeepSeek 会以 "No tool output found for tool call X" 400 拒绝。
        val lastFunctionCallIndex = input.indexOfLast { item ->
            item.jsonObject["type"]?.jsonPrimitive?.content == "function_call"
        }
        val firstOutputMessageIndex = input.indexOfFirst { item ->
            item.jsonObject["type"]?.jsonPrimitive?.content == "output_message"
        }
        val firstOutputIndex = input.indexOfFirst { item ->
            item.jsonObject["type"]?.jsonPrimitive?.content == "function_call_output"
        }
        assertEquals(-1, firstOutputMessageIndex, "回放请求体不应出现 output_message")
        assertEquals(lastFunctionCallIndex + 1, firstOutputIndex, "function_call_output 必须紧跟 function_call 闭合轮次")
    }
}
