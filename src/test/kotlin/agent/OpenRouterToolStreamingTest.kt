package agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenRouterToolStreamingTest {

    @Test
    fun `should not expose koog conversation control tools to openrouter`() {
        val toolRegistry = ToolRegistryBuilder()
            .tool(SayToUser)
            .tool(AskUser)
            .tool(ExitTool)
            .tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            .build()
        val expectedExposedToolNames = toolRegistry.tools
            .map { it.name }
            .filterNot { it in setOf("__say_to_user__", "__ask_user__", "__exit__") }

        val tools = buildOpenRouterToolsSchema(toolRegistry)
        val toolNames = tools.map {
            it.jsonObject["function"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: error("missing name")
        }

        assertFalse(toolNames.contains("__say_to_user__"))
        assertFalse(toolNames.contains("__ask_user__"))
        assertFalse(toolNames.contains("__exit__"))
        assertEquals(expectedExposedToolNames, toolNames)
    }

    @Test
    fun `should parse tool calls from stream payload`() {
        val toolCalls = extractOpenRouterToolCalls(
            """
            {
              "choices": [
                {
                  "delta": {
                    "tool_calls": [
                      {
                        "index": 0,
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "read_file",
                          "arguments": "{\"path\":\"build.gradle.kts\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, toolCalls.size)
        assertEquals("call_1", toolCalls.single().id)
        assertEquals("read_file", toolCalls.single().name)
        assertEquals("""{"path":"build.gradle.kts"}""", toolCalls.single().arguments)
    }

    @Test
    fun `should close thinking before emitting tool result block`() {
        val emitted = mutableListOf<String>()

        emitToolResultBlock(
            resultText = "文件内容",
            thinkingState = OpenRouterThinkingState(isThinkingOpen = true),
            emitChunk = emitted::add
        )

        assertEquals(listOf("</thinking>\n", "\n[工具结果]\n文件内容\n[/工具结果]\n"), emitted)
    }

    @Test
    fun `should stop looping when no tool calls are returned`() {
        val decision = decideNextToolLoopStep(
            toolCalls = emptyList(),
            iteration = 1,
            maxIterations = 4
        )

        assertTrue(decision.shouldStop)
        assertFalse(decision.hitIterationLimit)
    }

    @Test
    fun `should execute koog tool calls from openrouter tool calls`() = runBlocking {
        val tempFile = File("build/tmp/test-tool-execution/a.txt").apply {
            parentFile.mkdirs()
            writeText("hello from tool")
        }

        val toolRegistry = ToolRegistryBuilder()
            .tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            .build()
        val toolName = toolRegistry.tools.single().name

        val results = executeOpenRouterToolCalls(
            toolRegistry = toolRegistry,
            toolCalls = listOf(
                OpenRouterToolCall(
                    id = "call_1",
                    name = toolName,
                    arguments = """{"path":"${tempFile.absolutePath.replace("\\", "\\\\")}"}"""
                )
            )
        )

        assertEquals(1, results.size)
        assertEquals("call_1", results.single().id)
        assertEquals(toolName, results.single().tool)
        assertTrue(results.single().content.contains("hello from tool"))
    }

    @Test
    fun `should serialize tool call and tool result messages for openrouter transcript`() {
        val toolRegistry = ToolRegistryBuilder()
            .tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            .build()

        val requestBody = buildOpenRouterChatRequest(
            messages = listOf(
                Message.System("系统提示", RequestMetaInfo(Clock.System.now())),
                Message.User("读取 a.txt", RequestMetaInfo(Clock.System.now())),
                Message.Tool.Call(
                    "call_1",
                    "read_file",
                    """{"path":"a.txt"}""",
                    ResponseMetaInfo(Clock.System.now())
                ),
                Message.Tool.Result(
                    "call_1",
                    "read_file",
                    "文件内容",
                    RequestMetaInfo(Clock.System.now()),
                    false,
                    null
                )
            ),
            stream = true,
            toolRegistry = toolRegistry
        )

        val messages = requestBody["messages"]?.jsonArray ?: error("missing messages")

        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("assistant", messages[2].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("call_1", messages[2].jsonObject["tool_calls"]?.jsonArray?.single()?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals("read_file", messages[2].jsonObject["tool_calls"]?.jsonArray?.single()?.jsonObject?.get("function")?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertEquals("""{"path":"a.txt"}""", messages[2].jsonObject["tool_calls"]?.jsonArray?.single()?.jsonObject?.get("function")?.jsonObject?.get("arguments")?.jsonPrimitive?.content)
        assertEquals("tool", messages[3].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("call_1", messages[3].jsonObject["tool_call_id"]?.jsonPrimitive?.content)
        assertEquals("文件内容", messages[3].jsonObject["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should append tool result before follow up assistant turn`() = runBlocking {
        val storageDir = File("build/tmp/test-tool-loop-history").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val provider: ChatHistoryProvider = FileChatHistoryProvider(storageDir.absolutePath)
            val transcripts = mutableListOf<List<Message>>()
            val emitted = mutableListOf<String>()

            val result = runStreamingToolLoop(
                chatHistoryProvider = provider,
                sessionId = "session-1",
                input = "读取 a.txt",
                streamTurn = { transcript, emitChunk ->
                    transcripts += transcript
                    if (transcripts.size == 1) {
                        emitChunk("<thinking>先看文件</thinking>\n")
                        OpenRouterToolTurn(
                            taggedResponse = "<thinking>先看文件</thinking>",
                            toolCalls = listOf(OpenRouterToolCall("call_1", "read_file", """{"path":"a.txt"}"""))
                        )
                    } else {
                        emitChunk("这是文件内容总结")
                        OpenRouterToolTurn(
                            taggedResponse = "这是文件内容总结",
                            toolCalls = emptyList()
                        )
                    }
                },
                executeToolCalls = { toolCalls ->
                    assertEquals(1, toolCalls.size)
                    listOf(
                        Message.Tool.Result(
                            "call_1",
                            "read_file",
                            "a.txt 的内容",
                            RequestMetaInfo(Clock.System.now()),
                            false,
                            null
                        )
                    )
                },
                emitChunk = { emitted.add(it) }
            )

            assertEquals(2, transcripts.size)
            assertEquals("读取 a.txt", (transcripts[0].last() as Message.User).content)
            assertTrue(transcripts[1].any { it is Message.Tool.Call && it.id == "call_1" })
            assertTrue(transcripts[1].any { it is Message.Tool.Result && it.id == "call_1" && it.content == "a.txt 的内容" })
            assertTrue(emitted.joinToString("").contains("[工具结果]\na.txt 的内容\n[/工具结果]"))
            assertEquals("这是文件内容总结", result)
        } finally {
            storageDir.deleteRecursively()
        }
    }

    @Test
    fun `should finish unified loop without tool calls`() = runBlocking {
        val storageDir = File("build/tmp/test-unified-no-tool").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val provider: ChatHistoryProvider = FileChatHistoryProvider(storageDir.absolutePath)

            val result = runStreamingToolLoop(
                chatHistoryProvider = provider,
                sessionId = "session-1",
                input = "你好",
                streamTurn = { transcript, _ ->
                    assertEquals("你好", (transcript.last() as Message.User).content)
                    OpenRouterToolTurn(
                        taggedResponse = "你好，我在这里。",
                        toolCalls = emptyList()
                    )
                },
                executeToolCalls = { error("should not execute tools") }
            )

            assertEquals("你好，我在这里。", result)
        } finally {
            storageDir.deleteRecursively()
        }
    }
}
