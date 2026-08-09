@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
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
import com.agent.shared.chat.model.*
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.halilibo.richtext.ui.string.RichTextStringStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToInt

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
internal const val TOOL_GROUP_EXPAND_DURATION_MILLIS = 560

/** 工具组内容收起同样放慢，避免完成后视觉反馈过于仓促。 */
internal const val TOOL_GROUP_COLLAPSE_DURATION_MILLIS = 440

/** 单个工具详情展开的时长。 */
internal const val TOOL_ROW_EXPAND_DURATION_MILLIS = 340

/** 单个工具详情收起的时长。 */
internal const val TOOL_ROW_COLLAPSE_DURATION_MILLIS = 260

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
internal const val REASONING_BODY_EXPAND_DURATION_MILLIS = 220

/** 思考正文收起略快，避免重复查看时阻塞时间线扫读。 */
internal const val REASONING_BODY_COLLAPSE_DURATION_MILLIS = 160

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

/** 组内不存在进行中的工具时，工具组应自行收起。 */
internal fun shouldAutoCollapseTimelineToolGroup(items: List<ToolEventItem>): Boolean =
    items.isNotEmpty() && items.none { it.status == ToolEventStatus.Started }

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

/**
 * 完整会话时间线，按顺序渲染所有用户消息、助手回答、思考块和工具事件。
 */
@Composable
internal fun ConversationTimeline(
    conversation: ChatConversationUiState,
    pendingMessageEntry: PendingMessageEntry? = null,
    onMessageEntryFinished: (Long) -> Unit = {},
) {
    if (conversation.items.isEmpty() && conversation.executionState == ExecutionState.Idle) {
        Text(
            text = "可以开始新的任务",
            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
        )
        return
    }
    val failedState = conversation.executionState as? ExecutionState.Failed
    val hasFailedToolEvent = conversation.items.any {
        it is ToolEventItem && it.status == ToolEventStatus.Failed
    }
    val displayItems = groupTimelineItems(conversation.items)
    val entryMotionTarget = latestMatchingUserMessage(conversation.items, pendingMessageEntry?.content)
    Column(modifier = Modifier.fillMaxWidth()) {
        displayItems.forEachIndexed { index, displayItem ->
            if (index > 0) {
                Spacer(
                    modifier = Modifier.height(
                        timelineDisplayItemSpacing(displayItems[index - 1], displayItem).dp,
                    ),
                )
            }
            when (displayItem) {
                is TimelineDisplayItem.ToolGroup -> TimelineToolGroup(displayItem.items)
                is TimelineDisplayItem.ReasoningGroup -> TimelineReasoningItem(mergeReasoningItems(displayItem.items))
                is TimelineDisplayItem.Content -> when (val item = displayItem.item) {
                is ChatMessageItem -> {
                    if (item.message.role == ChatRole.User) {
                        UserMessageCard(
                            content = item.message.content,
                            entryMotionId = pendingMessageEntry?.id?.takeIf { item === entryMotionTarget },
                            onEntryMotionFinished = onMessageEntryFinished,
                        )
                    } else {
                        AssistantMessageBlock(
                            content = item.message.content,
                            isStreaming = item === conversation.items.getOrNull(conversation.streamingAssistantItemIndex ?: -1),
                        )
                    }
                }

                is ReasoningItem -> TimelineReasoningItem(item)
                is AnsweredQuestionsItem -> TimelineAnswersItem(item)
                is ToolEventItem -> TimelineToolTextRow(
                    item = rememberTimelineToolDisplayItem(item),
                    isFailure = item.status == ToolEventStatus.Failed,
                )
                }
            }
        }
        if (conversation.executionState == ExecutionState.Running) {
            buildSecondaryStatus(conversation)?.let { status ->
                if (displayItems.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                )
            }
        }
        if (failedState != null && !hasFailedToolEvent) {
            if (displayItems.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${failedState.error.title}: ${failedState.error.message}",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppDanger,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

/** 新消息进入动效的初始下移距离，需要足够大才能被看见。 */
private val MESSAGE_ENTRY_TRAVEL = 24.dp

/**
 * 单条用户消息卡片。
 */
@Composable
private fun UserMessageCard(
    content: String,
    entryMotionId: Long?,
    onEntryMotionFinished: (Long) -> Unit,
) {
    val travelDistancePx = with(LocalDensity.current) { MESSAGE_ENTRY_TRAVEL.toPx() }
    val progress = remember(entryMotionId) { Animatable(if (entryMotionId == null) 1f else 0f) }
    LaunchedEffect(entryMotionId) {
        val motionId = entryMotionId ?: return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.001f,
            ),
        )
        onEntryMotionFinished(motionId)
    }
    val visuals = messageEntryVisuals(
        progress = progress.value,
        travelDistancePx = travelDistancePx,
    )
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth * 0.8f)
                .wrapContentWidth()
                .graphicsLayer {
                    alpha = visuals.alpha
                    scaleX = visuals.scale
                    scaleY = visuals.scale
                    translationY = visuals.translationY
                    // 从最靠近发送按钮的右下角展开，动效来源与用户操作位置一致。
                    transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 1f)
                },
            shape = RoundedCornerShape(8.dp),
            color = AppUserCardBackground,
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(color = AppText),
            )
        }
    }
}

/**
 * 单条助手回答块。
 */
