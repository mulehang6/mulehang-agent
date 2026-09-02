package com.agent.shared.settings.model

import kotlinx.serialization.Serializable

/** 扩展包来源类型；Git 包需先经 GUI 安装到受控目录，运行时不会隐式拉取。 */
@Serializable
enum class AgentExtensionSourceType {
    LOCAL,
    GIT,
}

/** GUI 管理的一条扩展包记录。 */
@Serializable
data class AgentExtensionPackageSettings(
    val id: String,
    val source: String,
    val sourceType: AgentExtensionSourceType = AgentExtensionSourceType.LOCAL,
    val installedPath: String? = null,
    val enabled: Boolean = true,
)

/**
 * settings.json 中的资源运行时配置。
 *
 * `.agents` 本身不需要在这里配置：它始终只作为 Pi 兼容的 Skill 根。项目信任记录保存在
 * 用户级文档，由桌面 GUI 显式维护，避免项目配置自我授权执行本地扩展。
 */
@Serializable
data class AgentResourceSettings(
    val extensionPackages: List<AgentExtensionPackageSettings> = emptyList(),
    val trustedProjectPaths: List<String> = emptyList(),
    val skillDirectories: List<String> = emptyList(),
    val promptDirectories: List<String> = emptyList(),
)
