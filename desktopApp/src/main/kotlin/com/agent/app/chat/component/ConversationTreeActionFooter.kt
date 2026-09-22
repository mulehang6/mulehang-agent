package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.LocalDesktopPalette
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

/** 固定在树页底部的单一路径切换动作与非交互状态。 */
@Composable
internal fun ConversationTreeActionFooter(
    primaryEnabled: Boolean,
    inProgress: Boolean,
    operationError: String?,
    onPrimary: () -> Unit,
    primaryLabel: String = "切换",
) {
    val palette = LocalDesktopPalette.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        operationError?.let { Text(it, color = palette.danger) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inProgress) Text("正在生成分支摘要…", color = AppMuted)
            DefaultButton(onClick = onPrimary, enabled = primaryEnabled && !inProgress) { Text(primaryLabel) }
        }
    }
}
