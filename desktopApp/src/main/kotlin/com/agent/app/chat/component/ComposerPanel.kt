@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.agent.app.chat.presentation.buildComposerPrimaryActionVisual
import com.agent.app.chat.presentation.buildContextTooltip
import com.agent.app.chat.presentation.contextRingSweepAngle
import com.agent.app.chat.presentation.groupProfilesByProvider
import com.agent.app.chat.presentation.modelVariantsFor
import com.agent.app.chat.presentation.reasoningControlLabel
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.isStoppable
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.ComposerBackground
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.RingContextIndicator
import com.agent.app.design.RingDropdownMenuItem
import com.agent.app.design.RingInputField
import com.agent.app.design.RingIsland
import com.agent.app.design.RingPrimaryButton
import com.agent.app.design.RingPermissionDropdownMenuItem
import com.agent.app.design.RingSelectChip
import com.agent.app.design.RingTooltip
import com.agent.app.platform.pickFiles
import com.agent.app.tool.component.QuestionCard
import com.agent.app.tool.component.ApprovalCard
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
import kotlinx.coroutines.launch

internal const val PENDING_CARD_ENTER_DURATION_MILLIS = 180
internal const val PENDING_CARD_EXIT_DURATION_MILLIS = 120

/**
 * Composer 底部可互斥展开的菜单。
 */
internal enum class ComposerMenu {
    PROVIDER,
    MODEL,
    REASONING,
    PERMISSION,
}

/**
 * 点击另一触发器时直接切换菜单，重复点击当前触发器时关闭。
 */
internal fun nextComposerMenu(
    current: ComposerMenu?,
    requested: ComposerMenu,
): ComposerMenu? = requested.takeUnless { it == current }

/**
 * 旧 popup 的延迟关闭回调只能关闭自己，不能覆盖刚切换的新菜单。
 */
internal fun dismissComposerMenu(
    current: ComposerMenu?,
    dismissed: ComposerMenu,
): ComposerMenu? = current.takeUnless { it == dismissed }

/**
 * 将 Composer 主动作状态映射为矢量图标。
 */
internal fun composerPrimaryActionGlyph(danger: Boolean): HeaderGlyph =
    if (danger) HeaderGlyph.STOP else HeaderGlyph.SEND

/**
 * 输入框最多占用主工作区的一半，确保时间线始终保留足够的可见空间。
 */
internal fun maxComposerInputHeight(workspaceHeight: Dp): Dp = workspaceHeight / 2

/**
 * 仅在输入内容超过可见区域时显示输入区滚动条。
 */
internal fun shouldShowComposerInputScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0

/**
 * 拖选文本接近输入框边缘时返回应执行的滚动增量，中央区域保持静止。
 */
internal fun composerSelectionScrollDelta(pointerY: Float, viewportHeight: Int): Float = when {
    pointerY < 18f -> -24f
    pointerY > viewportHeight - 18f -> 24f
    else -> 0f
}

/**
 * Shift 加方向键扩展选择范围时，让外层输入区域跟随选区继续滚动。
 */
internal fun composerKeyboardSelectionScrollDelta(
    key: Key,
    isShiftPressed: Boolean,
): Float = if (!isShiftPressed) {
    0f
} else {
    when (key) {
        Key.DirectionUp -> -28f
        Key.DirectionDown -> 28f
        else -> 0f
    }
}

/**
 * 原型下方 plan + composer 区域。
 */
