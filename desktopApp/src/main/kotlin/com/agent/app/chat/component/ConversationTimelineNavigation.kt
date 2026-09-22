package com.agent.app.chat.component

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.conversationEntryPath
import kotlin.math.abs
import kotlin.math.roundToInt

/** 时间线导航中的一个稳定用户轮次及其预览正文。 */
internal data class TimelineTurnPresentation(
    val anchorId: String,
    val sourceUserEntryId: String?,
    val userText: String,
    val assistantText: String?,
)

/** 一个用户消息锚点在窗口坐标中的垂直范围。 */
internal data class TimelineTurnBounds(
    val top: Float,
    val bottom: Float,
)

/** 为当前可见路径生成用户轮次；树会话使用条目 ID，旧会话回退到用户序号。 */
internal fun buildTimelineTurnPresentations(
    conversation: ChatConversationUiState,
): List<TimelineTurnPresentation> {
    if (conversation.treeFormatVersion <= 0) return buildLegacyTimelineTurns(conversation)
    val turns = mutableListOf<TimelineTurnPresentation>()
    conversationEntryPath(conversation.entries, conversation.activeEntryId).forEach { entry ->
        when (entry) {
            is ConversationEntry.Message -> when (entry.message.role) {
                ChatRole.User -> turns += TimelineTurnPresentation(
                    anchorId = entry.id,
                    sourceUserEntryId = entry.id,
                    userText = entry.message.content,
                    assistantText = null,
                )

                ChatRole.Assistant -> updateLastAssistantPreview(turns, entry.message.content)
                ChatRole.System -> Unit
            }

            else -> Unit
        }
    }
    return turns
}

/** 将标签节点解析到被标记条目，其余节点保持自身标识。 */
internal fun conversationEntryNavigationAnchorId(
    entries: List<ConversationEntry>,
    entryId: String,
): String? {
    val entry = entries.firstOrNull { it.id == entryId } ?: return null
    return if (entry is ConversationEntry.Label) {
        entry.targetEntryId.takeIf { targetId -> entries.any { it.id == targetId } }
            ?: entry.parentId
    } else {
        entry.id
    }
}

/**
 * 返回包含目标条目的完整分支末端，避免为了定位历史节点截断该节点之后的时间线。
 * 当前路径优先，其次使用持久末端路径，其他分支选择最近创建的后代 leaf。
 */
internal fun conversationEntryNavigationLeaf(
    entries: List<ConversationEntry>,
    entryId: String,
    activeEntryId: String?,
    headEntryId: String?,
): String? {
    val targetId = conversationEntryNavigationAnchorId(entries, entryId) ?: return null
    if (conversationEntryPath(entries, activeEntryId).any { it.id == targetId }) return activeEntryId
    if (conversationEntryPath(entries, headEntryId).any { it.id == targetId }) return headEntryId

    val entriesById = entries.associateBy(ConversationEntry::id)
    val childrenByParent = entries
        .asSequence()
        .filterNot { it is ConversationEntry.Label }
        .groupBy(ConversationEntry::parentId)
    val descendants = mutableSetOf<String>()
    val pending = ArrayDeque<String>().apply { add(targetId) }
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (descendants.add(current)) {
            childrenByParent[current].orEmpty().forEach { child -> pending.add(child.id) }
        }
    }
    return descendants
        .asSequence()
        .mapNotNull(entriesById::get)
        .filter { entry -> childrenByParent[entry.id].isNullOrEmpty() }
        .maxWithOrNull(compareBy<ConversationEntry>(ConversationEntry::createdAt).thenBy(ConversationEntry::id))
        ?.id
        ?: targetId
}

/**
 * 为每个渲染时间线段返回对应条目 ID；工具调用与结果共享同一段，隐藏设置条目贴近最近内容。
 */
internal fun buildTimelineDisplayEntryIds(
    conversation: ChatConversationUiState,
): List<Set<String>> {
    if (conversation.treeFormatVersion <= 0) return emptyList()
    val slots = mutableListOf<TimelineEntryProjectionSlot>()
    val leadingHiddenIds = linkedSetOf<String>()

    fun appendVisible(entryId: String, toolName: String? = null, toolCallId: String? = null) {
        val entryIds = linkedSetOf<String>().apply {
            addAll(leadingHiddenIds)
            add(entryId)
        }
        leadingHiddenIds.clear()
        slots += TimelineEntryProjectionSlot(entryIds, toolName, toolCallId)
    }

    fun appendHidden(entryId: String) {
        val latest = slots.lastOrNull()
        if (latest == null) leadingHiddenIds += entryId else latest.entryIds += entryId
    }

    conversationEntryPath(conversation.entries, conversation.activeEntryId).forEach { entry ->
        when (entry) {
            is ConversationEntry.Message -> appendVisible(entry.id)
            is ConversationEntry.Reasoning -> appendVisible(entry.id)
            is ConversationEntry.ToolCall -> appendVisible(entry.id, entry.toolName, entry.toolCallId)
            is ConversationEntry.ToolResult -> {
                val matchingIndex = slots.indexOfLast { slot ->
                    !slot.toolCompleted && slot.toolName != null &&
                            (entry.toolCallId?.let { it == slot.toolCallId } ?: (entry.toolName == slot.toolName))
                }
                if (matchingIndex >= 0) {
                    slots[matchingIndex].entryIds += entry.id
                    slots[matchingIndex].toolCompleted = true
                } else {
                    appendVisible(entry.id, entry.toolName, entry.toolCallId)
                    slots.last().toolCompleted = true
                }
            }

            is ConversationEntry.Answers -> appendVisible(entry.id)
            is ConversationEntry.Custom -> appendVisible(entry.id)
            is ConversationEntry.BranchSummary,
            is ConversationEntry.Label,
            is ConversationEntry.ModelChange,
            is ConversationEntry.ReasoningEffortChange,
                -> appendHidden(entry.id)
        }
    }
    if (leadingHiddenIds.isNotEmpty() && slots.isNotEmpty()) slots.last().entryIds += leadingHiddenIds

    var slotIndex = 0
    return groupTimelineItems(conversation.items).map { displayItem ->
        buildSet {
            repeat(displayItem.itemCount) {
                slots.getOrNull(slotIndex++)?.entryIds?.let(::addAll)
            }
        }
    }
}

