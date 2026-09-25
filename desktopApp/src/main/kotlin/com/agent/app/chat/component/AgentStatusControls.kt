package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.shared.agent.status.AgentTodoStatus
import com.agent.shared.chat.model.ExecutionState
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.component.Text
import kotlin.time.Duration.Companion.milliseconds

/** 输入框上方的只读 TODO 胶囊，展开内容向上生长。 */
@Composable
internal fun AgentTodoCapsule(conversation: ChatConversationUiState?) {
    val todos = conversation?.agentTodos.orEmpty()
    if (todos.isEmpty()) return
    var expanded by remember(conversation?.id) { mutableStateOf(false) }
    val reducedMotion = prefersReducedMotion()
    val done = todos.count { it.status == AgentTodoStatus.COMPLETED }
    val current = todos.firstOrNull { it.status == AgentTodoStatus.IN_PROGRESS }
        ?: todos.firstOrNull { it.status == AgentTodoStatus.PENDING }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = expanded,
            enter = if (reducedMotion) EnterTransition.None else expandVertically(tween(180)) + fadeIn(tween(180)),
            exit = if (reducedMotion) ExitTransition.None else shrinkVertically(tween(110)) + fadeOut(tween(110)),
        ) {
            JewelSurface(
                role = JewelSurfaceRole.CHROME,
                radius = 12.dp,
                solidColor = DetailIslandsOuterBackground,
                borderColor = AppLine,
                modifier = Modifier.widthIn(max = 480.dp).padding(bottom = 6.dp),
            ) {
                Column(
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    todos.forEach { item ->
                        Text(
                            text = "${if (item.status == AgentTodoStatus.COMPLETED) "✓" else "○"} ${item.content}",
                            color = if (item.status == AgentTodoStatus.COMPLETED) AppMuted else AppText,
                        )
                    }
                }
            }
        }
        JewelSurface(
            role = JewelSurfaceRole.CHROME,
            radius = 999.dp,
            solidColor = DetailIslandBackground,
            borderColor = AppLine,
            modifier = Modifier.clickable { expanded = !expanded },
        ) {
            Text(
                text = "TODO $done/${todos.size}${current?.let { " · ${it.content.take(38)}" }.orEmpty()}  ${if (expanded) "⌄" else "⌃"}",
                color = AppText,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** 输入框下方的运行指标；点击时弹出与模型输入逐字一致的状态详情。 */
@Composable
internal fun AgentRunMetrics(conversation: ChatConversationUiState?) {
    val saved = conversation?.agentStatus ?: return
    val snapshot = saved.snapshot
    var now by remember(conversation.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var detailsOpen by remember(conversation.id) { mutableStateOf(false) }
    val running = conversation.executionState == ExecutionState.Running
    LaunchedEffect(conversation.id, running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1_000.milliseconds)
        }
    }
    val startedAt = snapshot.timestampMillis - snapshot.turnElapsedMillis
    val elapsed = if (running) (now - startedAt).coerceAtLeast(0L) else snapshot.turnElapsedMillis
    val seconds = elapsed / 1_000
    val label = buildString {
        append("工具 ${snapshot.toolCallCount}   耗时 ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")
        if (snapshot.errorCount > 0) append("   错误 ${snapshot.errorCount}")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(label, color = AppMuted, modifier = Modifier.clickable { detailsOpen = true }.padding(horizontal = 4.dp, vertical = 2.dp))
        if (detailsOpen) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = IntOffset(0, -32),
                onDismissRequest = { detailsOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                JewelSurface(
                    role = JewelSurfaceRole.CHROME,
                    radius = 12.dp,
                    solidColor = DetailIslandsOuterBackground,
                    borderColor = AppLine,
                    modifier = Modifier.widthIn(min = 300.dp, max = 520.dp),
                ) {
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("Agent 状态", color = AppText)
                        Text(saved.modelMessageText, color = AppMuted, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}
