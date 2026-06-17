package com.agent.shared.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 验证 DeepSeek chat-completions 专用流式适配。
 */
class DeepSeekChatCompletionsStreamerTest {

    /**
     * 请求体应显式开启 thinking，并带上 reasoning effort。
     */
    @Test
    fun `should enable thinking mode in deepseek request`() = runTest {
        var capturedRequest: DeepSeekChatCompletionRequest? = null
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { request, _ ->
                capturedRequest = request
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-1",
                        created = 1L,
                        model = "deepseek-v4-flash",
                        choices = emptyList(),
                        usage = DeepSeekUsage(totalTokens = 4, promptTokens = 1, completionTokens = 3),
                    ),
                )
            },
        )

        streamer.stream(prompt = "你好", config = deepSeekProfile()).toList()
        val request = requireNotNull(capturedRequest)

        assertEquals("deepseek-v4-flash", request.model)
        assertEquals(true, request.stream)
        assertEquals(DeepSeekThinking(type = "enabled"), request.thinking)
        assertEquals("medium", request.reasoningEffort)
        assertEquals(listOf(DeepSeekChatMessage(role = "user", content = "你好")), request.messages)
    }

    /**
     * 请求体中的 reasoning effort 应跟随上层传入值，而不是固定写死。
     */
    @Test
    fun `should map request reasoning effort into deepseek payload`() = runTest {
        var capturedRequest: DeepSeekChatCompletionRequest? = null
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { request, _ ->
                capturedRequest = request
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-2",
                        created = 2L,
                        model = "deepseek-v4-flash",
                    ),
                )
            },
        )

        streamer.stream(
            request = AgentRunRequest(
                prompt = "你好",
                profile = deepSeekProfile(),
                reasoningEffort = ReasoningEffort.LOW,
            ),
        ).toList()

        assertEquals("low", capturedRequest?.reasoningEffort)
    }

    /**
     * 结构化历史应在当前用户输入之前写入 DeepSeek messages。
     */
    @Test
    fun `should map structured history into deepseek messages before current user prompt`() = runTest {
        var capturedRequest: DeepSeekChatCompletionRequest? = null
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { request, _ ->
                capturedRequest = request
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-history",
                        created = 1L,
                        model = "deepseek-v4-flash",
                    ),
                )
            },
        )

        streamer.stream(
            AgentRunRequest(
                prompt = "second turn",
                profile = deepSeekProfile(),
                history = listOf(
                    AgentConversationHistoryMessage.User("first turn"),
                    AgentConversationHistoryMessage.Assistant(
                        parts = listOf(
                            AgentConversationHistoryPart.Reasoning(
                                summary = "先分析",
                                rawText = "先分析原始思考",
                            ),
                            AgentConversationHistoryPart.ToolCall(
                                name = "read_file",
                                argumentsPreview = """{"path":"README.md"}""",
                            ),
                            AgentConversationHistoryPart.ToolResult(
                                name = "read_file",
                                resultPreview = "ok",
                            ),
                            AgentConversationHistoryPart.Text("最终回答"),
                        ),
                    ),
                ),
            ),
        ).toList()

        assertEquals(
            listOf(
                DeepSeekChatMessage(role = "user", content = "first turn"),
                DeepSeekChatMessage(
                    role = "assistant",
                    content = """
[reasoning]
先分析原始思考
[/reasoning]

[tool_call:read_file]
{"path":"README.md"}
[/tool_call]

[tool_result:read_file]
ok
[/tool_result]

最终回答
                    """.trimIndent(),
                ),
                DeepSeekChatMessage(role = "user", content = "second turn"),
            ),
            capturedRequest?.messages,
        )
    }

    /**
     * DeepSeek V4 的 max thinking 档位应原样写入 reasoning_effort。
     */
    @Test
    fun `should map max reasoning effort into deepseek payload`() = runTest {
        var capturedRequest: DeepSeekChatCompletionRequest? = null
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { request, _ ->
                capturedRequest = request
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-max",
                        created = 4L,
                        model = "deepseek-v4-flash",
                    ),
                )
            },
        )

        streamer.stream(
            request = AgentRunRequest(
                prompt = "你好",
                profile = deepSeekProfile(),
                reasoningEffort = ReasoningEffort.MAX,
            ),
        ).toList()

        assertEquals("max", capturedRequest?.reasoningEffort)
    }

    /**
     * 上层裁剪为 null 时，DeepSeek 请求体不应再回退写入 medium。
     */
    @Test
    fun `should keep null reasoning effort out of deepseek payload`() = runTest {
        var capturedRequest: DeepSeekChatCompletionRequest? = null
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { request, _ ->
                capturedRequest = request
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-3",
                        created = 3L,
                        model = "deepseek-v4-flash",
                    ),
                )
            },
        )

        streamer.stream(
            request = AgentRunRequest(
                prompt = "你好",
                profile = deepSeekProfile(),
                reasoningEffort = null,
            ),
        ).toList()

        assertEquals(null, capturedRequest?.reasoningEffort)
    }

    /**
     * 请求诊断摘要只应包含安全字段，方便确认 reasoning_effort 是否真正写入。
     */
    @Test
    fun `should build safe request diagnostic summary`() {
        val request = DeepSeekChatCompletionRequest(
            model = "deepseek-v4-flash",
            messages = listOf(DeepSeekChatMessage(role = "user", content = "secret prompt")),
            tools = null,
            stream = true,
            streamOptions = DeepSeekStreamOptions(includeUsage = true),
            thinking = DeepSeekThinking(type = "enabled"),
            reasoningEffort = "max",
        )

        val summary = buildDeepSeekRequestDiagnostic(request)

        assertEquals(
            "DeepSeek request: model=deepseek-v4-flash thinking=enabled reasoning_effort=max tools=0 stream=true",
            summary,
        )
        assertFalse(summary.contains("secret prompt"))
        assertFalse(summary.contains("messages"))
    }

    /**
     * reasoning_content、tool_calls 与 content 应分别映射成 reasoning/tool/text frame，并在结束时补 reasoning complete。
     */
    @Test
    fun `should map reasoning and tool call chunks into stream frames`() = runTest {
        val streamer = DeepSeekChatCompletionsStreamer(
            chunkRunner = { _, _ ->
                flowOf(
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-1",
                        created = 1L,
                        model = "deepseek-v4-flash",
                        choices = listOf(
                            DeepSeekChatChoice(
                                index = 0,
                                delta = DeepSeekChatDelta(
                                    reasoningContent = "先判断问题",
                                    toolCalls = listOf(
                                        DeepSeekStreamToolCall(
                                            index = 0,
                                            id = "call-1",
                                            function = DeepSeekStreamFunction(
                                                name = "read_file",
                                                arguments = "{\"path\":\"README",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    DeepSeekChatCompletionChunk(
                        id = "chatcmpl-1",
                        created = 2L,
                        model = "deepseek-v4-flash",
                        choices = listOf(
                            DeepSeekChatChoice(
                                index = 0,
                                delta = DeepSeekChatDelta(
                                    content = "这是答案",
                                    toolCalls = listOf(
                                        DeepSeekStreamToolCall(
                                            index = 0,
                                            id = "call-1",
                                            function = DeepSeekStreamFunction(
                                                arguments = ".md\"}",
                                            ),
                                        ),
                                    ),
                                ),
                                finishReason = "stop",
                            ),
                        ),
                        usage = DeepSeekUsage(totalTokens = 12, promptTokens = 4, completionTokens = 8),
                    ),
                )
            },
        )

        val frames = streamer.stream(prompt = "你好", config = deepSeekProfile()).toList()

        assertEquals(6, frames.size)
        assertEquals(
            StreamFrame.ReasoningDelta(
                text = "先判断问题",
                index = 0,
            ),
            frames[0],
        )
        assertEquals(
            StreamFrame.ToolCallDelta(
                id = "call-1",
                index = 0,
                name = "read_file",
                content = "{\"path\":\"README",
            ),
            frames[1],
        )
        assertEquals(StreamFrame.TextDelta(text = "这是答案", index = 0), frames[2])
        assertEquals(
            StreamFrame.ToolCallDelta(
                id = "call-1",
                index = 0,
                name = null,
                content = ".md\"}",
            ),
            frames[3],
        )
        assertEquals(
            StreamFrame.ReasoningComplete(
                id = null,
                content = listOf("先判断问题"),
                index = 0,
            ),
            frames[4],
        )
        val end = assertIs<StreamFrame.End>(frames[5])
        assertEquals("stop", end.finishReason)
        assertEquals(12, end.metaInfo.totalTokensCount)
        assertEquals(4, end.metaInfo.inputTokensCount)
        assertEquals(8, end.metaInfo.outputTokensCount)
    }

    /**
     * 流式 assistant 收敛后必须先写回 prompt，后续 tool result 请求才能满足 DeepSeek 的相邻约束。
     */
    @Test
    fun `should keep streamed assistant tool call before tool result in deepseek request`() {
        val streamer = DeepSeekChatCompletionsStreamer()
        val initialPrompt = Prompt(
            messages = listOf(
                Message.User(
                    content = "first question",
                    metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
                ),
            ),
            id = "deepseek-streaming-prompt",
        )
        val promptWithAssistant = appendAssistantMessageToPrompt(
            currentPrompt = initialPrompt,
            response = Message.Assistant(
                parts = listOf(
                    MessagePart.Tool.Call(
                        id = "call-1",
                        tool = "read_file",
                        args = "{\"path\":\"README.md\"}",
                    ),
                ),
                metaInfo = ResponseMetaInfo.Empty,
                finishReason = "tool_calls",
            ),
            clock = KoogClock.System,
        )
        val promptWithToolResult = prompt(promptWithAssistant, KoogClock.System) {
            user {
                toolResult(
                    MessagePart.Tool.Result(
                        id = "call-1",
                        tool = "read_file",
                        output = "file-content",
                    ),
                )
            }
        }

        val request = streamer.buildRequest(
            prompt = promptWithToolResult,
            config = deepSeekProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
            tools = emptyList(),
        )

        assertEquals(
            listOf(
                DeepSeekChatMessage(role = "user", content = "first question"),
                DeepSeekChatMessage(
                    role = "assistant",
                    toolCalls = listOf(
                        DeepSeekToolCall(
                            id = "call-1",
                            function = DeepSeekToolFunctionCall(
                                name = "read_file",
                                arguments = "{\"path\":\"README.md\"}",
                            ),
                        ),
                    ),
                ),
                DeepSeekChatMessage(
                    role = "tool",
                    content = "file-content",
                    toolCallId = "call-1",
                ),
            ),
            request.messages,
        )
    }

    /**
     * gateway 当前 prompt 中的 assistant/tool 历史和工具 schema 应被写入 DeepSeek 请求体。
     */
    @Test
    fun `should build deepseek request from prompt and tools`() {
        val streamer = DeepSeekChatCompletionsStreamer()

        val prompt = Prompt(
            messages = listOf(
                Message.System(
                    content = "system rule",
                    metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
                ),
                Message.User(
                    content = "first question",
                    metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
                ),
                Message.Assistant(
                    parts = listOf(
                        MessagePart.Reasoning(content = "先分析"),
                        MessagePart.Tool.Call(
                            id = "call-1",
                            tool = "read_file",
                            args = "{\"path\":\"README.md\"}",
                        ),
                    ),
                    metaInfo = ResponseMetaInfo.Empty,
                    finishReason = "tool_calls",
                ),
                Message.User(
                    parts = listOf(
                        MessagePart.Tool.Result(
                            id = "call-1",
                            tool = "read_file",
                            output = "file-content",
                        ),
                    ),
                    metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
                ),
            ),
            id = "deepseek-prompt",
        )
        val tools = listOf(
            ToolDescriptor(
                name = "read_file",
                description = "Read a file",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "path",
                        description = "Absolute or relative path",
                        type = ToolParameterType.String,
                    ),
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "max_lines",
                        description = "Maximum lines to read",
                        type = ToolParameterType.Integer,
                    ),
                ),
            ),
        )

        val request = streamer.buildRequest(
            prompt = prompt,
            config = deepSeekProfile(),
            reasoningEffort = ReasoningEffort.HIGH,
            tools = tools,
        )

        assertEquals("deepseek-v4-flash", request.model)
        assertEquals("high", request.reasoningEffort)
        assertEquals(
            listOf(
                DeepSeekChatMessage(role = "system", content = "system rule"),
                DeepSeekChatMessage(role = "user", content = "first question"),
                DeepSeekChatMessage(
                    role = "assistant",
                    reasoningContent = "先分析",
                    toolCalls = listOf(
                        DeepSeekToolCall(
                            id = "call-1",
                            function = DeepSeekToolFunctionCall(
                                name = "read_file",
                                arguments = "{\"path\":\"README.md\"}",
                            ),
                        ),
                    ),
                ),
                DeepSeekChatMessage(
                    role = "tool",
                    content = "file-content",
                    toolCallId = "call-1",
                ),
            ),
            request.messages,
        )
        assertEquals(1, request.tools?.size)
        assertEquals("read_file", request.tools?.single()?.function?.name)
        assertEquals("Read a file", request.tools?.single()?.function?.description)
        val parameters = request.tools?.single()?.function?.parameters
        assertEquals("object", parameters?.get("type")?.toString()?.trim('"'))
        assertEquals(
            "[\"path\"]",
            parameters?.get("required").toString(),
        )
        assertEquals(
            "\"string\"",
            parameters?.get("properties")
                ?.toString()
                ?.let { if ("\"path\"" in it) "\"string\"" else null },
        )
        assertNull(request.tools?.single()?.function?.parameters["additionalProperties"])
    }

    private fun deepSeekProfile(): ConfigProfile = ConfigProfile(
        id = "deepseek",
        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "key",
        model = "deepseek-v4-flash",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
