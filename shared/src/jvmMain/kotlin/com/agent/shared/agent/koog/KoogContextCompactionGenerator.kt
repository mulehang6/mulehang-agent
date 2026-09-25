package com.agent.shared.agent.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.agent.prompt.buildPromptParams

/** 用当前会话模型和推理配置生成不含确定性状态消息的语义摘要。 */
internal class KoogContextCompactionGenerator {
    /** 摘要失败时抛出异常，由调用方保留原文。 */
    suspend fun generate(request: AgentRunRequest, prefix: List<AgentConversationHistoryMessage>): String {
        val prompt = Prompt.build(
            id = "mulehang-context-compaction",
            params = buildPromptParams(request.profile, request.reasoningEffort),
        ) {
            system(
                "Summarize earlier software-engineering conversation context for continuation. " +
                    "Preserve user goals, decisions, constraints, changed files, tool results, errors, " +
                    "verification and unresolved work. Do not invent facts or include status metadata. " +
                    "Output only the concise summary.",
            )
        }
        val agent = AIAgent.builder()
            .promptExecutor(buildPromptExecutor(request.profile))
            .llmModel(buildLlmModel(request.profile))
            .toolRegistry(ToolRegistry.EMPTY)
            .prompt(prompt)
            .functionalStrategy(contextCompactionStrategy)
            .build()
        return agent.run(renderCompactionSource(prefix), null).trim().also {
            require(it.isNotBlank()) { "上下文摘要为空。" }
        }
    }
}

/** 单次模型请求只提取助手文本。 */
private val contextCompactionStrategy = functionalStrategy<String, String>("context_compaction") { source ->
    requestLLM(source).parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
}

/** 将结构化轮次序列化为摘要任务文本，保留工具调用与结果的相邻顺序。 */
internal fun renderCompactionSource(history: List<AgentConversationHistoryMessage>): String = buildString {
    history.forEach { message ->
        when (message) {
            is AgentConversationHistoryMessage.User -> appendLine("USER: ${message.content}")
            is AgentConversationHistoryMessage.Assistant -> {
                appendLine("ASSISTANT:")
                message.parts.forEach { appendLine(it.toString()) }
            }
        }
    }
}
