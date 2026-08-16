package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.DesktopThemeMode
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text

/**
 * 渲染只包含系统、深色与浅色三种模式的 Jewel 主题设置。
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun ThemeSettingsContent(
    themeMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
) {
    Text("主题")
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("界面主题")
                Text("跟随系统，或固定使用深色与浅色外观。")
            }
            ListComboBox(
                items = DesktopThemeMode.entries,
                selectedIndex = DesktopThemeMode.entries.indexOf(themeMode),
                onSelectedItemChange = { index -> onThemeChanged(DesktopThemeMode.entries[index]) },
                itemKeys = { _, mode -> mode.storageValue },
                modifier = Modifier.width(170.dp),
            ) { mode, selected, active ->
                SimpleListItem(
                    text = mode.label,
                    selected = selected,
                    active = active,
                )
            }
        }
    }
}