/** 条目投影到单个时间线项时使用的临时关联。 */
private data class TimelineEntryProjectionSlot(
    val entryIds: MutableSet<String>,
    val toolName: String? = null,
    val toolCallId: String? = null,
    var toolCompleted: Boolean = false,
)

/** 旧线性会话按用户消息序号生成稳定于当前快照的回退锚点。 */
private fun buildLegacyTimelineTurns(
    conversation: ChatConversationUiState,
): List<TimelineTurnPresentation> {
    val turns = mutableListOf<TimelineTurnPresentation>()
    conversation.items.filterIsInstance<ChatMessageItem>().forEach { item ->
        when (item.message.role) {
            ChatRole.User -> turns += TimelineTurnPresentation(
                anchorId = "legacy-user-${turns.size}",
                sourceUserEntryId = null,
                userText = item.message.content,
                assistantText = null,
            )

            ChatRole.Assistant -> updateLastAssistantPreview(turns, item.message.content)
            ChatRole.System -> Unit
        }
    }
    return turns
}

/** 只有树格式轮次具备来源条目标识时，才允许标记为当前运行中的操作。 */
internal fun isTimelineOperationInProgress(
    operationEntryId: String?,
    sourceUserEntryId: String?,
): Boolean = sourceUserEntryId != null && operationEntryId == sourceUserEntryId

/** 用本轮最后一条非空助手正文替换预览，忽略流式阶段的空片段。 */
private fun updateLastAssistantPreview(
    turns: MutableList<TimelineTurnPresentation>,
    content: String,
) {
    val normalized = content.trim()
    if (normalized.isBlank() || turns.isEmpty()) return
    turns[turns.lastIndex] = turns.last().copy(assistantText = normalized)
}

/** 根据视口焦点线选择当前轮次，优先取焦点线上方最近的用户消息。 */
internal fun activeTimelineTurnIndex(
    turns: List<TimelineTurnPresentation>,
    anchorTops: Map<String, Float>,
    viewportTop: Float,
    viewportBottom: Float,
): Int {
    if (turns.isEmpty()) return -1
    val focusLine = viewportTop + (viewportBottom - viewportTop) * 0.32f
    return turns.indexOfLast { turn -> (anchorTops[turn.anchorId] ?: Float.POSITIVE_INFINITY) <= focusLine }
        .coerceAtLeast(0)
}

/** 将轨道上的纵向坐标映射到最近轮次。 */
internal fun timelineTurnIndexAtOffset(
    offsetY: Float,
    railHeight: Float,
    turnCount: Int,
): Int {
    if (turnCount <= 1 || railHeight <= 0f) return 0
    return ((offsetY.coerceIn(0f, railHeight) / railHeight) * (turnCount - 1))
        .roundToInt()
        .coerceIn(0, turnCount - 1)
}

/** T3 式刻度宽度：无交互时等宽，悬浮或键盘选择后才逐级强调邻域。 */
internal fun timelineTickWidthDp(index: Int, emphasisIndex: Int?): Int {
    if (emphasisIndex == null) return 8
    return when (abs(index - emphasisIndex)) {
        0 -> 24
        1 -> 16
        2 -> 10
        else -> 8
    }
}

/** 将稀疏轮次收拢为紧凑刻度组；轮次较多时才使用完整可用高度。 */
internal fun timelineTickTravelDp(
    railHeightDp: Float,
    turnCount: Int,
    tickHitHeightDp: Float = 14f,
    maxSpacingDp: Float = 14f,
): Float {
    if (turnCount <= 1) return 0f
    val availableTravel = (railHeightDp - tickHitHeightDp).coerceAtLeast(0f)
    return minOf(availableTravel, maxSpacingDp * (turnCount - 1))
}

/** 仅在非紧凑布局、至少两轮且正文左侧留白足够时显示导航轨。 */
internal fun shouldShowTimelineNavigation(
    turnCount: Int,
    compact: Boolean,
    leftGutterDp: Float,
): Boolean = turnCount >= 2 && !compact && leftGutterDp >= 48f

/** 根据实际测量边界换算正文左侧留白，防止按理想宽度推算后让轨道覆盖正文。 */
internal fun timelineLeftGutterDp(
    viewportLeftPx: Float,
    contentLeftPx: Float,
    density: Float,
): Float = if (density <= 0f) {
    0f
} else {
    ((contentLeftPx - viewportLeftPx) / density).coerceAtLeast(0f)
}

/** 将窗口坐标中的用户消息位置换算成滚动容器目标值。 */
internal fun timelineScrollTarget(
    currentScroll: Int,
    anchorTop: Float,
    viewportTop: Float,
    maxScroll: Int,
    topInsetPx: Float,
): Int = (currentScroll + anchorTop - viewportTop - topInsetPx)
    .roundToInt()
    .coerceIn(0, maxScroll)
