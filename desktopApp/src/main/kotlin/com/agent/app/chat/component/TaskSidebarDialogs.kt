@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.presentation.resolveWorkspaceForTaskCreation
import com.agent.app.chat.state.ChatTaskGroup
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatTaskStatus
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.ConversationTitleState
import com.agent.app.chat.state.WorkspaceTaskSectionUiState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppDanger
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.design.PopupMenuBackground
import com.agent.app.design.PopupMenuBorder
import com.agent.app.design.PopupMenuSelectedBackground
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.JewelDialog
import com.agent.app.design.OffsetPopupPositionProvider
import com.agent.app.design.iconKey
import com.agent.app.platform.pickWorkspaceDirectory
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
/**
 * 为 task 名称提供可编辑的重命名弹窗。
 */
@Composable
internal fun TaskRenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(TextFieldValue(initialTitle)) }
    JewelDialog(
        title = "重命名任务",
        confirmLabel = "重命名",
        confirmEnabled = title.text.isNotBlank(),
        onConfirm = { onConfirm(title.text) },
        onDismiss = onDismiss,
    ) {
        TextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 编辑一个工作区的显示名称与实际目录，并将更新应用到该组历史任务。 */
@Composable
internal fun WorkspaceEditDialog(
    workspace: WorkspaceTaskSectionUiState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, path: String) -> String?,
) {
    var name by remember(workspace.workspacePath) { mutableStateOf(TextFieldValue(workspace.label)) }
    var path by remember(workspace.workspacePath) { mutableStateOf(TextFieldValue(workspace.workspacePath)) }
    var validationMessage by remember(workspace.workspacePath) { mutableStateOf<String?>(null) }
    JewelDialog(
        title = "编辑工作区",
        confirmLabel = "保存",
        height = 340.dp,
        onDismiss = onDismiss,
        onConfirm = {
            validationMessage = onConfirm(name.text, path.text)
            if (validationMessage == null) onDismiss()
        },
    ) {
        Text("工作区名称")
        TextField(
            value = name,
            onValueChange = {
                name = it
                validationMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("工作目录")
        TextField(
            value = path,
            onValueChange = {
                path = it
                validationMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                pickWorkspaceDirectory()?.let { selectedPath ->
                    path = TextFieldValue(selectedPath)
                    validationMessage = null
                }
            },
        ) { Text("选择目录") }
        validationMessage?.let { message ->
            Text(text = message, style = JewelTheme.defaultTextStyle.copy(color = AppDanger))
        }
    }
}

/**
 * AI 标题生成中的三点呼吸提示；与任务运行中的旋转进度圈明确区分。
 */
@Composable
internal fun TitleGeneratingIndicator() {
    val transition = rememberInfiniteTransition(label = "title-generating-dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(TITLE_GENERATING_DOT_COUNT) { index ->
            val intensity by transition.animateFloat(
                initialValue = 0.32f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 360,
                        delayMillis = index * 100,
                        easing = CubicBezierEasing(0.22f, 0.82f, 0.24f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "title-generating-dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        alpha = intensity
                        scaleX = 0.82f + intensity * 0.18f
                        scaleY = 0.82f + intensity * 0.18f
                    }
                    .background(AppAccent, CircleShape),
            )
        }
    }
}

/**
 * 在条目右侧提供新建、运行和完成三种紧凑状态标识。
 */
@Composable
internal fun TaskStatusIndicator(status: ChatTaskStatus) {
    val rotationTransition = rememberInfiniteTransition(label = "running-task-indicator")
    val rotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_050, easing = LinearEasing),
        ),
        label = "running-task-rotation",
    )
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = if (status == ChatTaskStatus.RUNNING) rotation else 0f },
    ) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val inset = 2.5.dp.toPx()
        when (status) {
            ChatTaskStatus.NEW -> drawCircle(
                color = AppMuted,
                radius = (size.minDimension - inset * 2f) / 2f,
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4.dp.toPx(), 2.4.dp.toPx())),
                ),
            )

            ChatTaskStatus.RUNNING -> drawArc(
                color = AppAccent,
                startAngle = -72f,
                sweepAngle = 246f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                style = stroke,
            )

            ChatTaskStatus.DONE -> {
                drawLine(
                    color = AppSuccess,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.53f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.73f),
                    strokeWidth = 1.9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = AppSuccess,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.73f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.3f),
                    strokeWidth = 1.9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
