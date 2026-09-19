package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** 持久展示资源待重载状态；普通通知超时不会影响这个横幅。 */
@Composable
internal fun ResourceReloadBanner(
    onReload: () -> Unit,
    reloadEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 8.dp,
        solidColor = AppAccent.copy(alpha = 0.10f),
        borderColor = AppAccent.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("需要重新加载", style = JewelTheme.defaultTextStyle)
                Text(
                    if (reloadEnabled) {
                        "已保存的 MCP、Hooks 或扩展会在重新加载后用于新任务。"
                    } else {
                        "当前任务结束后即可重新加载，不会中途断开工具。"
                    },
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
            }
            SettingsActionButton("重新加载", emphasized = true, enabled = reloadEnabled, onClick = onReload)
        }
    }
}
