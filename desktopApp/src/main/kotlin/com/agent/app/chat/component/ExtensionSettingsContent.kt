@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.app.platform.pickWorkspaceDirectory
import com.agent.shared.agent.resource.AgentExtensionPackageResource
import com.agent.shared.agent.resource.AgentMcpServerResource
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentSkillResource
import com.agent.shared.agent.resource.DesktopExtensionPackageInstaller
import com.agent.shared.settings.model.AgentExtensionPackageSettings
import com.agent.shared.settings.model.AgentExtensionSourceType
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/**
 * 受控扩展中心：用户可在用户或项目范围管理本地/Git 包、启用状态、项目资源信任、MCP 声明和
 * 解析诊断。Git 仅在这里明确操作时安装或更新，重新加载不会发生网络访问。
 */
@Composable
internal fun ExtensionSettingsContent(
    document: SettingsDocument,
    layer: ConfigLayer,
    projectRoot: Path?,
    userHome: Path,
    extensionPackages: List<AgentExtensionPackageResource>,
    loadedSkills: List<AgentSkillResource>,
    resourceDiagnostics: List<AgentResourceDiagnostic>,
    mcpServers: List<AgentMcpServerResource>,
    onDocumentChange: (SettingsDocument) -> Unit,
    onChangeNotification: (String) -> Unit,
    onReloadResources: () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val installer = remember { DesktopExtensionPackageInstaller() }
    var gitPackageId by remember { mutableStateOf("") }
    var gitSource by remember { mutableStateOf("") }
    var operationFeedback by remember { mutableStateOf<String?>(null) }
    val resources = document.agentResources

    GroupHeader("扩展中心")
    if (layer == ConfigLayer.USER && projectRoot != null) {
        ProjectResourceTrustCard(
            document = document,
            projectRoot = projectRoot,
            onDocumentChange = onDocumentChange,
            onChangeNotification = onChangeNotification,
        )
    }
    ExtensionSettingsCard {
        Text("本地扩展包", style = JewelTheme.defaultTextStyle.copy(color = AppText))
        Text(
            "选择含 package.json、skills/ 或 prompts/ 的目录。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        SettingsActionButton("添加本地目录", emphasized = true) {
            pickWorkspaceDirectory()?.let { directory ->
                val extension = AgentExtensionPackageSettings(
                    id = suggestedExtensionId(directory, resources.extensionPackages),
                    source = directory,
                    sourceType = AgentExtensionSourceType.LOCAL,
                    installedPath = directory,
                )
                onDocumentChange(document.withExtensionPackage(extension))
                operationFeedback = "已加入待保存的本地扩展包配置。"
                onChangeNotification("已添加本地扩展包：${extension.id}")
            }
        }
    }
    ExtensionSettingsCard {
        Text("Git 扩展包", style = JewelTheme.defaultTextStyle.copy(color = AppText))
        ExtensionSettingsField(
            label = "包 ID",
            value = gitPackageId,
            placeholder = "例如 team-tools",
            onValueChange = { gitPackageId = it },
        )
        ExtensionSettingsField(
            label = "Git 地址",
            value = gitSource,
            placeholder = "https://example.com/extension.git",
            onValueChange = { gitSource = it },
        )
        SettingsActionButton("安装 Git 包", emphasized = true) {
            val packageId = gitPackageId.trim()
            val source = gitSource.trim()
            if (packageId.isBlank() || source.isBlank()) {
                operationFeedback = "请填写包 ID 和 Git 地址。"
                return@SettingsActionButton
            }
            operationFeedback = "正在安装 Git 扩展包…"
            val managedBase = if (layer == ConfigLayer.PROJECT) projectRoot ?: userHome else userHome
            scope.launch {
                runCatching { installer.installGit(source, packageId, managedBase) }
                    .onSuccess { result ->
                        onDocumentChange(
                            document.withExtensionPackage(
                                AgentExtensionPackageSettings(
                                    id = packageId,
                                    source = source,
                                    sourceType = AgentExtensionSourceType.GIT,
                                    installedPath = result.installedPath.toString(),
                                ),
                            ),
                        )
                        operationFeedback = "${result.message} 请保存设置后重新加载资源。"
                        onChangeNotification("已安装 Git 扩展包：$packageId")
                    }
                    .onFailure { error ->
                        operationFeedback = "Git 扩展包安装失败：${error.message ?: "未知错误"}"
                    }
            }
        }
    }
    AutoLoadedSkillsCard(userHome = userHome, loadedSkills = loadedSkills)
    ExtensionSettingsCard {
        Text("附加资源目录", style = JewelTheme.defaultTextStyle.copy(color = AppText))
        Text(
            "只在这里添加默认目录以外的 Skills 或 prompts 目录；项目目录内容仍受资源信任规则约束。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsActionButton("添加 Skills 目录", emphasized = true) {
                pickWorkspaceDirectory()?.let { directory ->
                    val updated = document.withResourceDirectory(ResourceDirectoryKind.SKILL, directory)
                    onDocumentChange(updated)
                    if (updated != document) {
                        operationFeedback = "已加入待保存的 Skills 目录。"
                        onChangeNotification("已添加 Skills 目录：$directory")
                    }
                }
            }
            SettingsActionButton("添加 prompts 目录", emphasized = true) {
                pickWorkspaceDirectory()?.let { directory ->
                    val updated = document.withResourceDirectory(ResourceDirectoryKind.PROMPT, directory)
                    onDocumentChange(updated)
                    if (updated != document) {
                        operationFeedback = "已加入待保存的 prompts 目录。"
                        onChangeNotification("已添加 prompts 目录：$directory")
                    }
                }
            }
        }
        ConfiguredResourceDirectories(
            skillDirectories = resources.skillDirectories,
            promptDirectories = resources.promptDirectories,
            onRemove = { kind, directory ->
                onDocumentChange(document.withoutResourceDirectory(kind, directory))
                onChangeNotification("已移除 ${kind.label} 目录：$directory")
            },
        )
    }
    operationFeedback?.let { feedback ->
        Text(feedback, style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
    }

    GroupHeader("已配置扩展包")
    if (resources.extensionPackages.isEmpty()) {
        Text("尚未配置扩展包。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
    } else {
        resources.extensionPackages.forEach { setting ->
            val discovered = extensionPackages.firstOrNull { packageResource -> packageResource.id == setting.id }
            ExtensionPackageCard(
                setting = setting,
                discovered = discovered,
                onEnabledChange = { enabled ->
                    onDocumentChange(document.withUpdatedExtension(setting.id) { current -> current.copy(enabled = enabled) })
                    onChangeNotification("已${if (enabled) "启用" else "停用"}扩展包：${setting.id}")
                },
                onUpdateGit = if (setting.sourceType == AgentExtensionSourceType.GIT) {
                    {
                        val installedPath = setting.installedPath?.takeIf(String::isNotBlank)
                        if (installedPath == null) {
                            operationFeedback = "该 Git 扩展包尚未安装。"
                        } else {
                            operationFeedback = "正在更新 ${setting.id}…"
                            scope.launch {
                                runCatching { installer.updateGit(Paths.get(installedPath)) }
                                    .onSuccess { result ->
                                        onDocumentChange(
                                            document.withUpdatedExtension(setting.id) { current ->
                                                current.copy(installedPath = result.installedPath.toString())
                                            },
                                        )
                                        operationFeedback = "${result.message} 请重新加载资源。"
                                        onChangeNotification("已更新 Git 扩展包：${setting.id}")
                                    }
                                    .onFailure { error ->
                                        operationFeedback = "Git 更新失败：${error.message ?: "未知错误"}"
                                    }
                            }
                        }
                    }
                } else {
                    null
                },
                onRemove = {
                    onDocumentChange(document.withoutExtension(setting.id))
                    operationFeedback = "已移除 ${setting.id}"
                    onChangeNotification("已移除扩展包：${setting.id}")
                },
            )
        }
    }

    GroupHeader("MCP 服务")
    if (mcpServers.isEmpty()) {
        Text("当前没有可用 MCP 声明。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
    } else {
        mcpServers.forEach { server ->
            Text(
                text = "${server.id}  ·  ${server.transport.name.lowercase().replace('_', '-')}  ·  ${server.packageId}",
                style = JewelTheme.defaultTextStyle.copy(color = AppText),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    GroupHeader("资源诊断")
    if (resourceDiagnostics.isEmpty()) {
        Text("没有资源诊断。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
    } else {
        resourceDiagnostics.forEach { diagnostic ->
            Text(
                text = "${diagnostic.severity.name.lowercase()} · ${diagnostic.message}",
                style = JewelTheme.defaultTextStyle.copy(
                    color = if (diagnostic.severity.name == "ERROR") AppDanger else AppMuted,
                ),
            )
        }
    }
    SettingsActionButton("重新加载资源", emphasized = true) {
        operationFeedback = if (onReloadResources()) {
            "资源已重新加载；当前运行不会改变。"
        } else {
            "当前没有可重载的工作区资源。"
        }
    }
}

/** 为当前打开的项目提供用户级显式信任开关，项目配置本身不能自我授权。 */
@Composable
private fun ProjectResourceTrustCard(
    document: SettingsDocument,
    projectRoot: Path,
    onDocumentChange: (SettingsDocument) -> Unit,
    onChangeNotification: (String) -> Unit,
) {
    val normalizedPath = runCatching { projectRoot.toRealPath().toString() }
        .getOrDefault(projectRoot.toAbsolutePath().normalize().toString())
    val trusted = normalizedPath in document.agentResources.trustedProjectPaths
    ExtensionSettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(
                checked = trusted,
                onCheckedChange = { enabled ->
                    val updatedTrusts = document.agentResources.trustedProjectPaths
                        .toMutableSet()
                        .apply {
                            if (enabled) add(normalizedPath) else remove(normalizedPath)
                        }
                        .sorted()
                    onDocumentChange(
                        document.copy(
                            agentResources = document.agentResources.copy(trustedProjectPaths = updatedTrusts),
                        ),
                    )
                    onChangeNotification(if (enabled) "已信任当前项目资源" else "已取消信任当前项目资源")
                },
            )
            Column {
                Text("信任当前项目资源", style = JewelTheme.defaultTextStyle.copy(color = AppText))
                Text(
                    "启用后才会加载该项目的 Skills、prompts 与项目扩展包。",
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
            }
        }
    }
}

/** 绘制一个扩展中心的静态设置卡片。 */
@Composable
internal fun ExtensionSettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppChipBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** 绘制单个包的启用、更新与移除操作。 */
@Composable
private fun ExtensionPackageCard(
    setting: AgentExtensionPackageSettings,
    discovered: AgentExtensionPackageResource?,
    onEnabledChange: (Boolean) -> Unit,
    onUpdateGit: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    ExtensionSettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(discovered?.displayName ?: setting.id, style = JewelTheme.defaultTextStyle.copy(color = AppText))
                Text(
                    "${setting.sourceType.name.lowercase()} · ${setting.installedPath ?: setting.source}",
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
            }
            Checkbox(checked = setting.enabled, onCheckedChange = onEnabledChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onUpdateGit?.let { update -> SettingsActionButton("Git 更新", onClick = update) }
            SettingsActionButton("移除", destructive = true, onClick = onRemove)
        }
    }
}

/** 绘制 Git 包安装表单使用的可控文本字段。 */
@Composable
private fun ExtensionSettingsField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val editorValue = rememberExternalTextFieldValue(value)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(86.dp), style = JewelTheme.defaultTextStyle.copy(color = AppText))
        TextField(
            value = editorValue.value,
            onValueChange = { nextValue ->
                editorValue.value = nextValue
                onValueChange(nextValue.text)
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
        )
    }
}

/** 描述用户在扩展中心显式配置的额外资源目录种类。 */
private enum class ResourceDirectoryKind(
    val label: String,
) {
    SKILL("Skills"),
    PROMPT("prompts"),
}

/** 列出已配置的附加目录，并提供不触碰磁盘的移除操作。 */
@Composable
private fun ConfiguredResourceDirectories(
    skillDirectories: List<String>,
    promptDirectories: List<String>,
    onRemove: (ResourceDirectoryKind, String) -> Unit,
) {
    val directories = buildList {
        skillDirectories.forEach { directory -> add(ResourceDirectoryKind.SKILL to directory) }
        promptDirectories.forEach { directory -> add(ResourceDirectoryKind.PROMPT to directory) }
    }
    if (directories.isEmpty()) {
        Text("没有附加资源目录。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        return
    }
    directories.forEach { (kind, directory) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${kind.label} · $directory",
                modifier = Modifier.weight(1f),
                style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
            )
            SettingsActionButton("移除", destructive = true, compact = true) { onRemove(kind, directory) }
        }
    }
}

/** 把本地目录名转为稳定、可保存的包 id，并在当前范围内消除冲突。 */
private fun suggestedExtensionId(
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
private fun SettingsDocument.withExtensionPackage(extension: AgentExtensionPackageSettings): SettingsDocument = copy(
    agentResources = agentResources.copy(
        extensionPackages = agentResources.extensionPackages
            .filterNot { setting -> setting.id == extension.id } + extension,
    ),
)

/** 更新指定 id 的扩展包；找不到时保持原文档不变。 */
private fun SettingsDocument.withUpdatedExtension(
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
private fun SettingsDocument.withoutExtension(id: String): SettingsDocument = copy(
    agentResources = agentResources.copy(
        extensionPackages = agentResources.extensionPackages.filterNot { setting -> setting.id == id },
    ),
)

/** 在相应设置范围加入规范化后的附加资源目录，重复输入不会产生多个相同项。 */
private fun SettingsDocument.withResourceDirectory(
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
private fun SettingsDocument.withoutResourceDirectory(
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
