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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppHeaderBackground
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.buildHeaderActions

/**
 * 原型顶部标题栏。
 */
@Composable
internal fun ChatHeader(state: ChatWindowState) {
    val activeConversation = state.ui.activeConversationOrNull
    val breadcrumb = activeConversation?.workspacePath ?: "workspace / none"
    val actions = buildHeaderActions()
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RingHeaderActionButton(glyph = actions.left.glyph, inline = true)
                Text(
                    text = "Air",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AppText,
                    ),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = breadcrumb,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = activeConversation?.title ?: "No task selected",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = AppText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.right.forEach { action ->
                    RingHeaderActionButton(glyph = action.glyph, inline = true)
                }
            }
        }
    }
}
