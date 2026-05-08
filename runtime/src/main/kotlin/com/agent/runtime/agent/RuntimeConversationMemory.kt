package com.agent.runtime.agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemoryConfig
import ai.koog.agents.chatMemory.feature.ChatMemoryPreProcessor
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.time.Clock

/**
 * 为 runtime 提供进程内共享的会话历史存取与窗口裁剪。
 */
class RuntimeConversationMemory(
    val chatHistoryProvider: ChatHistoryProvider = InMemoryChatHistoryProvider(),
    windowSize: Int = DEFAULT_WINDOW_SIZE,
) {
    private val preprocessors: List<ChatMemoryPreProcessor> = ChatMemoryConfig()
        .apply {
            chatHistoryProvider(chatHistoryProvider)
            windowSize(windowSize)
        }
        .preprocessors
        .toList()

    /**
     * 把当前 memory 配置应用到 Koog ChatMemory feature。
     */
    fun applyTo(config: ChatMemoryConfig) {
        config.chatHistoryProvider(chatHistoryProvider)
        preprocessors.forEach(config::addPreProcessor)
    }

    /**
     * 读取会话历史，并按当前预处理链裁剪。
     */
    suspend fun loadHistory(sessionId: String): List<Message> = preprocess(chatHistoryProvider.load(sessionId))

    /**
     * 追加一次用户/助手对话轮次，并按当前窗口大小落盘。
     */
    suspend fun appendTurn(
        sessionId: String,
        userPrompt: String,
        assistantResponse: String,
    ) {
        val updatedMessages = loadHistory(sessionId) + listOf(
            Message.User(
                content = userPrompt,
                metaInfo = RequestMetaInfo.create(Clock.System),
            ),
            Message.Assistant(
                content = assistantResponse,
                metaInfo = ResponseMetaInfo.create(Clock.System),
            ),
        )
        chatHistoryProvider.store(sessionId, preprocess(updatedMessages))
    }

    private fun preprocess(messages: List<Message>): List<Message> =
        preprocessors.fold(messages) { current, preProcessor -> preProcessor.preprocess(current) }

    private companion object {
        private const val DEFAULT_WINDOW_SIZE = 20
    }
}
