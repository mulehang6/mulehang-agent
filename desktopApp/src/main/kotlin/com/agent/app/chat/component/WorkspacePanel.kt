package com.agent.app.chat.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX
import com.agent.app.chat.presentation.itemContentSize
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import com.agent.app.design.RightRailGlyph

/**
 * 原型主工作区。
 */
@Composable
internal fun WorkspacePanel(
    state: ChatWindowState,
    activeRailView: RightRailGlyph,
    filterToolActivityOnly: Boolean,
    railFeedback: String?,
    modifier: Modifier = Modifier,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val conversationId = activeConversation?.id
    val scrollState = remember(conversationId) { ScrollState(0) }
    val isFollowingLatest = remember(conversationId) { mutableStateOf(true) }
    val totalContentSize = activeConversation?.items?.sumOf(::itemContentSize) ?: 0

    LaunchedEffect(scrollState.value) {
        isFollowingLatest.value = scrollState.value >= scrollState.maxValue - TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX
    }

    LaunchedEffect(totalContentSize) {
        if (isFollowingLatest.value) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(0.dp),
        color = AppPanelBackground,
        border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (activeConversation == null) {
                    EmptyWorkspaceState()
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (railFeedback != null) {
                            RailFeedbackCard(railFeedback)
                        }
                        when (activeRailView) {
                            RightRailGlyph.CODE -> ConversationTimeline(activeConversation)
                            RightRailGlyph.TERMINAL -> TerminalPanel(activeConversation, filterToolActivityOnly)
                            RightRailGlyph.HISTORY -> HistoryPanel(activeConversation, filterToolActivityOnly)
                            else -> ConversationTimeline(activeConversation)
                        }
                    }
                }
            }
            FooterComposerSection(state)
        }
    }
}

/**
 * 空任务态主区。
 */
@Composable
private fun EmptyWorkspaceState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Create a task to start working.",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = AppText,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = "The prototype layout is ready, but there is no active task yet.",
            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
        )
    }
}
