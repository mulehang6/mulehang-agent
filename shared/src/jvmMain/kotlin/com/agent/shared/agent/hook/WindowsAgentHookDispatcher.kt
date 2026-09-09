package com.agent.shared.agent.hook

import com.agent.shared.settings.model.AgentHookCommand
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.settings.model.AgentHookMatcher
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.tool.runtime.DesktopProcessRunner
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Hook 可以要求调用方继续、自动放行、转人工确认或阻断本次动作。 */
enum class AgentHookDecision {
    CONTINUE,
    ALLOW,
    ASK,
    BLOCK,
}

/** 一次 Hook 调用的上下文；payload 会以 JSON 通过 stdin 传给命令。 */
data class AgentHookDispatchRequest(
    val event: AgentHookEvent,
    val sessionId: String,
    val workspacePath: String,
    val matcherValue: String? = null,
    val payload: JsonObject = buildJsonObject { },
)

/** Hook 命令返回给运行时的可执行结果。 */
data class AgentHookDispatchResult(
    val decision: AgentHookDecision = AgentHookDecision.CONTINUE,
    val additionalContext: String? = null,
    val updatedInput: JsonObject? = null,
)

/** 隔离 Hook 运行时，便于本地工具与 Agent 生命周期共享同一套策略。 */
interface AgentHookDispatcher {
    /** 按配置顺序运行匹配的 Hook，并返回合并后的执行结果。 */
    suspend fun dispatch(request: AgentHookDispatchRequest): AgentHookDispatchResult
}

/** 未配置 Hook 时的零开销实现。 */
object NoAgentHookDispatcher : AgentHookDispatcher {
    override suspend fun dispatch(request: AgentHookDispatchRequest): AgentHookDispatchResult = AgentHookDispatchResult()
}

/** 子进程执行结果，供 Hook 解析逻辑和单元测试共用。 */
data class AgentHookCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean,
)

/** 执行 Hook 命令的窄接口，避免测试依赖真实 Windows Shell。 */
fun interface AgentHookCommandExecutor {
    /** 在给定工作目录执行一条 `cmd.exe` 命令，并把 input 写入 stdin。 */
    fun execute(
        command: String,
        workingDirectory: File,
        input: String,
        timeoutMillis: Long,
    ): AgentHookCommandResult
}

/** 用 Windows cmd 执行用户全局设置中的 Hook 命令。 */
class WindowsAgentHookCommandExecutor(
    private val processRunner: DesktopProcessRunner = DesktopProcessRunner(HOOK_OUTPUT_LIMIT_BYTES),
) : AgentHookCommandExecutor {
    /** 执行一条 Hook 命令并保留 stdout 与 stderr，以供事件语义判定。 */
    override fun execute(
        command: String,
        workingDirectory: File,
        input: String,
        timeoutMillis: Long,
    ): AgentHookCommandResult {
        val result = processRunner.run(
            DesktopProcessRunner.Args(
                command = listOf("cmd.exe", "/d", "/s", "/c", command),
                workingDirectory = workingDirectory,
                timeoutMillis = timeoutMillis,
                standardInput = input,
            ),
        )
        return AgentHookCommandResult(
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode,
            timedOut = result.outcome == DesktopProcessRunner.Outcome.TIMED_OUT,
        )
    }

    private companion object {
        const val HOOK_OUTPUT_LIMIT_BYTES = 64 * 1024
    }
}

/**
 * 执行 Junie 事件兼容的命令 Hook。
 *
 * 只接受用户级设置传入的 [settings]。异步命令不参与本轮决策，顺序命令则以最后一个明确
 * 决策为准，且 `BLOCK` 一旦出现立即停止后续命令。
 */
