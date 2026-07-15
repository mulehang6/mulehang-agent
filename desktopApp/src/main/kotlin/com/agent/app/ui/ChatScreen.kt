package com.agent.app.ui

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatTaskGroup
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.isStoppable
import com.agent.app.chat.export.buildConversationMarkdown
import com.agent.app.chat.export.sanitizeFileName
import com.agent.app.chat.export.writeConversationMarkdown
import com.agent.app.chat.presentation.TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX
import com.agent.app.chat.presentation.buildComposerPrimaryActionVisual
import com.agent.app.chat.presentation.buildReasoningHeadline
import com.agent.app.chat.presentation.buildSecondaryStatus
import com.agent.app.chat.presentation.buildToolEventHeadline
import com.agent.app.chat.presentation.buildToolEventKindLabel
import com.agent.app.chat.presentation.groupProfilesByProvider
import com.agent.app.chat.presentation.itemContentSize
import com.agent.app.chat.presentation.modelVariantsFor
import com.agent.app.chat.presentation.resolveWorkspaceForTaskCreation
import com.agent.app.chat.presentation.toolEventHasDetails
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.plan.parseUpdatePlanPreview
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

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
            TopHeader(state)
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

/**
 * 原型顶部标题栏。
 */
@Composable
private fun TopHeader(state: ChatWindowState) {
    val activeConversation = state.ui.activeConversationOrNull
    val breadcrumb = activeConversation?.workspacePath ?: "workspace / none"
    val actions = buildHeaderActions()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppHeaderBackground,
        border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RingHeaderActionButton(glyph = actions.left.glyph, inline = true)
                Text(
                    text = "Air",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AppText,
                    ),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = breadcrumb,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = activeConversation?.title ?: "No task selected",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = AppText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.right.forEach { action ->
                    RingHeaderActionButton(glyph = action.glyph, inline = true)
                }
            }
        }
    }
}

/**
 * 原型左侧 task 侧栏。
 */
@Composable
private fun TaskSidebar(
    state: ChatWindowState,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val startTaskInCurrentWorkspace: () -> Unit = {
        val workspacePath = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = state.ui.activeConversationOrNull?.workspacePath,
            forceDirectoryPicker = false,
            pickWorkspaceDirectory = ::pickWorkspaceDirectory,
        )
        if (workspacePath != null) {
            state.createConversationForWorkspace(workspacePath)
        }
    }
    val startTaskInSelectedWorkspace: () -> Unit = {
        val workspacePath = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = state.ui.activeConversationOrNull?.workspacePath,
            forceDirectoryPicker = true,
            pickWorkspaceDirectory = ::pickWorkspaceDirectory,
        )
        if (workspacePath != null) {
            state.createConversationForWorkspace(workspacePath)
        }
    }
    val filteredSections = remember(state.ui.taskSections, searchQuery) {
        state.ui.taskSections.map { section ->
            section.copy(
                tasks = section.tasks.filter { task ->
                    searchQuery.isBlank() ||
                            task.title.contains(searchQuery, ignoreCase = true) ||
                            task.subtitle.contains(searchQuery, ignoreCase = true)
                },
            )
        }
    }
    Column(
        modifier = modifier
            .background(AppSidebarBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RingInputField(
                modifier = Modifier.weight(1f),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = "Search tasks",
                iconGlyph = HeaderGlyph.SEARCH,
                borderless = true,
            )
            RingHeaderActionButton(
                glyph = HeaderGlyph.ADD,
                onClick = startTaskInSelectedWorkspace,
                inline = true,
            )
        }
        RingPrimaryButton(
            text = "New Task",
            onClick = startTaskInCurrentWorkspace,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            filteredSections.forEach { section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    section.tasks.forEach { task ->
                        TaskListItem(
                            task = task,
                            selected = task.id == state.ui.activeTaskId,
                            onClick = { state.selectConversation(task.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧 task 条目。
 */
@Composable
private fun TaskListItem(
    task: ChatTaskListItemUiState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotColor = if (task.group == ChatTaskGroup.DONE) AppSuccess else AppAccent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AppSelectedBackground else Color.Transparent,
        border = null,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape),
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppText,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = task.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.stats,
                style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
            )
        }
    }
}

/**
 * 原型主工作区。
 */
@Composable
private fun WorkspacePanel(
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

/**
 * 把挂起问题/审批显示在输入区上方，紧跟 composer 之上。
 */
@Composable
private fun PendingCards(
    conversation: ChatConversationUiState,
    state: ChatWindowState,
) {
    conversation.pendingQuestion?.let { pending ->
        QuestionCard(
            pending = pending,
            onOptionClick = state::answerPendingQuestion,
            onSubmitText = state::answerPendingQuestion,
        )
    }
    conversation.pendingApproval?.let { pending ->
        ApprovalCard(
            pending = pending,
            onApprove = { state.answerPendingApproval(true) },
            onReject = { state.answerPendingApproval(false) },
        )
    }
}

/**
 * 完整会话时间线，按顺序渲染所有用户消息、助手回答、思考块和工具事件。
 */
@Composable
private fun ConversationTimeline(conversation: ChatConversationUiState) {
    if (conversation.items.isEmpty() && conversation.executionState == ExecutionState.Idle) {
        Text(
            text = "Ready for a new task",
            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
        )
        return
    }
    val failedState = conversation.executionState as? ExecutionState.Failed
    val hasFailedToolEvent = conversation.items.any {
        it is ToolEventItem && it.status == ToolEventStatus.Failed
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        conversation.items.forEach { item ->
            when (item) {
                is ChatMessageItem -> {
                    if (item.message.role == ChatRole.User) {
                        UserMessageCard(item.message.content)
                    } else {
                        AssistantMessageBlock(item.message.content)
                    }
                }

                is ReasoningItem -> TimelineReasoningItem(item)
                is ToolEventItem -> TimelineToolEvent(item)
            }
        }
        if (conversation.executionState == ExecutionState.Running) {
            buildSecondaryStatus(conversation)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                )
            }
        }
        if (failedState != null && !hasFailedToolEvent) {
            Text(
                text = "${failedState.error.title}: ${failedState.error.message}",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppDanger,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

/**
 * 单条用户消息卡片。
 */
@Composable
private fun UserMessageCard(content: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(8.dp),
            color = AppUserCardBackground,
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(color = AppText),
            )
        }
    }
}

/**
 * 单条助手回答块。
 */
@Composable
private fun AssistantMessageBlock(content: String) {
    val paragraphs = content
        .trim()
        .takeIf(String::isNotBlank)
        ?.split(Regex("\n\\s*\n"))
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?: listOf("No assistant output yet for this task.")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppText,
                    lineHeight = 23.sp,
                ),
            )
        }
    }
}

