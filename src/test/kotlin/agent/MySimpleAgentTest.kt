package agent

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MySimpleAgentTest {

    @Test
    fun `should keep simple chat in streaming mode`() {
        assertFalse(shouldUseToolAgent("你好"))
        assertFalse(shouldUseToolAgent("你是谁"))
    }

    @Test
    fun `should use tool agent for file operations`() {
        assertTrue(shouldUseToolAgent("读取 src/main/kotlin/agent/MySimpleAgent.kt"))
        assertTrue(shouldUseToolAgent("请列出当前目录"))
        assertTrue(shouldUseToolAgent("帮我写入一个文件"))
    }

    @Test
    fun `should route simple chat to streaming runner directly`() = runBlocking {
        val notices = mutableListOf<String>()

        val response = runAgentByMode(
            input = "你好",
            sessionId = "session-1",
            streamedOutput = StringBuilder(),
            streamingRunner = { input, _ -> "stream:$input" },
            toolRunner = { _, _ -> error("tool runner should not be used") },
            onToolModeSelected = notices::add
        )

        assertEquals("stream:你好", response)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `should route file requests to tool runner directly`() = runBlocking {
        val notices = mutableListOf<String>()

        val response = runAgentByMode(
            input = "请读取 build.gradle.kts",
            sessionId = "session-1",
            streamedOutput = StringBuilder(),
            streamingRunner = { _, _ -> error("streaming runner should not be used") },
            toolRunner = { input, _ -> "tool:$input" },
            onToolModeSelected = notices::add
        )

        assertEquals("tool:请读取 build.gradle.kts", response)
        assertEquals(listOf("[系统] 检测到文件操作请求，已切换到非流式工具模式。"), notices)
    }

    @Test
    fun `should fallback when streaming request gets http 400`() {
        val error = IllegalStateException(
            "Error from client: DeepSeekLLMClient\nStatus code: 400\nMessage: Expected status code 200 but was 400"
        )

        assertTrue(shouldFallbackToNonStreaming(error))
    }

    @Test
    fun `should fallback when DeepSeek reports missing reasoning content`() {
        val error = IllegalStateException(
            """
            Error from client: DeepSeekLLMClient Status code: 400 Error body:
            {"error":{"message":"Missing `reasoning_content` field in the assistant message at message index 3.","type":"invalid_request_error"}}
            """.trimIndent()
        )

        assertTrue(shouldFallbackToNonStreaming(error))
    }

    @Test
    fun `should not fallback for unrelated errors`() {
        val error = IllegalStateException("Status code: 401")

        assertFalse(shouldFallbackToNonStreaming(error))
    }

    @Test
    fun `should retry with fallback runner after streaming 400 failure`() = runBlocking {
        val streamedOutput = StringBuilder()
        val notices = mutableListOf<String>()

        val response = runAgentWithFallback(
            input = "你好",
            sessionId = "session-1",
            streamedOutput = streamedOutput,
            streamingRunner = { _, _ ->
                throw IllegalStateException("Expected status code 200 but was 400")
            },
            fallbackRunner = { input, sessionId -> "fallback:$sessionId:$input" },
            onFallback = notices::add
        )

        assertEquals("fallback:session-1:你好", response)
        assertEquals(listOf("[系统] DeepSeek 流式请求失败（400），已自动切换到非流式模式重试。"), notices)
    }

    @Test
    fun `should use dedicated fallback runner for non tool chat`() = runBlocking {
        val notices = mutableListOf<String>()

        val response = runAgentByMode(
            input = "你好",
            sessionId = "session-1",
            streamedOutput = StringBuilder(),
            streamingRunner = { _, _ ->
                throw IllegalStateException("Error from client: DeepSeekLLMClient Status code: 400")
            },
            toolRunner = { _, _ -> error("tool runner should not be used as chat fallback") },
            fallbackRunner = { input, sessionId -> "fallback:$sessionId:$input" },
            onFallback = notices::add
        )

        assertEquals("fallback:session-1:你好", response)
        assertEquals(listOf("[系统] DeepSeek 流式请求失败（400），已自动切换到非流式模式重试。"), notices)
    }

    @Test
    fun `should rethrow non fallback errors`() {
        val streamedOutput = StringBuilder()

        assertFailsWith<IllegalStateException> {
            runBlocking {
                runAgentWithFallback(
                    input = "你好",
                    sessionId = "session-1",
                    streamedOutput = streamedOutput,
                    streamingRunner = { _, _ -> throw IllegalStateException("Status code: 401") },
                    fallbackRunner = { _, _ -> "ok" }
                )
            }
        }
    }
}
