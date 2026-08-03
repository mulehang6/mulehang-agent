package com.agent.shared.agent.koog

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.time.KoogClock
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 回归测试：用真实 Koog OpenAILLMClient + 录制型 HTTP 层跑完整三轮 Responses 请求，
 * 验证工具结果续传的请求体在规范化后以空文本 user 消息闭合工具轮次，
 * 满足 DeepSeek thinking mode 对 reasoning_text 回传的校验。
 */
class KoogResponsesReplayDiagnosticTest {

    private val createdEvent = """{"type":"response.created","response":{"id":"dd8c2dd7-d538-4ce1-9144-270cd81b408a","object":"response","created_at":1785771440,"status":"in_progress","background":false,"completed_at":null,"content_filters":null,"error":null,"frequency_penalty":0.0,"incomplete_details":null,"instructions":null,"max_output_tokens":null,"max_tool_calls":null,"model":"deepseek-v4-flash","moderation":null,"output":[],"parallel_tool_calls":true,"presence_penalty":0.0,"previous_response_id":null,"prompt_cache_key":null,"prompt_cache_retention":null,"reasoning":{"effort":null,"summary":null},"safety_identifier":null,"service_tier":"default","store":false,"temperature":1.0,"text":{"format":{"type":"text"},"verbosity":null},"tool_choice":"auto","tools":[{"type":"function","name":"list_dir","description":"列出目录内容","parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]},"strict":null}],"top_logprobs":0,"top_p":1.0,"truncation":"disabled","usage":null,"user":null,"metadata":{}},"sequence_number":0}"""

    private fun completedEvent(usage: String) = """{"type":"response.completed","response":{"id":"dd8c2dd7-d538-4ce1-9144-270cd81b408a","object":"response","created_at":1785771440,"status":"completed","background":false,"completed_at":1785771441,"content_filters":null,"error":null,"frequency_penalty":0.0,"incomplete_details":null,"instructions":null,"max_output_tokens":null,"max_tool_calls":null,"model":"deepseek-v4-flash","moderation":null,"output":[],"parallel_tool_calls":true,"presence_penalty":0.0,"previous_response_id":null,"prompt_cache_key":null,"prompt_cache_retention":null,"reasoning":{"effort":null,"summary":null},"safety_identifier":null,"service_tier":"default","store":false,"temperature":1.0,"text":{"format":{"type":"text"},"verbosity":null},"tool_choice":"auto","tools":[{"type":"function","name":"list_dir","description":"列出目录内容","parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]},"strict":null}],"top_logprobs":0,"top_p":1.0,"truncation":"disabled","usage":$usage,"user":null,"metadata":{}},"sequence_number":7}"""

    /** 第一轮：DeepSeek 真实流式响应（reasoning delta + output_item.done + function_call）。 */
    private val roundOneEvents = listOf(
        createdEvent,
        """{"type":"response.reasoning_text.delta","content_index":0,"delta":"The user wants me to list files.","item_id":"500f2afa-c2ff-48de-a666-0aa29cd09d7b","output_index":0,"sequence_number":1}""",
        """{"type":"response.reasoning_text.delta","content_index":0,"delta":" I will use list_dir.","item_id":"500f2afa-c2ff-48de-a666-0aa29cd09d7b","output_index":0,"sequence_number":2}""",
        """{"type":"response.output_item.done","item":{"type":"reasoning","id":"500f2afa-c2ff-48de-a666-0aa29cd09d7b","status":"completed","content":[{"type":"reasoning_text","text":"The user wants me to list files. I will use list_dir."}],"summary":[]},"output_index":0,"sequence_number":3}""",
        """{"type":"response.function_call_arguments.delta","delta":"{","item_id":"ebce5038-4bb3-4cee-89c6-007e4eaa7478","output_index":1,"sequence_number":4}""",
        """{"type":"response.function_call_arguments.delta","delta":"\"path\":\".\"}","item_id":"ebce5038-4bb3-4cee-89c6-007e4eaa7478","output_index":1,"sequence_number":5}""",
        """{"type":"response.output_item.done","item":{"type":"function_call","id":"ebce5038-4bb3-4cee-89c6-007e4eaa7478","status":"completed","arguments":"{\"path\":\".\"}","call_id":"call_00_ST2kmrDrOAX50Qf6j5513639","name":"list_dir"},"output_index":1,"sequence_number":6}""",
        completedEvent("""{"input_tokens":353,"input_tokens_details":{"cached_tokens":256},"output_tokens":63,"output_tokens_details":{"reasoning_tokens":20},"total_tokens":416}"""),
    )