/**
 * 时间线中的思考块展示。
 */
@Composable
private fun TimelineReasoningItem(item: ReasoningItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildReasoningHeadline(item),
            style = MaterialTheme.typography.titleSmall.copy(
                color = AppMuted,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = item.displayText,
            style = MaterialTheme.typography.bodySmall.copy(
                color = AppMuted,
                lineHeight = 20.sp,
            ),
        )
    }
}

/**
 * 时间线中的工具事件条目。
 */
@Composable
private fun TimelineToolEvent(item: ToolEventItem) {
    val kindLabel = buildToolEventKindLabel(item)
    val preview = item.preview?.takeIf(String::isNotBlank)
    val errorMessage = item.errorMessage?.takeIf(String::isNotBlank)
    val isFailed = item.status == ToolEventStatus.Failed
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildToolEventHeadline(item),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isFailed) AppDanger else AppText,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (kindLabel != null) {
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
                )
            }
        }
        if (preview != null && toolEventHasDetails(item)) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted,
                    lineHeight = 18.sp,
                ),
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppDanger,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

/**
 * 原型下方 plan + composer 区域。
 */
@Composable
private fun FooterComposerSection(state: ChatWindowState) {
    val activeConversation = state.ui.activeConversationOrNull
    val planCard = activeConversation?.let { extractPlanCard(it.items) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (activeConversation != null) {
                PendingCards(activeConversation, state)
            }
            if (planCard != null) {
                PlanCard(
                    title = planCard.title,
                    entries = planCard.entries,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
            ComposerPanel(
                state = state,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 计划卡片。
 */
@Composable
private fun PlanCard(
    title: String,
    entries: List<TaskPlanEntry>,
    modifier: Modifier = Modifier,
) {
    RingIsland(
        modifier = modifier,
        color = AppSidebarBackground,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = if (entry.active) AppAccent else AppChipBackground,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.number.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (entry.active) Color.White else AppMuted,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = AppText),
                    )
                }
            }
        }
    }
}

