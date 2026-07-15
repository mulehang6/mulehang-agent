package com.agent.app.chat.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.buildComposerPrimaryActionVisual
import com.agent.app.chat.presentation.groupProfilesByProvider
import com.agent.app.chat.presentation.modelVariantsFor
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.isStoppable
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppText
import com.agent.app.design.ComposerBackground
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.RingInputField
import com.agent.app.design.RingIsland
import com.agent.app.design.RingPrimaryButton
import com.agent.app.design.RingSelectChip
import com.agent.app.platform.pickFiles
import com.agent.app.tool.component.ApprovalCard
import com.agent.app.tool.component.QuestionCard
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset

/**
 * 原型下方 plan + composer 区域。
 */
@Composable
internal fun FooterComposerSection(state: ChatWindowState) {
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
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(color = AppText),
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
