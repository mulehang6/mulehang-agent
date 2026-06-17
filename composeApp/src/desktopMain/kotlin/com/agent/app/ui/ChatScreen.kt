@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.graphics.drawscope.Stroke
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelCapabilitiesResolver
import com.agent.shared.config.ModelVariant
import com.agent.shared.config.ProviderType
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.PermissionPreset
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus
import java.awt.FileDialog
import java.awt.Frame
import java.util.Locale

/**
 * codex-like 聊天主界面。
 */
@Composable
fun ChatScreen(
    state: ChatWindowState,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF7F2E8), Color(0xFFEFE7D7)),
                ),
            ),
    ) {
        WorkspaceSidebar(
            state = state,
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight(),
        )
        ChatWorkspacePanel(
            state = state,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 工作目录分组侧栏。
 */
@Composable
private fun WorkspaceSidebar(
    state: ChatWindowState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEBE3D3), Color(0xFFE4DBC9)),
                ),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF2A8F64), Color(0xFF95C76E)),
                        ),
                        shape = RoundedCornerShape(9.dp),
                    ),
            )
            Text(
                text = "Mulehang",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewConversationButton(
                onClick = { state.createConversationForWorkspace(state.ui.activeConversation.workspacePath) },
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x9EFFF9F0),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppLineSoft),
            ) {
                Text(
                    text = "⋯",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = AppMuted,
                )
            }
        }
        Text(
            text = "BY WORKSPACE",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF7C7265),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            ),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.ui.workspaceGroups) { group ->
                WorkspaceGroupCard(
                    group = group,
                    activeConversationId = state.ui.activeConversationId,
                    onConversationSelected = state::selectConversation,
                    onCreateConversation = state::createConversationForWorkspace,
                )
            }
        }
    }
}

/**
 * 单个工作目录分组卡片。
 */
@Composable
private fun WorkspaceGroupCard(
    group: WorkspaceConversationGroupUiState,
    activeConversationId: String,
    onConversationSelected: (String) -> Unit,
    onCreateConversation: (String) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .background(
                color = if (hovered) Color(0xB0FFFCF5) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = if (hovered) 1.dp else 0.dp,
                color = AppLineSoft,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppText,
                    ),
                )
                Text(
                    text = group.workspacePath,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF887D6F),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hovered) {
                Surface(
                    onClick = { onCreateConversation(group.workspacePath) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1423211D),
                ) {
                    Text(
                        text = "+",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = AppText,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        group.conversations.forEach { conversation ->
            Surface(
                onClick = { onConversationSelected(conversation.id) },
                shape = RoundedCornerShape(12.dp),
                color = if (conversation.id == activeConversationId) {
                    Color(0xDBFFFDF5)
                } else {
                    Color.Transparent
                },
                border = if (conversation.id == activeConversationId) {
                    androidx.compose.foundation.BorderStroke(1.dp, AppLineSoft)
                } else {
                    null
                },
            ) {
                Text(
                    text = conversation.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodySmall.copy(color = AppText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 主聊天工作区。
 */
@Composable
private fun ChatWorkspacePanel(
    state: ChatWindowState,
    modifier: Modifier = Modifier,
) {
    val activeConversation = state.ui.activeConversation
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xE0FCF9F4)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (activeConversation.items.isEmpty()) "Conversation / Empty" else "Conversation / Active",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AppMuted,
                    letterSpacing = 1.1.sp,
                ),
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xB3FFFFFF),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppLineSoft),
            ) {
                Text(
                    text = "Workspace grouped by pwd",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF50483F)),
                )
            }
        }
        state.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9E4D30)),
            )
        }
        activeConversation.pendingQuestion?.let { pending ->
            QuestionCard(
                pending = pending,
                onOptionClick = state::answerPendingQuestion,
                onSubmitText = state::answerPendingQuestion,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (activeConversation.items.isEmpty()) {
                LandingState(state.ui.activeWorkspaceLabel)
            } else {
                ConversationTimeline(activeConversation)
            }
        }
        activeConversation.pendingApproval?.let { pending ->
            Spacer(modifier = Modifier.height(12.dp))
            ApprovalCard(
                pending = pending,
                onApprove = { state.answerPendingApproval(true) },
                onReject = { state.answerPendingApproval(false) },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        ComposerPanel(state = state)
    }
}

/**
 * 空态主区。
 */
@Composable
private fun LandingState(workspaceLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 34.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "围绕 $workspaceLabel 开始一段新对话。",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = AppText,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.2).sp,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "保留 codex 风格的大留白，但起手就告诉用户可以围绕当前工作目录做代码审查、解释文件、修改代码和规划任务。",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = AppMuted,
                lineHeight = 26.sp,
            ),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AbilityCard(title = "代码审查", description = "针对当前工作目录的改动做 review，优先找 bug 风险和漏测点。")
            AbilityCard(title = "解释文件", description = "快速讲清某个目录、模块或函数在这个仓库里的职责。")
            AbilityCard(title = "修改代码", description = "结合权限档位直接编辑工作区文件，不需要离开当前会话。")
            AbilityCard(title = "规划任务", description = "先给计划，再进入实现，避免复杂需求直接把代码写乱。")
        }
    }
}