/**
 * 原型 composer。
 */
@Composable
private fun ComposerPanel(
    state: ChatWindowState,
    modifier: Modifier = Modifier,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val profiles = state.availableProfiles
    val selectedProfile = state.activeProfile
    val executionState = activeConversation?.executionState ?: ExecutionState.Idle
    val primaryActionVisual = buildComposerPrimaryActionVisual(executionState)
    val providerProfiles = groupProfilesByProvider(profiles)
    val currentProvider = selectedProfile?.providerId ?: profiles.firstOrNull()?.providerId
    val currentProviderProfiles = providerProfiles[currentProvider].orEmpty()
    val selectedVariants = selectedProfile?.let(::modelVariantsFor).orEmpty()
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var reasoningExpanded by remember { mutableStateOf(false) }
    var permissionExpanded by remember { mutableStateOf(false) }

    RingIsland(
        modifier = modifier,
        color = ComposerBackground,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!activeConversation?.attachments.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    activeConversation.attachments.forEach { attachment ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = AppChipBackground,
                        ) {
                            Text(
                                text = attachment.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall.copy(color = AppText),
                            )
                        }
                    }
                }
            }

            RingInputField(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (shouldSubmitComposerKey(event.key, event.type, event.isShiftPressed)) {
                            if (executionState.isStoppable()) {
                                state.cancelActiveRun()
                            } else {
                                state.sendDraft()
                            }
                            true
                        } else {
                            false
                        }
                    },
                value = state.ui.draft,
                onValueChange = state::updateDraft,
                minLines = 3,
                placeholder = "Ask anything...",
                borderless = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RingHeaderActionButton(
                        glyph = HeaderGlyph.ADD,
                        onClick = { state.attachFiles(pickFiles()) },
                        inline = true,
                    )
                    RingSelectChip(
                        label = selectedProfile?.providerLabel ?: currentProvider ?: "Provider",
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = !providerExpanded },
                        modifier = Modifier.width(120.dp),
                    ) {
                        providerProfiles.forEach { (_, providerModels) ->
                            val first = providerModels.firstOrNull() ?: return@forEach
                            DropdownMenuItem(
                                text = { Text(first.providerLabel) },
                                onClick = {
                                    providerExpanded = false
                                    state.selectProfile(first.id)
                                },
                            )
                        }
                    }
                    RingSelectChip(
                        label = selectedProfile?.modelLabel ?: selectedProfile?.model ?: "Model",
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded },
                        modifier = Modifier.width(152.dp),
                    ) {
                        currentProviderProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.modelLabel ?: profile.model) },
                                onClick = {
                                    modelExpanded = false
                                    state.selectProfile(profile.id)
                                },
                            )
                        }
                    }
                    if (selectedVariants.isNotEmpty()) {
                        RingSelectChip(
                            label = activeConversation?.reasoningEffort?.name ?: "Reasoning",
                            expanded = reasoningExpanded,
                            onExpandedChange = { reasoningExpanded = !reasoningExpanded },
                            modifier = Modifier.width(120.dp),
                        ) {
                            selectedVariants.forEach { variant ->
                                val effort = variant.reasoningEffort ?: return@forEach
                                DropdownMenuItem(
                                    text = { Text(effort.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                    onClick = {
                                        reasoningExpanded = false
                                        state.updateReasoningEffort(effort)
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RingSelectChip(
                        label = permissionLabel(state.ui.permissionPreset),
                        expanded = permissionExpanded,
                        tone = permissionTone(state.ui.permissionPreset),
                        onExpandedChange = { permissionExpanded = !permissionExpanded },
                        modifier = Modifier.width(126.dp),
                    ) {
                        PermissionPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(permissionLabel(preset)) },
                                onClick = {
                                    permissionExpanded = false
                                    state.updatePermission(preset)
                                },
                            )
                        }
                    }
                    RingPrimaryButton(
                        text = if (primaryActionVisual.danger) "Stop" else "Send",
                        onClick = {
                            if (executionState.isStoppable()) {
                                state.cancelActiveRun()
                            } else {
                                state.sendDraft()
                            }
                        },
                        containerColor = if (primaryActionVisual.danger) AppDanger else AppAccent,
                    )
                }
            }
        }
    }
}

/**
 * 右侧 rail 操作后的轻量反馈。
 */
