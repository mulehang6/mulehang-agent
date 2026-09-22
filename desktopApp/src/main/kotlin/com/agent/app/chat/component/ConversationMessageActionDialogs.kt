@file:Suppress("UnstableApiUsage")

package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import com.agent.app.design.AppDanger
import com.agent.app.design.JewelDialog
import org.jetbrains.jewel.ui.component.Text

/** 展示消息操作无法完成的错误，确认后仅关闭提示。 */
@Composable
internal fun MessageActionErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    JewelDialog(
        title = "无法完成操作",
        confirmLabel = "知道了",
        dismissLabel = null,
        onDismiss = onDismiss,
        onConfirm = onDismiss,
    ) {
        Text(message, color = AppDanger)
    }
}
