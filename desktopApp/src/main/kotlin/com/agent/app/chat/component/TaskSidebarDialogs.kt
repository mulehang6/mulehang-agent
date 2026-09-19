@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatTaskStatus
import com.agent.app.chat.state.WorkspaceTaskSectionUiState
import com.agent.app.design.*
import com.agent.app.platform.pickWorkspaceDirectory
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

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
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                style = stroke,
            )

            ChatTaskStatus.DONE -> {
                drawLine(
                    color = AppSuccess,
                    start = Offset(size.width * 0.24f, size.height * 0.53f),
                    end = Offset(size.width * 0.44f, size.height * 0.73f),
                    strokeWidth = 1.9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = AppSuccess,
                    start = Offset(size.width * 0.44f, size.height * 0.73f),
                    end = Offset(size.width * 0.78f, size.height * 0.3f),
                    strokeWidth = 1.9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