@Composable
private fun AssistantMessageBlock(
    content: String,
    isStreaming: Boolean,
) {
    val document = remember(content, isStreaming) {
        if (isStreaming) {
            parseAssistantMarkdownStreamingDocument(content.trim())
        } else {
            parseAssistantMarkdownDocument(content.trim())
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        document.blocks.forEach { block ->
            when (block) {
                is AssistantMarkdownBlock.Text -> AssistantMarkdownText(block.content)
                is AssistantMarkdownBlock.PlantUml -> PlantUmlDiagram(block.source)
                is AssistantMarkdownBlock.Code -> AssistantCodeBlock(block.language, block.source)
                is AssistantMarkdownBlock.Image -> AssistantMarkdownImage(block.alt, block.url)
                is AssistantMarkdownBlock.DefinitionList -> AssistantDefinitionListItem(block.term, block.definition)
                is AssistantMarkdownBlock.HtmlSpan -> AssistantSafeHtmlSpan(block.content, block.colorName)
                is AssistantMarkdownBlock.HtmlBlock -> AssistantSafeHtmlBlock(block.content, block.alignment)
                is AssistantMarkdownBlock.InlineMath -> AssistantMathFormula(block.source, display = false)
                is AssistantMarkdownBlock.DisplayMath -> AssistantMathFormula(block.source, display = true)
                is AssistantMarkdownBlock.Details -> AssistantMarkdownDetails(block.summary, block.content)
            }
        }
        AssistantFootnoteSection(document.footnotes)
    }
}

/** 正文行内代码直接沿用应用强调色，不额外绘制背景包裹框。 */
internal val AssistantMarkdownInlineCodeForeground = AppAccent

/** 助手 Markdown 正文使用柔和灰白，避免深色背景上的纯白产生眩光。 */
internal val AssistantMarkdownBodyForeground = Color(0xFFC7CBD3)

/** 标题和粗体只比正文略亮，保留层级而不回到纯白。 */
internal val AssistantMarkdownStrongForeground = Color(0xFFD8DCE3)

/** 返回正文 Markdown 共用的行内字符串样式，令链接与代码片段保持统一视觉层级。 */
internal fun assistantMarkdownStringStyle(): RichTextStringStyle = RichTextStringStyle(
    boldStyle = androidx.compose.ui.text.SpanStyle(
        color = AssistantMarkdownStrongForeground,
        fontWeight = FontWeight.SemiBold,
    ),
    codeStyle = assistantMarkdownInlineCodeStyle(),
    linkStyle = assistantMarkdownLinkStyle(),
)

/**
 * 以应用主题渲染普通 Markdown 文本，显式回传前景色避免深色主题被默认黑色覆盖。
 */
@Composable
internal fun AssistantMarkdownText(content: String) {
    val normalizedContent = remember(content) { normalizeAssistantMarkdown(content).trim() }
    if (normalizedContent.isBlank()) return
    if (containsAssistantMarkdownInlineExtensions(normalizedContent)) {
        AssistantMarkdownInlineExtensionsText(normalizedContent)
        return
    }
    RichTextThemeProvider(
        textStyleProvider = { LocalTextStyle.current },
        textStyleBackProvider = { style, children ->
            CompositionLocalProvider(LocalTextStyle provides style) { children() }
        },
        contentColorProvider = { AssistantMarkdownBodyForeground },
        contentColorBackProvider = { color, children ->
            CompositionLocalProvider(LocalContentColor provides color) { children() }
        },
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(lineHeight = 25.sp),
        ) {
            SelectionContainer {
                BasicRichText(
                    style = RichTextStyle(
                        stringStyle = assistantMarkdownStringStyle(),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Markdown(content = normalizedContent)
                }
            }
        }
    }
}

/**
 * 在后台生成 PlantUML SVG，完成后由 Skia 直接绘制；失败时保留原始源码供用户检查。
 */
@Composable
private fun PlantUmlDiagram(source: String) {
    var showSource by remember(source) { mutableStateOf(false) }
    var copied by remember(source) { mutableStateOf(false) }
    var copyNoticeVersion by remember(source) { mutableIntStateOf(0) }
    var renderedSvg by remember(source) { mutableStateOf<Result<String>?>(null) }
    LaunchedEffect(source) {
        renderedSvg = runCatching {
            withContext(Dispatchers.Default) {
                renderPlantUmlToSvg(source)
            }
        }
    }
    LaunchedEffect(copyNoticeVersion) {
        if (copyNoticeVersion > 0) {
            delay(1_500)
            copied = false
        }
    }
    when (val result = renderedSvg) {
        null -> Text(
            text = "正在渲染 PlantUML 图表…",
            style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
        )

        else -> {
            val svg = result.getOrNull()
            if (svg != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (showSource) {
                        PlantUmlSource(
                            source = source,
                            onShowRendered = { showSource = false },
                            onCopied = {
                                copied = true
                                copyNoticeVersion += 1
                            },
                        )
                    } else {
                        PlantUmlSvg(
                            svg = svg,
                            source = source,
                            onShowSource = { showSource = true },
                            onCopied = {
                                copied = true
                                copyNoticeVersion += 1
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = copied,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        enter = fadeIn(tween(durationMillis = 120)),
                        exit = fadeOut(tween(durationMillis = 160)),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppHoverBackground,
                            border = BorderStroke(1.dp, AppLine.copy(alpha = 0.7f)),
                        ) {
                            Text(
                                text = "已复制 UML 源码",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium.copy(color = AppText),
                            )
                        }
                    }
                }
            } else {
                AssistantCodeBlock(language = "plantuml", source = source)
            }
        }
    }
}

/** 在与图表一致的容器中展示 PlantUML 原始源码，并保留返回渲染视图的入口。 */
@Composable
private fun PlantUmlSource(
    source: String,
    onShowRendered: () -> Unit,
    onCopied: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF24272E),
        border = BorderStroke(1.dp, AppLine.copy(alpha = 0.65f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLANT_UML_CONTROL_BAR_HEIGHT_DP.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlantUmlControlButton(text = "渲染", onClick = onShowRendered)
                PlantUmlCopyControl(source = source, onCopied = onCopied)
            }
            AssistantCodeBlock(language = "plantuml", source = source)
        }
    }
}

/** UML 图形可视画布的高度；控制栏占用独立的顶部层，不与图形共用空间。 */
internal const val PLANT_UML_CANVAS_HEIGHT_DP = 460

/** UML 图表容器最大宽度，保留会话页可用的滚动留白。 */
internal const val PLANT_UML_MAX_WIDTH_DP = 720

/** UML 控制栏固定高度，保证其作为独立且可点击的顶层。 */
internal const val PLANT_UML_CONTROL_BAR_HEIGHT_DP = 48

/** PlantUML 图形缩放的上限，避免极端放大导致渲染开销失控。 */
private const val PLANT_UML_MAX_SCALE = 3f

