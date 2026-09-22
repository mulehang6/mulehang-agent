@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.itemContentSize
import com.agent.app.chat.presentation.shouldForceScrollToLatestAfterSubmit
import com.agent.app.chat.state.BranchNavigationSummary
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.RightRailGlyph
import com.agent.shared.chat.model.ExecutionState
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * 原型主工作区。
 */
@Composable
internal fun WorkspacePanel(
    state: ChatWindowState,
    activeRailView: RightRailGlyph,
    filterToolActivityOnly: Boolean,
    terminalTabs: TerminalTabsState,
    terminalPanelVisible: Boolean,
    terminalSessions: TerminalSessionStore,
    onSelectTerminalTab: (Long) -> Unit,
    onAddTerminalTab: () -> Unit,
    onCloseTerminalTab: (Long) -> Unit,
    onCloseOtherTerminalTabs: (Long) -> Unit,
    onHideTerminalPanel: () -> Unit,
    sidePanelVisible: Boolean = terminalPanelVisible && terminalTabs.hasActiveTab(),
    sidePanel: @Composable (Modifier, onRevealConversationEntry: (String) -> Unit) -> Unit =
        { terminalModifier, _ ->
            EmbeddedTerminalPanel(
                tabs = terminalTabs,
                sessions = terminalSessions,
                onSelectTab = onSelectTerminalTab,
                onAddTab = onAddTerminalTab,
                onCloseTab = onCloseTerminalTab,
                onCloseOtherTabs = onCloseOtherTerminalTabs,
                onHidePanel = onHideTerminalPanel,
                modifier = terminalModifier,
            )
        },
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val conversationId = activeConversation?.id
    val scrollState = remember(conversationId) { ScrollState(0) }
    val isFollowingLatest = remember(conversationId) { mutableStateOf(true) }
    val submittedMessageScrollRequest = remember(conversationId) { mutableStateOf(0) }
    var messageEntry by remember(conversationId) { mutableStateOf<PendingMessageEntry?>(null) }
    var returningToHead by remember(conversationId) { mutableStateOf(false) }
    var returnToHeadError by remember(conversationId) { mutableStateOf<String?>(null) }
    var messageOperationEntryId by remember(conversationId) { mutableStateOf<String?>(null) }
    var messageActionError by remember(conversationId) { mutableStateOf<String?>(null) }
    var timelineViewportBounds by remember(conversationId) { mutableStateOf<Rect?>(null) }
    var timelineContentBounds by remember(conversationId) { mutableStateOf<Rect?>(null) }
    val timelineTurnBounds = remember(conversationId) { mutableStateMapOf<String, TimelineTurnBounds>() }
    val timelineEntryBounds = remember(conversationId, activeConversation?.activeEntryId) {
        mutableStateMapOf<String, TimelineTurnBounds>()
    }
    var pendingTreeEntryId by remember(conversationId) { mutableStateOf<String?>(null) }
    var nextMessageEntryId by remember(conversationId) { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val totalContentSize = activeConversation?.items?.sumOf(::itemContentSize) ?: 0
    val timelineTurns = remember(
        activeConversation?.treeFormatVersion,
        activeConversation?.entries,
        activeConversation?.activeEntryId,
        activeConversation?.items,
    ) {
        activeConversation?.let(::buildTimelineTurnPresentations).orEmpty()
    }
    LaunchedEffect(timelineTurns.map(TimelineTurnPresentation::anchorId)) {
        val activeIds = timelineTurns.mapTo(mutableSetOf(), TimelineTurnPresentation::anchorId)
        timelineTurnBounds.keys.toList().filterNot(activeIds::contains).forEach(timelineTurnBounds::remove)
    }
    LaunchedEffect(scrollState) {
        snapshotFlow {
            isTimelineFollowingLatest(
                scrollValue = scrollState.value,
                maxScrollValue = scrollState.maxValue,
            )
        }.collect { followingLatest ->
            isFollowingLatest.value = followingLatest
        }
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

    val pendingTreeEntryBounds = pendingTreeEntryId?.let(timelineEntryBounds::get)
    LaunchedEffect(
        pendingTreeEntryId,
        pendingTreeEntryBounds != null,
        timelineViewportBounds != null,
    ) {
        val entryId = pendingTreeEntryId ?: return@LaunchedEffect
        val entryBounds = pendingTreeEntryBounds ?: return@LaunchedEffect
        val viewport = timelineViewportBounds ?: return@LaunchedEffect
        val target = timelineScrollTarget(
            currentScroll = scrollState.value,
            anchorTop = entryBounds.top,
            viewportTop = viewport.top,
            maxScroll = scrollState.maxValue,
            topInsetPx = with(density) { 24.dp.toPx() },
        )
        scrollState.animateScrollTo(target, tween(durationMillis = 140))
        if (pendingTreeEntryId == entryId) pendingTreeEntryId = null
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

    /** 按 pi-web 语义直接回到用户消息之前，不为消息级编辑生成离开路径摘要。 */
    val editFromUserEntry: (String) -> Unit = { entryId ->
        if (conversationId != null && messageOperationEntryId == null) {
            messageOperationEntryId = entryId
            scope.launch {
                val result = state.conversationTreeController.editFromUserEntry(
                    conversationId = conversationId,
                    userEntryId = entryId,
                    summary = BranchNavigationSummary(),
                )
                messageOperationEntryId = null
                if (!result.succeeded) messageActionError = result.message
            }
        }
    }

    /** 从消息创建独立任务，不改变源会话的内部条目图。 */
    val createConversationFromUserEntry: (String) -> Unit = { entryId ->
        if (conversationId != null && messageOperationEntryId == null) {
            messageOperationEntryId = entryId
            val result = state.conversationTreeController.createConversationFromUserEntry(
                conversationId,
                entryId,
            )
            messageOperationEntryId = null
            if (!result.succeeded) messageActionError = result.message
        }
    }

    /** 从会话树定位条目；必要时先无摘要切到包含该条目的完整分支。 */
    val revealConversationEntry: (String) -> Unit = { entryId ->
        val conversation = state.ui.activeConversationOrNull
        if (conversation != null && conversation.id == conversationId) {
            val anchorId = conversationEntryNavigationAnchorId(conversation.entries, entryId)
            val targetLeafId = conversationEntryNavigationLeaf(
                entries = conversation.entries,
                entryId = entryId,
                activeEntryId = conversation.activeEntryId,
                headEntryId = conversation.headEntryId,
            )
            if (anchorId == null || targetLeafId == null) {
                messageActionError = "条目不存在，无法定位。"
            } else {
                messageActionError = null
                isFollowingLatest.value = false
                pendingTreeEntryId = anchorId
                if (targetLeafId != conversation.activeEntryId) {
                    timelineEntryBounds.clear()
                    scope.launch {
                        val result = state.conversationTreeController.switchToLeaf(
                            conversationId = conversation.id,
                            leafEntryId = targetLeafId,
                            summary = BranchNavigationSummary(),
                        )
                        if (!result.succeeded) {
                            if (pendingTreeEntryId == anchorId) pendingTreeEntryId = null
                            messageActionError = result.message
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = ISLANDS_LAYOUT_GAP,
            ),
    ) {
        ResizableWorkspaceLayout(
            terminalVisible = sidePanelVisible,
            compact = compact,
            modifier = Modifier.fillMaxSize(),
            workspace = { workspaceModifier ->
                JewelSurface(
                    role = JewelSurfaceRole.PANEL,
                    modifier = workspaceModifier,
                    radius = 14.dp,
                    solidColor = AppWorkspaceBackground,
                    borderColor = Color.Transparent,
                    borderWidth = 0.dp,
                ) {
                    BoxWithConstraints {
                        val composerInputMaxHeight = maxComposerInputHeight(maxHeight)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (
                                activeConversation != null &&
                                state.conversationTreeController.isAwayFromHead(activeConversation.id)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = if (compact) 16.dp else 32.dp,
                                            end = if (compact) 16.dp else 32.dp,
                                            top = 16.dp,
                                            bottom = 4.dp,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    HistoricalBranchBanner(
                                        returning = returningToHead,
                                        errorMessage = returnToHeadError,
                                        onReturnToHead = {
                                            returnToHeadError = null
                                            returningToHead = true
                                            scope.launch {
                                                val result =
                                                    state.conversationTreeController.returnToHead(activeConversation.id)
                                                returningToHead = false
                                                if (!result.succeeded) returnToHeadError = result.message
                                            }
                                        },
                                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                                    )
                                }
                            }
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        timelineViewportBounds = coordinates.boundsInWindow()
                                    },
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
                                                .widthIn(max = 720.dp)
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coordinates ->
                                                    timelineContentBounds = coordinates.boundsInWindow()
                                                },
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            when (activeRailView) {
                                                RightRailGlyph.CODE -> ConversationTimeline(
                                                    conversation = activeConversation,
                                                    pendingMessageEntry = messageEntry,
                                                    onMessageEntryFinished = onMessageEntryFinished,
                                                    operationEntryId = messageOperationEntryId,
                                                    onTurnPositioned = { anchorId, top, bottom ->
                                                        timelineTurnBounds[anchorId] = TimelineTurnBounds(top, bottom)
                                                    },
                                                    onEntryPositioned = { entryId, top, bottom ->
                                                        timelineEntryBounds[entryId] = TimelineTurnBounds(top, bottom)
                                                    },
                                                    onEditFromHere = editFromUserEntry,
                                                    onNewSession = createConversationFromUserEntry,
                                                )

                                                RightRailGlyph.HISTORY -> HistoryPanel(
                                                    activeConversation,
                                                    filterToolActivityOnly
                                                )

                                                else -> ConversationTimeline(
                                                    conversation = activeConversation,
                                                    pendingMessageEntry = messageEntry,
                                                    onMessageEntryFinished = onMessageEntryFinished,
                                                    operationEntryId = messageOperationEntryId,
                                                    onTurnPositioned = { anchorId, top, bottom ->
                                                        timelineTurnBounds[anchorId] = TimelineTurnBounds(top, bottom)
                                                    },
                                                    onEntryPositioned = { entryId, top, bottom ->
                                                        timelineEntryBounds[entryId] = TimelineTurnBounds(top, bottom)
                                                    },
                                                    onEditFromHere = editFromUserEntry,
                                                    onNewSession = createConversationFromUserEntry,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (shouldShowTimelineScrollbar(scrollState.maxValue)) {
                                    VerticalScrollbar(
                                        scrollState = scrollState,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .fillMaxHeight()
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                    )
                                }
                                val leftGutterDp = timelineViewportBounds?.let { viewport ->
                                    timelineContentBounds?.let { content ->
                                        timelineLeftGutterDp(
                                            viewportLeftPx = viewport.left,
                                            contentLeftPx = content.left,
                                            density = density.density,
                                        )
                                    }
                                } ?: 0f
                                val showTimelineNavigation = activeRailView != RightRailGlyph.HISTORY &&
                                        shouldShowTimelineNavigation(
                                            turnCount = timelineTurns.size,
                                            compact = compact,
                                            leftGutterDp = leftGutterDp,
                                        )
                                if (showTimelineNavigation && maxHeight >= 144.dp) {
                                    val viewport = timelineViewportBounds
                                    val activeTurnIndex = viewport?.let { bounds ->
                                        activeTimelineTurnIndex(
                                            turns = timelineTurns,
                                            anchorTops = timelineTurnBounds.mapValues { it.value.top },
                                            viewportTop = bounds.top,
                                            viewportBottom = bounds.bottom,
                                        )
                                    } ?: 0
                                    ConversationTimelineMinimap(
                                        turns = timelineTurns,
                                        activeIndex = activeTurnIndex,
                                        railHeight = minOf(maxHeight - 48.dp, 280.dp),
                                        onNavigate = { index, immediate ->
                                            val targetTurn = timelineTurns.getOrNull(index)
                                            val targetBounds = targetTurn?.let { timelineTurnBounds[it.anchorId] }
                                            val currentViewport = timelineViewportBounds
                                            if (targetBounds != null && currentViewport != null) {
                                                val target = timelineScrollTarget(
                                                    currentScroll = scrollState.value,
                                                    anchorTop = targetBounds.top,
                                                    viewportTop = currentViewport.top,
                                                    maxScroll = scrollState.maxValue,
                                                    topInsetPx = with(density) { 24.dp.toPx() },
                                                )
                                                scope.launch {
                                                    if (immediate) {
                                                        scrollState.scrollTo(target)
                                                    } else {
                                                        scrollState.animateScrollTo(target, tween(durationMillis = 140))
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = 16.dp),
                                    )
                                }
                                if (
                                    activeConversation != null &&
                                    shouldShowScrollToBottomButton(
                                        isFollowingLatest = isFollowingLatest.value,
                                        hasTimelineContent = activeConversation.items.isNotEmpty(),
                                    )
                                ) {
                                    val scrollToBottomButtonStyle = timelineScrollToBottomButtonStyle()
                                    // Jewel 的 tooltip 重载会包裹触发器，BoxScope 对齐必须留在其直接子节点上。
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 28.dp, bottom = 20.dp)
                                            .size(36.dp),
                                    ) {
                                        IconActionButton(
                                            key = AllIconsKeys.General.ArrowDown,
                                            contentDescription = "回到底部",
                                            onClick = { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
                                            modifier = Modifier.fillMaxSize(),
                                            iconModifier = Modifier.size(16.dp),
                                            style = scrollToBottomButtonStyle,
                                            tooltip = { Text("回到底部") },
                                        )
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
            terminal = { terminalModifier -> sidePanel(terminalModifier, revealConversationEntry) },
        )
    }
    messageActionError?.let { error ->
        MessageActionErrorDialog(
            message = error,
            onDismiss = { messageActionError = null },
        )
    }
}
