package com.agent.shared.agent

import ai.koog.agents.core.tools.ToolRegistry
import com.agent.shared.state.PermissionPreset

/**
 * 根据当前会话上下文创建桌面工具注册表。
 */
class DesktopToolRegistryFactory(
    private val workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
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
            ),
        )
    }
}