/**
 * 能力入口卡片。
 */
@Composable
private fun AbilityCard(
    title: String,
    description: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xC7FFFCF6),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLineSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppMuted,
                    lineHeight = 22.sp,
                ),
            )
        }
    }
}

/**
 * 对话态时间线。
 */
@Composable
private fun ConversationTimeline(
    conversation: ChatConversationUiState,
) {
    val listState = rememberLazyListState()
    var followLatest by remember(conversation.id) { mutableStateOf(true) }
    var observedItemCount by remember(conversation.id) { mutableStateOf(conversation.items.size) }
    val nextFollowLatest = nextAutoScrollFollowState(
        currentFollowLatest = followLatest,
        lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
        totalItems = conversation.items.size,
        previousTotalItems = observedItemCount,
    )
    LaunchedEffect(conversation.id, conversation.items.size, nextFollowLatest) {
        if (conversation.items.isNotEmpty() && nextFollowLatest) {
            listState.scrollToItem(timelineAutoScrollAnchorIndex(conversation.items.size))
        }
    }
    SideEffect {
        followLatest = nextFollowLatest
        observedItemCount = conversation.items.size
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 34.dp, vertical = 18.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(conversation.items) { item ->
            when (item) {
                is ChatMessageItem -> ChatMessageBlock(item)
                is ReasoningItem -> ReasoningBlock(item)
                is ToolEventItem -> ToolEventBlock(item)
            }
        }
        item(key = "timeline-anchor-${conversation.id}") {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

/**
 * composer 面板。
 */
@Composable
private fun ComposerPanel(state: ChatWindowState) {
    val activeConversation = state.ui.activeConversation
    val profiles = state.availableProfiles
    val selectedProfile = state.activeProfile
    val primaryActionVisual = buildComposerPrimaryActionVisual(activeConversation.executionState)
    val providerProfiles = groupProfilesByProvider(profiles)
    val currentProvider = selectedProfile?.providerId ?: profiles.firstOrNull()?.providerId
    val currentProviderProfiles = providerProfiles[currentProvider].orEmpty()
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var reasoningExpanded by remember { mutableStateOf(false) }
    var permissionExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (activeConversation.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                activeConversation.attachments.forEach { attachment ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF3EDDF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppLineSoft),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(AppAccent, CircleShape),
                            )
                            Text(
                                text = attachment.name,
                                style = MaterialTheme.typography.bodySmall.copy(color = AppText),
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF7FFFDF7),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2A3D3528)),
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (shouldSubmitComposerKey(event.key, event.type, event.isShiftPressed)) {
                                state.sendDraft()
                                true
                            } else {
                                false
                            }
                        },
                    value = state.ui.draft,
                    onValueChange = state::updateDraft,
                    minLines = 3,
                    placeholder = {
                        Text("Describe the task, paste code, or ask the agent to inspect the current workspace.")
                    },
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconPillButton(
                            symbol = "+",
                            onClick = { state.attachFiles(pickFiles()) },
                        )
                        DividerMark()

                        SelectorChip(
                            label = "Provider",
                            value = selectedProfile?.providerLabel ?: currentProvider ?: "None",
                            expanded = providerExpanded,
                            onExpandedChange = { providerExpanded = !providerExpanded },
                        ) {
                            providerProfiles.forEach { (providerId, providerModels) ->
                                DropdownMenuItem(
                                    text = { Text(providerModels.firstOrNull()?.providerLabel ?: providerId) },
                                    onClick = {
                                        providerExpanded = false
                                        providerModels
                                            .firstOrNull()
                                            ?.let { profile -> state.selectProfile(profile.id) }
                                    },
                                )
                            }
                        }

                        SelectorChip(
                            label = "Model",
                            value = selectedProfile?.modelLabel ?: selectedProfile?.model ?: "None",
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = !modelExpanded },
                        ) {
                            currentProviderProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(profile.modelLabel ?: profile.model)
                                            Text(
                                                text = profile.model,
                                                style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                                            )
                                        }
                                    },
                                    onClick = {
                                        modelExpanded = false
                                        state.selectProfile(profile.id)
                                    },
                                )
                            }
                        }

                        val selectedVariants = selectedProfile?.let(::modelVariantsFor).orEmpty()
                        if (selectedVariants.isNotEmpty()) {
                            val selectedReasoningValue = selectedVariants
                                .firstOrNull { it.reasoningEffort == activeConversation.reasoningEffort }
                                ?.id
                                ?: selectedVariants.first().id
                            SelectorChip(
                                label = "Thinking",
                                value = selectedReasoningValue,
                                expanded = reasoningExpanded,
                                onExpandedChange = { reasoningExpanded = !reasoningExpanded },
                            ) {
                                selectedVariants.forEach { variant ->
                                    val effort = variant.reasoningEffort ?: return@forEach
                                    DropdownMenuItem(
                                        text = { Text(variant.id) },
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ContextRingChip(activeConversation.contextUsageFraction)
                        SelectorChip(
                            label = "Permission",
                            value = permissionLabel(state.ui.permissionPreset),
                            expanded = permissionExpanded,
                            onExpandedChange = { permissionExpanded = !permissionExpanded },
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
                        DividerMark()
                        IconPillButton(
                            symbol = primaryActionVisual.symbol,
                            danger = primaryActionVisual.danger,
                            onClick = if (activeConversation.executionState == ExecutionState.Running) {
                                state::cancelActiveRun
                            } else {
                                state::sendDraft
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 新建对话按钮。
 */
@Composable
private fun NewConversationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppDark,
            contentColor = Color(0xFFFFF9EF),
        ),
    ) {
        Text(text = "+ New conversation")
    }
}

/**
 * 图标胶囊按钮。
 */
@Composable
private fun IconPillButton(
    symbol: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) ComposerDanger else AppDark,
            contentColor = Color(0xFFFFF8EF),
        ),
        modifier = Modifier.size(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(text = symbol, fontWeight = FontWeight.Bold)
    }
}

/**
 * 底栏分隔符。
 */
@Composable
private fun DividerMark() {
    Text(
        text = "|",
        color = Color(0xFF9A8D7D),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
    )
}

/**
 * 通用下拉选择胶囊。
 */
@Composable
private fun SelectorChip(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Box {
        Surface(
            onClick = onExpandedChange,
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFEFE7D7),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLineSoft),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF7A6F62),
                        letterSpacing = 0.8.sp,
                    ),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppText),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onExpandedChange,
        ) {
            menuContent()
        }
    }
}

