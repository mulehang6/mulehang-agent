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
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.agent.app.chat.presentation.buildComposerPrimaryActionVisual
import com.agent.app.chat.presentation.buildContextTooltip
import com.agent.app.chat.presentation.contextRingSweepAngle
import com.agent.app.chat.presentation.groupProfilesByProvider
import com.agent.app.chat.presentation.modelVariantsFor
import com.agent.app.chat.presentation.reasoningControlLabel
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.isStoppable
import com.agent.app.chat.state.resolveContextWindow
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppText
import com.agent.app.design.ComposerBackground
import com.agent.app.design.ComposerInputBackground
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.iconKey
import com.agent.app.platform.pickFiles
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal const val COMPOSER_INPUT_HORIZONTAL_PADDING_DP = 12
internal const val COMPOSER_INPUT_VERTICAL_PADDING_DP = 8
internal const val PERMISSION_MENU_WIDTH_DP = 360
internal const val PERMISSION_MENU_TITLE_START_PADDING_DP = 16

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
    var expandedMenu by remember { mutableStateOf<ComposerMenu?>(null) }
    var draftFieldValue by remember { mutableStateOf(TextFieldValue(state.ui.draft)) }
    val inputScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var inputViewportHeight by remember { mutableStateOf(0) }
    val inputContentOffset = composerInputContentOffset()

    LaunchedEffect(state.ui.draft) {
        if (draftFieldValue.text != state.ui.draft) {
            draftFieldValue = TextFieldValue(state.ui.draft)
        }
        inputScrollState.scrollTo(inputScrollState.maxValue)
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
                                    ) { Icon(AllIconsKeys.Actions.Cancel, "移除 ${attachment.name}") }
                                }
                            }
                        }
                    }
                }
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
                            state.updateDraft(updated.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
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
                    ActionButton(
                        onClick = { state.attachFiles(pickFiles()) },
                        tooltip = { Text("添加附件") },
                    ) { Icon(HeaderGlyph.ADD.iconKey, "添加附件") }
                    ComposerMenuButton(
                        label = selectedProfile?.providerLabel ?: currentProvider ?: "服务商",
                        expanded = expandedMenu == ComposerMenu.PROVIDER,
                        onExpandedChange = { shouldExpand ->
                            expandedMenu = ComposerMenu.PROVIDER.takeIf { shouldExpand }
                        },
                        onDismissRequest = {
                            expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.PROVIDER)
                        },
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
                    ComposerMenuButton(
                        label = selectedProfile?.modelLabel ?: selectedProfile?.model ?: "模型",
                        expanded = expandedMenu == ComposerMenu.MODEL,
                        onExpandedChange = { shouldExpand ->
                            expandedMenu = ComposerMenu.MODEL.takeIf { shouldExpand }
                        },
                        onDismissRequest = {
                            expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.MODEL)
                        },
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
                    if (selectedVariants.isNotEmpty()) {
                        ComposerMenuButton(
                            label = reasoningControlLabel(activeConversation?.reasoningEffort),
                            expanded = expandedMenu == ComposerMenu.REASONING,
                            onExpandedChange = { shouldExpand ->
                                expandedMenu = ComposerMenu.REASONING.takeIf { shouldExpand }
                            },
                            onDismissRequest = {
                                expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.REASONING)
                            },
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
                    }
                    ComposerContextIndicator(
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
                    PermissionMenuButton(
                        label = permissionPresentation(permissionPreset).label,
                        expanded = expandedMenu == ComposerMenu.PERMISSION,
                        onExpandedChange = { shouldExpand ->
                            expandedMenu = ComposerMenu.PERMISSION.takeIf { shouldExpand }
                        },
                        onDismissRequest = {
                            expandedMenu = dismissComposerMenu(expandedMenu, ComposerMenu.PERMISSION)
                        },
                        selectedPreset = permissionPreset,
                        onPresetSelected = { preset ->
                            expandedMenu = null
                            state.updatePermission(preset)
                        },
                    )
                    DefaultButton(
                        onClick = {
                            if (executionState.isStoppable()) {
                                state.cancelActiveRun()
                            } else {
                                onSendDraft()
                            }
                        },
                    ) {
                        Icon(
                            composerPrimaryActionGlyph(primaryActionVisual.danger).iconKey,
                            if (primaryActionVisual.danger) "停止当前任务" else "发送消息",
                        )
                    }
                }
            }
        }
    }
}

/** 使用 Jewel 按钮和原生菜单承载 Composer 的业务选择器。 */
@Composable
private fun ComposerMenuButton(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    menuModifier: Modifier = Modifier,
    content: MenuScope.() -> Unit,
) {
    Box {
        OutlinedButton(onClick = { onExpandedChange(!expanded) }) { Text(label) }
        if (expanded) {
            PopupMenu(
                onDismissRequest = {
                    onDismissRequest()
                    true
                },
                horizontalAlignment = Alignment.Start,
                modifier = menuModifier,
                content = content,
            )
        }
    }
}

/** 渲染锚定在权限按钮上的专用弹层，避免通用菜单将 hover 覆盖成灰色。 */
@Composable
private fun PermissionMenuButton(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    selectedPreset: PermissionPreset,
    onPresetSelected: (PermissionPreset) -> Unit,
) {
    val density = LocalDensity.current

    Box {
        OutlinedButton(onClick = { onExpandedChange(!expanded) }) { Text(label) }
        if (expanded) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = IntOffset(0, with(density) { 4.dp.roundToPx() }),
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                JewelSurface(
                    role = JewelSurfaceRole.CHROME,
                    radius = 8.dp,
                    solidColor = AppChipBackground,
                    borderColor = AppLine,
                    modifier = Modifier.width(PERMISSION_MENU_WIDTH_DP.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Permission Mode",
                            modifier = Modifier.padding(
                                start = PERMISSION_MENU_TITLE_START_PADDING_DP.dp,
                                end = 12.dp,
                                top = 2.dp,
                                bottom = 4.dp,
                            ),
                        )
                        PermissionPreset.entries.forEach { preset ->
                            PermissionPresetMenuCard(
                                presentation = permissionPresentation(preset),
                                selected = preset == selectedPreset,
                                onClick = { onPresetSelected(preset) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 返回权限卡所需的背景色；选中态优先且其 hover 不产生额外变化。 */
internal fun permissionPresetCardBackground(selected: Boolean, hovered: Boolean): Color =
    if (selected || hovered) AppSelectedBackground else Color.Transparent

/** 渲染带风险色标签的权限审批卡，并自行管理 hover 与点击状态。 */
@Composable
private fun PermissionPresetMenuCard(
    presentation: PermissionPresentation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = selected || hovered

    JewelSurface(
        role = JewelSurfaceRole.CHROME,
        radius = 7.dp,
        solidColor = permissionPresetCardBackground(selected = selected, hovered = hovered),
        borderColor = Color.Transparent,
        borderWidth = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(presentation.tone, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = presentation.label,
                    style = JewelTheme.defaultTextStyle.copy(color = Color.White),
                )
            }
            Text(
                text = presentation.description,
                style = JewelTheme.defaultTextStyle.copy(color = if (highlighted) AppText else AppMuted),
            )
        }
    }
}
