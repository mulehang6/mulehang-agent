package com.agent.app.chat.component

import androidx.compose.runtime.Composable

/** 按最近一次消息操作结果显示错误或文件恢复汇总。 */
@Composable
internal fun WorkspacePanelActionDialogs(
    messageActionError: String?,
    fileRestoreSummary: String?,
    onDismissMessageError: () -> Unit,
    onDismissFileSummary: () -> Unit,
) {
    messageActionError?.let { error ->
        MessageActionErrorDialog(message = error, onDismiss = onDismissMessageError)
    }
    fileRestoreSummary?.let { summary ->
        FileRestoreSummaryDialog(summary, onDismiss = onDismissFileSummary)
    }
}
