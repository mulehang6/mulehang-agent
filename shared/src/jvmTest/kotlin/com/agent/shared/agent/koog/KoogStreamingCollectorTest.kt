package com.agent.shared.agent.koog

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentStreamEvent
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 验证流式片段的累积、推理内容与工具调用终止条件。 */
class KoogStreamingCollectorTest {

    /**
     * 自定义流式节点应把文本与思考 frame 同时转换为 UI 事件和 assistant message。
     */
    @Test
    fun `should collect streaming text and reasoning into assistant message`() = runTest {
        val emittedEvents = mutableListOf<AgentStreamEvent>()

        val message = collectAssistantMessageFromStream(
            frames = flow {
                emit(StreamFrame.ReasoningDelta(id = "r1", text = "raw", summary = "summary"))
                emit(StreamFrame.TextDelta("hel"))
                emit(StreamFrame.TextDelta("lo"))
                emit(
                    StreamFrame.ReasoningComplete(
                        id = "r1",
                        content = listOf("raw", " detail"),
                        summary = listOf("summary", " done"),
                    ),
                )
                emit(StreamFrame.End(finishReason = "stop"))
            },
            emitEvent = { event: AgentStreamEvent -> emittedEvents.add(event) },
        )

        assertEquals(
            listOf(
                AgentStreamEvent.ReasoningDelta(summary = "summary", rawText = "raw"),
                AgentStreamEvent.TextDelta("hel"),
                AgentStreamEvent.TextDelta("lo"),
                AgentStreamEvent.ReasoningCompleted(
                    summary = "summary done",
                    rawText = "raw detail",
                ),
            ),
            emittedEvents,
        )
        assertEquals("stop", message.finishReason)
        assertEquals(
            listOf(
                MessagePart.Reasoning(
                    id = "r1",
                    content = listOf("raw", " detail"),
                    summary = listOf("summary", " done"),
                    encrypted = "",
                ),
                MessagePart.Text("hello"),
            ),
            message.parts,
        )
    }

    /**
     * reasoning 文本已通过增量事件到达时，空的完成事件不能清除该文本；否则工具结果续传会丢失
     * Responses 协议要求回放的 reasoning_text。
     */
    @Test
    fun `should retain reasoning delta when completion content is empty`() = runTest {
        val message = collectAssistantMessageFromStream(
            frames = flowOf(
                StreamFrame.ReasoningDelta(
                    id = "reasoning-1",
                    text = "需要先读取文件。",
                ),
                StreamFrame.ReasoningComplete(
                    id = "reasoning-1",
                    content = emptyList(),
                ),
                StreamFrame.ToolCallComplete(
                    id = "call-1",
                    name = "read_file",
                    content = "{\"path\":\"README.md\"}",
                ),
                StreamFrame.End(finishReason = "tool_calls"),
            ),
            emitEvent = {},
        )

        assertEquals(
            listOf("需要先读取文件。"),
            message.parts.filterIsInstance<MessagePart.Reasoning>().single().content,
        )
    }