/** PlantUML 图像的原始像素尺寸。 */
internal data class PlantUmlIntrinsicSize(
    val width: Float,
    val height: Float,
)

/** 从 PlantUML 的 SVG 根节点读取图表原始尺寸，供适配、缩放和拖拽计算共用。 */
internal fun svgIntrinsicSize(svg: String): PlantUmlIntrinsicSize {
    val viewBox = SVG_VIEW_BOX_ATTRIBUTE.find(svg)
    if (viewBox != null) {
        return PlantUmlIntrinsicSize(
            width = viewBox.groupValues[1].toFloat(),
            height = viewBox.groupValues[2].toFloat(),
        )
    }
    val width = SVG_WIDTH_ATTRIBUTE.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    val height = SVG_HEIGHT_ATTRIBUTE.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    return PlantUmlIntrinsicSize(width = width ?: 1f, height = height ?: 1f)
}

/** SVG 的 viewBox 是最可靠的绘制尺寸来源。 */
private val SVG_VIEW_BOX_ATTRIBUTE = Regex(
    """\bviewBox\s*=\s*[\"']\s*[-+]?\d+(?:\.\d+)?[\s,]+[-+]?\d+(?:\.\d+)?[\s,]+([-+]?\d+(?:\.\d+)?)[\s,]+([-+]?\d+(?:\.\d+)?)\s*[\"']""",
    RegexOption.IGNORE_CASE,
)

/** 当 SVG 未提供 viewBox 时，退回使用根节点的宽高属性。 */
private val SVG_WIDTH_ATTRIBUTE = Regex(
    """\bwidth\s*=\s*[\"']\s*([-+]?\d+(?:\.\d+)?)(?:px)?\s*[\"']""",
    RegexOption.IGNORE_CASE,
)

/** 当 SVG 未提供 viewBox 时，退回使用根节点的宽高属性。 */
private val SVG_HEIGHT_ATTRIBUTE = Regex(
    """\bheight\s*=\s*[\"']\s*([-+]?\d+(?:\.\d+)?)(?:px)?\s*[\"']""",
    RegexOption.IGNORE_CASE,
)

/** 计算完整显示 PlantUML 图像所需的等比缩放，不放大小于视口的图形。 */
internal fun plantUmlFitScale(
    intrinsicSize: PlantUmlIntrinsicSize,
    viewportWidth: Float,
    viewportHeight: Float,
): Float =
    if (viewportWidth > 0f && viewportHeight > 0f) {
        minOf(viewportWidth / intrinsicSize.width, viewportHeight / intrinsicSize.height).coerceAtMost(1f)
    } else {
        1f
    }

/** 将滚轮或按钮的倍率变更限制在当前查看器支持的范围内。 */
internal fun plantUmlZoomedScale(
    scale: Float,
    multiplier: Float,
    minimumScale: Float,
): Float = (scale * multiplier).coerceIn(minimumScale, PLANT_UML_MAX_SCALE)

/**
 * 使用 PlantUML 在 JVM 内生成的 SVG 以矢量方式绘制图表，缩放时保持文字与连线清晰。
 *
 * 控制栏始终位于裁切画布之上；画布仅占用控制栏下方的独立区域。
 */
@Composable
private fun PlantUmlSvg(
    svg: String,
    source: String,
    onShowSource: () -> Unit,
    onCopied: () -> Unit,
) {
    val document = remember(svg) { SVGDOM(Data.makeFromBytes(svg.encodeToByteArray())) }
    val intrinsicSize = remember(svg) { svgIntrinsicSize(svg) }
    DisposableEffect(document) {
        onDispose(document::close)
    }
    val density = LocalDensity.current
    val canvasHeight = PLANT_UML_CANVAS_HEIGHT_DP.dp
    val controlBarHeight = PLANT_UML_CONTROL_BAR_HEIGHT_DP.dp
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = PLANT_UML_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF24272E),
            border = BorderStroke(1.dp, AppLine.copy(alpha = 0.65f)),
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight + controlBarHeight)
                .clipToBounds(),
        ) {
            val viewportWidth = with(density) { maxWidth.toPx() }
            val viewportHeight = with(density) { canvasHeight.toPx() }
            val fitScale = plantUmlFitScale(intrinsicSize, viewportWidth, viewportHeight)
            val minimumScale = (fitScale * 0.5f).coerceAtLeast(0.05f)
            var scale by remember(svg) { mutableFloatStateOf(fitScale) }
            var offset by remember(svg) { mutableStateOf(Offset.Zero) }
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .clipToBounds()
                    .onPointerEvent(
                        eventType = PointerEventType.Scroll,
                        pass = PointerEventPass.Initial,
                    ) { event ->
                        val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (scrollY != 0f) {
                            event.changes.forEach { it.consume() }
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = if (scrollY < 0f) 1.12f else 0.9f,
                                minimumScale = minimumScale,
                            )
                        }
                    }
                    .pointerInput(svg, minimumScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = zoom,
                                minimumScale = minimumScale,
                            )
                            offset += pan
                        }
                    },
            ) {
                val scaledWidth = intrinsicSize.width * scale
                val scaledHeight = intrinsicSize.height * scale
                val centeredPosition = Offset(
                    x = (size.width - scaledWidth) / 2f,
                    y = (size.height - scaledHeight) / 2f,
                )
                withTransform({
                    translate(
                        left = centeredPosition.x + offset.x,
                        top = centeredPosition.y + offset.y,
                    )
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                }) {
                    document.setContainerSize(intrinsicSize.width, intrinsicSize.height)
                    drawIntoCanvas { canvas ->
                        document.render(canvas.skiaCanvas)
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(controlBarHeight)
                    .zIndex(1f),
                color = Color(0xFF24272E),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlantUmlControlButton(
                        text = "−",
                        onClick = {
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = 1f / 1.2f,
                                minimumScale = minimumScale,
                            )
                        },
                    )
                    PlantUmlControlButton(
                        text = "${(scale * 100).roundToInt()}%",
                        onClick = { scale = 1f; offset = Offset.Zero },
                    )
                    PlantUmlControlButton(
                        text = "+",
                        onClick = {
                            scale = plantUmlZoomedScale(
                                scale = scale,
                                multiplier = 1.2f,
                                minimumScale = minimumScale,
                            )
                        },
                    )
                    PlantUmlControlButton(text = "适配", onClick = { scale = fitScale; offset = Offset.Zero })
                    PlantUmlControlButton(text = "源码", onClick = onShowSource)
                    PlantUmlCopyControl(source = source, onCopied = onCopied)
                }
            }
        }
        }
    }
}

