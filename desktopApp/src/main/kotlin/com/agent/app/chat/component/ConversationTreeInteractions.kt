package com.agent.app.chat.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.agent.shared.chat.model.ConversationEntry
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * 处理 Pi 式树键盘导航；高频选择不注册全局快捷键或动画。
 */
internal fun handleConversationTreeKeyEvent(
    event: KeyEvent,
    rows: List<ConversationEntryTreeRow>,
    selectedEntryId: String?,
    collapsedIds: Set<String>,
    onSelectEntry: (String) -> Unit,
    onToggleCollapsed: (ConversationEntryTreeRow) -> Unit,
    onSubmit: () -> Unit,
    onCopyEntry: (ConversationEntry) -> Unit,
    onEditLabel: (ConversationEntry) -> Unit,
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

        (event.key == Key.Enter || event.key == Key.Spacebar) && selectedRow != null -> {
            onSubmit()
            true
        }

        event.isCtrlPressed && event.key == Key.C && selectedRow != null -> {
            onCopyEntry(selectedRow.entry)
            true
        }

        event.isShiftPressed && event.key == Key.L && selectedRow != null -> {
            onEditLabel(selectedRow.entry)
            true
        }

        else -> false
    }
}

/** 将条目正文复制到系统剪贴板，失败时返回可展示的错误。 */
internal fun copyConversationEntryText(entry: ConversationEntry): String? = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(entryPreview(entry)), null)
}.exceptionOrNull()?.let { error -> "复制文本失败：${error.message ?: "系统剪贴板不可用"}" }

/** 判断树底部的单一“切换”动作是否可以提交。 */
internal fun isConversationTreeSwitchEnabled(
    selectedEntryId: String?,
    activeEntryId: String?,
    inProgress: Boolean,
): Boolean = selectedEntryId != null && selectedEntryId != activeEntryId && !inProgress
