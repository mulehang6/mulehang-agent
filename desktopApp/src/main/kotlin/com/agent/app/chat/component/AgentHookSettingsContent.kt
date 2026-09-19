@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.shared.settings.model.AgentHookCommand
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.settings.model.AgentHookMatcher
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text

/** 在既有设置页的扩展区编辑仅用户级生效的 Junie 兼容 Agent Hooks。 */
@Composable
internal fun AgentHookSettingsContent(
    document: SettingsDocument,
    savedHooks: AgentHookSettings,
    layer: ConfigLayer,
    onSave: () -> Unit,
    onDocumentChange: (SettingsDocument) -> Unit,
) {
    GroupHeader("Agent Hooks")
    if (layer != ConfigLayer.USER) {
        Text(
            "Hooks 只允许在全局设置中配置，项目 settings 不会保存或执行命令。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        return
    }
    Text(
        "命令通过 cmd.exe 执行，并从 stdin 接收 JSON。支持 SessionStart、UserPromptSubmit、PreToolUse、Stop、StopFailure、PermissionRequest 与 SessionEnd。",
        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
    )
    HookExecutionResults()
    SettingsActionButton("添加 Hook 规则", emphasized = true) {
        onDocumentChange(document.withAddedHookRule())
    }
    val rules = document.hooks.hooks.flatMap { (event, matchers) ->
        matchers.mapIndexed { index, matcher -> HookRuleReference(event, index, matcher) }
    }
    var editingRuleOrigins by remember(layer) { mutableStateOf(emptyMap<HookRuleKey, HookRuleReference>()) }
    LaunchedEffect(savedHooks) {
        editingRuleOrigins = editingRuleOrigins.filterKeys { key ->
            document.hooks.hooks[key.event]?.getOrNull(key.index) != savedHooks.hooks[key.event]?.getOrNull(key.index)
        }
    }
    if (rules.isEmpty()) {
        Text("尚未配置 Hook。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        if (document.hooks != savedHooks) {
            ExtensionSettingsCard {
                Text("已移除全部 Hook 规则，保存后写入配置。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
                SettingsActionButton("保存", emphasized = true, onClick = onSave)
            }
        }
    } else {
        rules.forEach { rule ->
            val key = HookRuleKey(rule.event, rule.index)
            val editingOrigin = editingRuleOrigins[key]
            val savedMatcher = editingOrigin?.matcher ?: savedHooks.hooks[rule.event]?.getOrNull(rule.index)
            if (savedMatcher == rule.matcher && key !in editingRuleOrigins) {
                SavedHookRuleCard(
                    rule = rule,
                    onEdit = { editingRuleOrigins = editingRuleOrigins + (key to rule) },
                    onRemove = { onDocumentChange(document.withoutHookRule(rule.event, rule.index)) },
                )
            } else {
                HookRuleEditor(
                    rule = rule,
                    saveEnabled = editingOrigin?.let { origin ->
                        origin.event != rule.event || origin.matcher != rule.matcher
                    } ?: (savedMatcher == null || savedMatcher != rule.matcher),
                    onChange = { updatedEvent, updatedMatcher ->
                        if (editingOrigin != null && updatedEvent != rule.event) {
                            val updatedKey = HookRuleKey(
                                event = updatedEvent,
                                index = document.hooks.hooks[updatedEvent].orEmpty().size,
                            )
                            editingRuleOrigins = editingRuleOrigins - key + (updatedKey to editingOrigin)
                        }
                        onDocumentChange(document.withUpdatedHookRule(rule.event, rule.index, updatedEvent, updatedMatcher))
                    },
                    onRemove = {
                        editingRuleOrigins = editingRuleOrigins - key
                        onDocumentChange(document.withoutHookRule(rule.event, rule.index))
                    },
                    onCancel = {
                        editingRuleOrigins = editingRuleOrigins - key
                        onDocumentChange(
                            when {
                                editingOrigin != null -> document.withRestoredHookRule(rule.event, rule.index, editingOrigin)
                                savedMatcher != null -> document.withUpdatedHookRule(
                                    rule.event,
                                    rule.index,
                                    rule.event,
                                    savedMatcher,
                                )
                                else -> document.withoutHookRule(rule.event, rule.index)
                            },
                        )
                    },
                    onSave = onSave,
                )
            }
        }
    }
}

/** 已保存 Hook 规则在当前事件列表中的稳定位置。 */
private data class HookRuleKey(
    val event: AgentHookEvent,
    val index: Int,
)

/** 当前文档中单条 Hook 规则的稳定位置。 */
private data class HookRuleReference(
    val event: AgentHookEvent,
    val index: Int,
    val matcher: AgentHookMatcher,
)

/** 用摘要卡片替代已保存规则的编辑表单，编辑动作会在原位置恢复表单。 */
@Composable
private fun SavedHookRuleCard(
    rule: HookRuleReference,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    ExtensionSettingsCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SettingsInlineSpacing),
            verticalArrangement = Arrangement.spacedBy(SettingsInlineSpacing),
        ) {
            Text(hookEventLabel(rule.event), style = JewelTheme.defaultTextStyle.copy(color = AppText))
            Text("${rule.matcher.hooks.size} 条命令", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
            SettingsActionButton("编辑", onClick = onEdit)
            SettingsActionButton("删除", destructive = true, onClick = onRemove)
        }
    }
}

