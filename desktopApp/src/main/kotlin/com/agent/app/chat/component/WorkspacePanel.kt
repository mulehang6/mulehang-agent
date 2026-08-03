package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX
import com.agent.app.chat.presentation.itemContentSize
import com.agent.app.chat.presentation.shouldForceScrollToLatestAfterSubmit
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppMuted
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppText
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.RingPrimaryButton
import com.agent.shared.chat.model.ExecutionState
import kotlinx.coroutines.launch

/**
 * 原型主工作区。
 */
@Composable
internal fun WorkspacePanel(
    state: ChatWindowState,
    activeRailView: RightRailGlyph,
    filterToolActivityOnly: Boolean,
    terminalTabs: TerminalTabsState,
    terminalSessions: TerminalSessionStore,
    onSelectTerminalTab: (Long) -> Unit,
    onAddTerminalTab: () -> Unit,
    onCloseTerminalTab: (Long) -> Unit,
    onCloseOtherTerminalTabs: (Long) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val conversationId = activeConversation?.id
    val scrollState = remember(conversationId) { ScrollState(0) }
    val isFollowingLatest = remember(conversationId) { mutableStateOf(true) }
    val submittedMessageScrollRequest = remember(conversationId) { mutableStateOf(0) }
    var messageEntry by remember(conversationId) { mutableStateOf<PendingMessageEntry?>(null) }
    var nextMessageEntryId by remember(conversationId) { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val totalContentSize = activeConversation?.items?.sumOf(::itemContentSize) ?: 0

    LaunchedEffect(scrollState.value) {
        isFollowingLatest.value = scrollState.value >= scrollState.maxValue - TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX
    }

    LaunchedEffect(totalContentSize) {
        if (isFollowingLatest.value) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(scrollState.maxValue) {
        if (shouldKeepTimelineAtBottomAfterViewportChange(isFollowingLatest.value)) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(submittedMessageScrollRequest.value) {
        if (submittedMessageScrollRequest.value > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val onSendDraft: () -> Unit = {
        val draft = state.ui.draft
        if (shouldForceScrollToLatestAfterSubmit(draft)) {
            isFollowingLatest.value = true
            submittedMessageScrollRequest.value += 1
            // 没有活动会话时草稿不会成为时间线消息，记录进入动效只会让后续同文本消息误动画。
            if (activeConversation != null) {
                nextMessageEntryId += 1
                messageEntry = PendingMessageEntry(
                    id = nextMessageEntryId,
                    content = draft.trim(),
                )
            }
        }
        state.sendDraft()
    }

    val onMessageEntryFinished: (Long) -> Unit = { finishedId ->
        if (messageEntry?.id == finishedId) messageEntry = null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 8.dp else 0.dp,
                vertical = if (compact) 8.dp else 16.dp,
            ),
    ) {
        ResizableWorkspaceLayout(
            terminalVisible = terminalTabs.hasActiveTab() && activeConversation != null,
            compact = compact,
            modifier = Modifier.fillMaxSize(),
            workspace = { workspaceModifier ->
                Surface(
                    modifier = workspaceModifier,
                    shape = RoundedCornerShape(14.dp),
                    color = AppWorkspaceBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.42f)),
                ) {
                    BoxWithConstraints {
                        val composerInputMaxHeight = maxComposerInputHeight(maxHeight)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                if (
                                    activeConversation == null ||
                                    (activeConversation.items.isEmpty() && activeConversation.executionState == ExecutionState.Idle)
                                ) {
                                    EmptyWorkspaceState(state)
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 720.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        when (activeRailView) {
                                            RightRailGlyph.CODE -> ConversationTimeline(
                                                conversation = activeConversation,
                                                pendingMessageEntry = messageEntry,
                                                onMessageEntryFinished = onMessageEntryFinished,
                                            )
                                            RightRailGlyph.HISTORY -> HistoryPanel(
                                                activeConversation,
                                                filterToolActivityOnly
                                            )

                                            else -> ConversationTimeline(
                                                conversation = activeConversation,
                                                pendingMessageEntry = messageEntry,
                                                onMessageEntryFinished = onMessageEntryFinished,
                                            )
                                        }
                                    }
                                }
                            }
                            if (shouldShowTimelineScrollbar(scrollState.maxValue)) {
                                CompositionLocalProvider(
                                    LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                                        unhoverColor = Color(0xFF747983),
                                        hoverColor = Color(0xFFB8BEC8),
                                    ),
                                ) {
                                    VerticalScrollbar(
                                        adapter = rememberScrollbarAdapter(scrollState),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .fillMaxHeight()
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                    )
                                }
                            }
                            if (
                                activeConversation != null &&
                                shouldShowScrollToBottomButton(
                                    isFollowingLatest = isFollowingLatest.value,
                                    hasTimelineContent = activeConversation.items.isNotEmpty(),
                                )
                            ) {
                                Surface(
                                    onClick = { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 28.dp, bottom = 20.dp)
                                        .size(52.dp),
                                    shape = CircleShape,
                                    color = AppChipBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Canvas(modifier = Modifier.size(22.dp)) {
                                            val stroke = 2.dp.toPx()
                                            val tint = AppMuted
                                            drawLine(
                                                color = tint,
                                                start = Offset(size.width * 0.5f, size.height * 0.16f),
                                                end = Offset(size.width * 0.5f, size.height * 0.75f),
                                                strokeWidth = stroke,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = tint,
                                                start = Offset(size.width * 0.25f, size.height * 0.53f),
                                                end = Offset(size.width * 0.5f, size.height * 0.78f),
                                                strokeWidth = stroke,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = tint,
                                                start = Offset(size.width * 0.75f, size.height * 0.53f),
                                                end = Offset(size.width * 0.5f, size.height * 0.78f),
                                                strokeWidth = stroke,
                                                cap = StrokeCap.Round,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        FooterComposerSection(
                            state = state,
                            compact = compact,
                            onSendDraft = onSendDraft,
                            composerInputMaxHeight = composerInputMaxHeight,
                        )
                    }
                    }
                }
            },
            terminal = { terminalModifier ->
                EmbeddedTerminalPanel(
                    tabs = terminalTabs,
                    sessions = terminalSessions,
                    onSelectTab = onSelectTerminalTab,
                    onAddTab = onAddTerminalTab,
                    onCloseTab = onCloseTerminalTab,
                    onCloseOtherTabs = onCloseOtherTerminalTabs,
                    modifier = terminalModifier,
                )
            },
        )
    }
}

/**
 * 仅在用户未跟随最新输出时显示回到底部动作。
 */
internal fun shouldShowScrollToBottomButton(
    isFollowingLatest: Boolean,
    hasTimelineContent: Boolean = true,
): Boolean = hasTimelineContent && !isFollowingLatest

/**
 * 底部输入区因审批或提问卡片扩高时，决定是否保持时间线贴住最新输出。
 */
internal fun shouldKeepTimelineAtBottomAfterViewportChange(isFollowingLatest: Boolean): Boolean =
    isFollowingLatest

/**
 * 提问或审批挂起时都应在 composer 上方展示独立交互卡片。
 */
internal fun shouldShowPendingInteractionCard(
    hasPendingQuestion: Boolean,
    hasPendingApproval: Boolean,
): Boolean = hasPendingQuestion || hasPendingApproval

/**
 * 主内容实际溢出时才显示垂直滚动条。
 */
internal fun shouldShowTimelineScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0

/**
 * 空任务态主区。
 */
@Composable
private fun EmptyWorkspaceState(state: ChatWindowState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "从一个任务开始",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = AppText,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = "选择工作区后，告诉 MH Agent 你想推进什么。",
            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyStateAction("审查改动", "审查当前工作区的改动，优先指出高风险问题。", state)
            EmptyStateAction("解释项目", "解释这个项目的结构、入口和关键数据流。", state)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyStateAction("规划任务", "为这个需求制定一份可执行的实施计划。", state)
            EmptyStateAction("修复问题", "定位并修复当前项目中的问题。", state)
        }
    }
}

/**
 * 空态中的高价值起步动作，点击后只填充草稿，不自动发送。
 */
@Composable
private fun EmptyStateAction(
    label: String,
    prompt: String,
    state: ChatWindowState,
) {
    RingPrimaryButton(
        text = label,
        onClick = { state.updateDraft(prompt) },
        containerColor = AppChipBackground,
    )
}