@Composable
private fun RailFeedbackCard(message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppChipBackground,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
        )
    }
}

/**
 * 终端视图。
 */
@Composable
private fun TerminalPanel(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
) {
    val entries = buildTerminalEntries(conversation, filterToolActivityOnly)
    RingIsland(
        modifier = Modifier.fillMaxWidth(),
        color = AppSidebarBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            entries.forEach { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AppText,
                        lineHeight = 20.sp,
                    ),
                )
            }
        }
    }
}

/**
 * 历史视图。
 */
@Composable
private fun HistoryPanel(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
) {
    val entries = buildHistoryEntries(conversation, filterToolActivityOnly)
    RingIsland(
        modifier = Modifier.fillMaxWidth(),
        color = AppSidebarBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            entries.forEach { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AppText,
                        lineHeight = 20.sp,
                    ),
                )
            }
        }
    }
}

/**
 * 右侧固定工具栏。
 */
@Composable
private fun ToolRail(
    activeGlyph: RightRailGlyph,
    onToolClick: (RightRailGlyph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolGroups = buildRightRailGroups()
    Column(
        modifier = modifier
            .background(AppRailBackground)
            .padding(top = 10.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        toolGroups.forEachIndexed { groupIndex, group ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.forEach { item ->
                    RingRailActionButton(
                        glyph = item.glyph,
                        active = item.glyph == activeGlyph,
                        onClick = { onToolClick(item.glyph) },
                    )
                }
            }
            if (groupIndex != toolGroups.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .width(18.dp)
                        .height(1.dp)
                        .background(AppLine),
                )
            }
        }
    }
}

/**
 * 计划卡片里的步骤条目。
 */
data class TaskPlanEntry(
    val number: Int,
    val text: String,
    val active: Boolean = false,
)

/**
 * 从真实 update_plan 工具事件中提取出的 plan 卡片。
 */
data class PlanCardUiState(
    val title: String,
    val entries: List<TaskPlanEntry>,
)

private data class UpdatePlanPayload(
    val explanation: String? = null,
    val plan: List<UpdatePlanStepPayload> = emptyList(),
)

private data class UpdatePlanStepPayload(
    val step: String,
    val status: String,
)

/**
 * 仅当 agent 真实调用 update_plan 且携带 plan 数据时，才显示 Plan 卡片。
 */
internal fun extractPlanCard(items: List<ConversationItem>): PlanCardUiState? {
    val payload = items
        .asReversed()
        .filterIsInstance<ToolEventItem>()
        .filter { it.toolName == "update_plan" && !it.preview.isNullOrBlank() }
        .firstNotNullOfOrNull { it.preview?.let(::parseUpdatePlanPayload) }
        ?: return null
    if (payload.plan.isEmpty()) return null
    return PlanCardUiState(
        title = "Plan",
        entries = payload.plan.mapIndexed { index, step ->
            TaskPlanEntry(
                number = index + 1,
                text = step.step,
                active = step.status == "in_progress",
            )
        },
    )
}

/**
 * 解析 update_plan 的工具参数预览。
 */
private fun parseUpdatePlanPayload(preview: String): UpdatePlanPayload? = runCatching {
    val parsedPreview = parseUpdatePlanPreview(preview) ?: return@runCatching null
    if (parsedPreview.plan.isEmpty()) {
        null
    } else {
        UpdatePlanPayload(
            explanation = parsedPreview.explanation,
            plan = parsedPreview.plan.map { step ->
                UpdatePlanStepPayload(
                    step = step.step,
                    status = step.status,
                )
            },
        )
    }
}.getOrNull()

/**
 * 仅在 Enter 抬起且未按住 Shift 时发送 composer。
 */
internal fun shouldSubmitComposerKey(
    key: Key,
    eventType: KeyEventType,
    isShiftPressed: Boolean,
): Boolean = key == Key.Enter && eventType == KeyEventType.KeyUp && !isShiftPressed

/**
 * 选择权限文案。
 */
private fun permissionLabel(permissionPreset: PermissionPreset): String = when (permissionPreset) {
    PermissionPreset.DEFAULT -> "Ask permission"
    PermissionPreset.AUTO -> "Auto"
    PermissionPreset.EDIT_ALLOW -> "Edit allow"
    PermissionPreset.PLAN -> "Plan"
    PermissionPreset.BRAVE -> "Brave"
}

/**
 * 权限色调。
 */
