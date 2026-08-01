package com.agent.shared.tool.runtime

import ai.koog.agents.core.tools.ToolRegistry
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.PermissionPreset

/**
 * 根据当前会话上下文创建桌面工具注册表。
 */
class DesktopToolRegistryFactory(
    private val workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
    private val isCancelled: () -> Boolean = { false },
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
            ),
        )
    }
}
