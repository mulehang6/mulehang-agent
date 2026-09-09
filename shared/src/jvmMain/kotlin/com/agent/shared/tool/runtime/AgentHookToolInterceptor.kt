package com.agent.shared.tool.runtime

import com.agent.shared.agent.hook.AgentHookDecision
import com.agent.shared.agent.hook.AgentHookDispatchRequest
import com.agent.shared.agent.hook.AgentHookDispatcher
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.ToolRisk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 已通过 PreToolUse 检查的工具参数，以及 Hook 追加的模型上下文。 */
internal data class HookedToolInput(
    val input: JsonObject,
    val additionalContext: String? = null,
    val forceManualApproval: Boolean = false,
) {
    /** Hook 给出的附加上下文会随工具结果返回给模型。 */
    fun attachContext(result: String): String = additionalContext
        ?.takeIf(String::isNotBlank)
        ?.let { context -> "$result\n\n[Hook context]\n$context" }
        ?: result

    /** 取更新后的字符串参数；类型不匹配时安全地回退模型原始参数。 */
    fun string(name: String, fallback: String): String =
        input[name]?.jsonPrimitive?.contentOrNull ?: fallback

    /** 取更新后的 Int 参数；类型不匹配时安全地回退模型原始参数。 */
    fun int(name: String, fallback: Int): Int = input[name]?.jsonPrimitive?.intOrNull ?: fallback

    /** 取更新后的 Long 参数；类型不匹配时安全地回退模型原始参数。 */
    fun long(name: String, fallback: Long): Long = input[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: fallback

    /** 取更新后的 Boolean 参数；类型不匹配时安全地回退模型原始参数。 */
    fun boolean(name: String, fallback: Boolean): Boolean = input[name]?.jsonPrimitive?.booleanOrNull ?: fallback

    /** 取更新后的字符串数组参数；非字符串元素被忽略。 */
    fun strings(name: String, fallback: List<String>): List<String> =
        (input[name] as? JsonArray)
            ?.mapNotNull { value -> value.jsonPrimitive.contentOrNull }
            ?: fallback
}

/**
 * 将 PreToolUse 的同步 Hook 协议适配到 Koog 的普通工具函数。
 *
 * `ask` 时复用应用现有审批桥接，`block` 和 `deny` 则在工具执行前直接失败，避免副作用发生。
 */
internal class AgentHookToolInterceptor(
    private val dispatcher: AgentHookDispatcher,
    private val interactionBridge: DesktopToolInteractionBridge,
    private val sessionId: String,
    private val workspacePath: String,
) {
    /** 运行 PreToolUse，必要时请求用户确认，并返回可替换的工具参数。 */
    fun intercept(
        toolName: String,
        input: JsonObject,
        hasNativeApproval: Boolean = false,
    ): HookedToolInput {
        val result = runBlocking {
            dispatcher.dispatch(
                AgentHookDispatchRequest(
                    event = com.agent.shared.settings.model.AgentHookEvent.PRE_TOOL_USE,
                    sessionId = sessionId,
                    workspacePath = workspacePath,
                    matcherValue = toolName,
                    payload = input,
                ),
            )
        }
        when (result.decision) {
            AgentHookDecision.BLOCK -> error("Hook 已阻止工具调用：$toolName")
            AgentHookDecision.ASK -> if (!hasNativeApproval) requestApproval(toolName, result.updatedInput ?: input)
            AgentHookDecision.ALLOW,
            AgentHookDecision.CONTINUE,
                -> Unit
        }
        return HookedToolInput(
            input = result.updatedInput ?: input,
            additionalContext = result.additionalContext,
            forceManualApproval = hasNativeApproval && result.decision == AgentHookDecision.ASK,
        )
    }

    /** 在原生权限预设与审批器决策前执行 PermissionRequest Hook。 */
    fun inspectPermission(
        toolName: String,
        summary: String,
        targetPath: String = "",
        payloadPreview: String? = null,
    ): AgentHookDecision = runBlocking {
        dispatcher.dispatch(
            AgentHookDispatchRequest(
                event = com.agent.shared.settings.model.AgentHookEvent.PERMISSION_REQUEST,
                sessionId = sessionId,
                workspacePath = workspacePath,
                matcherValue = toolName,
                payload = buildJsonObject {
                    put("tool_name", toolName)
                    put("summary", summary)
                    put("target_path", targetPath)
                    payloadPreview?.let { value -> put("payload", value) }
                },
            ),
        ).decision
    }

    /** 将 Hook 的 ask 决策转换为统一的原生审批卡片。 */
    private fun requestApproval(toolName: String, input: JsonObject) {
        val approved = runBlocking {
            interactionBridge.requestApproval(
                ApprovalRequest(
                    requestId = UUID.randomUUID().toString(),
                    toolName = toolName,
                    summary = "Hook 请求确认工具调用",
                    payloadPreview = input.toString(),
                    risk = ToolRisk.UNKNOWN,
                    forceManual = true,
                ),
            )
        }
        check(approved) { "用户已拒绝 Hook 请求的工具调用。" }
    }
}
