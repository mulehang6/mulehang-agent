@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)
@file:Suppress("UnstableApiUsage")

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.platform.TerminalShellCatalog
import com.agent.shared.session.DEFAULT_DESKTOP_TERMINAL_SHELL_ID
import com.agent.shared.session.DesktopTerminalPreferences
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.search.SpeedSearchableComboBox

/**
 * 全局工具设置，当前包含仅影响后续新终端的默认 Shell 选择。
 */
@Composable
internal fun ToolsSettingsContent(
    preferences: DesktopTerminalPreferences,
    shellCatalog: TerminalShellCatalog,
    compact: Boolean,
    onPreferencesChanged: (DesktopTerminalPreferences) -> Unit,
) {
    GroupHeader("工具")
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DefaultTerminalShellSetting(
                preferences = preferences,
                shellCatalog = shellCatalog,
                compact = compact,
                onPreferencesChanged = onPreferencesChanged,
            )
        }
    }
}

/** 渲染带速度搜索的默认 Shell 下拉框和不可用回退说明。 */
@Composable
private fun DefaultTerminalShellSetting(
    preferences: DesktopTerminalPreferences,
    shellCatalog: TerminalShellCatalog,
    compact: Boolean,
    onPreferencesChanged: (DesktopTerminalPreferences) -> Unit,
) {
    val options = remember(shellCatalog) { terminalShellOptions(shellCatalog) }
    val selectedIndex = terminalShellSelectionIndex(options, preferences)
    val selector = @Composable {
        SpeedSearchArea(modifier = Modifier.fillMaxWidth()) {
            SpeedSearchableComboBox(
                items = options.map(TerminalShellOption::label),
                selectedIndex = selectedIndex,
                onSelectedItemChange = { index ->
                    onPreferencesChanged(
                        preferences.copy(defaultShellId = options[index].storageId),
                    )
                },
                itemKeys = { index, _ -> options[index].storageId },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (compact) {
            Text("默认 Shell")
            Text("仅影响之后新建的终端；已打开会话继续运行。", color = AppMuted)
            selector()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("默认 Shell")
                    Text("仅影响之后新建的终端；已打开会话继续运行。", color = AppMuted)
                }
                Column(modifier = Modifier.width(240.dp)) {
                    selector()
                }
            }
        }
        unavailableTerminalShellMessage(preferences, shellCatalog)?.let { message ->
            Text(message, color = AppMuted)
        }
    }
}

/** Shell 下拉框中显示的一个选项。 */
internal data class TerminalShellOption(
    val storageId: String,
    val label: String,
)

/**
 * 根据启动时检测到的目录建立可选项；极端情况下没有检测结果时仍保留默认回退入口。
 */
internal fun terminalShellOptions(shellCatalog: TerminalShellCatalog): List<TerminalShellOption> =
    shellCatalog.availableShells
        .map { descriptor ->
            TerminalShellOption(
                storageId = descriptor.shell.storageId,
                label = descriptor.shell.label,
            )
        }
        .ifEmpty {
            listOf(
                TerminalShellOption(
                    storageId = DEFAULT_DESKTOP_TERMINAL_SHELL_ID,
                    label = "Windows PowerShell",
                ),
            )
        }

/**
 * 找出偏好 Shell 的下拉选中项；缺失项显示为 Windows PowerShell，但不改写已保存的偏好。
 */
internal fun terminalShellSelectionIndex(
    options: List<TerminalShellOption>,
    preferences: DesktopTerminalPreferences,
): Int {
    val preferredIndex = options.indexOfFirst { option -> option.storageId == preferences.defaultShellId }
    if (preferredIndex >= 0) return preferredIndex
    return options.indexOfFirst { option -> option.storageId == DEFAULT_DESKTOP_TERMINAL_SHELL_ID }
        .coerceAtLeast(0)
}

/** 返回已选 Shell 当前不可用时应展示的说明。 */
internal fun unavailableTerminalShellMessage(
    preferences: DesktopTerminalPreferences,
    shellCatalog: TerminalShellCatalog,
): String? = preferences.normalized().defaultShellId
    .takeIf { storageId -> !shellCatalog.isAvailable(storageId) }
    ?.let { storageId ->
        "“${shellCatalog.labelFor(storageId)}”当前不可用。该选择会保留；之后新建的终端将临时使用 Windows PowerShell。"
    }
