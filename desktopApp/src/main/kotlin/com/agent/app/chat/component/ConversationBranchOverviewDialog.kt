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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.BranchNavigationSummary
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelDialog
import com.agent.app.design.LocalDesktopPalette
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 展示只包含真实分叉末端的 pi-web 式概览。 */
@Composable
internal fun ConversationBranchOverviewDialog(
    state: ChatWindowState,
    conversation: ChatConversationUiState,
    onShowAllEntries: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(conversation.id) { mutableStateOf(TextFieldValue()) }
    var operationError by remember(conversation.id) { mutableStateOf<String?>(null) }
    val allItems = remember(conversation.entries, conversation.activeEntryId, conversation.headEntryId) {
        buildConversationBranchOverview(
            conversation.entries,
            conversation.activeEntryId,
            conversation.headEntryId,
        )
    }
    val items = remember(allItems, query.text) {
        val normalizedQuery = query.text.trim()
        if (normalizedQuery.isEmpty()) allItems else allItems.filter { item ->
            item.preview.contains(normalizedQuery, ignoreCase = true)
        }
    }
    var selectedLeafId by remember(conversation.id) {
        mutableStateOf(
            allItems.firstOrNull { it.isActiveLeaf }?.leafEntryId
                ?: allItems.firstOrNull { it.isHeadLeaf }?.leafEntryId
                ?: allItems.firstOrNull()?.leafEntryId,
        )
    }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(items) {
        if (items.none { it.leafEntryId == selectedLeafId }) selectedLeafId = items.firstOrNull()?.leafEntryId
    }
    LaunchedEffect(conversation.id) {
        items.indexOfFirst { it.leafEntryId == selectedLeafId }
            .takeIf { it >= 0 }
            ?.let { index -> listState.scrollToItem((index - BRANCH_OVERVIEW_INITIAL_CENTER_OFFSET).coerceAtLeast(0)) }
    }

    val submit: () -> Unit = {
        selectedLeafId?.let { leafEntryId ->
            operationError = null
            scope.launch {
                val result = state.conversationTreeController.switchToLeaf(
                    conversationId = conversation.id,
                    leafEntryId = leafEntryId,
                    summary = BranchNavigationSummary(),
                )
                if (result.succeeded) onDismiss() else operationError = result.message
            }
        }
    }
    val keyboardSelect: (Int) -> Unit = { delta ->
        val currentIndex = items.indexOfFirst { it.leafEntryId == selectedLeafId }.coerceAtLeast(0)
        val nextIndex = (currentIndex + delta).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        items.getOrNull(nextIndex)?.let { item ->
            selectedLeafId = item.leafEntryId
            scope.launch { listState.ensureItemVisibleMinimal(nextIndex) }
        }
    }

    JewelDialog(
        title = "会话树 · ${conversation.title}",
        confirmLabel = "切换到分支",
        confirmEnabled = selectedLeafId != null && !state.conversationTreeController.summaryInProgress,
        width = 940.dp,
        height = 660.dp,
        contentPadding = PaddingValues(0.dp),
        onDismiss = onDismiss,
        onConfirm = submit,
    ) {
        val palette = LocalDesktopPalette.current
        Column(Modifier.fillMaxSize().background(palette.panelBackground)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索分支") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onShowAllEntries) { Text("查看全部条目") }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (allItems.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("当前会话没有分支", color = AppText, fontWeight = FontWeight.SemiBold)
                        Text("线性历史不会被伪装成层层缩进的树。", color = AppMuted)
                        OutlinedButton(onClick = onShowAllEntries) { Text("查看全部条目") }
                    }
                } else if (items.isEmpty()) {
                    Text("没有匹配的分支", color = AppMuted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> { keyboardSelect(-1); true }
                                    Key.DirectionDown -> { keyboardSelect(1); true }
                                    Key.Enter -> { submit(); true }
                                    else -> false
                                }
                            }
                            .focusable(),
                    ) {
                        items(items, key = ConversationBranchOverviewItem::leafEntryId) { item ->
                            ConversationBranchOverviewRow(
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
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
            operationError?.let { error ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
                Text(error, color = palette.danger, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

/** 绘制一个固定缩进、可直接切换到末端的分支摘要行。 */
@Composable
private fun ConversationBranchOverviewRow(
    item: ConversationBranchOverviewItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember(item.leafEntryId) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected -> palette.selectedBackground
        hovered -> palette.hoverBackground
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(start = (10 + item.depth * 14).dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("└─", color = AppMuted, modifier = Modifier.width(28.dp))
        Text(
            item.preview,
            color = AppText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.skippedEntryCount > 0) {
            Text("+${item.skippedEntryCount}", color = AppMuted, modifier = Modifier.padding(start = 10.dp))
        }
        if (item.isHeadLeaf) Text("  末端", color = AppAccent, fontWeight = FontWeight.SemiBold)
        if (item.isActiveLeaf) Text("  当前", color = AppAccent, fontWeight = FontWeight.SemiBold)
    }
}

private const val BRANCH_OVERVIEW_INITIAL_CENTER_OFFSET = 6
