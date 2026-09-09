package com.agent.app.chat.state

import com.agent.shared.agent.api.ConversationTitleRequest
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
    ) {
        with(window) {
            val titleGenerator = conversationTitleGenerator ?: return
            invalidateConversationTitleGeneration(conversationId)
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
                    if (generatedTitle == null) {
                        conversation.copy(titleState = ConversationTitleState.FAILED)
                    } else {
                        conversation.copy(
                            title = generatedTitle,
                            titleState = ConversationTitleState.GENERATED,
                        )
                    }
                }
            }
        }
    }

    /**
     * 取消标题任务，并递增版本以阻止迟到结果覆盖当前状态。
     */
    fun invalidateConversationTitleGeneration(conversationId: String) {
        with(window) {
            conversationTitleJobs.remove(conversationId)?.cancel()
            conversationTitleGenerationVersions[conversationId] =
                (conversationTitleGenerationVersions[conversationId] ?: 0) + 1
        }
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
