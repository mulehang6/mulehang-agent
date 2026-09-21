@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.BranchSummaryMode
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.shared.chat.model.ConversationEntry
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** 绘制所选条目的标签与离开分支选项。 */
@Composable
internal fun ConversationEntryTreeInspector(
    selectedEntry: ConversationEntry?,
    currentLabel: String?,
    labelText: TextFieldValue,
    onLabelTextChange: (TextFieldValue) -> Unit,
    onSaveLabel: (ConversationEntry) -> Unit,
    willLeaveActiveBranch: Boolean,
    summaryMode: BranchSummaryMode,
    onSummaryModeChange: (BranchSummaryMode) -> Unit,
    customSummary: TextFieldValue,
    onCustomSummaryChange: (TextFieldValue) -> Unit,
    summaryInProgress: Boolean,
    operationError: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectedEntry?.let { entry ->
            val labelAction = conversationEntryLabelAction(currentLabel, labelText.text)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = labelText,
                    onValueChange = onLabelTextChange,
                    placeholder = { Text("为所选条目添加标签") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { onSaveLabel(entry) }, enabled = labelAction.enabled) {
                    Text(labelAction.label)
                }
                OutlinedButton(onClick = { copyEntryText(entry) }) { Text("复制文本") }
            }
            Text("标签只用于查找和标记条目，不会发送给模型。", color = AppMuted)
        }
        if (willLeaveActiveBranch) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("离开当前路径时")
                Dropdown(
                    menuContent = {
                        BranchSummaryMode.entries.forEach { mode ->
                            selectableItem(selected = summaryMode == mode, onClick = { onSummaryModeChange(mode) }) {
                                Text(summaryModeLabel(mode))
                            }
                        }
                    },
                ) { Text(summaryModeLabel(summaryMode)) }
                if (summaryMode == BranchSummaryMode.CUSTOM) {
                    TextField(
                        value = customSummary,
                        onValueChange = onCustomSummaryChange,
                        placeholder = { Text("自定义摘要提示") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (summaryInProgress) Text("正在生成分支摘要…", color = AppMuted)
        operationError?.let { Text(it, color = AppDanger) }
    }
}

/** 处理 Pi 式列表键盘导航，不注册全局双 Escape。 */
internal fun handleConversationTreeKeyEvent(
    event: KeyEvent,
    rows: List<ConversationEntryTreeRow>,
    selectedEntryId: String?,
    collapsedIds: Set<String>,
    onSelectEntry: (String) -> Unit,
    onToggleCollapsed: (ConversationEntryTreeRow) -> Unit,
    onSubmit: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val index = rows.indexOfFirst { it.entry.id == selectedEntryId }.coerceAtLeast(0)
    val selectedRow = rows.getOrNull(index)
    return when {
        event.key == Key.DirectionUp ->
            rows.getOrNull(index - 1)?.let { onSelectEntry(it.entry.id); true } ?: false
        event.key == Key.DirectionDown ->
            rows.getOrNull(index + 1)?.let { onSelectEntry(it.entry.id); true } ?: false
        event.key == Key.DirectionLeft && selectedRow != null -> {
            when {
                selectedRow.foldable && selectedRow.entry.id !in collapsedIds -> onToggleCollapsed(selectedRow)
                selectedRow.visibleParentId != null -> onSelectEntry(selectedRow.visibleParentId)
                else -> return false
            }
            true
        }
        event.key == Key.DirectionRight && selectedRow != null -> {
            when {
                selectedRow.foldable && selectedRow.entry.id in collapsedIds -> onToggleCollapsed(selectedRow)
                selectedRow.visibleChildIds.isNotEmpty() -> onSelectEntry(selectedRow.visibleChildIds.first())
                else -> return false
            }
            true
        }
        event.key == Key.Enter && selectedRow != null -> {
            onSubmit()
            true
        }
        event.isCtrlPressed && event.key == Key.C && selectedRow != null -> {
            copyEntryText(selectedRow.entry)
            true
        }
        else -> false
    }
}

/** 将条目正文复制到系统剪贴板。 */
private fun copyEntryText(entry: ConversationEntry) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(entryPreview(entry)), null)
}

/** 返回摘要选择的用户可见名称。 */
private fun summaryModeLabel(mode: BranchSummaryMode): String = when (mode) {
    BranchSummaryMode.NONE -> "不摘要"
    BranchSummaryMode.AUTOMATIC -> "自动摘要"
    BranchSummaryMode.CUSTOM -> "自定义提示摘要"
}
