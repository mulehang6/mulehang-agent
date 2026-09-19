package com.agent.app.chat.component

import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.model.AgentExtensionPackageSettings

/** 把本地目录名转为稳定、可保存的包 id，并在当前范围内消除冲突。 */
internal fun suggestedExtensionId(
    directory: String,
    existing: List<AgentExtensionPackageSettings>,
): String {
    val base = directory.substringAfterLast('\\').substringAfterLast('/')
        .replace(Regex("[^A-Za-z0-9._-]"), "-")
        .trim('-', '.', '_')
        .ifBlank { "extension" }
    var candidate = base
    var suffix = 2
    while (existing.any { setting -> setting.id == candidate }) {
        candidate = "$base-$suffix"
        suffix += 1
    }
    return candidate
}

/** 在一个 settings 文档中新增或按 id 替换扩展包记录。 */
internal fun SettingsDocument.withExtensionPackage(extension: AgentExtensionPackageSettings): SettingsDocument = copy(
    agentResources = agentResources.copy(
        extensionPackages = agentResources.extensionPackages
            .filterNot { setting -> setting.id == extension.id } + extension,
    ),
)

/** 更新指定 id 的扩展包；找不到时保持原文档不变。 */
internal fun SettingsDocument.withUpdatedExtension(
    id: String,
    transform: (AgentExtensionPackageSettings) -> AgentExtensionPackageSettings,
): SettingsDocument = copy(
    agentResources = agentResources.copy(
        extensionPackages = agentResources.extensionPackages.map { setting ->
            if (setting.id == id) transform(setting) else setting
        },
    ),
)

/** 仅从配置记录中移除指定扩展包，不触碰其本地安装目录。 */
internal fun SettingsDocument.withoutExtension(id: String): SettingsDocument = copy(
    agentResources = agentResources.copy(
        extensionPackages = agentResources.extensionPackages.filterNot { setting -> setting.id == id },
    ),
)

/** 在相应设置范围加入规范化后的附加资源目录，重复输入不会产生多个相同项。 */
internal fun SettingsDocument.withResourceDirectory(
    kind: ResourceDirectoryKind,
    directory: String,
): SettingsDocument {
    val normalized = directory.trim().takeIf(String::isNotBlank) ?: return this
    val updatedResources = when (kind) {
        ResourceDirectoryKind.SKILL -> agentResources.copy(
            skillDirectories = (agentResources.skillDirectories + normalized).distinct(),
        )

        ResourceDirectoryKind.PROMPT -> agentResources.copy(
            promptDirectories = (agentResources.promptDirectories + normalized).distinct(),
        )
    }
    return copy(agentResources = updatedResources)
}

/** 从相应设置范围移除附加资源目录，不删除用户选择的真实文件夹。 */
internal fun SettingsDocument.withoutResourceDirectory(
    kind: ResourceDirectoryKind,
    directory: String,
): SettingsDocument = copy(
    agentResources = when (kind) {
        ResourceDirectoryKind.SKILL -> agentResources.copy(
            skillDirectories = agentResources.skillDirectories.filterNot { it == directory },
        )

        ResourceDirectoryKind.PROMPT -> agentResources.copy(
            promptDirectories = agentResources.promptDirectories.filterNot { it == directory },
        )
    },
)