private fun permissionTone(permissionPreset: PermissionPreset): Color = when (permissionPreset) {
    PermissionPreset.DEFAULT -> AppChipBackground
    PermissionPreset.AUTO -> Color(0xFF204B8F)
    PermissionPreset.EDIT_ALLOW -> Color(0xFF66511C)
    PermissionPreset.PLAN -> Color(0xFF434750)
    PermissionPreset.BRAVE -> Color(0xFF652E36)
}

/**
 * 打开文件选择器。
 */
private fun pickFiles(): List<String> {
    val chooser = JFileChooser(FileSystemView.getFileSystemView().defaultDirectory).apply {
        isMultiSelectionEnabled = true
        fileSelectionMode = JFileChooser.FILES_ONLY
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.map(File::getAbsolutePath)
    } else {
        emptyList()
    }
}

/**
 * 打开工作目录选择器。
 */
private fun pickWorkspaceDirectory(): String? {
    val chooser = JFileChooser(FileSystemView.getFileSystemView().defaultDirectory).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

/**
 * 构造终端视图条目。
 */
private fun buildTerminalEntries(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
): List<String> {
    val entries = conversation.items.mapNotNull { item ->
        when (item) {
            is ChatMessageItem -> if (filterToolActivityOnly) {
                null
            } else {
                val prefix = if (item.message.role == ChatRole.User) "$" else "assistant>"
                "$prefix ${item.message.content.trim()}"
            }

            is ReasoningItem -> if (filterToolActivityOnly) null else "thinking> ${item.displayText.trim()}"
            is ToolEventItem -> "tool> ${item.toolName}${
                item.preview?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""
            }"
        }
    }
    return entries.ifEmpty { listOf(if (filterToolActivityOnly) "No tool activity yet." else "No timeline events yet.") }
}

/**
 * 构造历史视图条目。
 */
private fun buildHistoryEntries(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
): List<String> {
    val entries = conversation.history.flatMap { message ->
        when (message) {
            is com.agent.shared.agent.api.AgentConversationHistoryMessage.User -> if (filterToolActivityOnly) {
                emptyList()
            } else {
                listOf("user> ${message.content}")
            }

            is com.agent.shared.agent.api.AgentConversationHistoryMessage.Assistant -> {
                message.parts.mapNotNull { part ->
                    when (part) {
                        is com.agent.shared.agent.api.AgentConversationHistoryPart.Text -> if (filterToolActivityOnly) null else "assistant> ${part.text}"
                        is com.agent.shared.agent.api.AgentConversationHistoryPart.Reasoning -> if (filterToolActivityOnly) null else "reasoning> ${part.summary ?: part.rawText.orEmpty()}"
                        is com.agent.shared.agent.api.AgentConversationHistoryPart.ToolCall -> "tool-call> ${part.name}${part.argumentsPreview?.let { ": $it" } ?: ""}"
                        is com.agent.shared.agent.api.AgentConversationHistoryPart.ToolResult -> "tool-result> ${part.name}${part.resultPreview?.let { ": $it" } ?: ""}"
                    }
                }
            }
        }
    }
    return entries.ifEmpty { listOf(if (filterToolActivityOnly) "No tool history yet." else "No structured history yet.") }
}

/**
 * 提取最后一条助手正文，用于复制动作。
 */
private fun latestAssistantAnswerText(conversation: ChatConversationUiState): String? =
    conversation.items
        .asReversed()
        .filterIsInstance<ChatMessageItem>()
        .firstOrNull { it.message.role == ChatRole.Assistant }
        ?.message
        ?.content
        ?.trim()
        ?.takeIf(String::isNotBlank)

/**
 * 把文本写入系统剪贴板。
 */
private fun copyTextToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/**
 * 导出当前会话为 markdown。
 */
private fun exportConversationMarkdown(conversation: ChatConversationUiState): String? = runCatching {
    val chooser = JFileChooser(FileSystemView.getFileSystemView().defaultDirectory).apply {
        dialogTitle = "Save transcript"
        selectedFile = File("${sanitizeFileName(conversation.title)}.md")
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
        return null
    }
    val target = chooser.selectedFile.let { file ->
        if (file.extension.equals("md", ignoreCase = true)) file else File(file.parentFile, "${file.name}.md")
    }
    writeConversationMarkdown(target, buildConversationMarkdown(conversation))
    target.absolutePath
}.getOrNull()