class WindowsAgentHookDispatcher(
    private val settings: AgentHookSettings,
    private val commandExecutor: AgentHookCommandExecutor = WindowsAgentHookCommandExecutor(),
    private val onDiagnostic: (String) -> Unit = {},
) : AgentHookDispatcher {
    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 运行当前事件全部匹配项，并将 JSON 输出转换为可执行结果。 */
    override suspend fun dispatch(request: AgentHookDispatchRequest): AgentHookDispatchResult {
        val input = encodeInput(request)
        var decision = AgentHookDecision.CONTINUE
        var additionalContext: String? = null
        var updatedInput: JsonObject? = null
        var remainingSessionEndTimeout = SESSION_END_TOTAL_TIMEOUT_MILLIS

        val commands = settings.hooks[request.event]
            .orEmpty()
            .filter { matcher -> matcherMatches(matcher, request.matcherValue) }
            .flatMap(AgentHookMatcher::hooks)
        for (command in commands) {
            if (decision == AgentHookDecision.BLOCK) break
            if (command.type != COMMAND_TYPE) {
                onDiagnostic("Hook 已忽略：不支持 type=${command.type}。")
                continue
            }
            if (command.command.isBlank()) {
                onDiagnostic("Hook 已忽略：command 不能为空。")
                continue
            }
            val timeoutMillis = commandTimeoutMillis(
                command = command,
                event = request.event,
                remainingSessionEndTimeout = remainingSessionEndTimeout,
            )
            if (timeoutMillis <= 0L) {
                onDiagnostic("Hook 已跳过：SessionEnd 的总超时已耗尽。")
                continue
            }
            if (command.runAsync) {
                asyncScope.launch {
                    runCatching { execute(command, request, input, timeoutMillis) }
                        .onFailure { error -> onDiagnostic("异步 Hook 执行失败：${error.message ?: "未知错误"}") }
                }
                continue
            }
            val startedAt = System.nanoTime()
            val result = runCatching { execute(command, request, input, timeoutMillis) }
                .getOrElse { error ->
                    onDiagnostic("Hook 命令执行失败：${error.message ?: "未知错误"}")
                    executionFailureResult(request.event, command)
                }
            if (request.event == AgentHookEvent.SESSION_END) {
                remainingSessionEndTimeout -= elapsedMillis(startedAt)
            }
            if (result.additionalContext != null) additionalContext = result.additionalContext
            if (result.updatedInput != null) updatedInput = result.updatedInput
            if (result.decision != AgentHookDecision.CONTINUE) decision = result.decision
        }
        return AgentHookDispatchResult(
            decision = decision,
            additionalContext = additionalContext,
            updatedInput = updatedInput,
        )
    }

    /** 执行单条命令，并根据事件与退出码解析 Junie 兼容的阻断语义。 */
    private fun execute(
        command: AgentHookCommand,
        request: AgentHookDispatchRequest,
        input: String,
        timeoutMillis: Long,
    ): AgentHookDispatchResult {
        val result = commandExecutor.execute(
            command = command.command,
            workingDirectory = workspaceDirectory(request.workspacePath),
            input = input,
            timeoutMillis = timeoutMillis,
        )
        if (result.timedOut) {
            return timeoutResult(request.event, command)
        }
        val output = parseOutput(result.stdout)
        val exitDecision = exitDecision(request.event, command, result.exitCode)
        val outputDecision = output.decision
        val decision = when {
            exitDecision == AgentHookDecision.BLOCK -> AgentHookDecision.BLOCK
            outputDecision == AgentHookDecision.BLOCK -> AgentHookDecision.BLOCK
            outputDecision != AgentHookDecision.CONTINUE -> outputDecision
            else -> exitDecision
        }
        if (result.exitCode != null && result.exitCode != 0 && decision == AgentHookDecision.CONTINUE) {
            onDiagnostic("Hook 命令以退出码 ${result.exitCode} 结束：${result.stderr.ifBlank { result.stdout }.trim().take(DIAGNOSTIC_TEXT_LIMIT)}")
        }
        return AgentHookDispatchResult(
            decision = decision,
            additionalContext = output.additionalContext,
            updatedInput = output.updatedInput,
        )
    }

    /** 命令超时时，Stop 可由 blockOnError 阻断；其他事件保留默认继续或人工确认语义。 */
    private fun timeoutResult(
        event: AgentHookEvent,
        command: AgentHookCommand,
    ): AgentHookDispatchResult {
        onDiagnostic("Hook 命令超时。")
        val decision = when (event) {
            AgentHookEvent.PERMISSION_REQUEST -> AgentHookDecision.ASK
            AgentHookEvent.STOP -> if (command.blockOnError) AgentHookDecision.BLOCK else AgentHookDecision.CONTINUE
            else -> AgentHookDecision.CONTINUE
        }
        return AgentHookDispatchResult(decision = decision)
    }

    /** 进程无法启动等运行错误沿用超时的安全回退，绝不把异常传播给主 Agent 会话。 */
    private fun executionFailureResult(
        event: AgentHookEvent,
        command: AgentHookCommand,
    ): AgentHookDispatchResult = when (event) {
        AgentHookEvent.PERMISSION_REQUEST -> AgentHookDispatchResult(decision = AgentHookDecision.ASK)
        AgentHookEvent.STOP if command.blockOnError -> AgentHookDispatchResult(decision = AgentHookDecision.BLOCK)
        else -> AgentHookDispatchResult()
    }

    /** 根据事件和退出码实现 PreToolUse、PermissionRequest、Stop 的特殊规则。 */
    private fun exitDecision(
        event: AgentHookEvent,
        command: AgentHookCommand,
        exitCode: Int?,
    ): AgentHookDecision {
        if (exitCode == null || exitCode == 0) return AgentHookDecision.CONTINUE
        return when (event) {
            AgentHookEvent.PRE_TOOL_USE -> if (exitCode == PRE_TOOL_BLOCK_EXIT_CODE) AgentHookDecision.BLOCK else AgentHookDecision.CONTINUE
            AgentHookEvent.PERMISSION_REQUEST -> AgentHookDecision.ASK
            AgentHookEvent.STOP -> if (command.blockOnError) AgentHookDecision.BLOCK else AgentHookDecision.CONTINUE
            else -> AgentHookDecision.CONTINUE
        }
    }

    /** 输出为 JSON 对象时读取 Junie 约定字段，否则将非空 stdout 作为附加上下文。 */
    private fun parseOutput(stdout: String): ParsedOutput {
        val text = stdout.trim()
        if (text.isEmpty()) return ParsedOutput()
        val objectOutput = runCatching { HOOK_JSON.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return ParsedOutput(additionalContext = text.take(ADDITIONAL_CONTEXT_LIMIT))
        val decision = objectOutput["decision"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.lowercase()
            ?.let(::parseDecision)
            ?: objectOutput["continue"]
                ?.jsonPrimitive
                ?.booleanOrNull
                ?.let { continueRunning -> if (continueRunning) AgentHookDecision.CONTINUE else AgentHookDecision.BLOCK }
            ?: AgentHookDecision.CONTINUE
        return ParsedOutput(
            decision = decision,
            additionalContext = objectOutput["additionalContext"]?.jsonPrimitive?.contentOrNull?.take(ADDITIONAL_CONTEXT_LIMIT),
            updatedInput = objectOutput["updatedInput"] as? JsonObject,
        )
    }

    /** 匹配器错误不影响主会话，写入诊断后跳过该规则。 */
    private fun matcherMatches(matcher: AgentHookMatcher, value: String?): Boolean {
        val expression = matcher.matcher?.trim().orEmpty()
        return expression.isEmpty() || runCatching { Regex(expression).containsMatchIn(value.orEmpty()) }
            .onFailure { error -> onDiagnostic("Hook matcher 无效：${error.message ?: expression}") }
            .getOrDefault(false)
    }

    /** 生成传给 Hook 命令的稳定 JSON 输入。 */
    private fun encodeInput(request: AgentHookDispatchRequest): String = HOOK_JSON.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("session_id", request.sessionId)
            put("hook_event", request.event.wireValue)
            request.matcherValue?.let { value -> put("matcher_value", value) }
            put("input", request.payload)
        },
    )

    /** 未选择工作区时，使用当前进程目录以保持 Hook 可执行。 */
    private fun workspaceDirectory(workspacePath: String): File =
        File(workspacePath).takeIf(File::isDirectory) ?: File(System.getProperty("user.dir"))

    /** 命令超时按 Junie 当前事件默认值解析，SessionEnd 还受总预算限制。 */
    private fun commandTimeoutMillis(
        command: AgentHookCommand,
        event: AgentHookEvent,
        remainingSessionEndTimeout: Long,
    ): Long {
        val configuredMillis = (command.timeout ?: event.defaultTimeoutSeconds) * MILLIS_PER_SECOND
        return if (event == AgentHookEvent.SESSION_END) {
            minOf(configuredMillis, remainingSessionEndTimeout)
        } else {
            configuredMillis
        }
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLI

    private data class ParsedOutput(
        val decision: AgentHookDecision = AgentHookDecision.CONTINUE,
        val additionalContext: String? = null,
        val updatedInput: JsonObject? = null,
    )

    private companion object {
        const val COMMAND_TYPE = "command"
        const val PRE_TOOL_BLOCK_EXIT_CODE = 2
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val SESSION_END_TOTAL_TIMEOUT_MILLIS = 10_000L
        const val ADDITIONAL_CONTEXT_LIMIT = 16_000
        const val DIAGNOSTIC_TEXT_LIMIT = 500
        val HOOK_JSON = Json { ignoreUnknownKeys = true }
    }
}

/** 返回当前事件在 Junie 中使用的稳定名称。 */
private val AgentHookEvent.wireValue: String
    get() = when (this) {
        AgentHookEvent.SESSION_START -> "SessionStart"
        AgentHookEvent.USER_PROMPT_SUBMIT -> "UserPromptSubmit"
        AgentHookEvent.PRE_TOOL_USE -> "PreToolUse"
        AgentHookEvent.STOP -> "Stop"
        AgentHookEvent.STOP_FAILURE -> "StopFailure"
        AgentHookEvent.PERMISSION_REQUEST -> "PermissionRequest"
        AgentHookEvent.SESSION_END -> "SessionEnd"
    }

/** 事件默认超时，单位为秒，按 Junie 当前公开行为保持一致。 */
private val AgentHookEvent.defaultTimeoutSeconds: Int
    get() = when (this) {
        AgentHookEvent.SESSION_START,
        AgentHookEvent.USER_PROMPT_SUBMIT,
        AgentHookEvent.PERMISSION_REQUEST,
            -> 10
        AgentHookEvent.STOP -> 600
        AgentHookEvent.STOP_FAILURE -> 60
        AgentHookEvent.SESSION_END -> 2
        AgentHookEvent.PRE_TOOL_USE -> 60
    }

/** 解析允许的 JSON decision 值，未知值安全地回退为继续。 */
private fun parseDecision(value: String): AgentHookDecision = when (value) {
    "allow" -> AgentHookDecision.ALLOW
    "ask" -> AgentHookDecision.ASK
    "block", "deny" -> AgentHookDecision.BLOCK
    else -> AgentHookDecision.CONTINUE
}
