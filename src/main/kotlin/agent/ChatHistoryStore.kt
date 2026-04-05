package agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 过滤消息列表，只保留可持久化复用的用户和助手消息。
 */
internal fun retainChatMessages(messages: List<Message>): List<Message> {
    return messages.filter { message ->
        message is Message.User || message is Message.Assistant
    }
}

/**
 * 基于本地 JSON 文件存储会话历史。
 */
internal class FileChatHistoryProvider(private val storageDir: String) : ChatHistoryProvider {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        File(storageDir).mkdirs()
    }

    /**
     * 返回指定会话对应的历史文件。
     */
    private fun getFile(conversationId: String) = File(storageDir, "$conversationId.json")

    /**
     * 读取并过滤指定会话的历史消息。
     */
    override suspend fun load(conversationId: String): List<Message> {
        val file = getFile(conversationId)
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText()
            retainChatMessages(json.decodeFromString<List<Message>>(content))
        } catch (error: Exception) {
            println("[系统] 读取聊天记录失败：${error.message}")
            emptyList()
        }
    }

    /**
     * 将指定会话的可复用消息写入本地文件。
     */
    override suspend fun store(conversationId: String, messages: List<Message>) {
        val file = getFile(conversationId)
        try {
            val content = json.encodeToString(retainChatMessages(messages))
            file.writeText(content)
        } catch (error: Exception) {
            println("[系统] 存储聊天记录失败：${error.message}")
        }
    }
}

/**
 * 构造单轮对话的用户消息和助手回复消息。
 */
private fun buildChatTurn(input: String, response: String): List<Message> {
    return prompt("chat-turn") {
        user(input)
        assistant(response)
    }.messages
}

/**
 * 追加并裁剪会话历史，然后持久化当前对话轮次。
 */
internal suspend fun persistChatTurn(
    chatHistoryProvider: ChatHistoryProvider,
    sessionId: String,
    history: List<Message>,
    input: String,
    response: String
) {
    val updatedHistory = (history + buildChatTurn(input, response))
        .takeLast(CHAT_HISTORY_WINDOW_SIZE)

    chatHistoryProvider.store(sessionId, updatedHistory)
}
