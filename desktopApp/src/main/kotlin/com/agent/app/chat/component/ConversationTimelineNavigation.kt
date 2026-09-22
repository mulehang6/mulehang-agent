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
