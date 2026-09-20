package com.agent.app.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.platform.pickWorkspaceDirectory
import com.agent.shared.agent.resource.AgentExtensionPackageResource
import com.agent.shared.agent.resource.AgentMcpServerResource
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentSkillResource
import com.agent.shared.agent.resource.DesktopExtensionPackageInstaller
import com.agent.shared.agent.resource.McpServerConnectionStatus
import com.agent.shared.settings.model.AgentExtensionPackageSettings
import com.agent.shared.settings.model.AgentExtensionSourceType
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text

/**
 * 受控扩展中心的独立子页路由；每次只组合当前页面，避免 MCP、Hooks 和诊断内容堆叠在同一滚动区。
 */
@Composable
internal fun ExtensionSettingsContent(
    subsection: ExtensionSubsection,
    document: SettingsDocument,
    layer: ConfigLayer,
    projectRoot: Path?,
    userHome: Path,
    extensionPackages: List<AgentExtensionPackageResource>,
    loadedSkills: List<AgentSkillResource>,
    resourceDiagnostics: List<AgentResourceDiagnostic>,
    mcpServers: List<AgentMcpServerResource>,
    savedMcpServers: List<com.agent.shared.settings.model.McpServerSettings>,
    savedHooks: AgentHookSettings,
    mcpConnectionStatuses: List<McpServerConnectionStatus>,
    mcpJsonEditorState: McpJsonEditorState,
    mcpReloadPending: Boolean,
    mcpRetryEnabled: Boolean,
    onRetryMcp: () -> Unit,
    onSaveMcp: () -> Unit,
    onSaveHooks: () -> Unit,
    onSaveExtensions: () -> Unit,
    onDocumentChange: (SettingsDocument) -> Unit,
    onChangeNotification: (String) -> Unit,
    onValidationErrorChange: (String, String?) -> Unit,
    onResourceFilesChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val installer = remember { DesktopExtensionPackageInstaller() }
    var gitPackageId by remember { mutableStateOf("") }
    var gitSource by remember { mutableStateOf("") }
    var operationFeedback by remember { mutableStateOf<String?>(null) }
    val resources = document.agentResources

    when (subsection) {
        ExtensionSubsection.OVERVIEW -> {
            GroupHeader("扩展中心")
            SettingsActionButton("保存扩展", emphasized = true, onClick = onSaveExtensions)
            if (layer == ConfigLayer.USER && projectRoot != null) {
                ProjectResourceTrustCard(
                    document = document,
                    projectRoot = projectRoot,
                    onDocumentChange = onDocumentChange,
                )
            }
        }

        ExtensionSubsection.PACKAGES -> {
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
                                onResourceFilesChanged()
                            }
                            .onFailure { error ->
                                operationFeedback = "Git 扩展包安装失败：${error.message ?: "未知错误"}"
                            }
                    }
                }
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
                                                onResourceFilesChanged()
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
                            operationFeedback = "已移除待保存的扩展包 ${setting.id}。"
                        },
                    )
                }
            }
            operationFeedback?.let { feedback ->
                Text(feedback, style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            }
        }

        ExtensionSubsection.SKILLS -> {
            AutoLoadedSkillsCard(userHome = userHome, loadedSkills = loadedSkills)
            ResourceDirectorySettings(document, ResourceDirectoryKind.SKILL, onDocumentChange)
        }

        ExtensionSubsection.PROMPTS -> {
            GroupHeader("Prompts")
            ResourceDirectorySettings(document, ResourceDirectoryKind.PROMPT, onDocumentChange)
        }

        ExtensionSubsection.MCP -> {
            McpSettingsContent(
                document = document,
                discoveredServers = mcpServers,
                savedServers = savedMcpServers,
                connectionStatuses = mcpConnectionStatuses,
                layer = layer,
                editorState = mcpJsonEditorState,
                configurationPendingReload = mcpReloadPending,
                retryEnabled = mcpRetryEnabled,
                onRetry = onRetryMcp,
                onSave = onSaveMcp,
                onDocumentChange = onDocumentChange,
                onValidationErrorChange = onValidationErrorChange,
            )
        }

        ExtensionSubsection.HOOKS -> {
            AgentHookSettingsContent(
                document = document,
                savedHooks = savedHooks,
                layer = layer,
                onSave = onSaveHooks,
                onDocumentChange = onDocumentChange,
            )
        }

        ExtensionSubsection.DIAGNOSTICS -> {
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
        }
    }
}
