package com.agent.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus

/**
 * 最小聊天界面骨架。
 */
@Composable
fun ChatScreen(
    state: ChatWindowState,
) {
    val draft = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Active profile: ${state.state.activeProfileId ?: "none"}")
        state.errorMessage?.let { message ->
            Text("Error: $message")
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.state.items) { item ->
                when (item) {
                    is ChatMessageItem -> Text("${item.message.role}: ${item.message.content}")
                    is ReasoningItem -> ReasoningBlock(item)
                    is ToolEventItem -> Text(buildToolEventLabel(item))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = draft.value,
                onValueChange = { draft.value = it },
                label = { Text("Message") },
            )
            Button(
                onClick = {
                    val value = draft.value.trim()
                    if (value.isNotEmpty()) {
                        state.send(value)
                        draft.value = ""
                    }
                },
            ) {
                Text("Send")
            }
        }
    }
}

/**
 * 展示默认展开的思考块。
 */
@Composable
private fun ReasoningBlock(item: ReasoningItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (item.isStreaming) "Thinking: 思考中..." else "Thinking:")
        if (item.expanded) {
            Text(item.displayText)
        }
    }
}

/**
 * 将工具事件格式化为时间线可读文本。
 */
private fun buildToolEventLabel(item: ToolEventItem): String {
    val prefix = when (item.status) {
        ToolEventStatus.Started -> "Tool: 正在调用 ${item.toolName}"
        ToolEventStatus.Finished -> "Tool: ${item.toolName} 已返回"
        ToolEventStatus.Status -> "Status: ${item.preview.orEmpty()}"
    }
    return item.preview
        ?.takeIf { it.isNotBlank() && item.status != ToolEventStatus.Status }
        ?.let { "$prefix ($it)" }
        ?: prefix
}
