package com.agent.shared.agent.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import com.agent.shared.agent.api.ConversationTitleGenerator
import com.agent.shared.agent.api.ConversationTitleRequest
import com.agent.shared.agent.prompt.buildLlmModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 Koog 的无工具会话标题生成器。
 *
 * 与普通聊天 [KoogAgentGateway] 完全隔离：不注册任何工具（显式使用 [ToolRegistry.EMPTY]）、
 * 不携带聊天 history、不创建工具交互桥接，仅对首条用户消息发起一次非流式单轮请求。
 */
class KoogConversationTitleGenerator(
    private val executionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val agentRunner: suspend (ConversationTitleRequest) -> String = ::runTitleOnlyAgent,
) : ConversationTitleGenerator {
    /**
     * 根据首条用户消息生成简短标题；异常向调用方原样抛出，由调用方决定回退文案。
     */
    override suspend fun generate(request: ConversationTitleRequest): String =
        withContext(executionDispatcher) { agentRunner(request) }
}

/**
 * 构建一次性无工具 Koog agent 并返回其纯文本结果。
 */
internal suspend fun runTitleOnlyAgent(request: ConversationTitleRequest): String {
    val agent = AIAgent
        .builder()
        .promptExecutor(buildPromptExecutor(request.profile))
        .llmModel(buildLlmModel(request.profile))
        .toolRegistry(ToolRegistry.EMPTY)
        .prompt(buildConversationTitlePrompt(request.profile))
        .functionalStrategy(titleOnlyStrategy)
        .build()
    return agent.run(request.firstUserMessage, null)
}

/**
 * 单次请求策略：把首条用户消息发给模型并直接返回文本，不做任何工具或循环处理。
 */
private val titleOnlyStrategy = functionalStrategy<String, String>("conversation_title") { firstUserMessage ->
    requestLLM(firstUserMessage).extractText()
}

/**
 * 提取标题响应的纯文本正文；无工具注册时模型不会返回工具调用分片。
 */
private fun Message.Assistant.extractText(): String =
    parts.filterIsInstance<MessagePart.Text>().joinToString(separator = "\n") { it.text }