@Composable
internal fun FooterComposerSection(
    state: ChatWindowState,
    compact: Boolean,
    onSendDraft: () -> Unit,
    composerInputMaxHeight: Dp = 320.dp,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val planCard = activeConversation?.let { extractPlanCard(it.items) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (compact) 12.dp else 32.dp,
                top = 0.dp,
                end = if (compact) 12.dp else 32.dp,
                bottom = if (compact) 12.dp else 20.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (planCard != null) {
                PlanCard(
                    title = planCard.title,
                    entries = planCard.entries,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            activeConversation
                ?.takeIf {
                    shouldShowPendingInteractionCard(
                        hasPendingQuestion = it.pendingQuestion != null,
                        hasPendingApproval = it.pendingApproval != null,
                    )
                }
                ?.let { conversation ->
                    PendingInteractionCards(
                        conversation = conversation,
                        state = state,
                    )
                }
            ComposerPanel(
                state = state,
                compact = compact,
                onSendDraft = onSendDraft,
                composerInputMaxHeight = composerInputMaxHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 在 composer 上方叠加展示挂起的问题或审批卡片。
 */
@Composable
internal fun PendingInteractionCards(
    conversation: ChatConversationUiState,
    state: ChatWindowState,
) {
    val pendingQuestion = conversation.pendingQuestion
    val pendingApproval = conversation.pendingApproval
    AnimatedVisibility(
        visible = pendingQuestion != null || pendingApproval != null,
        enter = fadeIn(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) +
                slideInVertically(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) { height -> height / 8 },
        exit = fadeOut(tween(PENDING_CARD_EXIT_DURATION_MILLIS)) +
                slideOutVertically(tween(PENDING_CARD_EXIT_DURATION_MILLIS)) { height -> -height / 12 },
    ) {
        when {
            pendingQuestion != null -> QuestionCard(
                pending = pendingQuestion,
                onOptionClick = state::answerPendingQuestion,
                onSubmitText = state::answerPendingQuestion,
            )

            pendingApproval != null -> ApprovalCard(
                pending = pendingApproval,
                onResponse = state::answerPendingApproval,
            )
        }
    }
}

/**
 * 原型 composer。
 */
@Composable
private fun ComposerPanel(
    state: ChatWindowState,
    compact: Boolean,
    onSendDraft: () -> Unit,
    composerInputMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val profiles = state.availableProfiles
    val selectedProfile = state.activeProfile
    val executionState = activeConversation?.executionState ?: ExecutionState.Idle
    val permissionPreset = activeConversation?.permissionPreset ?: PermissionPreset.DEFAULT
    val primaryActionVisual = buildComposerPrimaryActionVisual(executionState)
    val providerProfiles = groupProfilesByProvider(profiles)
    val currentProvider = selectedProfile?.providerId ?: profiles.firstOrNull()?.providerId
    val currentProviderProfiles = providerProfiles[currentProvider].orEmpty()
    val selectedVariants = selectedProfile?.let(::modelVariantsFor).orEmpty()
    var expandedMenu by remember { mutableStateOf<ComposerMenu?>(null) }
    val inputScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var inputViewportHeight by remember { mutableStateOf(0) }

    LaunchedEffect(state.ui.draft) {
        inputScrollState.scrollTo(inputScrollState.maxValue)
    }

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
                        RingTooltip(attachment.path) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = AppChipBackground,
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = attachment.name,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                                            color = AppText
                                        ),
                                    )
                                    RingPrimaryButton(
                                        text = "×",
                                        onClick = { state.removeAttachment(attachment.path) },
                                        modifier = Modifier.size(26.dp),
                                        containerColor = AppLine,
                                        compact = true,
                                        tooltip = "移除 ${attachment.name}",
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> inputViewportHeight = size.height }
                    .onPointerEvent(
                        eventType = PointerEventType.Move,
                        pass = PointerEventPass.Final,
                    ) { event ->
                        val pointerY = event.changes.firstOrNull()?.position?.y ?: return@onPointerEvent
                        val scrollDelta = composerSelectionScrollDelta(pointerY, inputViewportHeight)
                        if (event.buttons.isPrimaryPressed && scrollDelta != 0f) {
                            scope.launch {
                                inputScrollState.scrollTo((inputScrollState.value + scrollDelta).toInt())
                            }
                        }
                    }
                    .heightIn(max = composerInputMaxHeight),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(inputScrollState),
                ) {
                    RingInputField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                val selectionScrollDelta = composerKeyboardSelectionScrollDelta(
                                    key = event.key,
                                    isShiftPressed = event.isShiftPressed,
                                )
                                if (event.type == KeyEventType.KeyDown && selectionScrollDelta != 0f) {
                                    scope.launch {
                                        inputScrollState.scrollTo(
                                            (inputScrollState.value + selectionScrollDelta).toInt(),
                                        )
                                    }
                                }
                                if (shouldSubmitComposerKey(event.key, event.type, event.isShiftPressed)) {
                                    if (executionState.isStoppable()) {
                                        state.cancelActiveRun()
                                    } else {
                                        onSendDraft()
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        value = state.ui.draft,
                        onValueChange = state::updateDraft,
                        minLines = 3,
                        placeholder = "描述你想完成的任务…",
                        borderless = true,
                    )
                }
                if (shouldShowComposerInputScrollbar(inputScrollState.maxValue)) {
                    CompositionLocalProvider(
                        LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                            unhoverColor = Color(0xFF8D96A6),
                            hoverColor = Color(0xFFD7DEEA),
                        ),
                    ) {
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(inputScrollState),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp, horizontal = 3.dp),
                        )
                    }
                }
            }

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
                        tooltip = "添加附件",
                    )
                    RingSelectChip(
                        label = selectedProfile?.providerLabel ?: currentProvider ?: "服务商",
                        expanded = expandedMenu == ComposerMenu.PROVIDER,
                        onExpandedChange = { shouldExpand ->
                            expandedMenu = ComposerMenu.PROVIDER.takeIf { shouldExpand }
                        },
                        onDismissRequest = {
                            expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.PROVIDER)
                        },
                        modifier = Modifier.width(if (compact) 96.dp else 120.dp),
                        tooltip = "选择服务商",
                    ) {
                        providerProfiles.entries.forEachIndexed { index, (_, providerModels) ->
                            val first = providerModels.firstOrNull() ?: return@forEachIndexed
                            RingDropdownMenuItem(
                                text = first.providerLabel,
                                selected = first.providerId == currentProvider,
                                itemIndex = index,
                                itemCount = providerProfiles.size,
                                onClick = {
                                    expandedMenu = null
                                    state.selectProfile(first.id)
                                },
                            )
                        }
                    }
                    RingSelectChip(
                        label = selectedProfile?.modelLabel ?: selectedProfile?.model ?: "模型",
                        expanded = expandedMenu == ComposerMenu.MODEL,
                        onExpandedChange = { shouldExpand ->
                            expandedMenu = ComposerMenu.MODEL.takeIf { shouldExpand }
                        },
                        onDismissRequest = {
                            expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.MODEL)
                        },
                        modifier = Modifier.width(if (compact) 124.dp else 152.dp),
                        tooltip = "选择模型",
                    ) {
                        currentProviderProfiles.forEachIndexed { index, profile ->
                            RingDropdownMenuItem(
                                text = profile.modelLabel ?: profile.model,
                                selected = profile.id == selectedProfile?.id,
                                itemIndex = index,
                                itemCount = currentProviderProfiles.size,
                                onClick = {
                                    expandedMenu = null
                                    state.selectProfile(profile.id)
                                },
                            )
                        }
                    }
                    if (selectedVariants.isNotEmpty()) {
                        RingSelectChip(
                            label = reasoningControlLabel(activeConversation?.reasoningEffort),
                            expanded = expandedMenu == ComposerMenu.REASONING,
                            onExpandedChange = { shouldExpand ->
                                expandedMenu = ComposerMenu.REASONING.takeIf { shouldExpand }
                            },
                            onDismissRequest = {
                                expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.REASONING)
                            },
                            modifier = Modifier.width(if (compact) 104.dp else 120.dp),
                            tooltip = "选择推理强度",
                        ) {
                        selectedVariants.forEachIndexed { index, variant ->
                            val effort = variant.reasoningEffort ?: return@forEachIndexed
                            RingDropdownMenuItem(
                                text = reasoningControlLabel(effort),
                                selected = effort == activeConversation?.reasoningEffort,
                                itemIndex = index,
                                itemCount = selectedVariants.size,
                                onClick = {
                                        expandedMenu = null
                                        state.updateReasoningEffort(effort)
                                    },
                                )
                            }
                        }
                    }
                    RingContextIndicator(
                        sweepAngle = contextRingSweepAngle(activeConversation?.contextUsageFraction ?: 0f),
                        tooltip = buildContextTooltip(activeConversation?.contextUsageFraction ?: 0f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RingSelectChip(
                        label = permissionLabel(permissionPreset),
                        expanded = expandedMenu == ComposerMenu.PERMISSION,
                        tone = permissionTone(permissionPreset),
                        onExpandedChange = { shouldExpand ->
                            expandedMenu = ComposerMenu.PERMISSION.takeIf { shouldExpand }
                        },
                        onDismissRequest = {
                            expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.PERMISSION)
                        },
                        modifier = Modifier.width(126.dp),
                        tooltip = "选择执行权限",
                    ) {
                        Text(
                            text = "权限模式",
                            modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 6.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(color = AppMuted),
                        )
                        PermissionPreset.entries.forEachIndexed { index, preset ->
                    RingPermissionDropdownMenuItem(
                        description = permissionDescription(preset),
                                badge = permissionBadge(preset),
                                badgeColor = permissionBadgeColor(preset),
                                selected = preset == permissionPreset,
                                itemIndex = index,
                                itemCount = PermissionPreset.entries.size,
                                onClick = {
                                    expandedMenu = null
                                    state.updatePermission(preset)
                                },
                            )
                        }
                    }
                    RingPrimaryButton(
                        text = primaryActionVisual.symbol,
                        onClick = {
                            if (executionState.isStoppable()) {
                                state.cancelActiveRun()
                            } else {
                                onSendDraft()
                            }
                        },
                        containerColor = if (primaryActionVisual.danger) AppDanger else AppAccent,
                        modifier = Modifier.size(40.dp),
                        compact = true,
                        iconGlyph = composerPrimaryActionGlyph(primaryActionVisual.danger),
                        tooltip = if (primaryActionVisual.danger) "停止当前任务" else "发送消息",
                    )
                }
            }
        }
    }
}

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
    PermissionPreset.DEFAULT -> "操作前询问"
    PermissionPreset.AUTO -> "自动"
    PermissionPreset.EDIT_ALLOW -> "允许编辑"
    PermissionPreset.PLAN -> "仅规划"
    PermissionPreset.BRAVE -> "全部允许"
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