/**
 * 仅在 Agent 完成回复后启用图表渲染，避免不完整的流式围栏触发布局抖动。
 */
internal fun shouldRenderMarkdownDiagram(isStreaming: Boolean): Boolean = !isStreaming

/** UML 工具栏使用轻量桌面控件，避免 Material 文本按钮的移动端视觉反馈。 */
@Composable
private fun PlantUmlControlButton(
    text: String,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) AppLine.copy(alpha = 0.72f) else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (hovered) AppText else AppMuted,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** 将 UML 原始源码复制到系统剪贴板，并通知图表容器展示短暂反馈。 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PlantUmlCopyControl(
    source: String,
    onCopied: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    PlantUmlControlButton(
        text = "复制",
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(java.awt.datatransfer.StringSelection(source)))
                onCopied()
            }
        },
    )
}

/**
 * 将回复中的常见安全 HTML 语义降级为 Markdown，避开 Compose Desktop 对 HTML 块的字面输出。
 */
internal fun normalizeAssistantMarkdown(content: String): String = content
    .replace(UNSAFE_HTML_BLOCK, "")
    .replace(HTML_LINE_BREAK, "\n")
    .replace(HTML_BOLD, "**$1**")
    .replace(HTML_ITALIC, "*$1*")
    .replace(HTML_INLINE_CODE, "`$1`")
    .replace(HTML_BLOCK_OPEN, "\n")
    .replace(HTML_BLOCK_CLOSE, "\n")
    .replace(HTML_TAG, "")

private val UNSAFE_HTML_BLOCK = Regex(
    pattern = "<\\s*(?:script|style)\\b[^>]*>.*?<\\s*/\\s*(?:script|style)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_LINE_BREAK = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
private val HTML_BOLD = Regex(
    pattern = "<\\s*(?:b|strong)\\b[^>]*>(.*?)<\\s*/\\s*(?:b|strong)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_ITALIC = Regex(
    pattern = "<\\s*(?:i|em)\\b[^>]*>(.*?)<\\s*/\\s*(?:i|em)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_INLINE_CODE = Regex(
    pattern = "<\\s*(?:code|kbd)\\b[^>]*>(.*?)<\\s*/\\s*(?:code|kbd)\\s*>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_BLOCK_OPEN = Regex("<\\s*(?:div|p|details|summary|dl|dt|dd)\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_BLOCK_CLOSE = Regex("<\\s*/\\s*(?:div|p|details|summary|dl|dt|dd)\\s*>", RegexOption.IGNORE_CASE)
private val HTML_TAG = Regex("<[^>]+>")

/**
 * 时间线中的思考块展示。
 */
@Composable
private fun TimelineReasoningItem(item: ReasoningItem) {
    var expanded by remember(item.isStreaming) { mutableStateOf(item.isStreaming) }
    val reasoningTint = timelineReasoningTint(item.isStreaming)
    val shimmerTransition = rememberInfiniteTransition(label = "thinking-shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -120f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
        ),
        label = "thinking-shimmer-offset",
    )
    val headlineBrush = Brush.linearGradient(
        colors = listOf(AppMuted, Color(0xFFEAF2FF), AppMuted),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 120f, 0f),
    )
    val headlineStyle = if (item.isStreaming) {
        MaterialTheme.typography.titleSmall.copy(
            brush = headlineBrush,
            fontWeight = FontWeight.SemiBold,
            fontSize = REASONING_HEADLINE_FONT_SIZE_SP.sp,
        )
    } else {
        MaterialTheme.typography.titleSmall.copy(
            color = reasoningTint,
            fontWeight = FontWeight.SemiBold,
            fontSize = REASONING_HEADLINE_FONT_SIZE_SP.sp,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimelineReasoningGlyph(streaming = item.isStreaming, tint = reasoningTint)
            Text(
                text = buildReasoningHeadline(item),
                modifier = Modifier.padding(start = 7.dp),
                style = headlineStyle,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(durationMillis = REASONING_BODY_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = REASONING_BODY_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = REASONING_BODY_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = REASONING_BODY_COLLAPSE_DURATION_MILLIS)),
        ) {
            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted,
                    fontSize = REASONING_BODY_FONT_SIZE_SP.sp,
                    lineHeight = 23.sp,
                ),
            )
        }
    }
}

/**
 * 时间线中的工具事件条目。
 */
