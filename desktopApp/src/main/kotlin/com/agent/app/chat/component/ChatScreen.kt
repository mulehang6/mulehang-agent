package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppBackground
import com.agent.app.design.RightRailGlyph
import com.agent.app.platform.copyTextToClipboard
import com.agent.app.platform.pickFiles

/**
 * 按原型重构后的桌面主界面。
 */
@Composable
fun ChatScreen(
    state: ChatWindowState,
) {
    var activeRailView by remember { mutableStateOf(RightRailGlyph.CODE) }
    var filterToolActivityOnly by remember { mutableStateOf(false) }
    var railFeedback by remember { mutableStateOf<String?>(null) }
    val activeConversation = state.ui.activeConversationOrNull

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        TaskSidebar(
            state = state,
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            ChatHeader(state)
            WorkspacePanel(
                state = state,
                activeRailView = activeRailView,
                filterToolActivityOnly = filterToolActivityOnly,
                railFeedback = railFeedback,
                modifier = Modifier.weight(1f),
            )
        }
        ToolRail(
            activeGlyph = if (filterToolActivityOnly) RightRailGlyph.FILTER else activeRailView,
            onToolClick = { glyph ->
                when (glyph) {
                    RightRailGlyph.CODE,
                    RightRailGlyph.TERMINAL,
                    RightRailGlyph.HISTORY -> {
                        activeRailView = glyph
                        railFeedback = null
                    }

                    RightRailGlyph.UPLOAD -> {
                        val selectedFiles = pickFiles()
                        if (selectedFiles.isNotEmpty()) {
                            state.attachFiles(selectedFiles)
                            railFeedback = "Attached ${selectedFiles.size} file(s)."
                        }
                    }

                    RightRailGlyph.DOWNLOAD -> {
                        railFeedback = activeConversation
                            ?.let(::exportConversationMarkdown)
                            ?.let { "Saved transcript to $it" }
                            ?: railFeedback
                    }

                    RightRailGlyph.COPY -> {
                        val answer = activeConversation?.let(::latestAssistantAnswerText)
                        if (!answer.isNullOrBlank()) {
                            copyTextToClipboard(answer)
                            railFeedback = "Copied latest answer."
                        }
                    }

                    RightRailGlyph.FILTER -> {
                        filterToolActivityOnly = !filterToolActivityOnly
                        railFeedback = if (filterToolActivityOnly) {
                            "Filtering tool activity."
                        } else {
                            "Showing all activity."
                        }
                    }
                }
            },
            modifier = Modifier
                .width(34.dp)
                .fillMaxHeight(),
        )
    }
}