    /** 第二轮：模型收到工具结果后继续调用工具（reasoning + 新的 function_call）。 */
    private val roundTwoEvents = listOf(
        createdEvent,
        """{"type":"response.reasoning_text.delta","content_index":0,"delta":"Need more info.","item_id":"aaaa0000-0000-0000-0000-000000000001","output_index":0,"sequence_number":1}""",
        """{"type":"response.output_item.done","item":{"type":"reasoning","id":"aaaa0000-0000-0000-0000-000000000001","status":"completed","content":[{"type":"reasoning_text","text":"Need more info."}],"summary":[]},"output_index":0,"sequence_number":2}""",
        """{"type":"response.function_call_arguments.delta","delta":"{","item_id":"cccc0000-0000-0000-0000-000000000003","output_index":1,"sequence_number":3}""",
        """{"type":"response.function_call_arguments.delta","delta":"\"path\":\"..\"}","item_id":"cccc0000-0000-0000-0000-000000000003","output_index":1,"sequence_number":4}""",
        """{"type":"response.output_item.done","item":{"type":"function_call","id":"cccc0000-0000-0000-0000-000000000003","status":"completed","arguments":"{\"path\":\"..\"}","call_id":"call_00_secondtoolcall","name":"list_dir"},"output_index":1,"sequence_number":5}""",
        completedEvent("""{"input_tokens":500,"input_tokens_details":{"cached_tokens":0},"output_tokens":40,"output_tokens_details":{"reasoning_tokens":10},"total_tokens":540}"""),
    )

    /** 第三轮：模型收到第二轮工具结果后输出最终文本。 */
    private val roundThreeEvents = listOf(
        createdEvent,
        """{"type":"response.output_text.delta","content_index":0,"delta":"done","item_id":"bbbb0000-0000-0000-0000-000000000002","output_index":0,"sequence_number":1}""",
        """{"type":"response.output_item.done","item":{"type":"message","id":"bbbb0000-0000-0000-0000-000000000002","status":"completed","content":[{"type":"output_text","annotations":[],"text":"done"}],"role":"assistant"},"output_index":0,"sequence_number":2}""",
        completedEvent("""{"input_tokens":100,"input_tokens_details":{"cached_tokens":0},"output_tokens":10,"output_tokens_details":{"reasoning_tokens":5},"total_tokens":110}"""),
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
     * 用真实 Koog 客户端跑三轮请求，验证第三轮（工具结果续传）请求体以空文本 user
     * 消息闭合，且前两轮 reasoning 被正确回放。
     */
    @Test
    fun `replayed request body ends with empty user message after tool outputs`() = runTest {
        val recording = RecordingKoogHttpClient(
            listOf(roundOneEvents, roundTwoEvents, roundThreeEvents),
        )
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
            reasoningEfforts = listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH),
        )
        val model = buildLlmModel(profile)
        val params = buildPromptParams(profile, reasoningEffort = ReasoningEffort.HIGH)

        val prompt = Prompt.build(id = "diagnostic", params = params) {
            system("You are a helpful assistant.")
            message(
                Message.User(
                    content = "列出当前目录下的文件",
                    metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
                ),
            )
        }

        var currentPrompt = prompt
        val toolResults = listOf(
            MessagePart.Tool.Result(
                id = "call_00_ST2kmrDrOAX50Qf6j5513639",
                tool = "list_dir",
                output = "[\"file1.txt\"]",
            ),
            MessagePart.Tool.Result(
                id = "call_00_secondtoolcall",
                tool = "list_dir",
                output = "[\"file2.txt\"]",
            ),
        )

        repeat(3) { round ->
            val assistant = collectAssistantMessageFromStream(
                frames = client.executeStreaming(currentPrompt, model, tools = emptyList()),
                emitEvent = {},
            )
            currentPrompt = appendAssistantMessageToPrompt(
                currentPrompt = currentPrompt,
                response = assistant,
                clock = KoogClock.System,
            )
            if (round < toolResults.size) {
                currentPrompt = currentPrompt.withMessages {
                    currentPrompt.messages + Message.User(
                        part = toolResults[round],
                        metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
                    )
                }
            }
        }

        val thirdBody = checkNotNull(recording.capturedBodies.getOrNull(2))
        val parsedInput = Json.parseToJsonElement(thirdBody).jsonObject["input"]!!.jsonArray

        // 前两轮的 reasoning 必须被回放。
        val reasoningCount = parsedInput.count { item ->
            (item as? JsonObject)?.get("type")?.jsonPrimitive?.content == "reasoning"
        }
        assertEquals(2, reasoningCount)

        // 请求体必须以空文本 user 消息闭合工具轮次。
        val lastItem = parsedInput.last().jsonObject
        assertEquals("message", lastItem["type"]?.jsonPrimitive?.content)
        assertEquals("user", lastItem["role"]?.jsonPrimitive?.content)
        val content = lastItem["content"]?.jsonArray?.single()?.jsonObject
        assertEquals("input_text", content?.get("type")?.jsonPrimitive?.content)
        assertEquals("", content?.get("text")?.jsonPrimitive?.content)
    }
}
