@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)
@file:Suppress("UnstableApiUsage")

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.BranchNavigationSummary
import com.agent.app.chat.state.BranchSummaryMode
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelDialog
import com.agent.app.design.LocalDesktopPalette
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.conversationEntryPath
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 展示采用 Pi 缩进语义、IDEA 设置页布局的会话条目树。 */
@Composable
internal fun ConversationEntryInspectorDialog(
    state: ChatWindowState,
    conversation: ChatConversationUiState,
    onShowOverview: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var filter by remember(conversation.id) { mutableStateOf(ConversationEntryFilter.DEFAULT) }
    var collapsedIds by remember(conversation.id) { mutableStateOf(emptySet<String>()) }
    var summaryMode by remember(conversation.id) { mutableStateOf(BranchSummaryMode.NONE) }
    var customSummary by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var operationError by remember(conversation.id) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val labels = remember(conversation.entries) { allEntryLabels(conversation.entries) }
    val visibleIds = remember(conversation.entries, query.text, filter, labels) {
        visibleConversationEntryIds(conversation.entries, query.text, filter, labels.keys)
    }
    var selectedEntryId by remember(conversation.id) {
        mutableStateOf(
            nearestVisibleConversationEntryId(conversation.entries, conversation.activeEntryId, visibleIds)
                ?: visibleIds.firstOrNull(),
        )
    }
    val rows = remember(conversation.entries, visibleIds, conversation.activeEntryId, collapsedIds) {
        flattenConversationEntryTree(
            entries = conversation.entries,
            visibleIds = visibleIds,
            activeEntryId = conversation.activeEntryId,
            collapsedIds = collapsedIds,
        )
    }
    val activePathIds = remember(conversation.entries, conversation.activeEntryId) {
        conversationEntryPath(conversation.entries, conversation.activeEntryId)
            .mapTo(mutableSetOf(), ConversationEntry::id)
    }
    val selectedEntry = conversation.entries.firstOrNull { it.id == selectedEntryId }
    var labelText by remember(conversation.id, selectedEntryId) {
        mutableStateOf(TextFieldValue(labels[selectedEntryId].orEmpty()))
    }
    val willLeaveActiveBranch = selectedEntryId?.let { entryId ->
        state.conversationTreeController.wouldLeaveActiveBranch(conversation.id, entryId)
    } == true
    val listState = rememberLazyListState()
    val treeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(query.text, filter) {
        collapsedIds = emptySet()
    }
    LaunchedEffect(rows, visibleIds, conversation.activeEntryId) {
        if (rows.none { it.entry.id == selectedEntryId }) {
            selectedEntryId = nearestVisibleConversationEntryId(
                conversation.entries,
                conversation.activeEntryId,
                rows.mapTo(mutableSetOf()) { it.entry.id },
            ) ?: rows.firstOrNull()?.entry?.id
        }
    }
    LaunchedEffect(conversation.id) {
        rows.indexOfFirst { it.entry.id == selectedEntryId }
            .takeIf { it >= 0 }
            ?.let { index -> listState.scrollToItem((index - CONVERSATION_TREE_INITIAL_CENTER_OFFSET).coerceAtLeast(0)) }
    }
    LaunchedEffect(selectedEntryId, labels) {
        labelText = TextFieldValue(labels[selectedEntryId].orEmpty())
    }

    val submitNavigation: () -> Unit = {
        selectedEntryId?.let { entryId ->
            operationError = null
            coroutineScope.launch {
                val result = state.conversationTreeController.navigateToEntry(
                    conversationId = conversation.id,
                    entryId = entryId,
                    summary = BranchNavigationSummary(summaryMode, customSummary.text),
                )
                if (result.succeeded) onDismiss() else operationError = result.message
            }
        }
    }
    val selectEntry: (String) -> Unit = { entryId ->
        selectedEntryId = entryId
        treeFocusRequester.requestFocus()
    }
    val selectEntryFromKeyboard: (String) -> Unit = { entryId ->
        selectedEntryId = entryId
        treeFocusRequester.requestFocus()
        rows.indexOfFirst { row -> row.entry.id == entryId }
            .takeIf { it >= 0 }
            ?.let { index ->
                coroutineScope.launch { listState.ensureItemVisibleMinimal(index) }
            }
    }
    val toggleCollapsed: (ConversationEntryTreeRow) -> Unit = { row ->
        if (row.foldable) {
            selectedEntryId = row.entry.id
            collapsedIds = if (row.entry.id in collapsedIds) {
                collapsedIds - row.entry.id
            } else {
                collapsedIds + row.entry.id
            }
            treeFocusRequester.requestFocus()
        }
    }

    JewelDialog(
        title = "会话树 · ${conversation.title}",
        confirmLabel = "导航到条目",
        confirmEnabled = selectedEntry != null && !state.conversationTreeController.summaryInProgress,
        width = 1000.dp,
        height = 720.dp,
        contentPadding = PaddingValues(0.dp),
        onDismiss = onDismiss,
        onConfirm = submitNavigation,
    ) {
        ConversationEntryTreeWorkspace(
            conversation = conversation,
            query = query,
            onQueryChange = { query = it },
            filter = filter,
            onFilterChange = { filter = it },
            labels = labels,
            rows = rows,
            selectedEntryId = selectedEntryId,
            activePathIds = activePathIds,
            collapsedIds = collapsedIds,
            listState = listState,
            focusRequester = treeFocusRequester,
            onSelectEntry = selectEntry,
            onToggleCollapsed = toggleCollapsed,
            onTreeKeyEvent = { event ->
                handleConversationTreeKeyEvent(
                    event = event,
                    rows = rows,
                    selectedEntryId = selectedEntryId,
                    collapsedIds = collapsedIds,
                    onSelectEntry = selectEntryFromKeyboard,
                    onToggleCollapsed = toggleCollapsed,
                    onSubmit = submitNavigation,
                )
            },
            selectedEntry = selectedEntry,
            labelText = labelText,
            onLabelTextChange = { labelText = it },
            onSaveLabel = { entry ->
                val result = state.conversationTreeController.setEntryLabel(
                    conversation.id,
                    entry.id,
                    labelText.text,
                )
                operationError = result.message.takeUnless { result.succeeded }
            },
            willLeaveActiveBranch = willLeaveActiveBranch,
            summaryMode = summaryMode,
            onSummaryModeChange = { summaryMode = it },
            customSummary = customSummary,
            onCustomSummaryChange = { customSummary = it },
            summaryInProgress = state.conversationTreeController.summaryInProgress,
            operationError = operationError,
            onShowOverview = onShowOverview,
        )
    }
}

