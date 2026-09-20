@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.rememberExternalTextFieldValue
import com.agent.app.platform.pickWorkspaceDirectory
import com.agent.shared.agent.resource.AgentExtensionPackageResource
import com.agent.shared.settings.model.AgentExtensionPackageSettings
import com.agent.shared.settings.model.SettingsDocument
import java.nio.file.Path
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** 为当前打开的项目提供用户级显式信任开关，项目配置本身不能自我授权。 */
@Composable
internal fun ProjectResourceTrustCard(
    document: SettingsDocument,
    projectRoot: Path,
    onDocumentChange: (SettingsDocument) -> Unit,
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
internal fun ExtensionPackageCard(
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
internal fun ExtensionSettingsField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val editorValue = rememberExternalTextFieldValue(value)
    SettingsRow(label) {
        TextField(
            value = editorValue.value,
            onValueChange = { nextValue ->
                editorValue.value = nextValue
                onValueChange(nextValue.text)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
        )
    }
}

/** 描述用户在扩展中心显式配置的额外资源目录种类。 */
internal enum class ResourceDirectoryKind(
    val label: String,
) {
    SKILL("Skills"),
    PROMPT("prompts"),
}

/** 列出已配置的附加目录，并提供不触碰磁盘的移除操作。 */
@Composable
internal fun ConfiguredResourceDirectories(
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


/** 分别编辑 Skills 与 Prompts 目录，保持已有配置写入和删除语义。 */
@Composable
internal fun ResourceDirectorySettings(
    document: SettingsDocument,
    kind: ResourceDirectoryKind,
    onDocumentChange: (SettingsDocument) -> Unit,
) {
    ExtensionSettingsCard {
        Text("附加 ${kind.label} 目录", style = JewelTheme.defaultTextStyle.copy(color = AppText))
        Text("默认目录以外的资源；项目内容仍受信任规则约束。", color = AppMuted)
        SettingsActionButton("添加 ${kind.label} 目录", emphasized = true) {
            pickWorkspaceDirectory()?.let { directory ->
                onDocumentChange(document.withResourceDirectory(kind, directory))
            }
        }
        ConfiguredResourceDirectories(
            skillDirectories = if (kind == ResourceDirectoryKind.SKILL) document.agentResources.skillDirectories else emptyList(),
            promptDirectories = if (kind == ResourceDirectoryKind.PROMPT) document.agentResources.promptDirectories else emptyList(),
            onRemove = { directoryKind, directory -> onDocumentChange(document.withoutResourceDirectory(directoryKind, directory)) },
        )
    }
}
