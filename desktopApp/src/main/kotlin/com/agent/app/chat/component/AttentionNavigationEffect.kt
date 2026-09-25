package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.agent.app.chat.state.ChatWindowState

/** 会话展示后消费一次系统通知的精确条目定位请求。 */
@Composable
internal fun ConsumeAttentionNavigation(
    state: ChatWindowState,
    conversationId: String?,
    revealEntry: (String) -> Unit,
    onFailure: (String) -> Unit,
) {
    val request = state.ui.attentionNavigation
    val activeEntryId = state.ui.activeConversationOrNull?.activeEntryId
    LaunchedEffect(request?.serial, conversationId, activeEntryId) {
        if (request != null && request.conversationId == conversationId) {
            val conversation = state.ui.activeConversationOrNull ?: return@LaunchedEffect
            if (!isConversationEntryOnActivePath(conversation.entries, activeEntryId, request.entryId)) {
                val result = state.conversationTreeController.switchToLeaf(conversation.id, request.entryId)
                if (!result.succeeded) {
                    onFailure(result.message ?: "无法定位通知条目。")
                    state.clearAttentionNavigation(request.serial)
                }
                return@LaunchedEffect
            }
            revealEntry(request.entryId)
            state.clearAttentionNavigation(request.serial)
        }
    }
}