/** 绘制与 IDEA 设置页相同的信息架构：左侧导航、单像素分隔线和右侧内容。 */
@Composable
private fun ConversationEntryTreeWorkspace(
    conversation: ChatConversationUiState,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    filter: ConversationEntryFilter,
    onFilterChange: (ConversationEntryFilter) -> Unit,
    labels: Map<String, String>,
    rows: List<ConversationEntryTreeRow>,
    selectedEntryId: String?,
    activePathIds: Set<String>,
    collapsedIds: Set<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusRequester: FocusRequester,
    onSelectEntry: (String) -> Unit,
    onToggleCollapsed: (ConversationEntryTreeRow) -> Unit,
    onTreeKeyEvent: (KeyEvent) -> Boolean,
    selectedEntry: ConversationEntry?,
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
    onShowOverview: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val filterCounts = remember(conversation.entries, labels) {
        ConversationEntryFilter.entries.associateWith { candidate ->
            visibleConversationEntryIds(conversation.entries, "", candidate, labels.keys).size
        }
    }
    Row(modifier = Modifier.fillMaxSize().background(palette.panelBackground)) {
        Column(
            modifier = Modifier.width(230.dp).fillMaxHeight()
                .background(palette.sidebarBackground)
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Text("查找条目", color = AppText, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Text("显示", color = AppMuted)
            Spacer(Modifier.height(6.dp))
            ConversationEntryFilter.entries.forEach { candidate ->
                ConversationTreeFilterItem(
                    filter = candidate,
                    count = filterCounts.getValue(candidate),
                    selected = filter == candidate,
                    onClick = { onFilterChange(candidate) },
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.line))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("会话树", color = AppMuted)
                Text("  ›  ", color = AppMuted)
                Text(
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${rows.size} 个条目", color = AppMuted)
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onShowOverview) { Text("分支概览") }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) {
                    Text("没有匹配的条目", color = AppMuted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent(onTreeKeyEvent)
                            .focusable(),
                    ) {
                        items(rows, key = { it.entry.id }) { row ->
                            ConversationEntryTreeRowItem(
                                row = row,
                                selected = row.entry.id == selectedEntryId,
                                onActivePath = row.entry.id in activePathIds,
                                collapsed = row.entry.id in collapsedIds,
                                label = labels[row.entry.id],
                                onClick = { onSelectEntry(row.entry.id) },
                                onToggleCollapsed = { onToggleCollapsed(row) },
                            )
                        }
                    }
                    VerticalScrollbar(
                        scrollState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
            ConversationEntryTreeInspector(
                selectedEntry = selectedEntry,
                currentLabel = selectedEntry?.id?.let(labels::get),
                labelText = labelText,
                onLabelTextChange = onLabelTextChange,
                onSaveLabel = onSaveLabel,
                willLeaveActiveBranch = willLeaveActiveBranch,
                summaryMode = summaryMode,
                onSummaryModeChange = onSummaryModeChange,
                customSummary = customSummary,
                onCustomSummaryChange = onCustomSummaryChange,
                summaryInProgress = summaryInProgress,
                operationError = operationError,
            )
        }
    }
}

private const val CONVERSATION_TREE_INITIAL_CENTER_OFFSET = 7

/** 绘制设置页左侧的无动画筛选项。 */
@Composable
private fun ConversationTreeFilterItem(
    filter: ConversationEntryFilter,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected -> palette.selectedBackground
        hovered -> palette.hoverBackground
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(filter.label, color = if (selected) AppText else AppMuted, maxLines = 1, modifier = Modifier.weight(1f))
        Text(count.toString(), color = AppMuted)
    }
}

/** 绘制一条不会因线性历史持续右移的扁平树行。 */
@Composable
private fun ConversationEntryTreeRowItem(
    row: ConversationEntryTreeRow,
    selected: Boolean,
    onActivePath: Boolean,
    collapsed: Boolean,
    label: String?,
    onClick: () -> Unit,
    onToggleCollapsed: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember(row.entry.id) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected -> palette.selectedBackground
        hovered -> palette.hoverBackground
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = conversationEntryConnector(row),
            color = AppMuted,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        Box(
            modifier = Modifier.size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = row.foldable, onClick = onToggleCollapsed),
            contentAlignment = Alignment.Center,
        ) {
            if (row.foldable) Text(if (collapsed) "›" else "⌄", color = AppMuted)
        }
        Text(if (onActivePath) "●" else "", color = AppAccent, modifier = Modifier.width(16.dp))
        Text(
            text = entryKindLabel(row.entry),
            color = AppMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entryPreview(row.entry).ifBlank { "（无文本）" },
            color = AppText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        label?.let {
            Text(
                text = "  #$it",
                color = AppAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}