    /**
     * 工具续传轮可能只产出空 reasoning item；收敛结果必须保留 Reasoning part（带空文本），
     * 使下一轮请求仍能回传 reasoning_text，且不向 UI 发出空思考事件。
     */
    @Test
    fun `should retain empty reasoning part without emitting empty completion event`() = runTest {
        val emittedEvents = mutableListOf<AgentStreamEvent>()

        val message = collectAssistantMessageFromStream(
            frames = flowOf(
                StreamFrame.ReasoningComplete(id = "reasoning-1", content = emptyList()),
                StreamFrame.ToolCallComplete(
                    id = "call-1",
                    name = "read_file",
                    content = """{"path":"README.md"}""",
                ),
                StreamFrame.End(finishReason = "tool_calls"),
            ),
            emitEvent = { event: AgentStreamEvent -> emittedEvents.add(event) },
        )

        assertEquals(
            listOf(
                MessagePart.Reasoning(id = "reasoning-1", content = listOf(""), encrypted = ""),
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = """{"path":"README.md"}""",
                ),
            ),
            message.parts,
        )
        assertFalse(emittedEvents.any { it is AgentStreamEvent.ReasoningCompleted })
    }

    /**
     * 自定义流式节点应把工具调用 frame 还原为可继续执行的 assistant message。
     */
    @Test
    fun `should collect streaming tool calls into assistant message`() = runTest {
        val message = collectAssistantMessageFromStream(
            frames = flowOf(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    name = "read_file",
                    content = "{\"path\":\"REA",
                ),
                StreamFrame.ToolCallComplete(
                    id = "call-1",
                    name = "read_file",
                    content = "{\"path\":\"README.md\"}",
                ),
                StreamFrame.End(finishReason = "tool_calls"),
            ),
            emitEvent = {},
        )

        assertEquals("tool_calls", message.finishReason)
        assertEquals(
            listOf(
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = "{\"path\":\"README.md\"}",
                ),
            ),
            message.parts,
        )
    }

    /**
     * 工具调用增量在不同 chunk 中只保留 index 或 id 时，仍应合并成同一个 tool call。
     */
    @Test
    fun `should merge tool call deltas by index when later chunks omit id`() = runTest {
        val message = collectAssistantMessageFromStream(
            frames = flowOf(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    index = 0,
                    name = "read_file",
                    content = "{\"path\":\"REA",
                ),
                StreamFrame.ToolCallDelta(
                    id = null,
                    name = null,
                    index = 0,
                    content = "DME.md\"}",
                ),
                StreamFrame.End(finishReason = "tool_calls"),
            ),
            emitEvent = {},
        )

        assertEquals("tool_calls", message.finishReason)
        assertEquals(
            listOf(
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = "{\"path\":\"README.md\"}",
                ),
            ),
            message.parts,
        )
    }

    /**
     * `tool_calls` 结束态若没有真正的工具调用 part，应尽早转成明确异常而不是让图节点卡死。
     */
    @Test
    fun `should reject tool call finish without tool call parts`() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            collectAssistantMessageFromStream(
                frames = flowOf(
                    StreamFrame.ReasoningDelta(id = "r1", text = "先判断问题"),
                    StreamFrame.End(finishReason = "tool_calls"),
                ),
                emitEvent = {},
            )
        }

        assertTrue(error.message.orEmpty().contains("tool_calls"))
    }

    /**
     * 只有 reasoning、没有文本或工具调用的响应同样无法命中策略图边，应该直接失败。
     */
    @Test
    fun `should reject reasoning only assistant message`() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            collectAssistantMessageFromStream(
                frames = flowOf(
                    StreamFrame.ReasoningDelta(id = "r1", text = "先判断问题"),
                    StreamFrame.ReasoningComplete(
                        id = "r1",
                        content = listOf("先判断问题"),
                    ),
                    StreamFrame.End(finishReason = "stop"),
                ),
                emitEvent = {},
            )
        }

        assertTrue(error.message.orEmpty().contains("思考内容"))
    }

    /**
     * 参考 paicli 的 ReAct 主循环，只要存在 tool call，就算同时带有文本也不能直接结束。
     */
    @Test
    fun `should keep looping when assistant message contains both text and tool calls`() {
        val assistant = Message.Assistant(
            listOf(
                MessagePart.Text("我先去读取文件。"),
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = "{\"path\":\"README.md\"}",
                ),
            ),
            ResponseMetaInfo.Empty,
            finishReason = "tool_calls",
        )

        assertFalse(assistant.shouldFinishReactLoop())
        assertEquals(null, assistant.finalTextForReactLoop())
    }

    /**
     * 参考 paicli 的 ReAct 主循环，只有纯文本且无工具调用时才结束当前轮次。
     */
    @Test
    fun `should finish react loop only for assistant text without tool calls`() {
        val assistant = Message.Assistant(
            listOf(
                MessagePart.Text("最终答案"),
            ),
            ResponseMetaInfo.Empty,
            finishReason = "stop",
        )

        assertTrue(assistant.shouldFinishReactLoop())
        assertEquals("最终答案", assistant.finalTextForReactLoop())
    }

}
