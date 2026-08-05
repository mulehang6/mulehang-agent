package com.agent.shared.agent.koog

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.Json
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.prompt.buildPromptParams
import com.agent.shared.settings.model.ConfigProfile

/**
 * 构建 agent 基础 prompt，只承载 provider 参数，不预写用户正文。
 */
internal fun buildAgentPrompt(
    profile: ConfigProfile,
    reasoningEffort: ReasoningEffort?,
): Prompt = Prompt.build(
    id = "mulehang-chat",
    params = buildPromptParams(profile, reasoningEffort),
) {
    system(agentSystemPrompt())
}

/**
 * 返回每轮 Agent 共用的系统约束，集中维护可见回复与桌面 Markdown 的协议。
 */
internal fun agentSystemPrompt(): String = """
    直接在回复正文中回答用户。
    回复使用 Markdown；标题井号后必须保留一个空格，列表标记后必须保留一个空格，
    段落、标题、围栏代码块之间保留必要的空行。
    需要输出流程或关系图时，将 Mermaid 放在 ```mermaid 围栏中，将 PlantUML 放在
    ```plantuml 围栏中；围栏必须闭合，且图表源码之外不要混入图表语法。
    不要把 Markdown 或 HTML 当作需要执行的脚本；仅输出用户需要的内容。
""".trimIndent()

/**
 * 构建标题生成专用 prompt；不复用聊天 system prompt，避免带入工具或 Markdown 协议。
 */
internal fun buildConversationTitlePrompt(profile: ConfigProfile): Prompt = Prompt.build(
    id = "mulehang-conversation-title",
    params = buildPromptParams(profile, reasoningEffort = null),
) {
    system(conversationTitleSystemPrompt())
}

/**
 * 标题生成的独立系统约束：只产出短标题正文，不解释、不使用工具、不使用 Markdown。
 */
internal fun conversationTitleSystemPrompt(): String = """
    你会看到用户发给编程助手的第一条消息。
    请为这次对话生成一个简短的中文标题，用于历史任务列表展示。
    严格遵守：
    - 只输出标题本身，不要前缀、解释或结尾标点。
    - 不要使用引号、Markdown 或代码块。
    - 长度控制在 6 到 16 个字符以内。
    - 不能调用工具，不能反问，不能拒绝回答。
""".trimIndent()

/**
 * 将会话历史和当前用户输入映射为 Koog 可消费的消息序列。
 */
internal fun buildConversationMessages(
    history: List<AgentConversationHistoryMessage>,
    prompt: String,
    clock: KoogClock = KoogClock.System,
): List<Message> = history.flatMap { message ->
    message.toKoogMessages(clock)
} + Message.User(
    content = prompt,
    metaInfo = RequestMetaInfo.create(clock = clock),
)

/**
 * 将单条结构化历史消息映射为一段 Koog 消息序列。
 */
private fun AgentConversationHistoryMessage.toKoogMessages(clock: KoogClock): List<Message> = when (this) {
    is AgentConversationHistoryMessage.User -> listOf(
        Message.User(
            content = content,
            metaInfo = RequestMetaInfo.create(clock = clock),
        ),
    )

    is AgentConversationHistoryMessage.Assistant -> assistantHistoryToKoogMessages(parts, clock)
}

/**
 * 将 assistant 历史片段展开为 Koog 所需的 assistant/user/tool-result 消息序列。
 */
