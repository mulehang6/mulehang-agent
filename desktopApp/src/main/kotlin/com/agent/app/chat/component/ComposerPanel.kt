@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
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
import com.agent.app.design.AppAccent
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.ComposerBackground
import com.agent.app.design.ComposerInputBackground
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.RingContextIndicator
import com.agent.app.design.RingDropdownMenuItem
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.RingInputField
import com.agent.app.design.RingIsland
import com.agent.app.design.RingPermissionDropdownMenuItem
import com.agent.app.design.RingPrimaryButton
import com.agent.app.design.RingSelectChip
import com.agent.app.design.RingTooltip
import com.agent.app.platform.pickFiles
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
import kotlinx.coroutines.launch

/**
 * 原型 composer。
 */
@Composable
internal fun ComposerPanel(
    state: ChatWindowState,
    onSendDraft: () -> Unit,
    composerInputMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val activeConversation = state.ui.activeConversationOrNull
    val profiles = state.availableProfiles
    val selectedProfile = state.activeProfile
    val executionState = activeConversation?.executionState ?: ExecutionState.Idle
    val composerBorderTransition = rememberInfiniteTransition(label = "composer-border-flow")
    val composerBorderProgress by composerBorderTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(COMPOSER_BORDER_FLOW_DURATION_MILLIS, easing = LinearEasing),
        ),
        label = "composer-border-progress",
    )
    val permissionPreset = activeConversation?.permissionPreset ?: PermissionPreset.DEFAULT
    val composerBorderColor = composerBorderTone(permissionPreset)
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
        modifier = modifier.drawWithContent {
            drawContent()
            val stroke = 2.dp.toPx()
            val inset = stroke / 2f
            val corner = 18.dp.toPx()
            val staticColor = composerBorderColor.copy(alpha = 0.72f)
            val borderPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = inset,
                        top = inset,
                        right = size.width - inset,
                        bottom = size.height - inset,
                        radiusX = corner,
                        radiusY = corner,
                    ),
                )
            }
            drawPath(
                path = borderPath,
                color = staticColor,
                style = Stroke(width = stroke),
            )
            if (shouldAnimateComposerBorder(executionState)) {
                val pathMeasure = PathMeasure().apply { setPath(borderPath, forceClosed = true) }
                composerBorderFlowSegments(pathMeasure.length, composerBorderProgress).forEach { segment ->
                    val flowPath = Path()
                    if (pathMeasure.getSegment(segment.startDistance, segment.endDistance, flowPath)) {
                        drawPath(
                            path = flowPath,
                            color = composerBorderColor,
                            style = Stroke(width = stroke * 1.3f),
                        )
                    }
                }
            }
        },
        color = ComposerBackground,
        shape = RoundedCornerShape(18.dp),
        borderColor = Color.Transparent,
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
                    .background(ComposerInputBackground, RoundedCornerShape(12.dp))
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
                        tooltip = buildContextTooltip(
                            usageFraction = activeConversation?.contextUsageFraction ?: 0f,
                            contextWindow = selectedProfile?.let(::resolveContextWindow),
                        ),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RingSelectChip(
                        label = permissionPresentation(permissionPreset).label,
                        expanded = expandedMenu == ComposerMenu.PERMISSION,
                        onExpandedChange = { shouldExpand ->
                            expandedMenu = ComposerMenu.PERMISSION.takeIf { shouldExpand }
                        },
                        onDismissRequest = {
                            expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.PERMISSION)
                        },
                        tooltip = "选择执行权限",
                    ) {
                        Text(
                            text = "Permission mode",
                            modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 6.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(color = AppMuted),
                        )
                        PermissionPreset.entries.forEachIndexed { index, preset ->
                            RingPermissionDropdownMenuItem(
                                description = permissionPresentation(preset).description,
                                badge = permissionPresentation(preset).label,
                                badgeColor = permissionPresentation(preset).tone,
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
