package com.agent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus
import androidx.compose.foundation.shape.RoundedCornerShape

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
                    is ChatMessageItem -> ChatMessageBlock(item)
                    is ReasoningItem -> ReasoningBlock(item)
                    is ToolEventItem -> ToolEventBlock(item)
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
 * 按消息角色渲染聊天正文。
 */
@Composable
private fun ChatMessageBlock(item: ChatMessageItem) {
    when (item.message.role) {
        ChatRole.System -> Text(
            text = buildChatMessageText(item),
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF6B7480),
                lineHeight = 20.sp,
            ),
        )

        ChatRole.User -> BubbleBlock(
            text = buildChatMessageText(item),
            containerColor = Color(0xFF34363D),
            contentColor = Color(0xFFF4F6FA),
        )

        ChatRole.Assistant -> Text(
            text = buildChatMessageText(item),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 14.sp,
                lineHeight = 24.sp,
                color = Color(0xFF1E2430),
            ),
        )
    }
}

/**
 * 展示默认展开的思考块。
 */
@Composable
private fun ReasoningBlock(item: ReasoningItem) {
    BubbleBlock(
        containerColor = Color(0xFFF2F4F7),
        contentColor = Color(0xFF273142),
        borderColor = Color(0xFFD4DAE3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildReasoningHeadline(item),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF526173),
                ),
            )
            if (item.expanded) {
                Text(
                    text = item.displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFF2A3342),
                    ),
                )
            }
        }
    }
}

/**
 * 展示工具调用等轻量时间线事件。
 */
@Composable
private fun ToolEventBlock(item: ToolEventItem) {
    Text(
        text = buildToolEventLabel(item),
        style = MaterialTheme.typography.bodySmall.copy(
            color = Color(0xFF6B7480),
            lineHeight = 20.sp,
        ),
    )
}

/**
 * 渲染整行圆角消息块。
 */
@Composable
private fun BubbleBlock(
    text: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
) {
    BubbleBlock(
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 14.sp,
                lineHeight = 24.sp,
                color = contentColor,
            ),
        )
    }
}

/**
 * 渲染支持自定义内容的整行圆角消息块。
 */
@Composable
private fun BubbleBlock(
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val borderModifier = if (borderColor != null) {
        Modifier.border(width = 1.dp, color = borderColor, shape = shape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(color = containerColor, shape = shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        androidx.compose.material3.ProvideTextStyle(
            value = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
            content = content,
        )
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

/**
 * 聊天消息正文直接显示内容，不再拼接角色前缀。
 */
internal fun buildChatMessageText(item: ChatMessageItem): String = item.message.content

/**
 * 思考块标题固定保留 Thinking 文案。
 */
internal fun buildReasoningHeadline(item: ReasoningItem): String =
    if (item.isStreaming) "Thinking: 思考中..." else "Thinking:"
