@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.buildComposerPrimaryActionVisual
import com.agent.app.chat.presentation.buildContextTooltip
import com.agent.app.chat.presentation.contextRingSweepAngle
import com.agent.app.chat.presentation.groupProfilesByProvider
import com.agent.app.chat.presentation.modelVariantsFor
import com.agent.app.chat.presentation.reasoningControlLabel
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.WorkspaceFileReference
import com.agent.app.chat.state.discoverWorkspaceFileReferences
import com.agent.app.chat.state.isStoppable
import com.agent.app.chat.state.resolveContextWindow
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.ComposerBackground
import com.agent.app.design.ComposerInputBackground
import com.agent.app.design.DesktopPalette
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.iconKey
import com.agent.app.platform.pickFiles
import com.agent.app.platform.readClipboardImageAsPng
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal const val COMPOSER_INPUT_HORIZONTAL_PADDING_DP = 12
internal const val COMPOSER_INPUT_VERTICAL_PADDING_DP = 8
internal const val PERMISSION_MENU_HOVERED_ALPHA = 0.56f
internal const val PERMISSION_MENU_SELECTED_ALPHA = 0.76f
internal const val PERMISSION_MENU_PRESSED_ALPHA = 0.86f

/** 返回权限菜单在当前主题下可读的文字颜色。 */
internal fun permissionPresetMenuTextColor(palette: DesktopPalette): Color = palette.text