@Composable
private fun TimelineToolGroup(items: List<ToolEventItem>) {
    var expanded by remember(items.map(ToolEventItem::toolCallId)) { mutableStateOf(true) }
    var userSetExpansion by remember(items.map(ToolEventItem::toolCallId)) { mutableStateOf(false) }
    var hovered by remember(items.map(ToolEventItem::toolCallId)) { mutableStateOf(false) }
    val displayItems = items.map { item -> rememberTimelineToolDisplayItem(item) }
    val shouldAutoCollapse = shouldAutoCollapseTimelineToolGroup(displayItems)
    LaunchedEffect(displayItems.map(ToolEventItem::status), userSetExpansion) {
        if (!userSetExpansion && shouldAutoCollapse) {
            delay(TOOL_GROUP_AUTO_COLLAPSE_DELAY_MILLIS.milliseconds)
            expanded = false
        }
    }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(
            durationMillis = if (expanded) TOOL_GROUP_EXPAND_DURATION_MILLIS else TOOL_GROUP_COLLAPSE_DURATION_MILLIS,
        ),
        label = "tool-group-chevron",
    )
    val activeTool = activeTimelineTool(displayItems)
    val groupGlyph = timelineToolGroupGlyph(displayItems)
    val groupTint = timelineToolGroupTint(displayItems)
    val groupTitleTint = timelineToolTitleTint(hovered = hovered, restingTint = AppMuted)
    val summaries = toolGroupSummaries(displayItems)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) {
                    hovered = false
                }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                userSetExpansion = true
                expanded = !expanded
            }
                .padding(horizontal = 4.dp, vertical = TOOL_EVENT_ROW_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = groupGlyph,
                transitionSpec = {
                    (fadeIn(tween(durationMillis = 300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)))
                        .togetherWith(fadeOut(tween(durationMillis = 220)))
                },
                label = "tool-group-glyph",
            ) { glyph ->
                TimelineToolGlyphIcon(
                    glyph = glyph,
                    tint = groupTint,
                    running = activeTool != null,
                    iconSize = 20.dp,
                )
            }
            Row(
                modifier = Modifier
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                summaries.forEachIndexed { index, summary ->
                    key(summary) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(durationMillis = 160)),
                            exit = ExitTransition.None,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (index > 0) {
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
                                    )
                                }
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = groupTitleTint,
                                        fontSize = TOOL_GROUP_TITLE_FONT_SIZE_SP.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(TOOL_GROUP_CHEVRON_GAP_DP.dp))
            TimelineToolChevronSlot(
                visible = shouldShowTimelineToolChevron(hovered),
                rotation = chevronRotation,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(durationMillis = TOOL_GROUP_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = TOOL_GROUP_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = TOOL_GROUP_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = TOOL_GROUP_COLLAPSE_DURATION_MILLIS)),
        ) {
            Column(
                modifier = Modifier
                    .drawBehind {
                        val guideX = 8.dp.toPx()
                        drawLine(
                            color = AppLine.copy(alpha = 0.72f),
                            start = Offset(guideX, 0f),
                            end = Offset(guideX, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(start = 20.dp, top = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                displayItems.forEach { item ->
                    TimelineToolStackCard(item = item, preview = false)
                }
            }
        }
    }
}

/**
 * 以可展开的紧凑摘要展示已经提交给 Agent 的批量问答。
 */
@Composable
private fun TimelineAnswersItem(item: AnsweredQuestionsItem) {
    var expanded by remember(item) { mutableStateOf(false) }
    var hovered by remember(item) { mutableStateOf(false) }
    val interactionSource = remember(item) { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS),
        label = "answers-chevron",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            ) {
            AnswersGlyphIcon(tint = AppText)
            Text(
                text = "Answers",
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { expanded = !expanded },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = timelineAnswersTitleTint(hovered),
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.width(TOOL_ROW_CHEVRON_GAP_DP.dp))
            TimelineToolChevronSlot(
                visible = shouldShowTimelineToolChevron(hovered),
                rotation = chevronRotation,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(durationMillis = 180)) + fadeIn(tween(durationMillis = 150)),
            exit = shrinkVertically(tween(durationMillis = 120)) + fadeOut(tween(durationMillis = 100)),
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        start = ANSWERS_DETAILS_START_PADDING_DP.dp,
                        end = 10.dp,
                        bottom = 6.dp,
                    )
                    .clip(RoundedCornerShape(DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP.dp))
                    .background(DetailIslandsOuterBackground)
                    .border(
                        1.dp,
                        AppLine.copy(alpha = 0.65f),
                        RoundedCornerShape(DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP.dp),
                    )
                    .padding(DETAIL_ISLANDS_OUTER_PADDING_DP.dp),
                verticalArrangement = Arrangement.spacedBy(DETAIL_ISLANDS_GAP_DP.dp),
            ) {
                item.answers.forEach { answer ->
                    DetailIsland {
                        Text(
                            text = answer.question,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AppText,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    DetailIsland {
                        Text(
                            text = answer.answer,
                            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
                        )
                    }
                }
            }
        }
    }
}

/** 绘制代表已提交问答的对话气泡图标，避免使用辨识度低的文字占位符。 */
@Composable
private fun AnswersGlyphIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.34f, size.height * 0.74f),
            end = Offset(size.width * 0.25f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.25f, size.height * 0.9f),
            end = Offset(size.width * 0.49f, size.height * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        listOf(0.32f, 0.5f, 0.68f).forEach { xRatio ->
            drawCircle(
                color = tint,
                radius = stroke * 0.42f,
                center = Offset(size.width * xRatio, size.height * 0.43f),
            )
        }
    }
}

/**
 * 暂存工具事件的展示状态，使快速完成的非终端工具仍可展示完整的运行图标动效。
 */
@Composable
private fun rememberTimelineToolDisplayItem(item: ToolEventItem): ToolEventItem {
    val identity = toolEventExpansionIdentity(item)
    var displayItem by remember(identity) { mutableStateOf(initialTimelineToolDisplayItem(item)) }
    var startedAtMillis by remember(identity) {
        mutableStateOf(if (item.status == ToolEventStatus.Started) System.currentTimeMillis() else 0L)
    }
    var hasSeenStartedState by remember(identity) { mutableStateOf(item.status == ToolEventStatus.Started) }
    LaunchedEffect(item) {
        if (item.status == ToolEventStatus.Started) {
            hasSeenStartedState = true
            startedAtMillis = System.currentTimeMillis()
            displayItem = item
        } else if (!hasSeenStartedState) {
            if (shouldSynthesizeRunningToolDisplay(item)) {
                hasSeenStartedState = true
                startedAtMillis = System.currentTimeMillis()
                displayItem = item.copy(status = ToolEventStatus.Started)
                delay(TOOL_MINIMUM_RUNNING_DISPLAY_MILLIS.milliseconds)
            }
            displayItem = item
        } else {
            val remainingDelayMillis = toolCompletionDelayMillis(
                item = item,
                startedAtMillis = startedAtMillis,
                nowMillis = System.currentTimeMillis(),
            )
            if (remainingDelayMillis > 0L) delay(remainingDelayMillis.milliseconds)
            displayItem = item
        }
    }
    return displayItem
}

/**
 * 快速完成的非终端工具首次进入组合时，先构造运行态以保证图标动效可见。
 */
