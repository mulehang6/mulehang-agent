package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.LocalDesktopPalette
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/** 固定在树页底部的路径切换与返回末端动作。 */
@Composable
internal fun ConversationTreeActionFooter(
    conversation: ChatConversationUiState,
    state: ChatWindowState,
    primaryLabel: String,
    primaryEnabled: Boolean,
    operationError: String?,
    onPrimary: () -> Unit,
    onError: (String?) -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        operationError?.let { Text(it, color = palette.danger) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (conversation.activeEntryId != conversation.headEntryId && conversation.headEntryId != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val result = state.conversationTreeController.returnToHead(conversation.id)
                            onError(result.message.takeUnless { result.succeeded })
                        }
                    },
                    enabled = !state.conversationTreeController.summaryInProgress,
                ) { Text("回到会话末端") }
                Spacer(Modifier.width(8.dp))
            }
            DefaultButton(onClick = onPrimary, enabled = primaryEnabled) { Text(primaryLabel) }
        }
    }
}
