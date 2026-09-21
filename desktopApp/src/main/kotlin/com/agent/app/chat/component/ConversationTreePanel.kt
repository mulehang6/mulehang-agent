@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.agent.app.design.LocalDesktopPalette
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.conversationEntryPath
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 分支概览只列出真实分叉的末端，并在切换后保持面板与完整图可见。 */
@Composable
internal fun ConversationBranchPanelContent(
    state: ChatWindowState,
    conversation: ChatConversationUiState,
    onShowAllEntries: () -> Unit,
    modifier: Modifier,
) {
    var query by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var operationError by remember(conversation.id) { mutableStateOf<String?>(null) }
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
    val switchPath: () -> Unit = {
        selectedLeafId?.let { leafEntryId ->
            scope.launch {
                val result = state.conversationTreeController.switchToLeaf(
                    conversationId = conversation.id,
                    leafEntryId = leafEntryId,
                    summary = BranchNavigationSummary(),
                )
                operationError = result.message.takeUnless { result.succeeded }
            }
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
                    OutlinedButton(onClick = onShowAllEntries) { Text("查看全部条目") }
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

                                    Key.Enter -> {
                                        switchPath(); true
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
            conversation = conversation,
            state = state,
            primaryLabel = "切换到分支",
            primaryEnabled = selectedLeafId != null && !state.conversationTreeController.summaryInProgress,
            operationError = operationError,
            onPrimary = switchPath,
            onError = { operationError = it },
        )
    }
}

/** 分支概览中的固定缩进末端。 */
@Composable
private fun ConversationBranchPanelRow(
    item: ConversationBranchOverviewItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember(item.leafEntryId) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    selected -> palette.selectedBackground
                    hovered -> palette.hoverBackground
                    else -> Color.Transparent
                },
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(start = (10 + item.depth * 12).dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("└─", color = AppMuted, modifier = Modifier.width(28.dp))
        Text(
            item.preview,
            color = AppText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (item.skippedEntryCount > 0) Text("+${item.skippedEntryCount}", color = AppMuted)
        if (item.isHeadLeaf) Text(" 末端", color = AppMuted)
        if (item.isActiveLeaf) Text(" 当前", color = AppAccent, fontWeight = FontWeight.SemiBold)
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
    var summaryMode by remember(conversation.id) { mutableStateOf(BranchSummaryMode.NONE) }
    var customSummary by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var operationError by remember(conversation.id) { mutableStateOf<String?>(null) }
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
    val selectedEntry = conversation.entries.firstOrNull { it.id == selectedEntryId }
    var labelText by remember(conversation.id, selectedEntryId) {
        mutableStateOf(TextFieldValue(labels[selectedEntryId].orEmpty()))
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
    val willLeaveActiveBranch = selectedEntryId?.let {
        state.conversationTreeController.wouldLeaveActiveBranch(conversation.id, it)
    } == true

    LaunchedEffect(query.text, filter) { collapsedIds = emptySet() }
    LaunchedEffect(rows) {
        if (rows.none { it.entry.id == selectedEntryId }) selectedEntryId = rows.firstOrNull()?.entry?.id
    }
    LaunchedEffect(selectedEntryId, labels) { labelText = TextFieldValue(labels[selectedEntryId].orEmpty()) }

    val switchPath: () -> Unit = {
        selectedEntryId?.let { entryId ->
            scope.launch {
                val result = state.conversationTreeController.navigateToEntry(
                    conversationId = conversation.id,
                    entryId = entryId,
                    summary = BranchNavigationSummary(summaryMode, customSummary.text),
                )
                operationError = result.message.takeUnless { result.succeeded }
            }
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
                            handleConversationTreeKeyEvent(
                                event = event,
                                rows = rows,
                                selectedEntryId = selectedEntryId,
                                collapsedIds = collapsedIds,
                                onSelectEntry = selectKeyboard,
                                onToggleCollapsed = toggleCollapsed,
                                onSubmit = switchPath,
                            )
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
                            onClick = {
                                selectedEntryId = row.entry.id
                                focusRequester.requestFocus()
                            },
                            onToggleCollapsed = { toggleCollapsed(row) },
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDesktopPalette.current.line))
        ConversationEntryTreeInspector(
            selectedEntry = selectedEntry,
            currentLabel = selectedEntryId?.let(labels::get),
            labelText = labelText,
            onLabelTextChange = { labelText = it },
            onSaveLabel = { entry ->
                val result = state.conversationTreeController.setEntryLabel(conversation.id, entry.id, labelText.text)
                operationError = result.message.takeUnless { result.succeeded }
            },
            willLeaveActiveBranch = willLeaveActiveBranch,
            summaryMode = summaryMode,
            onSummaryModeChange = { summaryMode = it },
            customSummary = customSummary,
            onCustomSummaryChange = { customSummary = it },
            summaryInProgress = state.conversationTreeController.summaryInProgress,
            operationError = operationError,
        )
        ConversationTreeActionFooter(
            conversation = conversation,
            state = state,
            primaryLabel = "切换到此路径",
            primaryEnabled = selectedEntry != null && !state.conversationTreeController.summaryInProgress,
            operationError = null,
            onPrimary = switchPath,
            onError = { operationError = it },
        )
    }
}

/** 完整条目视图中的一行，活动路径只改变强调，不改变列表排序。 */
@Composable
private fun ConversationEntryPanelRow(
    row: ConversationEntryTreeRow,
    selected: Boolean,
    onActivePath: Boolean,
    isHead: Boolean,
    collapsed: Boolean,
    label: String?,
    onClick: () -> Unit,
    onToggleCollapsed: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember(row.entry.id) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> palette.selectedBackground
                    hovered -> palette.hoverBackground
                    else -> Color.Transparent
                },
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(conversationEntryConnector(row), color = AppMuted, fontFamily = FontFamily.Monospace, maxLines = 1)
        Box(
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                .clickable(enabled = row.foldable, onClick = onToggleCollapsed),
            contentAlignment = Alignment.Center,
        ) {
            if (row.foldable) Text(if (collapsed) "›" else "⌄", color = AppMuted)
        }
        Text(if (onActivePath) "●" else "", color = AppAccent, modifier = Modifier.width(15.dp))
        Text(
            text = entryKindLabel(row.entry),
            color = if (onActivePath) AppText else AppMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(68.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entryPreview(row.entry).ifBlank { "（无文本）" },
            color = if (onActivePath) AppText else AppMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isHead) Text(" 末端", color = AppMuted, maxLines = 1)
        label?.let {
            Text(
                " #$it",
                color = AppAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }
    }
}
