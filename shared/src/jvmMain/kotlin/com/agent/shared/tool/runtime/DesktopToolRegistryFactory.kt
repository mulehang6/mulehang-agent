package com.agent.shared.tool.runtime

import ai.koog.agents.core.tools.ToolRegistry
import com.agent.shared.agent.status.AgentTodoRepository
import com.agent.shared.agent.hook.AgentHookDispatcher
import com.agent.shared.agent.hook.NoAgentHookDispatcher
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.PermissionPreset

/**
 * 根据当前会话上下文创建桌面工具注册表。
 */
class DesktopToolRegistryFactory(
    private val workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
    private val approvalAgent: ToolApprovalAgent = ManualFallbackToolApprovalAgent,
    private val isCancelled: () -> Boolean = { false },
    private val hookDispatcher: AgentHookDispatcher = NoAgentHookDispatcher,
    private val sessionId: String = "",
    private val fileMutationJournal: FileMutationJournal? = null,
    private val todoRepository: AgentTodoRepository? = null,
) {
    /**
     * 生成首批桌面工具注册表。
     */
    fun create(): ToolRegistry = ToolRegistry {
        tools(
            DesktopToolSet(
                workspacePath = workspacePath,
                permissionPreset = permissionPreset,
                interactionBridge = interactionBridge,
                isCancelled = isCancelled,
                approvalAgent = approvalAgent,
                hookDispatcher = hookDispatcher,
                sessionId = sessionId,
                fileMutationJournal = fileMutationJournal,
            ),
        )
        if (todoRepository != null && sessionId.isNotBlank()) {
            tools(AgentTodoToolSet(sessionId, todoRepository))
        }
    }
}
