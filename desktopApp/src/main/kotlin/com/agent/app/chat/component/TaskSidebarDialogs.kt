@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.agent.shared.chat.model.ConversationEntry
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** 仅列出用户消息，供用户从旧路径创建一个独立新会话。 */
@Composable
internal fun ConversationNewSessionDialog(
    taskTitle: String,
    candidates: List<ConversationEntry.Message>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selectedEntryId by remember(candidates) { mutableStateOf(candidates.lastOrNull()?.id) }
    JewelDialog(
        title = "从消息新建会话 · $taskTitle",
        confirmLabel = "新建会话",
        confirmEnabled = selectedEntryId != null,
        width = 560.dp,
        height = 520.dp,
        onDismiss = onDismiss,
        onConfirm = { selectedEntryId?.let(onConfirm) },
    ) {
        Text(
            "这里创建的是独立任务，只列出可恢复的用户输入。" +
                    "新会话会复制到该消息之前，并把原输入与附件放回输入框；" +
                    "它不会被计入当前会话的内部条目分支。",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            candidates.forEachIndexed { index, entry ->
                val selected = selectedEntryId == entry.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (selected) AppSelectedBackground else LocalDesktopPalette.current.panelBackground,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { selectedEntryId = entry.id }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${index + 1}", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
                    Text(entry.message.content.lineSequence().firstOrNull().orEmpty().ifBlank { "空消息" })
                }
            }
            if (candidates.isEmpty()) {
                Text("此会话还没有可用于新建会话的用户消息。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            }
        }
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
    val rotation = if (status == ChatTaskStatus.RUNNING) {
        val rotationTransition = rememberInfiniteTransition(label = "running-task-indicator")
        val runningRotation by rotationTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_050, easing = LinearEasing),
            ),
            label = "running-task-rotation",
        )
        runningRotation
    } else {
        0f
    }
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val inset = 2.5.dp.toPx()
        when (status) {
            ChatTaskStatus.NONE -> Unit

            ChatTaskStatus.WAITING -> drawCircle(
                color = AppAccent,
                radius = 4.dp.toPx(),
            )

            ChatTaskStatus.FAILED -> {
                drawCircle(color = AppDanger, radius = 6.dp.toPx(), style = stroke)
                drawLine(
                    color = AppDanger,
                    start = Offset(size.width / 2f, size.height * 0.31f),
                    end = Offset(size.width / 2f, size.height * 0.57f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = AppDanger, radius = 1.dp.toPx(), center = Offset(size.width / 2f, size.height * 0.7f))
            }

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

            ChatTaskStatus.PAUSED -> {
                drawLine(
                    color = AppMuted,
                    start = Offset(size.width * 0.38f, size.height * 0.29f),
                    end = Offset(size.width * 0.38f, size.height * 0.71f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = AppMuted,
                    start = Offset(size.width * 0.62f, size.height * 0.29f),
                    end = Offset(size.width * 0.62f, size.height * 0.71f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

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
