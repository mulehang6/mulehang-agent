package com.agent.shared.agent.hook

import com.agent.shared.settings.model.AgentHookCommand
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.settings.model.AgentHookMatcher
import com.agent.shared.settings.model.AgentHookSettings
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 验证 Hook 事件匹配、命令退出码和 JSON 输出的运行时语义。 */
class WindowsAgentHookDispatcherTest {
    /** PreToolUse 的 JSON 可以放行调用、附加上下文，并替换完整工具输入。 */
    @Test
    fun `pre tool hook should apply json decision and updated input`() = runTest {
        val dispatcher = dispatcher(
            event = AgentHookEvent.PRE_TOOL_USE,
            matcher = "apply_patch",
            result = AgentHookCommandResult(
                stdout = """{"decision":"allow","additionalContext":"先保留原文件头。","updatedInput":{"patch_text":"*** Begin Patch"}}""",
                stderr = "",
                exitCode = 0,
                timedOut = false,
            ),
        )

        val result = dispatcher.dispatch(
            request(event = AgentHookEvent.PRE_TOOL_USE, matcherValue = "apply_patch"),
        )

        assertEquals(AgentHookDecision.ALLOW, result.decision)
        assertEquals("先保留原文件头。", result.additionalContext)
        assertEquals("*** Begin Patch", result.updatedInput?.get("patch_text")?.toString()?.trim('"'))
    }

    /** PreToolUse 以退出码 2 结束时必须阻断，非零的普通退出码只保留原工具流程。 */
    @Test
    fun `pre tool hook should block only on exit code two`() = runTest {
        val blocked = dispatcher(
            event = AgentHookEvent.PRE_TOOL_USE,
            result = AgentHookCommandResult("", "", 2, timedOut = false),
        ).dispatch(request(AgentHookEvent.PRE_TOOL_USE))
        val continued = dispatcher(
            event = AgentHookEvent.PRE_TOOL_USE,
            result = AgentHookCommandResult("", "普通错误", 1, timedOut = false),
        ).dispatch(request(AgentHookEvent.PRE_TOOL_USE))

        assertEquals(AgentHookDecision.BLOCK, blocked.decision)
        assertEquals(AgentHookDecision.CONTINUE, continued.decision)
    }

    /** PermissionRequest 的错误或超时不能隐式放行，必须退回原生人工审批。 */
    @Test
    fun `permission hook should ask after command error or timeout`() = runTest {
        val commandError = dispatcher(
            event = AgentHookEvent.PERMISSION_REQUEST,
            result = AgentHookCommandResult("", "失败", 1, timedOut = false),
        ).dispatch(request(AgentHookEvent.PERMISSION_REQUEST))
        val timeout = dispatcher(
            event = AgentHookEvent.PERMISSION_REQUEST,
            result = AgentHookCommandResult("", "", null, timedOut = true),
        ).dispatch(request(AgentHookEvent.PERMISSION_REQUEST))

        assertEquals(AgentHookDecision.ASK, commandError.decision)
        assertEquals(AgentHookDecision.ASK, timeout.decision)
    }

    /** Hook 子进程无法启动时不能让主会话失败，权限事件仍需回退人工确认。 */
    @Test
    fun `hook command startup failure should use safe event fallback`() = runTest {
        val settings = AgentHookSettings(
            hooks = mapOf(
                AgentHookEvent.PRE_TOOL_USE to listOf(AgentHookMatcher(hooks = listOf(AgentHookCommand(command = "missing")))),
                AgentHookEvent.PERMISSION_REQUEST to listOf(AgentHookMatcher(hooks = listOf(AgentHookCommand(command = "missing")))),
            ),
        )
        val dispatcher = WindowsAgentHookDispatcher(
            settings = settings,
            commandExecutor = { _: String, _: File, _: String, _: Long -> error("程序不存在") },
        )

        val preTool = dispatcher.dispatch(request(AgentHookEvent.PRE_TOOL_USE))
        val permission = dispatcher.dispatch(request(AgentHookEvent.PERMISSION_REQUEST))

        assertEquals(AgentHookDecision.CONTINUE, preTool.decision)
        assertEquals(AgentHookDecision.ASK, permission.decision)
    }

    /** Stop 仅在明确配置 blockOnError 时把 Hook 命令错误转为继续请求。 */
    @Test
    fun `stop hook should honor block on error`() = runTest {
        val blocking = dispatcher(
            event = AgentHookEvent.STOP,
            command = AgentHookCommand(command = "hook", blockOnError = true),
            result = AgentHookCommandResult("", "失败", 1, timedOut = false),
        ).dispatch(request(AgentHookEvent.STOP))
        val nonBlocking = dispatcher(
            event = AgentHookEvent.STOP,
            command = AgentHookCommand(command = "hook", blockOnError = false),
            result = AgentHookCommandResult("", "失败", 1, timedOut = false),
        ).dispatch(request(AgentHookEvent.STOP))

        assertEquals(AgentHookDecision.BLOCK, blocking.decision)
        assertEquals(AgentHookDecision.CONTINUE, nonBlocking.decision)
    }

    /** 不匹配 matcher 时不执行命令，也不会从其输出注入上下文。 */
    @Test
    fun `matcher should skip unrelated tool`() = runTest {
        val dispatcher = dispatcher(
            event = AgentHookEvent.PRE_TOOL_USE,
            matcher = "run_powershell",
            result = AgentHookCommandResult("ignored", "", 0, timedOut = false),
        )

        val result = dispatcher.dispatch(request(AgentHookEvent.PRE_TOOL_USE, matcherValue = "read_file"))

        assertEquals(AgentHookDecision.CONTINUE, result.decision)
        assertNull(result.additionalContext)
    }

    /** 构造可预测的 dispatcher，不调用真实 Windows Shell。 */
    private fun dispatcher(
        event: AgentHookEvent,
        matcher: String? = null,
        command: AgentHookCommand = AgentHookCommand(command = "hook"),
        result: AgentHookCommandResult,
    ): WindowsAgentHookDispatcher = WindowsAgentHookDispatcher(
        settings = AgentHookSettings(
            hooks = mapOf(event to listOf(AgentHookMatcher(matcher = matcher, hooks = listOf(command)))),
        ),
        commandExecutor = { _: String, _: File, _: String, _: Long -> result },
    )

    /** 创建具有最小上下文的 Hook 调用。 */
    private fun request(
        event: AgentHookEvent,
        matcherValue: String? = null,
    ): AgentHookDispatchRequest = AgentHookDispatchRequest(
        event = event,
        sessionId = "session-1",
        workspacePath = ".",
        matcherValue = matcherValue,
        payload = buildJsonObject { put("value", "test") },
    )
}
