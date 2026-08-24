@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.agent.app.chat.presentation.*
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.*
import com.agent.app.tool.component.EditorDiffPreview
import com.agent.shared.chat.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
/**
 * 时间线在渲染前使用的展示段，不改变底层会话事件。
 */
internal sealed interface TimelineDisplayItem {
    /** 此展示段包含的原始时间线项数量。 */
    val itemCount: Int

    /** 不参与工具合并的普通时间线项。 */
    data class Content(val item: ConversationItem) : TimelineDisplayItem {
        override val itemCount: Int = 1
    }

    /** 相邻的工具调用组成的展示组，保留原始事件的先后顺序。 */
    data class ToolGroup(val items: List<ToolEventItem>) : TimelineDisplayItem {
        override val itemCount: Int = items.size
    }

    /** 未被正文、工具或状态事件隔开的思考片段视为同一段思考。 */
    data class ReasoningGroup(val items: List<ReasoningItem>) : TimelineDisplayItem {
        override val itemCount: Int = items.size
    }
}

/**
 * 合并相邻工具调用和思考片段；状态文本与其他时间线项均构成明确边界。
 */
internal fun groupTimelineItems(
    items: List<ConversationItem>,
): List<TimelineDisplayItem> {
    val result = mutableListOf<TimelineDisplayItem>()
    val pendingTools = mutableListOf<ToolEventItem>()
    val pendingReasoning = mutableListOf<ReasoningItem>()
    fun flushTools() {
        when (pendingTools.size) {
            0 -> Unit
            1 -> result += TimelineDisplayItem.Content(pendingTools.single())
            else -> result += TimelineDisplayItem.ToolGroup(pendingTools.toList())
        }
        pendingTools.clear()
    }
    fun flushReasoning() {
        when (pendingReasoning.size) {
            0 -> Unit
            1 -> result += TimelineDisplayItem.Content(pendingReasoning.single())
            else -> result += TimelineDisplayItem.ReasoningGroup(pendingReasoning.toList())
        }
        pendingReasoning.clear()
    }
    items.forEach { item ->
        when (item) {
            is ToolEventItem -> when {
                isAskUserToolEvent(item) -> {
                    flushReasoning()
                    flushTools()
                }
                item.status != ToolEventStatus.Status -> {
                    flushReasoning()
                    pendingTools += item
                }
                else -> {
                    flushReasoning()
                    flushTools()
                    result += TimelineDisplayItem.Content(item)
                }
            }

            is ReasoningItem -> {
                flushTools()
                pendingReasoning += item
            }

            else -> {
                flushReasoning()
                flushTools()
                result += TimelineDisplayItem.Content(item)
            }
        }
    }
    flushReasoning()
    flushTools()
    return result
}

/**
 * 将连续的 provider reasoning part 合成为一个可展开的展示项，同时保留完整文本和总耗时。
 */
internal fun mergeReasoningItems(items: List<ReasoningItem>): ReasoningItem {
    require(items.isNotEmpty()) { "Reasoning group must not be empty." }
    val hasSummary = items.any { !it.summaryText.isNullOrBlank() }
    val lastItem = items.last()
    return ReasoningItem(
        summaryText = items.mapNotNull(ReasoningItem::summaryText)
            .filter(String::isNotBlank)
            .takeIf { hasSummary }
            ?.joinToString(separator = "\n\n"),
        rawText = items.mapNotNull(ReasoningItem::rawText)
            .filter(String::isNotBlank)
            .joinToString(separator = "\n\n"),
        isStreaming = lastItem.isStreaming,
        startedAtMillis = items.first().startedAtMillis,
        durationMillis = if (lastItem.isStreaming) null else items.sumOf { it.durationMillis ?: 0L },
    )
}

/** 判断工具事件是否只应通过挂起问题卡交互，而不写入时间线。 */
private fun isAskUserToolEvent(item: ToolEventItem): Boolean = item.toolName == "ask_user"

/**
 * 构造收起状态下统一的工具组标题。
 */
internal fun buildToolGroupHeadline(count: Int): String = "Executed tools · $count"

/** 工具文字行的垂直内边距，保持为零以贴近终端式活动列表。 */
internal const val TOOL_EVENT_ROW_VERTICAL_PADDING_DP = 0

/** 工具组摘要与展开箭头间保持可感知但紧凑的距离。 */
internal const val TOOL_GROUP_CHEVRON_GAP_DP = 16

/** 单个工具名称与展开箭头间保持紧凑的辅助间距。 */
internal const val TOOL_ROW_CHEVRON_GAP_DP = 8

/** 详情卡片与工具名称的起点对齐，工具行本身不被纳入卡片。 */
internal const val TOOL_EVENT_DETAILS_START_PADDING_DP = 28

/** Answers 详情与标题文字而非左侧图标对齐。 */
internal const val ANSWERS_DETAILS_START_PADDING_DP = 26

