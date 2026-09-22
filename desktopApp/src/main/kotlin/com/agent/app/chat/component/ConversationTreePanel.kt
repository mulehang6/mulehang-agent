@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.BranchNavigationSummary
import com.agent.app.chat.state.BranchSummaryMode
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.conversationEntryPath
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 分支概览只列出真实分叉的末端，并在切换后保持面板与完整图可见。 */
@Composable
internal fun ConversationBranchPanelContent(
    state: ChatWindowState,
    conversation: ChatConversationUiState,
    modifier: Modifier,
) {
    var query by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var operationError by remember(conversation.id) { mutableStateOf<String?>(null) }
    var pendingSummaryLeafId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var customSummaryLeafId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var customSummaryPrompt by remember(conversation.id) { mutableStateOf("") }
    val allItems = remember(conversation.entries, conversation.activeEntryId, conversation.headEntryId) {
        buildConversationBranchOverview(conversation.entries, conversation.activeEntryId, conversation.headEntryId)
    }
    val items = remember(allItems, query.text) {
        query.text.trim().takeIf(String::isNotEmpty)?.let { text ->
            allItems.filter { it.preview.contains(text, ignoreCase = true) }
        } ?: allItems
    }
    var selectedLeafId by remember(conversation.id) {
        mutableStateOf(
            allItems.firstOrNull { it.isActiveLeaf }?.leafEntryId
                ?: allItems.firstOrNull { it.isHeadLeaf }?.leafEntryId
                ?: allItems.firstOrNull()?.leafEntryId,
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(items) {
        if (items.none { it.leafEntryId == selectedLeafId }) selectedLeafId = items.firstOrNull()?.leafEntryId
    }
    val switchToLeaf: (String, BranchNavigationSummary) -> Unit = { leafEntryId, summary ->
        operationError = null
        scope.launch {
            val result = state.conversationTreeController.switchToLeaf(
                conversationId = conversation.id,
                leafEntryId = leafEntryId,
                summary = summary,
            )
            operationError = result.message.takeUnless { result.succeeded }
        }
    }
    val requestSwitch: () -> Unit = {
        selectedLeafId?.takeIf { leafEntryId ->
            isConversationTreeSwitchEnabled(
                selectedEntryId = leafEntryId,
                activeEntryId = conversation.activeEntryId,
                inProgress = state.conversationTreeController.summaryInProgress,
            )
        }?.let { leafEntryId ->
            operationError = null
            customSummaryPrompt = ""
            pendingSummaryLeafId = leafEntryId
        }
    }
    Column(modifier) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索分支") },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                allItems.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("当前会话还是单一路径", color = AppText, fontWeight = FontWeight.SemiBold)
                    Text(
                        "要创建会话内分支，请在用户消息上选择“从此处编辑”，修改后重新发送。",
                        color = AppMuted,
                    )
                }

                items.isEmpty() -> Text("没有匹配的分支", color = AppMuted, modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val index = items.indexOfFirst { it.leafEntryId == selectedLeafId }.coerceAtLeast(0)
                                when (event.key) {
                                    Key.DirectionUp, Key.DirectionDown -> {
                                        val delta = if (event.key == Key.DirectionUp) -1 else 1
                                        val next = (index + delta).coerceIn(0, items.lastIndex)
                                        selectedLeafId = items[next].leafEntryId
                                        scope.launch { listState.ensureItemVisibleMinimal(next) }
                                        true
                                    }

                                    Key.Enter, Key.Spacebar -> {
                                        requestSwitch(); true
                                    }

                                    else -> false
                                }
                            }
                            .focusable(),
                    ) {
                        items(items, key = ConversationBranchOverviewItem::leafEntryId) { item ->
                            ConversationBranchPanelRow(
                                item = item,
                                selected = item.leafEntryId == selectedLeafId,
                                onClick = {
                                    selectedLeafId = item.leafEntryId
                                    focusRequester.requestFocus()
                                },
                            )
                        }
                    }
                    VerticalScrollbar(
                        scrollState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        }
        ConversationTreeActionFooter(
            primaryEnabled = isConversationTreeSwitchEnabled(
                selectedEntryId = selectedLeafId,
                activeEntryId = conversation.activeEntryId,
                inProgress = state.conversationTreeController.summaryInProgress,
            ),
            inProgress = state.conversationTreeController.summaryInProgress,
            operationError = operationError,
            onPrimary = requestSwitch,
        )
    }
    pendingSummaryLeafId?.let { leafEntryId ->
        ConversationTreeSummaryChoiceDialog(
            onChoose = { mode ->
                pendingSummaryLeafId = null
                if (mode == BranchSummaryMode.CUSTOM) {
                    customSummaryLeafId = leafEntryId
                } else {
                    switchToLeaf(leafEntryId, BranchNavigationSummary(mode))
                }
            },
            onDismiss = { pendingSummaryLeafId = null },
        )
    }
    customSummaryLeafId?.let { leafEntryId ->
        ConversationTreeCustomSummaryDialog(
            initialPrompt = customSummaryPrompt,
            onConfirm = { prompt ->
                customSummaryPrompt = prompt
                customSummaryLeafId = null
                switchToLeaf(leafEntryId, BranchNavigationSummary(BranchSummaryMode.CUSTOM, prompt))
            },
            onBack = {
                customSummaryLeafId = null
                pendingSummaryLeafId = leafEntryId
            },
        )
    }
}

