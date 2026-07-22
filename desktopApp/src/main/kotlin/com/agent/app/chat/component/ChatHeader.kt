package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppHeaderBackground
import com.agent.app.design.AppText
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.RingHeaderActionButton

/**
 * 原型顶部标题栏。
 */
@Composable
internal fun ChatHeader(
    sidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppHeaderBackground,
        border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RingHeaderActionButton(
                    glyph = HeaderGlyph.MENU,
                    onClick = onToggleSidebar,
                    inline = false,
                    tooltip = if (sidebarVisible) "隐藏任务侧栏" else "显示任务侧栏",
                )
                Text(
                    text = "MH Agent",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AppText,
                    ),
                )
            }
        }
    }
}
