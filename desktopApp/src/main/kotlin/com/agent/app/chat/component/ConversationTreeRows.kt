@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.OffsetPopupPositionProvider
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** 分支概览中的固定缩进末端。 */
@Composable
internal fun ConversationBranchPanelRow(
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
            modifier = Modifier.weight(1f),
        )
        if (item.skippedEntryCount > 0) Text("+${item.skippedEntryCount}", color = AppMuted)
        if (item.isHeadLeaf) Text(" 末端", color = AppMuted)
        if (item.isActiveLeaf) Text(" 当前", color = AppAccent, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 完整条目视图中的一行；右键操作和行内标签编辑不会改变活动 leaf。
 */
@Composable
internal fun ConversationEntryPanelRow(
    row: ConversationEntryTreeRow,
    selected: Boolean,
    onActivePath: Boolean,
    isHead: Boolean,
    collapsed: Boolean,
    label: String?,
    labelEditing: Boolean,
    labelText: TextFieldValue,
    contextMenuExpanded: Boolean,
    onLabelTextChange: (TextFieldValue) -> Unit,
    onSaveLabel: (String) -> Unit,
    onCancelLabelEdit: () -> Unit,
    onClick: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onOpenContextMenu: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onCopyText: () -> Unit,
    onEditLabel: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember(row.entry.id) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val labelFocusRequester = remember(row.entry.id) { androidx.compose.ui.focus.FocusRequester() }
    var anchorHeightPixels by remember(row.entry.id) { mutableStateOf(0) }
    var contextMenuClickPosition by remember(row.entry.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val contextMenuOffset = contextMenuOffsetForPointer(
        pointerPosition = contextMenuClickPosition,
        anchorHeightPixels = anchorHeightPixels,
        density = density.density,
    )
    LaunchedEffect(labelEditing) {
        if (labelEditing) labelFocusRequester.requestFocus()
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged { anchorHeightPixels = it.height }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    contextMenuClickPosition = event.changes.firstOrNull()?.position ?: Offset.Zero
                    onClick()
                    onOpenContextMenu()
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(if (labelEditing) 40.dp else 34.dp)
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
            Text(
                conversationEntryConnector(row),
                color = AppMuted,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
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
            if (labelEditing) {
                TextField(
                    value = labelText,
                    onValueChange = onLabelTextChange,
                    placeholder = { Text("标签，留空可清除") },
                    modifier = Modifier.weight(1f)
                        .then(Modifier.focusRequester(labelFocusRequester))
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter -> {
                                    onSaveLabel(labelText.text)
                                    true
                                }

                                Key.Escape -> {
                                    onCancelLabelEdit()
                                    true
                                }

                                else -> false
                            }
                        },
                )
            } else {
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
                        modifier = Modifier.widthIn(max = 96.dp),
                    )
                }
            }
        }
        if (contextMenuExpanded) {
            PopupMenu(
                onDismissRequest = {
                    onDismissContextMenu()
                    true
                },
                popupPositionProvider = remember(contextMenuOffset, density.density) {
                    OffsetPopupPositionProvider(contextMenuOffset, density.density)
                },
                modifier = Modifier.width(148.dp),
            ) {
                selectableItem(selected = false, onClick = {
                    onDismissContextMenu()
                    onCopyText()
                }) { Text("复制文本") }
                selectableItem(selected = false, onClick = {
                    onDismissContextMenu()
                    onEditLabel()
                }) { Text(if (label == null) "添加标签…" else "编辑标签…") }
            }
        }
    }
}