/** 完整条目检查器；切换活动路径时保留所有行以及稳定的兄弟顺序。 */
@Composable
internal fun ConversationEntryPanelContent(
    state: ChatWindowState,
    conversation: ChatConversationUiState,
    modifier: Modifier,
) {
    var query by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var filter by remember(conversation.id) { mutableStateOf(ConversationEntryFilter.DEFAULT) }
    var collapsedIds by remember(conversation.id) { mutableStateOf(emptySet<String>()) }
    var operationError by remember(conversation.id) { mutableStateOf<String?>(null) }
    var pendingSummaryEntryId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var customSummaryEntryId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var customSummaryPrompt by remember(conversation.id) { mutableStateOf("") }
    var labelEditingEntryId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var labelText by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var contextMenuEntryId by remember(conversation.id) { mutableStateOf<String?>(null) }
    val labels = remember(conversation.entries) { allEntryLabels(conversation.entries) }
    val visibleIds = remember(conversation.entries, query.text, filter, labels) {
        visibleConversationEntryIds(conversation.entries, query.text, filter, labels.keys)
    }
    val rows = remember(conversation.entries, visibleIds, collapsedIds) {
        flattenConversationEntryTree(
            entries = conversation.entries,
            visibleIds = visibleIds,
            activeEntryId = conversation.activeEntryId,
            collapsedIds = collapsedIds,
        )
    }
    var selectedEntryId by remember(conversation.id) {
        mutableStateOf(nearestVisibleConversationEntryId(conversation.entries, conversation.activeEntryId, visibleIds))
    }
    val activePathIds = remember(conversation.entries, conversation.activeEntryId) {
        conversationEntryPath(conversation.entries, conversation.activeEntryId).mapTo(
            mutableSetOf(),
            ConversationEntry::id
        )
    }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query.text, filter) { collapsedIds = emptySet() }
    LaunchedEffect(rows) {
        if (rows.none { it.entry.id == selectedEntryId }) selectedEntryId = rows.firstOrNull()?.entry?.id
        if (rows.none { it.entry.id == labelEditingEntryId }) labelEditingEntryId = null
    }
    LaunchedEffect(conversation.entries) {
        val entryIds = conversation.entries.mapTo(mutableSetOf(), ConversationEntry::id)
        if (pendingSummaryEntryId !in entryIds) pendingSummaryEntryId = null
        if (customSummaryEntryId !in entryIds) customSummaryEntryId = null
    }

    val navigate: (String, BranchNavigationSummary) -> Unit = { entryId, summary ->
        operationError = null
        scope.launch {
            val result = state.conversationTreeController.navigateToEntry(
                conversationId = conversation.id,
                entryId = entryId,
                summary = summary,
            )
            operationError = result.message.takeUnless { result.succeeded }
        }
    }
    val requestSwitch: () -> Unit = {
        selectedEntryId?.takeIf { entryId ->
            isConversationTreeSwitchEnabled(
                selectedEntryId = entryId,
                activeEntryId = conversation.activeEntryId,
                inProgress = state.conversationTreeController.summaryInProgress,
            )
        }?.let { entryId ->
            operationError = null
            customSummaryPrompt = ""
            pendingSummaryEntryId = entryId
        }
    }
    val selectKeyboard: (String) -> Unit = { entryId ->
        selectedEntryId = entryId
        focusRequester.requestFocus()
        rows.indexOfFirst { it.entry.id == entryId }.takeIf { it >= 0 }?.let { index ->
            scope.launch { listState.ensureItemVisibleMinimal(index) }
        }
    }
    val toggleCollapsed: (ConversationEntryTreeRow) -> Unit = { row ->
        if (row.foldable) {
            collapsedIds =
                if (row.entry.id in collapsedIds) collapsedIds - row.entry.id else collapsedIds + row.entry.id
        }
    }
    val beginLabelEdit: (ConversationEntry) -> Unit = { entry ->
        selectedEntryId = entry.id
        contextMenuEntryId = null
        labelText = TextFieldValue(labels[entry.id].orEmpty())
        labelEditingEntryId = entry.id
    }
    val saveLabel: (ConversationEntry, String) -> Unit = { entry, value ->
        val result = state.conversationTreeController.setEntryLabel(conversation.id, entry.id, value)
        operationError = result.message.takeUnless { result.succeeded }
        if (result.succeeded) {
            labelEditingEntryId = null
            focusRequester.requestFocus()
        }
    }
    val copyEntry: (ConversationEntry) -> Unit = { entry ->
        operationError = copyConversationEntryText(entry)
    }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索条目") },
                modifier = Modifier.weight(1f),
            )
            Dropdown(
                menuContent = {
                    ConversationEntryFilter.entries.forEach { candidate ->
                        selectableItem(selected = filter == candidate, onClick = { filter = candidate }) {
                            Text(candidate.label)
                        }
                    }
                },
            ) { Text(filter.label) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                Text("没有匹配的条目", color = AppMuted, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (labelEditingEntryId != null) {
                                false
                            } else {
                                handleConversationTreeKeyEvent(
                                    event = event,
                                    rows = rows,
                                    selectedEntryId = selectedEntryId,
                                    collapsedIds = collapsedIds,
                                    onSelectEntry = selectKeyboard,
                                    onToggleCollapsed = toggleCollapsed,
                                    onSubmit = requestSwitch,
                                    onCopyEntry = copyEntry,
                                    onEditLabel = beginLabelEdit,
                                )
                            }
                        }
                        .focusable(),
                ) {
                    items(rows, key = { it.entry.id }) { row ->
                        ConversationEntryPanelRow(
                            row = row,
                            selected = row.entry.id == selectedEntryId,
                            onActivePath = row.entry.id in activePathIds,
                            isHead = row.entry.id == conversation.headEntryId,
                            collapsed = row.entry.id in collapsedIds,
                            label = labels[row.entry.id],
                            labelEditing = labelEditingEntryId == row.entry.id,
                            labelText = labelText,
                            contextMenuExpanded = contextMenuEntryId == row.entry.id,
                            onLabelTextChange = { labelText = it },
                            onSaveLabel = { value -> saveLabel(row.entry, value) },
                            onCancelLabelEdit = {
                                labelEditingEntryId = null
                                focusRequester.requestFocus()
                            },
                            onClick = {
                                selectedEntryId = row.entry.id
                                focusRequester.requestFocus()
                            },
                            onToggleCollapsed = { toggleCollapsed(row) },
                            onOpenContextMenu = { contextMenuEntryId = row.entry.id },
                            onDismissContextMenu = { contextMenuEntryId = null },
                            onCopyText = { copyEntry(row.entry) },
                            onEditLabel = { beginLabelEdit(row.entry) },
                        )
                    }
                }
                VerticalScrollbar(
                    scrollState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
        }
        ConversationTreeActionFooter(
            primaryEnabled = isConversationTreeSwitchEnabled(
                selectedEntryId = selectedEntryId,
                activeEntryId = conversation.activeEntryId,
                inProgress = state.conversationTreeController.summaryInProgress,
            ),
            inProgress = state.conversationTreeController.summaryInProgress,
            operationError = operationError,
            onPrimary = requestSwitch,
        )
    }
    pendingSummaryEntryId?.let { entryId ->
        ConversationTreeSummaryChoiceDialog(
            onChoose = { mode ->
                pendingSummaryEntryId = null
                if (mode == BranchSummaryMode.CUSTOM) {
                    customSummaryEntryId = entryId
                } else {
                    navigate(entryId, BranchNavigationSummary(mode))
                }
            },
            onDismiss = { pendingSummaryEntryId = null },
        )
    }
    customSummaryEntryId?.let { entryId ->
        ConversationTreeCustomSummaryDialog(
            initialPrompt = customSummaryPrompt,
            onConfirm = { prompt ->
                customSummaryPrompt = prompt
                customSummaryEntryId = null
                navigate(entryId, BranchNavigationSummary(BranchSummaryMode.CUSTOM, prompt))
            },
            onBack = {
                customSummaryEntryId = null
                pendingSummaryEntryId = entryId
            },
        )
    }
}
