package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState

/** 在紧凑分支概览与完整 Pi 条目检查器之间切换。 */
@Composable
internal fun ConversationEntryTreeDialog(
    state: ChatWindowState,
    conversation: ChatConversationUiState,
    onDismiss: () -> Unit,
) {
    var view by remember(conversation.id) { mutableStateOf(ConversationTreeView.BRANCHES) }
    when (view) {
        ConversationTreeView.BRANCHES -> ConversationBranchOverviewDialog(
            state = state,
            conversation = conversation,
            onShowAllEntries = { view = ConversationTreeView.ALL_ENTRIES },
            onDismiss = onDismiss,
        )

        ConversationTreeView.ALL_ENTRIES -> ConversationEntryInspectorDialog(
            state = state,
            conversation = conversation,
            onShowOverview = { view = ConversationTreeView.BRANCHES },
            onDismiss = onDismiss,
        )
    }
}

/** 会话树弹窗的两个互补层级。 */
private enum class ConversationTreeView {
    BRANCHES,
    ALL_ENTRIES,
}