internal fun initialTimelineToolDisplayItem(item: ToolEventItem): ToolEventItem =
    if (shouldSynthesizeRunningToolDisplay(item)) item.copy(status = ToolEventStatus.Started) else item

/** 只有真实完成或失败的非终端工具需要补足此前未观测到的运行态。 */
internal fun shouldSynthesizeRunningToolDisplay(item: ToolEventItem): Boolean =
    !isTerminalToolEvent(item) && item.status in setOf(ToolEventStatus.Finished, ToolEventStatus.Failed)

/** 渲染工具组中前景当前卡和一张带纵深反馈的后置预览卡。 */
@Composable
private fun TimelineToolCardStack(items: List<ToolEventItem>) {
    val visibleItems = visibleToolCardStack(items)
    val currentItem = visibleItems.firstOrNull() ?: return
    val previewItem = visibleItems.getOrNull(1)
    val density = LocalDensity.current
    val previewOffsetX = with(density) { 8.dp.toPx() }
    val previewOffsetY = with(density) { 6.dp.toPx() }
    Box(modifier = Modifier.fillMaxWidth()) {
        previewItem?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 0.52f
                        scaleX = 0.97f
                        scaleY = 0.97f
                        translationX = previewOffsetX
                        translationY = previewOffsetY
                    },
            ) {
                TimelineToolStackCard(item = item, preview = true)
            }
        }
        AnimatedContent(
            targetState = currentItem,
            transitionSpec = {
                (slideInHorizontally(tween(durationMillis = 220)) { width -> width / 8 } +
                        fadeIn(tween(durationMillis = 180)) +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(durationMillis = 220)))
                    .togetherWith(
                        slideOutHorizontally(tween(durationMillis = 160)) { width -> -width / 5 } +
                                fadeOut(tween(durationMillis = 130)),
                    )
            },
            label = "tool-card-stack",
        ) { item ->
            TimelineToolStackCard(item = item, preview = false)
        }
    }
}

/** 渲染树状工具组中的紧凑工具行，不再包裹整行卡片。 */
@Composable
private fun TimelineToolStackCard(
    item: ToolEventItem,
    preview: Boolean,
) {
    TimelineToolTextRow(
        item = item,
        isFailure = item.status == ToolEventStatus.Failed,
        preview = preview,
    )
}

/**
 * 无边框的单个工具文本行，可按需展开完整输出或错误内容。
 */
@Composable
private fun TimelineToolTextRow(
    item: ToolEventItem,
    isFailure: Boolean,
    preview: Boolean = false,
) {
    val hasDetails = toolEventHasDetails(item) || item.errorMessage?.isNotBlank() == true
    val glyph = timelineToolGlyph(item)
    val running = shouldAnimateTimelineToolGlyph(item.status)
    var expanded by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    var userSetExpansion by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    var hovered by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    LaunchedEffect(item.status, item.resultDisplay, item.resultPreview, userSetExpansion) {
        if (!userSetExpansion) {
            when {
                shouldAutoExpandRunningTerminalOutput(item) -> expanded = true
                shouldAutoCollapseStandaloneTerminalTool(item) -> {
                    delay(TOOL_GROUP_AUTO_COLLAPSE_DELAY_MILLIS.milliseconds)
                    expanded = false
                }
            }
        }
    }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(
            durationMillis = if (expanded) TOOL_ROW_EXPAND_DURATION_MILLIS else TOOL_ROW_COLLAPSE_DURATION_MILLIS,
        ),
        label = "tool-text-chevron",
    )
    val titleTint = timelineToolTitleTint(
        hovered = hovered,
        restingTint = if (isFailure) AppDanger else AppText,
    )
    val input = timelineToolExpandedInput(item)
    val output = toolEventOutputText(item)
    val error = item.errorMessage?.takeIf(String::isNotBlank)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) {
                    hovered = false
                }
            .clickable(
                enabled = hasDetails && !preview,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                userSetExpansion = true
                expanded = !expanded
            }
                .padding(horizontal = 4.dp, vertical = TOOL_EVENT_ROW_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimelineToolGlyphIcon(
                glyph = glyph,
                tint = when {
                    isFailure -> AppDanger
                    running -> AppAccent
                    else -> AppMuted
                },
                running = running,
                iconSize = 18.dp,
            )
            Text(
                text = timelineToolRowHeadline(item),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = titleTint,
                    fontSize = TOOL_ROW_FONT_SIZE_SP.sp,
                    fontFamily = if (isTerminalToolEvent(item)) FontFamily.Monospace else FontFamily.Default,
                ),
            )
            if (hasDetails && !preview) {
                Spacer(modifier = Modifier.width(TOOL_ROW_CHEVRON_GAP_DP.dp))
                TimelineToolChevronSlot(
                    visible = shouldShowTimelineToolChevron(hovered),
                    rotation = chevronRotation,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && hasDetails && !preview,
            enter = expandVertically(tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = TOOL_ROW_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = TOOL_ROW_COLLAPSE_DURATION_MILLIS)),
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        start = TOOL_EVENT_DETAILS_START_PADDING_DP.dp,
                        end = 10.dp,
                        bottom = 6.dp,
                    )
                    .clip(RoundedCornerShape(DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP.dp))
                    .background(DetailIslandsOuterBackground)
                    .border(
                        1.dp,
                        AppLine.copy(alpha = 0.65f),
                        RoundedCornerShape(DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP.dp),
                    )
                    .padding(DETAIL_ISLANDS_OUTER_PADDING_DP.dp),
                verticalArrangement = Arrangement.spacedBy(DETAIL_ISLANDS_GAP_DP.dp),
            ) {
                input?.let { inputText ->
                    ToolEventOutputPane(
                        text = inputText,
                        backgroundColor = ToolEventInputPaneBackground,
                        textColor = AppMuted,
                    )
                }
                output?.let { outputText ->
                    ToolEventOutputPane(
                        text = outputText,
                        backgroundColor = ToolEventOutputPaneBackground,
                        textColor = AppMuted,
                    )
                }
                error?.let { errorText ->
                    ToolEventOutputPane(
                        text = errorText,
                        backgroundColor = Color(0xFF2A1518),
                        textColor = AppDanger,
                    )
                }
            }
        }
    }
}