/** 权限模式在菜单内的简短说明。 */
private fun permissionDescription(permissionPreset: PermissionPreset): String = when (permissionPreset) {
    PermissionPreset.DEFAULT -> "首次使用每种工具时请求确认"
    PermissionPreset.AUTO -> "自动执行安全的只读操作"
    PermissionPreset.EDIT_ALLOW -> "自动接受文件编辑权限"
    PermissionPreset.PLAN -> "修改前先完成计划"
    PermissionPreset.BRAVE -> "跳过所有权限确认"
}

/** 权限模式的视觉标签。 */
private fun permissionBadge(permissionPreset: PermissionPreset): String = when (permissionPreset) {
    PermissionPreset.DEFAULT -> "询问"
    PermissionPreset.AUTO -> "自动"
    PermissionPreset.EDIT_ALLOW -> "允许编辑"
    PermissionPreset.PLAN -> "计划"
    PermissionPreset.BRAVE -> "完全访问"
}

/** 权限模式的风险级别色。 */
private fun permissionBadgeColor(permissionPreset: PermissionPreset): Color = when (permissionPreset) {
    PermissionPreset.DEFAULT -> Color(0xFF5A5C60)
    PermissionPreset.AUTO -> Color(0xFF245286)
    PermissionPreset.EDIT_ALLOW -> Color(0xFF55479A)
    PermissionPreset.PLAN -> Color(0xFF76561B)
    PermissionPreset.BRAVE -> Color(0xFF8E3541)
}