/** 返回可编辑内容和只读占位符共同使用的左上角坐标。 */
internal fun composerInputContentOffset(): DpOffset = DpOffset(
    x = COMPOSER_INPUT_HORIZONTAL_PADDING_DP.dp,
    y = COMPOSER_INPUT_VERTICAL_PADDING_DP.dp,
)

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
                    label = reasoningControlLabel(activeConversation?.reasoningEffort),
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
    var draftFieldValue by remember { mutableStateOf(TextFieldValue(state.ui.draft)) }
    var commandSelectionIndex by remember { mutableIntStateOf(0) }
    var referenceSelectionIndex by remember { mutableIntStateOf(0) }
    var commandBrowserDismissed by remember { mutableStateOf(false) }
    var referenceBrowserDismissed by remember { mutableStateOf(false) }
    var suppressComposerEnterKeyUp by remember { mutableStateOf(false) }
    var workspaceReferences by remember { mutableStateOf<List<WorkspaceFileReference>>(emptyList()) }
    val inputScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var inputViewportHeight by remember { mutableStateOf(0) }
    val inputContentOffset = composerInputContentOffset()
    val iconActionButtonStyle = composerIconActionButtonStyle()
    val slashQuery = activeSlashCommandQuery(state.ui.draft, state.ui.draftSelectionStart)
    val referenceQuery = activeWorkspaceReferenceQuery(state.ui.draft, state.ui.draftSelectionStart)
    val matchingCommands = state.availablePromptCommands.filter { command ->
        slashQuery == null || command.name.contains(slashQuery, ignoreCase = true) ||
                command.description.contains(slashQuery, ignoreCase = true)
    }
    val commandBrowserVisible = slashQuery != null && !commandBrowserDismissed
    val referenceBrowserVisible = !commandBrowserVisible && referenceQuery != null && !referenceBrowserDismissed

    LaunchedEffect(state.ui.draft, state.ui.draftSelectionStart) {
        val selectionStart = state.ui.draftSelectionStart.coerceIn(0, state.ui.draft.length)
        if (
            draftFieldValue.text != state.ui.draft ||
            draftFieldValue.selection.start != selectionStart ||
            draftFieldValue.selection.end != selectionStart
        ) {
            draftFieldValue = TextFieldValue(state.ui.draft, selection = TextRange(selectionStart))
        }
        inputScrollState.scrollTo(inputScrollState.maxValue)
    }
    LaunchedEffect(slashQuery) {
        commandBrowserDismissed = false
    }
    LaunchedEffect(referenceQuery) {
        referenceBrowserDismissed = false
    }
    LaunchedEffect(matchingCommands) {
        commandSelectionIndex = commandSelectionIndex.coerceIn(0, (matchingCommands.lastIndex).coerceAtLeast(0))
    }
    LaunchedEffect(activeConversation?.workspacePath, referenceQuery) {
        workspaceReferences = if (referenceQuery == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                discoverWorkspaceFileReferences(
                    workspacePath = activeConversation?.workspacePath.orEmpty(),
                    query = referenceQuery,
                )
            }
        }
    }
    LaunchedEffect(workspaceReferences) {
        referenceSelectionIndex = referenceSelectionIndex.coerceIn(0, workspaceReferences.lastIndex.coerceAtLeast(0))
    }

    JewelSurface(
        role = JewelSurfaceRole.INPUT,
        radius = 18.dp,
        solidColor = ComposerBackground,
        borderColor = Color.Transparent,
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
                        Tooltip(tooltip = { Text(attachment.path) }) {
                            JewelSurface(
                                role = JewelSurfaceRole.CHROME,
                                radius = 999.dp,
                                solidColor = AppChipBackground,
                                borderColor = AppLine,
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = attachment.name,
                                        style = JewelTheme.defaultTextStyle.copy(color = AppText),
                                    )
                                    ActionButton(
                                        onClick = { state.removeAttachment(attachment.path) },
                                        tooltip = { Text("移除 ${attachment.name}") },
                                        style = iconActionButtonStyle,
                                    ) { Icon(AllIconsKeys.Actions.Cancel, "移除 ${attachment.name}") }
                                }
                            }
                        }
                    }
                }
            }

            if (commandBrowserVisible) {
                SlashCommandBrowser(
                    commands = matchingCommands,
                    selectedIndex = commandSelectionIndex,
                    onCommandSelected = { command ->
                        state.insertPromptCommand(command)
                        commandBrowserDismissed = true
                    },
                )
            } else if (referenceBrowserVisible) {
                WorkspaceFileReferenceBrowser(
                    references = workspaceReferences,
                    selectedIndex = referenceSelectionIndex,
                    onReferenceSelected = { reference ->
                        if (state.attachWorkspaceFile(reference.absolutePath) == null) {
                            referenceBrowserDismissed = true
                        }
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
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
                        .verticalScroll(inputScrollState)
                        .padding(
                            start = inputContentOffset.x,
                            top = inputContentOffset.y,
                            end = COMPOSER_INPUT_HORIZONTAL_PADDING_DP.dp + 6.dp,
                            bottom = inputContentOffset.y,
                        ),
                ) {
                    TextArea(
                        value = draftFieldValue,
                        onValueChange = { updated ->
                            draftFieldValue = updated
                            state.updateDraft(updated.text, updated.selection.start)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyUp &&
                                    event.key == Key.Enter &&
                                    suppressComposerEnterKeyUp
                                ) {
                                    suppressComposerEnterKeyUp = false
                                    return@onPreviewKeyEvent true
                                }
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.V &&
                                    event.isCtrlPressed
                                ) {
                                    val pastedImage = readClipboardImageAsPng()
                                    if (pastedImage != null) {
                                        state.addClipboardImage(pastedImage)
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                if (commandBrowserVisible && event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            commandSelectionIndex = (commandSelectionIndex + 1)
                                                .coerceAtMost(matchingCommands.lastIndex.coerceAtLeast(0))
                                            return@onPreviewKeyEvent true
                                        }

                                        Key.DirectionUp -> {
                                            commandSelectionIndex = (commandSelectionIndex - 1).coerceAtLeast(0)
                                            return@onPreviewKeyEvent true
                                        }

                                        Key.Enter,
                                        Key.Tab,
                                        -> {
                                            matchingCommands.getOrNull(commandSelectionIndex)?.let { command ->
                                                state.insertPromptCommand(command)
                                                commandBrowserDismissed = true
                                                suppressComposerEnterKeyUp = event.key == Key.Enter
                                                return@onPreviewKeyEvent true
                                            }
                                        }

                                        Key.Period -> if (event.isCtrlPressed) {
                                            matchingCommands.getOrNull(commandSelectionIndex)?.let { command ->
                                                state.insertPromptCommand(command)
                                                commandBrowserDismissed = true
                                                return@onPreviewKeyEvent true
                                            }
                                        }

                                        Key.Escape -> {
                                            commandBrowserDismissed = true
                                            return@onPreviewKeyEvent true
                                        }

                                        else -> Unit
                                    }
                                }
                                if (referenceBrowserVisible && event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            referenceSelectionIndex = (referenceSelectionIndex + 1)
                                                .coerceAtMost(workspaceReferences.lastIndex.coerceAtLeast(0))
                                            return@onPreviewKeyEvent true
                                        }

                                        Key.DirectionUp -> {
                                            referenceSelectionIndex = (referenceSelectionIndex - 1).coerceAtLeast(0)
                                            return@onPreviewKeyEvent true
                                        }

                                        Key.Enter,
                                        Key.Tab,
                                        -> {
                                            workspaceReferences.getOrNull(referenceSelectionIndex)?.let { reference ->
                                                if (state.attachWorkspaceFile(reference.absolutePath) == null) {
                                                    referenceBrowserDismissed = true
                                                }
                                                suppressComposerEnterKeyUp = event.key == Key.Enter
                                                return@onPreviewKeyEvent true
                                            }
                                        }

                                        Key.Escape -> {
                                            referenceBrowserDismissed = true
                                            return@onPreviewKeyEvent true
                                        }

                                        else -> Unit
                                    }
                                }
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
                        placeholder = null,
                        undecorated = true,
                    )
                }
                if (draftFieldValue.text.isEmpty()) {
                    Text(
                        text = "描述你想完成的任务…",
                        color = AppMuted,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = inputContentOffset.x,
                                top = inputContentOffset.y,
                            ),
                    )
                }
                if (shouldShowComposerInputScrollbar(inputScrollState.maxValue)) {
                    VerticalScrollbar(
                        scrollState = inputScrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 3.dp),
                    )
                }
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
                                        expandedMenu = ComposerMenu.PROVIDER.takeIf { shouldExpand }
                                        expandedMenuOpenedWithKeyboard = shouldExpand && openedWithKeyboard
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
                                        expandedMenu = ComposerMenu.MODEL.takeIf { shouldExpand }
                                        expandedMenuOpenedWithKeyboard = shouldExpand && openedWithKeyboard
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
                                        expandedMenu = ComposerMenu.REASONING.takeIf { shouldExpand }
                                        expandedMenuOpenedWithKeyboard = shouldExpand && openedWithKeyboard
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
                                            selected = effort == activeConversation?.reasoningEffort,
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
                                        expandedMenu = ComposerMenu.PERMISSION.takeIf { shouldExpand }
                                        expandedMenuOpenedWithKeyboard = shouldExpand && openedWithKeyboard
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
                        sweepAngle = contextRingSweepAngle(activeConversation?.contextUsageFraction ?: 0f),
                        tooltip = buildContextTooltip(
                            usageFraction = activeConversation?.contextUsageFraction ?: 0f,
                            contextWindow = selectedProfile?.let(::resolveContextWindow),
                        ),
                    )
                },
                primaryAction = {
                    ComposerPrimaryActionButton(
                        danger = primaryActionVisual.danger,
                        onClick = {
                            if (executionState.isStoppable()) {
                                state.cancelActiveRun()
                            } else {
                                onSendDraft()
                            }
                        },
                        iconKey = composerPrimaryActionGlyph(primaryActionVisual.danger).iconKey,
                        contentDescription = if (primaryActionVisual.danger) "停止当前任务" else "发送消息",
                    )
                },
            )
        }
    }
}

/** 返回权限菜单项在悬停、选中和按下状态下的语义色背景；静止行保持透明。 */
internal fun permissionPresetMenuRowBackground(
    tone: Color,
    selected: Boolean,
    hovered: Boolean,
    pressed: Boolean,
): Color = when {
    selected -> tone.copy(alpha = PERMISSION_MENU_SELECTED_ALPHA)
    pressed -> tone.copy(alpha = PERMISSION_MENU_PRESSED_ALPHA)
    hovered -> tone.copy(alpha = PERMISSION_MENU_HOVERED_ALPHA)
    else -> Color.Transparent
}
