package com.agent.app.chat.state

import com.agent.shared.agent.api.ConversationTitleRequest
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.conversationEntryPath
import com.agent.shared.settings.model.ConfigProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 管理异步标题生成与过期结果的失效。 */
internal class ChatTitleController(private val window: ChatWindowState) {
    /**
     * 以独立、无工具的标题生成器更新首条用户消息所在会话。
     */
    fun requestConversationTitle(
        conversationId: String,
        firstUserMessage: String,
        profile: ConfigProfile,
        isRegeneration: Boolean = false,
    ) {
        with(window) {
            val titleGenerator = conversationTitleGenerator ?: return
            invalidateConversationTitleGeneration(conversationId)
            if (isRegeneration) {
                mutateConversation(conversationId) { conversation ->
                    conversation.copy(titleRegenerationInProgress = true)
                }
            }
            val generationVersion = conversationTitleGenerationVersions.getValue(conversationId)
            conversationTitleJobs[conversationId] = scope.launch {
                val generatedTitle = try {
                    normalizeGeneratedConversationTitle(
                        rawTitle = titleGenerator.generate(
                            ConversationTitleRequest(
                                firstUserMessage = firstUserMessage,
                                profile = profile,
                            ),
                        ),
                        fallbackTitle = buildConversationTitle(firstUserMessage),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (conversationTitleGenerationVersions[conversationId] != generationVersion) return@launch
                conversationTitleJobs.remove(conversationId)
                mutateConversation(conversationId) { conversation ->
                    if (isRegeneration && generatedTitle == null) {
                        conversation.copy(titleRegenerationInProgress = false)
                    } else if (generatedTitle == null) {
                        conversation.copy(titleState = ConversationTitleState.FAILED)
                    } else {
                        conversation.copy(
                            title = generatedTitle,
                            titleState = ConversationTitleState.GENERATED,
                            titleRegenerationInProgress = false,
                        )
                    }
                }
            }
        }
    }

    /** 判断指定会话是否具备显式重新生成标题所需的消息、配置和生成器。 */
    fun canRegenerateConversationTitle(conversationId: String): Boolean {
        val conversation = window.findConversationOrNull(conversationId) ?: return false
        return window.conversationTitleGenerator != null &&
                window.profileForConversation(conversation) != null &&
                !conversation.titleRegenerationInProgress &&
                firstUserMessageForTitle(conversation) != null
    }

    /** 使用持久 head 路径上的首条用户消息重新生成标题，失败时保留原标题。 */
    fun regenerateConversationTitle(conversationId: String): Boolean {
        val conversation = window.findConversationOrNull(conversationId) ?: return false
        val firstUserMessage = firstUserMessageForTitle(conversation) ?: return false
        val profile = window.profileForConversation(conversation) ?: return false
        if (window.conversationTitleGenerator == null || conversation.titleRegenerationInProgress) return false
        requestConversationTitle(conversationId, firstUserMessage, profile, isRegeneration = true)
        return true
    }

    /**
     * 取消标题任务，并递增版本以阻止迟到结果覆盖当前状态。
     */
    fun invalidateConversationTitleGeneration(conversationId: String) {
        with(window) {
            conversationTitleJobs.remove(conversationId)?.cancel()
            conversationTitleGenerationVersions[conversationId] =
                (conversationTitleGenerationVersions[conversationId] ?: 0) + 1
            mutateConversation(conversationId) { conversation ->
                if (conversation.titleRegenerationInProgress) {
                    conversation.copy(titleRegenerationInProgress = false)
                } else {
                    conversation
                }
            }
        }
    }

    /** 返回树会话 head 路径或旧线性会话中的首条非空用户消息。 */
    private fun firstUserMessageForTitle(conversation: ChatConversationUiState): String? {
        val treeMessage = if (conversation.treeFormatVersion > 0) {
            conversationEntryPath(conversation.entries, conversation.headEntryId)
                .filterIsInstance<ConversationEntry.Message>()
                .firstOrNull { it.message.role == ChatRole.User }
                ?.message
                ?.content
        } else {
            null
        }
        val historyMessage = conversation.history
            .filterIsInstance<AgentConversationHistoryMessage.User>()
            .firstOrNull()
            ?.content
        val timelineMessage = conversation.items
            .filterIsInstance<ChatMessageItem>()
            .firstOrNull { it.message.role == ChatRole.User }
            ?.message
            ?.content
        return sequenceOf(treeMessage, historyMessage, timelineMessage)
            .firstNotNullOfOrNull { message -> message?.trim()?.takeIf(String::isNotBlank) }
    }

    /**
     * 恢复持久化任务前，取消所有窗口内尚未完成的标题生成请求。
     */
    fun invalidateAllConversationTitleGenerations() {
        with(window) {
            conversationTitleJobs.values.forEach(Job::cancel)
            conversationTitleJobs.clear()
            conversationTitleGenerationVersions.keys.toList().forEach(::invalidateConversationTitleGeneration)
        }
    }

    /**
     * 清洗模型格式标记，保证侧栏始终获得单行、有限长度的可读标题。
     */
    private fun normalizeGeneratedConversationTitle(
        rawTitle: String,
        fallbackTitle: String,
    ): String {
        val normalizedTitle = rawTitle
            .replace(Regex("[*_`]+"), "")
            .lineSequence()
            .joinToString(separator = " ") { line -> line.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’')
            .take(CONVERSATION_TITLE_MAX_LENGTH)
            .trim()
        return normalizedTitle.ifBlank { fallbackTitle }
    }

}