/**
 * 上下文占用圆环胶囊。
 */
@Composable
private fun ContextRingChip(
    usageFraction: Float,
) {
    var hovered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFF4EDDF),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLineSoft),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val strokeWidth = size.minDimension * 0.22f
                    val inset = strokeWidth / 2f
                    val sweepAngle = contextRingSweepAngle(usageFraction)
                    drawArc(
                        color = Color(0xFFD8D0C2),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width - strokeWidth,
                            height = size.height - strokeWidth,
                        ),
                        style = Stroke(width = strokeWidth),
                    )
                    if (sweepAngle > 0f) {
                        drawArc(
                            color = AppAccent,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                width = size.width - strokeWidth,
                                height = size.height - strokeWidth,
                            ),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                }
                Text(
                    text = buildContextUsageLabel(usageFraction),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
        if (hovered) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = with(density) { IntOffset(0, -42.dp.roundToPx()) },
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2C271F),
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = buildContextTooltip(usageFraction),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFFF7EB)),
                    )
                }
            }
        }
    }
}

/**
 * 按消息角色渲染聊天正文。
 */
@Composable
private fun ChatMessageBlock(item: ChatMessageItem) {
    when (item.message.role) {
        ChatRole.System -> Text(
            text = buildChatMessageText(item),
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF6B7480),
                lineHeight = 20.sp,
            ),
        )

        ChatRole.User -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BubbleBlock(
                text = buildChatMessageText(item),
                containerColor = Color(0xFF2B2620),
                contentColor = Color(0xFFFFF9EF),
            )
        }

        ChatRole.Assistant -> BubbleBlock(
            text = buildChatMessageText(item),
            containerColor = Color(0xDDF4EFE7),
            contentColor = AppText,
            borderColor = AppLineSoft,
        )
    }
}