/** Islands 外层容器采用更舒展的圆角，承载连续的原始详情。 */
internal const val DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP = 12

/** 外层岛屿中的每块内容使用更紧凑的圆角。 */
internal const val DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP = 8

/** 相邻内层岛屿之间保留稳定的呼吸间距。 */
internal const val DETAIL_ISLANDS_GAP_DP = 12

/** 外层岛屿向内留出更充足的边缘空间，避免小岛贴近大岛边框。 */
internal const val DETAIL_ISLANDS_OUTER_PADDING_DP = 16

/** 工具组标题的字号，略高于工具行以便快速扫读当前动作。 */
internal const val TOOL_GROUP_TITLE_FONT_SIZE_SP = 16

/** 工具行的字号，确保工具名称和参数在主时间线中清晰可读。 */
internal const val TOOL_ROW_FONT_SIZE_SP = 15

/** 工具组内容展开采用更舒展的时长，便于扫读连续工具调用。 */
internal const val TOOL_GROUP_EXPAND_DURATION_MILLIS = 160

/** 工具组内容收起同样放慢，避免完成后视觉反馈过于仓促。 */
internal const val TOOL_GROUP_COLLAPSE_DURATION_MILLIS = 140

/** 单个工具详情展开的时长。 */
internal const val TOOL_ROW_EXPAND_DURATION_MILLIS = 150

/** 单个工具详情收起的时长。 */
internal const val TOOL_ROW_COLLAPSE_DURATION_MILLIS = 120

/** 已完成工具组保留展开状态的时长，确保快速调用也能展示各自的收起反馈。 */
internal const val TOOL_GROUP_AUTO_COLLAPSE_DELAY_MILLIS = 320L

/** 工具输出面板的最大可视高度，超出部分保留在面板内滚动。 */
internal val TOOL_EVENT_OUTPUT_MAX_HEIGHT = 320.dp

/** 普通详情岛屿共享同一表面色，通过层级与间距而非色差组织输入和输出。 */
internal val DetailIslandBackground = Color(0xFF202125)

/** 外层岛屿使用独立的较亮深色表面，清楚包裹同色的内层内容岛。 */
internal val DetailIslandsOuterBackground = Color(0xFF27292E)

/** 工具输入岛屿沿用统一详情表面色。 */
internal val ToolEventInputPaneBackground = DetailIslandBackground

/** 工具输出岛屿沿用统一详情表面色。 */
internal val ToolEventOutputPaneBackground = DetailIslandBackground

/** 思考块标题的字号，保持内容块层级清晰。 */
internal const val REASONING_HEADLINE_FONT_SIZE_SP = 16

/** 思考块正文的字号，避免长文本显得过小。 */
internal const val REASONING_BODY_FONT_SIZE_SP = 15

/** 思考正文展开的时长，兼顾信息出现的可追踪性与响应感。 */
internal const val REASONING_BODY_EXPAND_DURATION_MILLIS = 160

/** 思考正文收起略快，避免重复查看时阻塞时间线扫读。 */
internal const val REASONING_BODY_COLLAPSE_DURATION_MILLIS = 140

/** 工具行使用的紧凑类型图标。 */
internal enum class TimelineToolGlyph {
    SEARCH,
    DIRECTORY,
    TERMINAL,
    EDIT,
    READ,
    NETWORK,
    GENERIC,
}

/** 工具时间线所需的图标类型。 */
internal data class TimelineToolPresentation(
    val glyph: TimelineToolGlyph,
)

/** 根据工具名解析时间线所需的图标。 */
internal fun timelineToolPresentation(item: ToolEventItem): TimelineToolPresentation {
    val toolName = item.toolName.lowercase()
    return when {
        isTerminalToolEvent(item) -> TimelineToolPresentation(TimelineToolGlyph.TERMINAL)
        toolName.contains("edit") || toolName.contains("patch") || toolName.contains("write") ->
            TimelineToolPresentation(TimelineToolGlyph.EDIT)

        toolName.contains("directory") || toolName.contains("list_dir") || toolName.contains("list_files") ->
            TimelineToolPresentation(TimelineToolGlyph.DIRECTORY)

        toolName.contains("grep") || toolName.contains("search") || toolName.contains("find") || toolName.contains("glob") ->
            TimelineToolPresentation(TimelineToolGlyph.SEARCH)

        toolName.contains("read") || toolName.contains("cat") ->
            TimelineToolPresentation(TimelineToolGlyph.READ)

        toolName.contains("http") || toolName.contains("web") || toolName.contains("download") ->
            TimelineToolPresentation(TimelineToolGlyph.NETWORK)

        else -> TimelineToolPresentation(TimelineToolGlyph.GENERIC)
    }
}

/** 根据工具名解析原型时间线中的工具类型图标。 */
internal fun timelineToolGlyph(item: ToolEventItem): TimelineToolGlyph = timelineToolPresentation(item).glyph

