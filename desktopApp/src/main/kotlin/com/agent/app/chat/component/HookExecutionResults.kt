package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.agent.app.design.AppMuted
import com.agent.shared.agent.hook.AgentHookExecutionHistory
import org.jetbrains.jewel.ui.component.Text

/** 只展示本次应用运行的有限诊断，不在设置中展示命令正文或输出。 */
@Composable
internal fun HookExecutionResults() {
    val recent by AgentHookExecutionHistory.recent.collectAsState()
    if (recent.isEmpty()) return
    ExtensionSettingsCard {
        Text("最近执行")
        recent.take(5).forEach { result ->
            val mode = if (result.asynchronous) "后台" else "同步"
            Text(
                "${hookEventLabel(result.event)} #${result.commandIndex + 1} · $mode · ${result.outcome} · ${result.durationMillis} ms",
                color = AppMuted,
            )
        }
    }
}
