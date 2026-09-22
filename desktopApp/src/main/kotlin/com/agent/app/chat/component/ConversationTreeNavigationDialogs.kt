@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.BranchSummaryMode
import com.agent.app.design.AppMuted
import com.agent.app.design.JewelDialog
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/**
 * 复刻 Pi `/tree` 在提交导航后的三种分支处理方式。
 *
 * 该弹窗只决定本次切换，不会把选择保存为长期偏好。
 */
@Composable
internal fun ConversationTreeSummaryChoiceDialog(
    onChoose: (BranchSummaryMode) -> Unit,
    onDismiss: () -> Unit,
) {
    JewelDialog(
        title = "切换路径",
        confirmLabel = "",
        onConfirm = {},
        onDismiss = onDismiss,
        dismissLabel = null,
        width = 460.dp,
        height = 310.dp,
        footerContent = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        Text("如何处理离开当前路径后的内容？", color = AppMuted)
        ConversationTreeSummaryChoiceButton("不摘要") { onChoose(BranchSummaryMode.NONE) }
        ConversationTreeSummaryChoiceButton("自动摘要") { onChoose(BranchSummaryMode.AUTOMATIC) }
        ConversationTreeSummaryChoiceButton("使用自定义提示摘要") { onChoose(BranchSummaryMode.CUSTOM) }
    }
}

/** 绘制占满弹窗内容宽度的一次性摘要选择。 */
@Composable
private fun ConversationTreeSummaryChoiceButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

/** 为本次 Pi `/tree` 摘要收集自定义指令；取消时由调用方返回三选一。 */
@Composable
internal fun ConversationTreeCustomSummaryDialog(
    initialPrompt: String,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
) {
    var prompt by remember(initialPrompt) { mutableStateOf(TextFieldValue(initialPrompt)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    JewelDialog(
        title = "自定义分支摘要",
        confirmLabel = "开始摘要",
        confirmEnabled = prompt.text.isNotBlank(),
        onConfirm = { onConfirm(prompt.text) },
        onDismiss = onBack,
        dismissLabel = "返回",
        width = 520.dp,
        height = 250.dp,
    ) {
        Text("输入这次摘要需要遵循的额外要求。", color = AppMuted)
        TextField(
            value = prompt,
            onValueChange = { prompt = it },
            placeholder = { Text("自定义摘要提示") },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}
