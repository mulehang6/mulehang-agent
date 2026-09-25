@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.AppUserCardBackground
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.time.Duration.Companion.milliseconds

/** 新消息进入动效的初始下移距离，需要足够大才能被看见。 */
private val MESSAGE_ENTRY_TRAVEL = 24.dp

/** 复制动作在原位显示成功或失败结果的短暂状态。 */
private enum class CopyFeedback {
    IDLE,
    COPIED,
    FAILED,
}

/**
 * 单条用户消息卡片，并为 Pi 式消息操作预留固定高度，避免悬停时推动后续内容。
 */
@Composable
internal fun UserMessageCard(
    turn: TimelineTurnPresentation,
    entryMotionId: Long?,
    operationInProgress: Boolean,
    onEntryMotionFinished: (Long) -> Unit,
    onPositioned: (anchorId: String, topInWindow: Float, bottomInWindow: Float) -> Unit,
    onEditFromHere: (String) -> Unit,
    onNewSession: (String) -> Unit,
    onRollback: (String, Boolean) -> Unit,
) {
    val travelDistancePx = with(LocalDensity.current) { MESSAGE_ENTRY_TRAVEL.toPx() }
    val progress = remember(entryMotionId) { Animatable(if (entryMotionId == null) 1f else 0f) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var actionsHaveFocus by remember { mutableStateOf(false) }
    LaunchedEffect(entryMotionId) {
        val motionId = entryMotionId ?: return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.001f,
            ),
        )
        onEntryMotionFinished(motionId)
    }
    val visuals = messageEntryVisuals(progress.value, travelDistancePx)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(hoverSource)
            .onGloballyPositioned { coordinates ->
                // 默认 boundsInWindow 会裁剪离屏内容，多个消息会因此得到相同的视口边缘坐标。
                val bounds = coordinates.boundsInWindow(clipBounds = false)
                onPositioned(turn.anchorId, bounds.top, bounds.bottom)
            },
        horizontalAlignment = Alignment.End,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopEnd,
        ) {
            JewelSurface(
                role = JewelSurfaceRole.PANEL,
                radius = 8.dp,
                solidColor = AppUserCardBackground,
                borderColor = Color.Transparent,
                modifier = Modifier
                    .widthIn(max = maxWidth * 0.8f)
                    .wrapContentWidth()
                    .graphicsLayer {
                        alpha = visuals.alpha
                        scaleX = visuals.scale
                        scaleY = visuals.scale
                        translationY = visuals.translationY
                        transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 1f)
                    },
            ) {
                Text(
                    text = turn.userText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = JewelTheme.defaultTextStyle.copy(color = AppText),
                )
            }
        }
        UserMessageActionRow(
            turn = turn,
            pointerVisible = hovered,
            operationInProgress = operationInProgress,
            onFocusWithinChange = { actionsHaveFocus = it },
            keyboardVisible = actionsHaveFocus,
            onEditFromHere = onEditFromHere,
            onNewSession = onNewSession,
            onRollback = onRollback,
        )
    }
}

/** 右对齐的消息动作行；透明状态仍保留 24dp 空间，但不会接受点击。 */
@Composable
private fun UserMessageActionRow(
    turn: TimelineTurnPresentation,
    pointerVisible: Boolean,
    keyboardVisible: Boolean,
    operationInProgress: Boolean,
    onFocusWithinChange: (Boolean) -> Unit,
    onEditFromHere: (String) -> Unit,
    onNewSession: (String) -> Unit,
    onRollback: (String, Boolean) -> Unit,
) {
    var copyFeedback by remember(turn.anchorId) { mutableStateOf(CopyFeedback.IDLE) }
    var copyFeedbackNonce by remember(turn.anchorId) { mutableIntStateOf(0) }
    var rollbackMenuExpanded by remember(turn.anchorId) { mutableStateOf(false) }
    val animatedPointerAlpha by animateFloatAsState(
        targetValue = if (pointerVisible || operationInProgress) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "user-message-actions-alpha",
    )
    val alpha = if (keyboardVisible) 1f else animatedPointerAlpha
    val actionsVisible = pointerVisible || keyboardVisible || operationInProgress
    LaunchedEffect(copyFeedback, copyFeedbackNonce) {
        if (copyFeedback != CopyFeedback.IDLE) {
            delay(1_400.milliseconds)
            copyFeedback = CopyFeedback.IDLE
        }
    }
    Row(
        modifier = Modifier
            .height(24.dp)
            .focusGroup()
            .onFocusChanged { onFocusWithinChange(it.hasFocus) }
            .focusable()
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MessageActionButton(
            icon = AllIconsKeys.Actions.Copy,
            label = when (copyFeedback) {
                CopyFeedback.IDLE -> "复制"
                CopyFeedback.COPIED -> "已复制"
                CopyFeedback.FAILED -> "复制失败"
            },
            enabled = actionsVisible,
            onClick = {
                copyFeedback = if (copyUserMessage(turn.userText)) CopyFeedback.COPIED else CopyFeedback.FAILED
                copyFeedbackNonce += 1
            },
        )
        turn.sourceUserEntryId?.let { entryId ->
            Box {
                Tooltip(tooltip = { Text("回退到此处") }) {
                    ActionButton(
                        onClick = { rollbackMenuExpanded = true },
                        enabled = actionsVisible && !operationInProgress,
                        contentPadding = PaddingValues(horizontal = 5.dp),
                    ) {
                        Text("↶", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
                    }
                }
                if (rollbackMenuExpanded) {
                    PopupMenu(
                        onDismissRequest = { rollbackMenuExpanded = false; true },
                        horizontalAlignment = Alignment.End,
                    ) {
                        selectableItem(selected = false, onClick = {
                            rollbackMenuExpanded = false
                            onRollback(entryId, false)
                        }) { Text("仅回退会话") }
                        selectableItem(selected = false, onClick = {
                            rollbackMenuExpanded = false
                            onRollback(entryId, true)
                        }) { Text("回退会话并恢复文件") }
                    }
                }
            }
            MessageActionButton(
                icon = AllIconsKeys.Actions.Edit,
                label = "从此处编辑",
                enabled = actionsVisible && !operationInProgress,
                onClick = { onEditFromHere(entryId) },
            )
            MessageActionButton(
                icon = AllIconsKeys.Vcs.Branch,
                label = "新会话",
                enabled = actionsVisible && !operationInProgress,
                onClick = { onNewSession(entryId) },
            )
        }
    }
}

/** 以弱化文字与紧凑图标绘制一项消息级动作。 */
@Composable
private fun MessageActionButton(
    icon: org.jetbrains.jewel.ui.icon.IconKey,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ActionButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, label, modifier = Modifier.size(13.dp), tint = AppMuted)
            Text(label, style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        }
    }
}

/** 将完整可见用户文本复制到系统剪贴板，并把平台异常转换成界面反馈。 */
private fun copyUserMessage(content: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(content), null)
}.isSuccess
