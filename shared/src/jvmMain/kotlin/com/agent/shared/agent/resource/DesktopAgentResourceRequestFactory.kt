package com.agent.shared.agent.resource

import com.agent.shared.settings.model.AgentExtensionPackageSettings
import com.agent.shared.settings.model.AgentExtensionSourceType
import com.agent.shared.settings.model.AgentResourceSettings
import com.agent.shared.settings.model.SettingsDocument
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 将双层 settings.json 转换为资源加载请求。
 *
 * 项目文档可声明资源，却不能把自己加入信任列表。信任只从用户级设置读取，因此克隆一个项目
 * 或打开陌生工作区不会自动执行其包声明。
 */
class DesktopAgentResourceRequestFactory(
    private val userHome: Path,
) {
    /** 根据当前工作区和原始用户/项目设置构建一次资源加载请求。 */
    fun create(
        workspacePath: Path?,
        userDocument: SettingsDocument,
        projectDocument: SettingsDocument,
    ): AgentResourceLoadRequest {
        val workspace = workspacePath?.normalizedExistingOrAbsolute()
        return AgentResourceLoadRequest(
            userHome = userHome,
            workspacePath = workspace,
            projectTrusted = workspace != null && userDocument.agentResources.trusts(workspace),
            userSkillDirectories = userDocument.agentResources.skillDirectories.toPaths(),
            projectSkillDirectories = projectDocument.agentResources.skillDirectories.toPaths(),
            userPromptDirectories = userDocument.agentResources.promptDirectories.toPaths(),
            projectPromptDirectories = projectDocument.agentResources.promptDirectories.toPaths(),
            packages = buildList {
                addAll(userDocument.agentResources.toInstalledPackages(userHome, AgentResourceOrigin.USER_CONFIGURATION))
                addAll(projectDocument.agentResources.toInstalledPackages(workspace, AgentResourceOrigin.PROJECT_CONFIGURATION))
            },
        )
    }

    /** 返回用户级 setting 中添加某工作区信任后的新文档，不改变其他资源配置。 */
    fun withProjectTrust(
        userDocument: SettingsDocument,
        workspacePath: Path,
        trusted: Boolean,
    ): SettingsDocument {
        val identity = workspacePath.normalizedIdentity()
        val current = userDocument.agentResources.trustedProjectPaths
            .mapNotNull { raw -> runCatching { Paths.get(raw).normalizedIdentity() }.getOrNull() }
            .toMutableSet()
        if (trusted) current += identity else current -= identity
        return userDocument.copy(
            agentResources = userDocument.agentResources.copy(
                trustedProjectPaths = current.sorted(),
            ),
        )
    }
}

/** 判断当前用户级信任列表是否包含指定工作区的真实路径。 */
private fun AgentResourceSettings.trusts(workspace: Path): Boolean {
    val identity = workspace.normalizedIdentity()
    return trustedProjectPaths.any { raw ->
        runCatching { Paths.get(raw).normalizedIdentity() == identity }.getOrDefault(false)
    }
}

/** 将 GUI 输入的路径列表过滤为空白和非法路径后的稳定绝对路径。 */
private fun List<String>.toPaths(): List<Path> = mapNotNull { raw ->
    raw.trim().takeIf(String::isNotBlank)?.let { value -> runCatching { Paths.get(value) }.getOrNull() }
}

/** 将一个 settings scope 的包配置映射到运行时可读取的安装根。 */
private fun AgentResourceSettings.toInstalledPackages(
    managedBase: Path?,
    origin: AgentResourceOrigin,
): List<InstalledAgentExtensionPackage> = extensionPackages.mapNotNull { setting ->
    setting.toInstalledPackage(managedBase, origin)
}

/** Git 包尚未安装时仍返回受控预期路径，让扩展中心展示“目录不存在”诊断而非隐式克隆。 */
private fun AgentExtensionPackageSettings.toInstalledPackage(
    managedBase: Path?,
    origin: AgentResourceOrigin,
): InstalledAgentExtensionPackage? {
    if (id.isBlank()) return null
    val root = installedPath?.takeIf(String::isNotBlank)
        ?: when (sourceType) {
            AgentExtensionSourceType.LOCAL -> source.takeIf(String::isNotBlank)
            AgentExtensionSourceType.GIT -> managedBase?.resolve(".mulehang/extensions/$id")?.toString()
        }
        ?: return null
    return runCatching {
        InstalledAgentExtensionPackage(
            id = id,
            root = Paths.get(root),
            enabled = enabled,
            origin = origin,
        )
    }.getOrNull()
}