private fun assistantHistoryToKoogMessages(
    parts: List<AgentConversationHistoryPart>,
    clock: KoogClock,
): List<Message> {
    val messages = mutableListOf<Message>()
    val assistantParts = mutableListOf<MessagePart.ResponsePart>()
    val pendingToolCalls = linkedMapOf<String, PendingHistoricalToolCall>()

    fun flushAssistant() {
        if (assistantParts.isEmpty()) return
        messages += Message.Assistant(
            parts = assistantParts.toList(),
            metaInfo = ResponseMetaInfo.Empty,
        )
        assistantParts.clear()
    }

    fun appendMissingToolResults() {
        if (pendingToolCalls.isEmpty()) return
        pendingToolCalls.values.forEach { toolCall ->
            messages += Message.User(
                part = MessagePart.Tool.Result(
                    id = toolCall.id,
                    tool = toolCall.name,
                    output = ORPHANED_TOOL_CALL_RESULT,
                ),
                metaInfo = RequestMetaInfo.create(clock = clock),
            )
        }
        pendingToolCalls.clear()
    }

    fun beforeAssistantPart() {
        if (assistantParts.isEmpty()) {
            appendMissingToolResults()
        }
    }

    /**
     * 在历史中出现非工具 part（文本、推理）前闭合尚未收到结果的工具轮次。
     *
     * 同一 assistant 历史消息可能同时包含工具调用与最终正文（例如工具失败后 agent
     * 继续运行并给出总结）。Koog 序列化时会按 part 拆分：先输出 function_call item，
     * 正文变成独立的 assistant message。若不在此处先补齐工具结果，正文消息会插在
     * function_call 与 function_call_output 之间，兼容服务端（如 DeepSeek）会以
     * "No tool output found for tool call X" 400 拒绝整个请求。
     */
    fun closePendingToolRound() {
        if (pendingToolCalls.isEmpty()) return
        flushAssistant()
        appendMissingToolResults()
    }

    parts.forEach { part ->
        when (part) {
            is AgentConversationHistoryPart.Text -> {
                closePendingToolRound()
                beforeAssistantPart()
                assistantParts += MessagePart.Text(part.text)
            }

            is AgentConversationHistoryPart.Reasoning -> {
                val content = part.rawText ?: part.summary.orEmpty()
                closePendingToolRound()
                beforeAssistantPart()
                // 历史模型不含 thinking signature；Koog 回传 reasoning 时要求 encrypted 非空，
                // 与流式累积一致的空字符串占位可让兼容端点（如 DeepSeek）接受该请求。
                assistantParts += MessagePart.Reasoning(
                    content = listOf(content),
                    summary = part.summary?.takeIf { it.isNotBlank() }?.let(::listOf),
                    encrypted = "",
                )
            }

            is AgentConversationHistoryPart.ToolCall -> {
                beforeAssistantPart()
                assistantParts += MessagePart.Tool.Call(
                    id = part.id,
                    tool = part.name,
                    args = part.argumentsPreview?.takeIf(::isValidJsonArguments) ?: "{}",
                )
                pendingToolCalls[historicalToolCallKey(part.id, part.name)] = PendingHistoricalToolCall(
                    id = part.id,
                    name = part.name,
                )
            }

            is AgentConversationHistoryPart.ToolResult -> {
                flushAssistant()
                messages += Message.User(
                    part = MessagePart.Tool.Result(
                        id = part.id,
                        tool = part.name,
                        output = part.resultPreview.orEmpty(),
                    ),
                    metaInfo = RequestMetaInfo.create(clock = clock),
                )
                pendingToolCalls.removeHistoricalToolCall(part.id, part.name)
            }
        }
    }

    flushAssistant()
    appendMissingToolResults()
    return messages
}

/**
 * 判断历史工具调用参数是否可直接作为 Koog 请求参数回放。
 *
 * 历史中的 `argumentsPreview` 是面向 UI 的截断预览（默认 120 字符），可能既不是完整
 * JSON 也不是 JSON 文本；Koog 序列化 assistant 消息时会对 args 做懒解析，非法 JSON
 * 会直接抛 JsonDecodingException，因此非 JSON 预览必须降级为合法占位。
 */
private fun isValidJsonArguments(arguments: String): Boolean =
    runCatching { Json.parseToJsonElement(arguments) }.isSuccess

/**
 * 记录已经进入历史 assistant/tool_calls、但尚未匹配到 tool result 的工具调用。
 */
private data class PendingHistoricalToolCall(
    val id: String?,
    val name: String,
)

/**
 * 为被中断或失败的历史工具调用补齐协议要求的工具结果文本。
 */
private const val ORPHANED_TOOL_CALL_RESULT = "工具调用未完成，未产生可用结果。"

/**
 * 生成历史工具调用匹配键；缺少 id 时退回工具名以匹配旧事件。
 */
private fun historicalToolCallKey(id: String?, name: String): String = id ?: name

/**
 * 从待匹配工具调用中移除已收到结果的项，优先按 id，其次按工具名兼容旧历史。
 */
private fun MutableMap<String, PendingHistoricalToolCall>.removeHistoricalToolCall(id: String?, name: String) {
    if (id != null && remove(id) != null) return
    val fallbackKey = entries.firstOrNull { (_, call) -> call.name == name }?.key ?: return
    remove(fallbackKey)
}