/** 绘制思考块标题前的闪光图标；流式思考中使用克制的呼吸反馈。 */
@Composable
private fun TimelineReasoningGlyph(streaming: Boolean, tint: Color) {
    val transition = rememberInfiniteTransition(label = "timeline-reasoning-glyph-motion")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "timeline-reasoning-glyph-breathe",
    )
    Canvas(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(16.dp)
            .graphicsLayer {
                scaleX = if (streaming) scale else 1f
                scaleY = if (streaming) scale else 1f
            },
    ) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val stroke = 1.5.dp.toPx()
        drawLine(tint, Offset(center.x, size.height * 0.13f), Offset(center.x, size.height * 0.87f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.13f, center.y), Offset(size.width * 0.87f, center.y), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.24f, size.height * 0.24f), Offset(size.width * 0.76f, size.height * 0.76f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.76f, size.height * 0.24f), Offset(size.width * 0.24f, size.height * 0.76f), stroke, StrokeCap.Round)
    }
}

/** 绘制原型中的工具类型图标；运行时轻微摆动，强调执行仍在推进。 */
@Composable
private fun TimelineToolGlyphIcon(
    glyph: TimelineToolGlyph,
    tint: Color,
    running: Boolean,
    iconSize: Dp = 18.dp,
) {
    val transition = rememberInfiniteTransition(label = "timeline-tool-glyph-motion")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing),
        ),
        label = "timeline-tool-glyph-progress",
    )
    val animatedProgress = if (running) progress else 0f
    Canvas(
        modifier = Modifier.size(iconSize),
    ) {
        val stroke = 1.5.dp.toPx()
        when (glyph) {
            TimelineToolGlyph.SEARCH -> {
                listOf(0.3f, 0.5f, 0.7f).forEach { yRatio ->
                    drawLine(
                        tint.copy(alpha = 0.48f),
                        Offset(size.width * 0.08f, size.height * yRatio),
                        Offset(size.width * 0.92f, size.height * yRatio),
                        stroke,
                        StrokeCap.Round,
                    )
                }
                val scanX = 0.24f + animatedProgress * 0.48f
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.25f,
                    center = Offset(size.width * scanX, size.height * 0.5f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawLine(
                    tint,
                    Offset(size.width * (scanX + 0.16f), size.height * 0.66f),
                    Offset(size.width * (scanX + 0.34f), size.height * 0.84f),
                    stroke,
                    StrokeCap.Round,
                )
            }

            TimelineToolGlyph.DIRECTORY -> {
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { index, yRatio ->
                    val nodeTint = tint.copy(alpha = if (!running || animatedProgress >= index / 3f) 1f else 0.35f)
                    drawCircle(nodeTint, radius = stroke * 0.45f, center = Offset(size.width * 0.2f, size.height * yRatio))
                    drawLine(
                        nodeTint,
                        Offset(size.width * 0.34f, size.height * yRatio),
                        Offset(size.width * (0.68f + index * 0.05f), size.height * yRatio),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }

            TimelineToolGlyph.TERMINAL -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.1f, size.height * 0.16f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawLine(tint, Offset(size.width * 0.27f, size.height * 0.38f), Offset(size.width * 0.42f, size.height * 0.5f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.42f, size.height * 0.5f), Offset(size.width * 0.27f, size.height * 0.62f), stroke, StrokeCap.Round)
                val cursorTint = tint.copy(alpha = if (!running || animatedProgress < 0.55f) 1f else 0.28f)
                drawLine(cursorTint, Offset(size.width * 0.52f, size.height * 0.63f), Offset(size.width * 0.72f, size.height * 0.63f), stroke, StrokeCap.Round)
            }

            TimelineToolGlyph.EDIT -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.12f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.54f, size.height * 0.74f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                val penOffset = (animatedProgress - 0.5f) * size.width * 0.16f
                drawLine(
                    tint,
                    Offset(size.width * 0.35f + penOffset, size.height * 0.69f),
                    Offset(size.width * 0.82f + penOffset, size.height * 0.22f),
                    stroke * 1.35f,
                    StrokeCap.Round,
                )
                drawLine(tint, Offset(size.width * 0.31f, size.height * 0.75f), Offset(size.width * 0.44f, size.height * 0.7f), stroke, StrokeCap.Round)
                val cursorTint = tint.copy(alpha = if (!running || animatedProgress < 0.5f) 1f else 0.28f)
                drawLine(cursorTint, Offset(size.width * 0.27f, size.height * 0.26f), Offset(size.width * 0.27f, size.height * 0.5f), stroke, StrokeCap.Round)
            }

            TimelineToolGlyph.READ -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.12f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.76f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawLine(tint, Offset(size.width * 0.34f, size.height * 0.42f), Offset(size.width * 0.66f, size.height * 0.42f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.34f, size.height * 0.6f), Offset(size.width * 0.66f, size.height * 0.6f), stroke, StrokeCap.Round)
                val scanY = size.height * (0.26f + animatedProgress * 0.48f)
                drawLine(tint.copy(alpha = 0.78f), Offset(size.width * 0.28f, scanY), Offset(size.width * 0.72f, scanY), stroke, StrokeCap.Round)
            }

            TimelineToolGlyph.NETWORK -> {
                drawLine(tint, Offset(size.width * 0.18f, size.height * 0.38f), Offset(size.width * 0.72f, size.height * 0.38f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.72f, size.height * 0.38f), Offset(size.width * 0.57f, size.height * 0.24f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.72f, size.height * 0.38f), Offset(size.width * 0.57f, size.height * 0.52f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.82f, size.height * 0.65f), Offset(size.width * 0.28f, size.height * 0.65f), stroke, StrokeCap.Round)
                drawCircle(tint, radius = stroke * 0.55f, center = Offset(size.width * (0.28f + animatedProgress * 0.45f), size.height * 0.65f))
            }

            TimelineToolGlyph.GENERIC -> {
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { index, yRatio ->
                    drawLine(
                        color = tint,
                        start = Offset(size.width * 0.14f, size.height * yRatio),
                        end = Offset(size.width * 0.86f, size.height * yRatio),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = tint,
                        radius = stroke * 0.72f,
                        center = Offset(
                            x = size.width * listOf(0.34f, 0.68f, 0.46f)[index],
                            y = size.height * yRatio,
                        ),
                    )
                }
            }

        }
    }
}