/** 编辑一个事件、匹配器和其顺序命令列表。 */
@Composable
private fun HookRuleEditor(
    rule: HookRuleReference,
    saveEnabled: Boolean,
    onChange: (AgentHookEvent, AgentHookMatcher) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val comboBoxStyle = rememberProviderProtocolComboBoxStyle()
    ExtensionSettingsCard {
        SettingsRow("事件") {
            ListComboBox(
                items = AgentHookEvent.entries,
                selectedIndex = AgentHookEvent.entries.indexOf(rule.event),
                onSelectedItemChange = { index -> onChange(AgentHookEvent.entries[index], rule.matcher) },
                itemKeys = { _, event -> event.name },
                modifier = Modifier.fillMaxWidth(),
                style = comboBoxStyle,
            ) { event, selected, active ->
                SimpleListItem(text = hookEventLabel(event), selected = selected, active = active)
            }
        }
        SettingsField(
            label = "匹配器",
            value = rule.matcher.matcher.orEmpty(),
            placeholder = "可选正则，例如 apply_patch|run_powershell",
        ) { matcher ->
            onChange(rule.event, rule.matcher.copy(matcher = matcher.trim().ifBlank { null }))
        }
        if (rule.matcher.hooks.isEmpty()) {
            Text("至少添加一条命令。", style = JewelTheme.defaultTextStyle.copy(color = AppMuted))
        }
        rule.matcher.hooks.forEachIndexed { commandIndex, command ->
            HookCommandEditor(
                event = rule.event,
                command = command,
                onChange = { updated ->
                    onChange(
                        rule.event,
                        rule.matcher.copy(
                            hooks = rule.matcher.hooks.mapIndexed { index, current ->
                                if (index == commandIndex) updated else current
                            },
                        ),
                    )
                },
                onRemove = {
                    onChange(
                        rule.event,
                        rule.matcher.copy(hooks = rule.matcher.hooks.filterIndexed { index, _ -> index != commandIndex }),
                    )
                },
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SettingsInlineSpacing),
            verticalArrangement = Arrangement.spacedBy(SettingsInlineSpacing),
        ) {
            SettingsActionButton("保存", emphasized = true, enabled = saveEnabled, onClick = onSave)
            SettingsActionButton("添加命令", emphasized = true) {
                onChange(rule.event, rule.matcher.copy(hooks = rule.matcher.hooks + AgentHookCommand(command = "")))
            }
            SettingsActionButton("移除规则", destructive = true, onClick = onRemove)
            SettingsActionButton("取消", onClick = onCancel)
        }
    }
}

/** 编辑单条命令的文本、超时、异步与 Stop 专属的 blockOnError。 */
@Composable
private fun HookCommandEditor(
    event: AgentHookEvent,
    command: AgentHookCommand,
    onChange: (AgentHookCommand) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsInlineSpacing)) {
        Text("命令", style = JewelTheme.defaultTextStyle.copy(color = AppText))
        SettingsField(
            label = "cmd.exe",
            value = command.command,
            placeholder = "例如 echo {\"additionalContext\":\"...\"}",
        ) { value -> onChange(command.copy(command = value)) }
        SettingsField(
            label = "超时（秒）",
            value = command.timeout?.toString().orEmpty(),
            placeholder = "留空使用 ${hookDefaultTimeoutSeconds(event)} 秒",
        ) { value -> onChange(command.copy(timeout = value.trim().toIntOrNull())) }
        SettingsRow("后台执行") {
            Checkbox(checked = command.runAsync, onCheckedChange = { enabled -> onChange(command.copy(runAsync = enabled)) })
        }
        Text(
            if (command.runAsync) "不等待命令完成；结果不改变当前请求或审批决策。会话结束时取消。"
            else "等待命令完成后继续；可修改输入、附加上下文或阻止当前操作。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        if (event == AgentHookEvent.STOP) {
            SettingsRow("出错时阻止结束") {
                Checkbox(checked = command.blockOnError, onCheckedChange = { enabled -> onChange(command.copy(blockOnError = enabled)) })
            }
        }
        SettingsActionButton("删除命令", destructive = true, onClick = onRemove)
    }
}

