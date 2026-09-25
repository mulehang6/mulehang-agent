@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.buildComposerPrimaryActionVisual
import com.agent.app.chat.presentation.buildContextTooltip
import com.agent.app.chat.presentation.contextRingSweepAngle
import com.agent.app.chat.presentation.groupProfilesByProvider
import com.agent.app.chat.presentation.modelVariantsFor
import com.agent.app.chat.presentation.reasoningControlLabel
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.isStoppable
import com.agent.app.chat.state.resolveContextWindow
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.iconKey
import com.agent.app.platform.pickFiles
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

/** 渲染输入区的模型、权限选择器与发送操作，并保存菜单交互状态。 */
@Composable
internal fun ComposerActions(state: ChatWindowState, onSendDraft: () -> Unit) {
    val activeConversation = state.ui.activeConversationOrNull
    val profiles = state.availableProfiles
    val selectedProfile = state.activeProfile
    val executionState = activeConversation?.executionState ?: ExecutionState.Idle
    val permissionPreset = activeConversation?.permissionPreset ?: state.ui.permissionPreset
    val iconActionButtonStyle = composerIconActionButtonStyle()
    val primaryActionVisual = buildComposerPrimaryActionVisual(executionState)
    val providerProfiles = groupProfilesByProvider(profiles)
    val currentProvider = selectedProfile?.providerId ?: profiles.firstOrNull()?.providerId
    val currentProviderProfiles = providerProfiles[currentProvider].orEmpty()
    val selectedVariants = selectedProfile?.let(::modelVariantsFor).orEmpty()
    val selectorSlots = buildList {
        add(
            ComposerSelectorSlot(
                menu = ComposerMenu.PROVIDER,
                label = selectedProfile?.providerLabel ?: currentProvider ?: "服务商",
            ),
        )
        add(
            ComposerSelectorSlot(
                menu = ComposerMenu.MODEL,
                label = selectedProfile?.modelLabel ?: selectedProfile?.model ?: "模型",
            ),
        )
        if (selectedVariants.isNotEmpty()) {
            add(
                ComposerSelectorSlot(
                    menu = ComposerMenu.REASONING,
                    label = reasoningControlLabel(activeConversation?.reasoningEffort ?: state.ui.newReasoningEffort),
                ),
            )
        }
        add(
            ComposerSelectorSlot(
                menu = ComposerMenu.PERMISSION,
                label = permissionPresentation(permissionPreset).label,
            ),
        )
    }
    var expandedMenu by remember { mutableStateOf<ComposerMenu?>(null) }
    var expandedMenuOpenedWithKeyboard by remember { mutableStateOf(false) }

    /** 统一维护四个选择器的互斥展开状态，并保留键盘打开方式。 */
    fun updateExpandedMenu(menu: ComposerMenu, shouldExpand: Boolean, openedWithKeyboard: Boolean) {
        expandedMenu = composerMenuAfterTriggerChange(expandedMenu, menu, shouldExpand)
        expandedMenuOpenedWithKeyboard = shouldExpand && openedWithKeyboard
    }

    ComposerControlBar(
        attachment = {
            ActionButton(
                onClick = { state.attachFiles(pickFiles()) },
                tooltip = { Text("添加附件") },
                style = iconActionButtonStyle,
                modifier = Modifier.size(36.dp),
            ) { Icon(HeaderGlyph.ADD.iconKey, "添加附件") }
        },
        selectorGroup = {
            ComposerSelectorStrip(
                slots = selectorSlots,
                keepCardVisible = expandedMenu != null,
                control = { slot, displayLabel, showChevron, selectorModifier, compactPreview ->
                    val expanded = !compactPreview && expandedMenu == slot.menu
                    when (slot.menu) {
                        ComposerMenu.PROVIDER -> ComposerSelectorMenuButton(
                            label = slot.label,
                            displayLabel = displayLabel,
                            showChevron = showChevron,
                            expanded = expanded,
                            onExpandedChange = { shouldExpand, openedWithKeyboard ->
                                updateExpandedMenu(ComposerMenu.PROVIDER, shouldExpand, openedWithKeyboard)
                            },
                            onDismissRequest = {
                                expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.PROVIDER)
                                expandedMenuOpenedWithKeyboard = false
                            },
                            modifier = selectorModifier,
                            keyboardTriggeredPopup = expandedMenuOpenedWithKeyboard,
                        ) {
                            providerProfiles.entries.forEach { (_, providerModels) ->
                                val first = providerModels.firstOrNull() ?: return@forEach
                                selectableItem(
                                    selected = first.providerId == currentProvider,
                                    onClick = {
                                        expandedMenu = null
                                        state.selectProfile(first.id)
                                    },
                                ) { Text(first.providerLabel) }
                            }
                        }

                        ComposerMenu.MODEL -> ComposerSelectorMenuButton(
                            label = slot.label,
                            displayLabel = displayLabel,
                            showChevron = showChevron,
                            expanded = expanded,
                            onExpandedChange = { shouldExpand, openedWithKeyboard ->
                                updateExpandedMenu(ComposerMenu.MODEL, shouldExpand, openedWithKeyboard)
                            },
                            onDismissRequest = {
                                expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.MODEL)
                                expandedMenuOpenedWithKeyboard = false
                            },
                            modifier = selectorModifier,
                            keyboardTriggeredPopup = expandedMenuOpenedWithKeyboard,
                        ) {
                            currentProviderProfiles.forEach { profile ->
                                selectableItem(
                                    selected = profile.id == selectedProfile?.id,
                                    onClick = {
                                        expandedMenu = null
                                        state.selectProfile(profile.id)
                                    },
                                ) { Text(profile.modelLabel ?: profile.model) }
                            }
                        }

                        ComposerMenu.REASONING -> ComposerSelectorMenuButton(
                            label = slot.label,
                            displayLabel = displayLabel,
                            showChevron = showChevron,
                            expanded = expanded,
                            onExpandedChange = { shouldExpand, openedWithKeyboard ->
                                updateExpandedMenu(ComposerMenu.REASONING, shouldExpand, openedWithKeyboard)
                            },
                            onDismissRequest = {
                                expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.REASONING)
                                expandedMenuOpenedWithKeyboard = false
                            },
                            modifier = selectorModifier,
                            keyboardTriggeredPopup = expandedMenuOpenedWithKeyboard,
                        ) {
                            selectedVariants.forEach { variant ->
                                val effort = variant.reasoningEffort ?: return@forEach
                                selectableItem(
                                    selected = effort == (activeConversation?.reasoningEffort ?: state.ui.newReasoningEffort),
                                    onClick = {
                                        expandedMenu = null
                                        state.updateReasoningEffort(effort)
                                    },
                                ) { Text(reasoningControlLabel(effort)) }
                            }
                        }

                        ComposerMenu.PERMISSION -> ComposerPermissionMenuButton(
                            label = slot.label,
                            displayLabel = displayLabel,
                            showChevron = showChevron,
                            expanded = expanded,
                            onExpandedChange = { shouldExpand, openedWithKeyboard ->
                                updateExpandedMenu(ComposerMenu.PERMISSION, shouldExpand, openedWithKeyboard)
                            },
                            onDismissRequest = {
                                expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.PERMISSION)
                                expandedMenuOpenedWithKeyboard = false
                            },
                            selectedPreset = permissionPreset,
                            onPresetSelected = { preset ->
                                expandedMenu = null
                                state.updatePermission(preset)
                            },
                            modifier = selectorModifier,
                            keyboardTriggeredPopup = expandedMenuOpenedWithKeyboard,
                        )
                    }
                },
            )
        },
        contextIndicator = {
            ComposerContextIndicator(
                sweepAngle = contextRingSweepAngle(state.activeContextUsageFraction),
                tooltip = buildContextTooltip(
                    usageFraction = state.activeContextUsageFraction,
                    contextWindow = selectedProfile?.let(::resolveContextWindow),
                ),
            )
        },
        primaryAction = {
            ComposerPrimaryActionButton(
                danger = primaryActionVisual.danger,
                onClick = {
                    if (executionState.isStoppable()) {
                        state.pauseActiveRun()
                    } else if (executionState == ExecutionState.Paused || executionState == ExecutionState.Interrupted) {
                        state.resumeActiveRun()
                    } else {
                        onSendDraft()
                    }
                },
                iconKey = if (executionState == ExecutionState.Paused || executionState == ExecutionState.Interrupted) {
                    HeaderGlyph.RESUME.iconKey
                } else {
                    composerPrimaryActionGlyph(primaryActionVisual.danger).iconKey
                },
                contentDescription = when {
                    executionState.isStoppable() -> "暂停当前任务"
                    executionState == ExecutionState.Paused || executionState == ExecutionState.Interrupted -> "继续当前任务"
                    else -> "发送消息"
                },
            )
        },
    )
}