/** 返回工具收起行的主文案；终端工具只保留实际命令。 */
internal fun timelineToolRowHeadline(item: ToolEventItem): String =
    if (isTerminalToolEvent(item)) buildToolEventInlineInput(item) ?: item.toolName else item.toolName

/** 返回非终端工具应在展开区展示的输入参数。 */
internal fun timelineToolExpandedInput(item: ToolEventItem): String? =
    buildToolEventInlineInput(item)?.takeUnless { isTerminalToolEvent(item) }

/** 返回同组最新仍在运行的工具，确保标题随当前动作更新。 */
internal fun activeTimelineTool(items: List<ToolEventItem>): ToolEventItem? =
    items.lastOrNull { it.status == ToolEventStatus.Started }

/** 返回同组最新的失败工具，确保结束态不会掩盖错误反馈。 */
internal fun failedTimelineTool(items: List<ToolEventItem>): ToolEventItem? =
    items.lastOrNull { it.status == ToolEventStatus.Failed }

/** 进行中的组沿用当前工具图标；失败优先于完成后的通用工具箱。 */
internal fun timelineToolGroupGlyph(items: List<ToolEventItem>): TimelineToolGlyph =
    activeTimelineTool(items)?.let(::timelineToolGlyph)
        ?: failedTimelineTool(items)?.let(::timelineToolGlyph)
        ?: TimelineToolGlyph.GENERIC

/** 运行状态使用蓝色强调；失败保持危险色；完成后回归中性的工具箱图标。 */
internal fun timelineToolGroupTint(items: List<ToolEventItem>): Color =
    when {
        activeTimelineTool(items) != null -> AppAccent
        failedTimelineTool(items) != null -> AppDanger
        else -> AppMuted
    }

/** 悬浮只提升工具标题文字，保留图标和其他状态反馈的原有色彩。 */
internal fun timelineToolTitleTint(hovered: Boolean, restingTint: Color): Color =
    if (hovered) AppAccent else restingTint

/** 已提交问答行沿用工具时间线的文字悬浮反馈，不绘制额外交互底色。 */
internal fun timelineAnswersTitleTint(hovered: Boolean): Color =
    timelineToolTitleTint(hovered = hovered, restingTint = AppText)

/** 思考运行和完成分别使用蓝、紫，避免时间线只剩一片灰色。 */
internal fun timelineReasoningTint(streaming: Boolean): Color = if (streaming) AppAccent else AppReasoning

/**
 * 将工具调用归纳为按首次出现顺序追加的动作摘要。
 *
 * 终端统一使用数量中性的复数文案，避免为单次或多次调用维护额外的语法分支。
 */
internal fun toolGroupSummaries(items: List<ToolEventItem>): List<String> =
    items.map(::toolGroupSummary).distinct()

/** 返回单个工具在工具组中的稳定摘要。 */
private fun toolGroupSummary(item: ToolEventItem): String {
    val toolName = item.toolName.lowercase()
    return when {
        isTerminalToolEvent(item) -> "Ran commands"
        toolName.contains("edit") || toolName.contains("patch") || toolName.contains("write") -> "Edited files"
        toolName.contains("list") || toolName.contains("directory") || toolName.contains("grep") ||
            toolName.contains("search") || toolName.contains("find") || toolName.contains("glob") -> "Searched files"
        toolName.contains("read") || toolName.contains("cat") -> "Read files"
        else -> "Used tools"
    }
}

/** 仅进行中的工具让图标本体持续运动，完成与失败状态保持静止。 */
internal fun shouldAnimateTimelineToolGlyph(status: ToolEventStatus): Boolean = status == ToolEventStatus.Started

/** 工具组默认收起，由用户决定何时查看批量工具详情。 */
internal fun initialTimelineToolGroupExpanded(): Boolean = false

/** 单独呈现的成功终端工具应在展示完成反馈后自动收起输出。 */
internal fun shouldAutoCollapseStandaloneTerminalTool(item: ToolEventItem): Boolean =
    isTerminalToolEvent(item) && item.status == ToolEventStatus.Finished

/**
 * 返回相邻展示段之间的垂直间距：连续工具调用保持紧凑，跨内容段落留出呼吸感。
 */
internal fun timelineDisplayItemSpacing(
    previous: TimelineDisplayItem,
    current: TimelineDisplayItem,
): Int = if (previous.isToolInvocation() && current.isToolInvocation()) 4 else 10

/**
 * 判断展示段是否代表工具调用；状态文本是独立的时间线内容，而非工具行。
 */
private fun TimelineDisplayItem.isToolInvocation(): Boolean = when (this) {
    is TimelineDisplayItem.ToolGroup,
    -> true

    is TimelineDisplayItem.ReasoningGroup -> false
    is TimelineDisplayItem.Content -> item is ToolEventItem && item.status != ToolEventStatus.Status
}