/** 保存前验证 Hook 的正则、命令、超时和事件特定字段。 */
internal fun validateAgentHookSettings(document: SettingsDocument): String? {
    document.hooks.hooks.forEach { (event, matchers) ->
        matchers.forEachIndexed { matcherIndex, matcher ->
            matcher.matcher?.takeIf(String::isNotBlank)?.let { expression ->
                if (runCatching { Regex(expression) }.isFailure) {
                    return "${hookEventLabel(event)} 的第 ${matcherIndex + 1} 条 matcher 不是有效正则。"
                }
            }
            if (matcher.hooks.isEmpty()) return "${hookEventLabel(event)} 至少需要一条命令。"
            matcher.hooks.forEachIndexed { commandIndex, command ->
                if (command.type != "command") return "${hookEventLabel(event)} 只支持 command 类型 Hook。"
                if (command.command.isBlank()) return "${hookEventLabel(event)} 的第 ${commandIndex + 1} 条命令不能为空。"
                val timeout = command.timeout
                if (timeout != null && timeout <= 0) return "${hookEventLabel(event)} 的命令超时必须大于 0。"
                if (command.blockOnError && event != AgentHookEvent.STOP) {
                    return "blockOnError 仅适用于 Stop Hook。"
                }
            }
        }
    }
    return null
}

/** 添加一条默认的 UserPromptSubmit 规则，命令刻意留空以要求用户明确填写。 */
private fun SettingsDocument.withAddedHookRule(): SettingsDocument = copy(
    hooks = hooks.copy(
        hooks = hooks.hooks + (
            AgentHookEvent.USER_PROMPT_SUBMIT to
                (hooks.hooks[AgentHookEvent.USER_PROMPT_SUBMIT].orEmpty() + AgentHookMatcher(hooks = listOf(AgentHookCommand(command = ""))))
            ),
    ),
)

/** 原子替换一条规则，事件改变时把规则移动到新事件列表末尾。 */
private fun SettingsDocument.withUpdatedHookRule(
    currentEvent: AgentHookEvent,
    index: Int,
    updatedEvent: AgentHookEvent,
    updatedMatcher: AgentHookMatcher,
): SettingsDocument {
    val byEvent = hooks.withoutRule(currentEvent, index) ?: return this
    val updatedRules = byEvent[updatedEvent].orEmpty().toMutableList()
    val insertIndex = if (currentEvent == updatedEvent) index.coerceAtMost(updatedRules.size) else updatedRules.size
    updatedRules.add(insertIndex, updatedMatcher)
    byEvent[updatedEvent] = updatedRules
    return copy(hooks = AgentHookSettings(byEvent))
}

/** 取消跨事件编辑时把规则恢复到保存前的事件和列表位置。 */
private fun SettingsDocument.withRestoredHookRule(
    currentEvent: AgentHookEvent,
    currentIndex: Int,
    original: HookRuleReference,
): SettingsDocument {
    val byEvent = hooks.withoutRule(currentEvent, currentIndex) ?: return this
    val originalRules = byEvent[original.event].orEmpty().toMutableList()
    originalRules.add(original.index.coerceIn(0, originalRules.size), original.matcher)
    byEvent[original.event] = originalRules
    return copy(hooks = AgentHookSettings(byEvent))
}

/** 移除指定规则，空事件列表不会写入 settings.json。 */
private fun SettingsDocument.withoutHookRule(
    event: AgentHookEvent,
    index: Int,
): SettingsDocument {
    val byEvent = hooks.withoutRule(event, index) ?: return this
    return copy(hooks = AgentHookSettings(byEvent))
}

/** 移除一个规则并返回可继续编辑的映射；索引过期时保留原文档。 */
private fun AgentHookSettings.withoutRule(
    event: AgentHookEvent,
    index: Int,
): MutableMap<AgentHookEvent, List<AgentHookMatcher>>? {
    val byEvent = hooks.toMutableMap()
    val rules = byEvent[event].orEmpty().toMutableList()
    if (index !in rules.indices) return null
    rules.removeAt(index)
    if (rules.isEmpty()) byEvent.remove(event) else byEvent[event] = rules
    return byEvent
}

/** UI 采用事件的 Junie 名称，配置写入同名 JSON key。 */
internal fun hookEventLabel(event: AgentHookEvent): String = when (event) {
    AgentHookEvent.SESSION_START -> "SessionStart"
    AgentHookEvent.USER_PROMPT_SUBMIT -> "UserPromptSubmit"
    AgentHookEvent.PRE_TOOL_USE -> "PreToolUse"
    AgentHookEvent.STOP -> "Stop"
    AgentHookEvent.STOP_FAILURE -> "StopFailure"
    AgentHookEvent.PERMISSION_REQUEST -> "PermissionRequest"
    AgentHookEvent.SESSION_END -> "SessionEnd"
}

/** 与运行时保持一致的事件默认超时说明，单位为秒。 */
private fun hookDefaultTimeoutSeconds(event: AgentHookEvent): Int = when (event) {
    AgentHookEvent.SESSION_START,
    AgentHookEvent.USER_PROMPT_SUBMIT,
    AgentHookEvent.PERMISSION_REQUEST,
        -> 10
    AgentHookEvent.STOP -> 600
    AgentHookEvent.STOP_FAILURE,
    AgentHookEvent.PRE_TOOL_USE,
        -> 60
    AgentHookEvent.SESSION_END -> 2
}

private val SettingsInlineSpacing = androidx.compose.ui.unit.Dp(8f)
