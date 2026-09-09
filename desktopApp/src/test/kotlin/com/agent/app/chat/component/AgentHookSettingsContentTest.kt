package com.agent.app.chat.component

import com.agent.shared.settings.model.AgentHookCommand
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.settings.model.AgentHookMatcher
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.settings.model.SettingsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 验证设置页在写入前会拒绝无法安全执行的 Hook 配置。 */
class AgentHookSettingsContentTest {
    /** 无效 matcher 不能写入，因为运行期不能可靠选择匹配命令。 */
    @Test
    fun `should reject invalid matcher`() {
        val validation = validateAgentHookSettings(
            document(event = AgentHookEvent.PRE_TOOL_USE, matcher = "[", command = AgentHookCommand(command = "echo ok")),
        )

        assertEquals("PreToolUse 的第 1 条 matcher 不是有效正则。", validation)
    }

    /** 命令和超时必须在保存前有效。 */
    @Test
    fun `should reject blank command and nonpositive timeout`() {
        val blankCommand = validateAgentHookSettings(
            document(event = AgentHookEvent.STOP, command = AgentHookCommand(command = "")),
        )
        val invalidTimeout = validateAgentHookSettings(
            document(event = AgentHookEvent.STOP, command = AgentHookCommand(command = "echo ok", timeout = 0)),
        )

        assertEquals("Stop 的第 1 条命令不能为空。", blankCommand)
        assertEquals("Stop 的命令超时必须大于 0。", invalidTimeout)
    }

    /** blockOnError 只代表 Stop 的继续语义，其他事件不能暗中改变流程。 */
    @Test
    fun `should allow block on error only for stop`() {
        val invalid = validateAgentHookSettings(
            document(
                event = AgentHookEvent.PERMISSION_REQUEST,
                command = AgentHookCommand(command = "echo ok", blockOnError = true),
            ),
        )
        val valid = validateAgentHookSettings(
            document(event = AgentHookEvent.STOP, command = AgentHookCommand(command = "echo ok", blockOnError = true)),
        )

        assertEquals("blockOnError 仅适用于 Stop Hook。", invalid)
        assertNull(valid)
    }

    /** 生成最小的单事件 settings 文档。 */
    private fun document(
        event: AgentHookEvent,
        matcher: String? = null,
        command: AgentHookCommand,
    ): SettingsDocument = SettingsDocument(
        hooks = AgentHookSettings(
            hooks = mapOf(event to listOf(AgentHookMatcher(matcher = matcher, hooks = listOf(command)))),
        ),
    )
}