/**
 * 展示默认展开的思考块。
 */
@Composable
private fun ReasoningBlock(item: ReasoningItem) {
    BubbleBlock(
        containerColor = Color(0xFFF2F4F7),
        contentColor = Color(0xFF273142),
        borderColor = Color(0xFFD4DAE3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildReasoningHeadline(item),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF526173),
                ),
            )
            if (item.expanded) {
                Text(
                    text = item.displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFF2A3342),
                    ),
                )
            }
        }
    }
}

/**
 * 展示工具调用等轻量时间线事件。
 */
@Composable
private fun ToolEventBlock(item: ToolEventItem) {
    BubbleBlock(
        text = buildToolEventLabel(item),
        containerColor = Color(0xF8F8F5EE),
        contentColor = AppMuted,
        borderColor = AppLineSoft,
    )
}

/**
 * 渲染整行圆角消息块。
 */
@Composable
private fun BubbleBlock(
    text: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
) {
    BubbleBlock(
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 14.sp,
                lineHeight = 24.sp,
                color = contentColor,
            ),
        )
    }
}

/**
 * 渲染支持自定义内容的整行圆角消息块。
 */
@Composable
private fun BubbleBlock(
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val borderModifier = if (borderColor != null) {
        Modifier.border(width = 1.dp, color = borderColor, shape = shape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(color = containerColor, shape = shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        ProvideTextStyle(
            value = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
            content = content,
        )
    }
}

/**
 * 将工具事件格式化为时间线可读文本。
 */
private fun buildToolEventLabel(item: ToolEventItem): String {
    val prefix = when (item.status) {
        ToolEventStatus.Started -> "Tool: 正在调用 ${item.toolName}"
        ToolEventStatus.Finished -> "Tool: ${item.toolName} 已返回"
        ToolEventStatus.Status -> "Status: ${item.preview.orEmpty()}"
    }
    return item.preview
        ?.takeIf { it.isNotBlank() && item.status != ToolEventStatus.Status }
        ?.let { "$prefix ($it)" }
        ?: prefix
}

/**
 * 聊天消息正文直接显示内容，不再拼接角色前缀。
 */
internal fun buildChatMessageText(item: ChatMessageItem): String = item.message.content

/**
 * 思考块标题固定保留 Thinking 文案。
 */
internal fun buildReasoningHeadline(item: ReasoningItem): String =
    if (item.isStreaming) "Thinking: 思考中..." else "Thinking:"

/**
 * 将工作目录映射为侧栏分组标题。
 */
internal fun buildWorkspaceLabel(path: String): String =
    path.trimEnd('\\', '/').substringAfterLast('\\').substringAfterLast('/')

/**
 * provider 类型的展示文案。
 */
internal fun providerLabel(providerType: ProviderType): String = when (providerType) {
    ProviderType.OPENAI_RESPONSES -> "OpenAI"
    ProviderType.OPENAI_CHAT_COMPLETIONS -> "OpenAI Compatible"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.GOOGLE -> "Google"
}

/**
 * 返回 profile 支持的模型变体。
 */
internal fun modelVariantsFor(profile: ConfigProfile): List<ModelVariant> =
    ModelCapabilitiesResolver.resolve(profile).variants.values.toList()

/**
 * 按配置 providerId 分组模型，避免同类 OpenAI-compatible provider 被混在一起。
 */
internal fun groupProfilesByProvider(profiles: List<ConfigProfile>): Map<String, List<ConfigProfile>> =
    profiles.groupBy { it.providerId }

/**
 * 生成上下文圆环 hover 文案。
 */
internal fun buildContextTooltip(usageFraction: Float): String =
    "${formatContextUsagePercent(usageFraction)} used"

/**
 * 生成上下文圆环旁的可见百分比文案。
 */
internal fun buildContextUsageLabel(usageFraction: Float): String =
    formatContextUsagePercent(usageFraction)

/**
 * composer 主按钮的展示状态。
 */
internal data class ComposerPrimaryActionVisual(
    val symbol: String,
    val danger: Boolean,
)

/**
 * 根据执行状态生成 composer 主按钮视觉。
 */
internal fun buildComposerPrimaryActionVisual(executionState: ExecutionState): ComposerPrimaryActionVisual =
    if (executionState == ExecutionState.Running) {
        ComposerPrimaryActionVisual(symbol = "■", danger = true)
    } else {
        ComposerPrimaryActionVisual(symbol = "↑", danger = false)
    }

/**
 * 将上下文占比换算为圆环 sweep angle，并为非零占用保留最小可见弧度。
 */
internal fun contextRingSweepAngle(usageFraction: Float): Float {
    val clampedFraction = usageFraction.coerceIn(0f, 1f)
    if (clampedFraction <= 0f) return 0f
    if (clampedFraction >= 1f) return 360f
    return (clampedFraction * 360f).coerceAtLeast(MIN_VISIBLE_CONTEXT_SWEEP_ANGLE)
}

/**
 * 仅当用户仍停在底部附近时，才让时间线自动跟随最新内容。
 */
internal fun shouldAutoScrollToLatest(
    lastVisibleIndex: Int?,
    totalItems: Int,
    trailingThreshold: Int = TIMELINE_AUTO_SCROLL_THRESHOLD_ITEMS,
): Boolean {
    if (lastVisibleIndex == null) {
        return true
    }
    val threshold = trailingThreshold.coerceAtLeast(0)
    return lastVisibleIndex >= timelineAutoScrollAnchorIndex(totalItems) - threshold
}

/**
 * 根据当前滚动位置和是否刚追加内容，决定是否继续保持“跟随最新内容”。
 */
internal fun nextAutoScrollFollowState(
    currentFollowLatest: Boolean,
    lastVisibleIndex: Int?,
    totalItems: Int,
    previousTotalItems: Int,
    trailingThreshold: Int = TIMELINE_AUTO_SCROLL_THRESHOLD_ITEMS,
): Boolean {
    if (shouldAutoScrollToLatest(lastVisibleIndex, totalItems, trailingThreshold)) {
        return true
    }
    return currentFollowLatest && totalItems > previousTotalItems
}

/**
 * 时间线自动滚动时使用的尾部锚点索引。
 */
internal fun timelineAutoScrollAnchorIndex(totalItems: Int): Int = totalItems.coerceAtLeast(0)

/**
 * 生成适合上下文圆环显示的小百分比文案。
 */
private fun formatContextUsagePercent(usageFraction: Float): String {
    val percent = usageFraction.coerceIn(0f, 1f) * 100f
    return when {
        percent <= 0f -> "0%"
        percent < 0.1f -> "<0.1%"
        percent < 10f -> String.format(Locale.US, "%.1f%%", percent)
        else -> "${percent.toInt()}%"
    }
}

/**
 * 判断 composer 键盘事件是否应触发发送；Shift+Enter 保留给多行输入。
 */
internal fun shouldSubmitComposerKey(
    key: Key,
    type: KeyEventType,
    isShiftPressed: Boolean,
): Boolean =
    key == Key.Enter && type == KeyEventType.KeyUp && !isShiftPressed

/**
 * 权限档位的展示文案。
 */
private fun permissionLabel(permissionPreset: PermissionPreset): String = when (permissionPreset) {
    PermissionPreset.AUTO -> "auto"
    PermissionPreset.DEFAULT -> "default"
    PermissionPreset.EDIT_ALLOW -> "edit allow"
    PermissionPreset.PLAN -> "plan"
    PermissionPreset.BRAVE -> "brave"
}

private const val TIMELINE_AUTO_SCROLL_THRESHOLD_ITEMS = 1

private const val MIN_VISIBLE_CONTEXT_SWEEP_ANGLE = 6f

private val ComposerDanger = Color(0xFFC94F4F)

/**
 * 选择本地附件文件。
 */
private fun pickFiles(): List<String> {
    val dialog = FileDialog(null as Frame?, "Select attachments", FileDialog.LOAD).apply {
        isMultipleMode = true
        isVisible = true
    }
    return dialog.files?.map { it.absolutePath }.orEmpty()
}

/**
 * 页面主文字色。
 */
private val AppText = Color(0xFF241F18)

/**
 * 页面次级文字色。
 */
private val AppMuted = Color(0xFF6D655B)

/**
 * 页面深色按钮色。
 */
private val AppDark = Color(0xFF23211D)

/**
 * 页面柔和描边色。
 */
private val AppLineSoft = Color(0x2A3D3528)

/**
 * 页面强调色。
 */
private val AppAccent = Color(0xFF1F8A5D)
