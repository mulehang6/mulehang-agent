package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.LocalDesktopPalette
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/** 提示时间线正在投影历史 leaf，并提供精确返回持久末端的动作。 */
@Composable
internal fun HistoricalBranchBanner(
    returning: Boolean,
    errorMessage: String?,
    onReturnToHead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDesktopPalette.current
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        modifier = modifier.fillMaxWidth(),
        radius = 10.dp,
        solidColor = palette.hoverBackground,
        borderColor = palette.line,
        borderWidth = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("正在查看历史分支", color = AppText, fontWeight = FontWeight.SemiBold)
                    Text("后续条目仍然保留，可以随时回到原会话末端。", color = AppMuted)
                }
                OutlinedButton(onClick = onReturnToHead, enabled = !returning) {
                    Text(if (returning) "正在返回…" else "回到会话末端")
                }
            }
            errorMessage?.let { Text(it, color = AppDanger, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