/**
 * 以固定最大高度显示工具详情的原始代码文本，并仅在内容溢出时展示滚动条。
 */
@Composable
private fun ToolEventOutputPane(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TOOL_EVENT_OUTPUT_MAX_HEIGHT)
            .clip(RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp))
            .background(backgroundColor)
            .border(
                1.dp,
                AppLine.copy(alpha = 0.5f),
                RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp),
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(10.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                color = textColor,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp,
            ),
        )
        if (shouldShowToolOutputScrollbar(maxScrollValue = scrollState.maxValue)) {
            CompositionLocalProvider(
                LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                    unhoverColor = Color(0xFF8D96A6),
                    hoverColor = Color(0xFFD7DEEA),
                ),
            ) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp, horizontal = 3.dp),
                )
            }
        }
    }
}

/** 渲染 Answers 详情中的单个内层岛屿，保持问答内容在同一视觉语法内。 */
@Composable
private fun DetailIsland(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp))
            .background(DetailIslandBackground)
            .border(
                1.dp,
                AppLine.copy(alpha = 0.5f),
                RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp),
            )
            .padding(10.dp),
        content = { content() },
    )
}

/**
 * 绘制与展开状态同步旋转的轻量箭头。
 */
@Composable
private fun ToolEventChevron(
    rotation: Float,
    tint: Color = AppMuted,
) {
    Canvas(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = 1.8.dp.toPx()
        drawLine(tint, Offset(size.width * 0.34f, size.height * 0.22f), Offset(size.width * 0.64f, size.height * 0.5f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.64f, size.height * 0.5f), Offset(size.width * 0.34f, size.height * 0.78f), stroke, StrokeCap.Round)
    }
}

/** 工具行仅在鼠标悬浮时展示展开方向，避免静态时间线产生视觉噪声。 */
internal fun shouldShowTimelineToolChevron(hovered: Boolean): Boolean = hovered

/** 保留固定箭头槽位，避免鼠标进出时工具名称和摘要发生位移。 */
@Composable
private fun TimelineToolChevronSlot(
    visible: Boolean,
    rotation: Float,
) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { alpha = if (visible) 1f else 0f },
        contentAlignment = Alignment.Center,
    ) {
        ToolEventChevron(rotation = rotation)
    }
}

/**
 * 旧工具卡片保留至本次渲染替换完成，避免影响其现有辅助函数的复用。
 */
@Composable
private fun TimelineToolEvent(
    item: ToolEventItem,
) {
    val errorMessage = item.errorMessage?.takeIf(String::isNotBlank)
    val isFailed = item.status == ToolEventStatus.Failed
    val isTerminalTool = isTerminalToolEvent(item)
    val hasDetails = toolEventHasDetails(item)
    val inlineInput = buildToolEventInlineInput(item)
    var expanded by remember(toolEventExpansionIdentity(item)) {
        mutableStateOf(shouldExpandToolEventByDefault(item))
    }
    var chevronHovered by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(durationMillis = 160),
        label = "tool-event-chevron",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = AppPanelBackground,
            border = BorderStroke(1.dp, AppLine.copy(alpha = 0.65f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (shouldShowToolEventHeadline(item)) {
                        Text(
                            text = buildToolEventHeadline(item),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isFailed) AppDanger else AppText,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    inlineInput?.let { input ->
                        Text(
                            text = input,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isTerminalTool) AppText else AppMuted,
                                fontFamily = if (isTerminalTool) FontFamily.Monospace else FontFamily.Default,
                            ),
                        )
                    }
                }
                if (hasDetails) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .onPointerEvent(PointerEventType.Enter) { chevronHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { chevronHovered = false }
                            .background(
                                color = if (chevronHovered) AppLine.copy(alpha = 0.72f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                        ) {
                            val stroke = 1.8.dp.toPx()
                            drawLine(AppMuted, Offset(size.width * 0.22f, size.height * 0.34f), Offset(size.width * 0.5f, size.height * 0.64f), stroke, StrokeCap.Round)
                            drawLine(AppMuted, Offset(size.width * 0.5f, size.height * 0.64f), Offset(size.width * 0.78f, size.height * 0.34f), stroke, StrokeCap.Round)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(durationMillis = 180)) + fadeIn(tween(durationMillis = 150)),
                exit = shrinkVertically(tween(durationMillis = 120)) + fadeOut(tween(durationMillis = 100)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    toolEventOutputText(item)?.let { output ->
                        ToolEventOutputPane(
                            text = output,
                            backgroundColor = Color(0xFF17181A),
                            textColor = AppMuted,
                        )
                    }
                    if (errorMessage != null) {
                        ToolEventOutputPane(
                            text = errorMessage,
                            backgroundColor = Color(0xFF2A1518),
                            textColor = AppDanger,
                        )
                    }
                }
            }
            }
        }
        if (isTerminalTool) {
            buildToolEventOperationIntent(item)?.let { intent ->
                Text(
                    text = intent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppText,
                        lineHeight = 23.sp,
                    ),
                )
            }
        }
    }
}

/**
 * 返回工具详情箭头的旋转角度，展开时朝上，收起时朝下。
 */
internal fun toolEventChevronRotation(expanded: Boolean): Float = if (expanded) 90f else 0f

/**
 * 返回决定工具卡片展开状态归属的稳定字段，结果文本更新不应重置用户的展开选择。
 */
internal fun toolEventExpansionIdentity(item: ToolEventItem): List<Any?> = listOf(
    item.toolCallId,
    item.toolName,
    item.preview,
)

/**
 * 工具输出列表存在可滚动内容时显示滚动条，避免短输出产生无意义的视觉噪声。
 */
internal fun shouldShowToolOutputScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0
