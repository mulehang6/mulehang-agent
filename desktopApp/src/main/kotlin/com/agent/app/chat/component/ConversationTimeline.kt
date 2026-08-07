@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.agent.app.chat.presentation.buildReasoningHeadline
import com.agent.app.chat.presentation.buildSecondaryStatus
import com.agent.app.chat.presentation.buildToolEventInlineInput
import com.agent.app.chat.presentation.buildToolEventHeadline
import com.agent.app.chat.presentation.buildToolEventOperationIntent
import com.agent.app.chat.presentation.isTerminalToolEvent
import com.agent.app.chat.presentation.shouldShowToolEventHeadline
import com.agent.app.chat.presentation.toolEventHasDetails
import com.agent.app.chat.presentation.toolEventOutputText
import com.agent.app.chat.presentation.shouldExpandToolEventByDefault
import com.agent.app.chat.presentation.shouldAutoExpandRunningTerminalOutput
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.AppDanger
import com.agent.app.design.AppAccent
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppReasoning
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.design.AppUserCardBackground
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.string.RichTextStringStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM

/**
 * 时间线在渲染前使用的展示段，不改变底层会话事件。
 */
internal sealed interface TimelineDisplayItem {
    /** 此展示段包含的原始时间线项数量。 */
    val itemCount: Int

    /** 不参与工具合并的普通时间线项。 */
    data class Content(val item: com.agent.shared.chat.model.ConversationItem) : TimelineDisplayItem {
        override val itemCount: Int = 1
    }

    /** 相邻的工具调用组成的展示组，保留原始事件的先后顺序。 */
    data class ToolGroup(val items: List<ToolEventItem>) : TimelineDisplayItem {
        override val itemCount: Int = items.size
    }
}

/**
 * 合并相邻工具调用；状态文本与其他时间线项均构成明确边界。
 */
internal fun groupTimelineItems(
    items: List<com.agent.shared.chat.model.ConversationItem>,
): List<TimelineDisplayItem> {
    val result = mutableListOf<TimelineDisplayItem>()
    val pendingTools = mutableListOf<ToolEventItem>()
    fun flushTools() {
        when (pendingTools.size) {
            0 -> Unit
            1 -> result += TimelineDisplayItem.Content(pendingTools.single())
            else -> result += TimelineDisplayItem.ToolGroup(pendingTools.toList())
        }
        pendingTools.clear()
    }
    items.forEach { item ->
        if (item is ToolEventItem && item.status != ToolEventStatus.Status && item.status != ToolEventStatus.Failed) {
            pendingTools += item
        } else {
            flushTools()
            result += TimelineDisplayItem.Content(item)
        }
    }
    flushTools()
    return result
}

/**
 * 构造收起状态下统一的工具组标题。
 */
internal fun buildToolGroupHeadline(count: Int): String = "Executed tools · $count"

/** 工具文字行的垂直内边距，保持为零以贴近终端式活动列表。 */
internal const val TOOL_EVENT_ROW_VERTICAL_PADDING_DP = 0

/** 工具组标题的字号，略高于工具行以便快速扫读当前动作。 */
internal const val TOOL_GROUP_TITLE_FONT_SIZE_SP = 16

/** 工具行的字号，确保工具名称和参数在主时间线中清晰可读。 */
internal const val TOOL_ROW_FONT_SIZE_SP = 15

/** 工具组标题切换采用较慢的双轨过渡，避免连续工具调用显得跳跃。 */
internal const val TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS = 420

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

/** 思考块标题的字号，保持内容块层级清晰。 */
internal const val REASONING_HEADLINE_FONT_SIZE_SP = 16

/** 思考块正文的字号，避免长文本显得过小。 */
internal const val REASONING_BODY_FONT_SIZE_SP = 15

/** 工具行使用的紧凑类型图标。 */
internal enum class TimelineToolGlyph {
    SEARCH,
    DIRECTORY,
    TERMINAL,
    EDIT,
    READ,
    NETWORK,
    GENERIC,
    SUCCESS,
}

/** 工具的图标与工具组动态标题，标题为空时维持数量摘要。 */
internal data class TimelineToolPresentation(
    val glyph: TimelineToolGlyph,
    val groupHeadline: String?,
)

/** 根据工具名解析时间线所需的图标和当前动作标题。 */
internal fun timelineToolPresentation(item: ToolEventItem): TimelineToolPresentation {
    val toolName = item.toolName.lowercase()
    return when {
        isTerminalToolEvent(item) -> TimelineToolPresentation(TimelineToolGlyph.TERMINAL, null)
        toolName.contains("edit") || toolName.contains("patch") || toolName.contains("write") ->
            TimelineToolPresentation(TimelineToolGlyph.EDIT, "Editing…")

        toolName.contains("directory") || toolName.contains("list_dir") || toolName.contains("list_files") ->
            TimelineToolPresentation(TimelineToolGlyph.DIRECTORY, "Gathering context…")

        toolName.contains("grep") || toolName.contains("search") || toolName.contains("find") || toolName.contains("glob") ->
            TimelineToolPresentation(TimelineToolGlyph.SEARCH, "Gathering context…")

        toolName.contains("read") || toolName.contains("cat") ->
            TimelineToolPresentation(TimelineToolGlyph.READ, "Gathering context…")

        toolName.contains("http") || toolName.contains("web") || toolName.contains("download") ->
            TimelineToolPresentation(TimelineToolGlyph.NETWORK, "Searching web…")

        else -> TimelineToolPresentation(TimelineToolGlyph.GENERIC, null)
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

/** 进行中的组沿用当前工具图标，完成后统一显示成功勾选。 */
internal fun timelineToolGroupGlyph(items: List<ToolEventItem>): TimelineToolGlyph =
    activeTimelineTool(items)?.let(::timelineToolGlyph) ?: TimelineToolGlyph.SUCCESS

/** 运行状态使用蓝色强调；全部完成后用绿色明确反馈成功。 */
internal fun timelineToolGroupTint(items: List<ToolEventItem>): Color =
    if (activeTimelineTool(items) == null) AppSuccess else AppAccent

/** 思考运行和完成分别使用蓝、紫，避免时间线只剩一片灰色。 */
internal fun timelineReasoningTint(streaming: Boolean): Color = if (streaming) AppAccent else AppReasoning

/** 返回工具组当前的语义标题；Shell 和未知工具保留稳定数量摘要。 */
internal fun toolGroupHeadline(items: List<ToolEventItem>): String {
    val runningTools = items.filter { it.status == ToolEventStatus.Started }
    if (runningTools.isEmpty()) return buildToolGroupHeadline(items.size)

    return runningTools
        .asReversed()
        .firstNotNullOfOrNull { item -> timelineToolPresentation(item).groupHeadline }
        ?: items
            .asReversed()
            .firstNotNullOfOrNull { item -> timelineToolPresentation(item).groupHeadline }
        ?: buildToolGroupHeadline(items.size)
}

/** 仅进行中的工具让图标本体持续运动，完成与失败状态保持静止。 */
internal fun shouldAnimateTimelineToolGlyph(status: ToolEventStatus): Boolean = status == ToolEventStatus.Started

/** 组内不存在进行中的工具时，工具组应自行收起。 */
internal fun shouldAutoCollapseTimelineToolGroup(items: List<ToolEventItem>): Boolean =
    items.isNotEmpty() && items.none { it.status == ToolEventStatus.Started }

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
                is ToolEventItem -> TimelineToolTextRow(item, isFailure = item.status == ToolEventStatus.Failed)
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
        if (shouldRenderMarkdownDiagram(isStreaming)) {
            parseAssistantMarkdownDocument(content.trim())
        } else {
            AssistantMarkdownDocument(
                blocks = listOf(AssistantMarkdownBlock.Text(content.trim())),
                footnotes = emptyList(),
            )
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

/** 返回正文 Markdown 共用的行内字符串样式，令链接与代码片段保持统一视觉层级。 */
internal fun assistantMarkdownStringStyle(): RichTextStringStyle = RichTextStringStyle(
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
        contentColorProvider = { AppText },
        contentColorBackProvider = { color, children ->
            CompositionLocalProvider(LocalContentColor provides color) { children() }
        },
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
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
    var renderedSvg by remember(source) { mutableStateOf<Result<String>?>(null) }
    LaunchedEffect(source) {
        renderedSvg = runCatching {
            withContext(Dispatchers.Default) { renderPlantUmlToSvg(source) }
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
                PlantUmlSvg(svg)
            } else {
                AssistantMarkdownText("```plantuml\n$source\n```")
            }
        }
    }
}

/**
 * 使用 Compose Desktop 已携带的 Skia SVG 支持绘制本地 PlantUML 输出。
 */
@Composable
private fun PlantUmlSvg(svg: String) {
    val document = remember(svg) { SVGDOM(Data.makeFromBytes(svg.encodeToByteArray())) }
    DisposableEffect(document) {
        onDispose(document::close)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.65f)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            document.setContainerSize(size.width, size.height)
            drawIntoCanvas { canvas ->
                document.render(canvas.skiaCanvas)
            }
        }
    }
}

/**
 * 仅在 Agent 完成回复后启用图表渲染，避免不完整的流式围栏触发布局抖动。
 */
internal fun shouldRenderMarkdownDiagram(isStreaming: Boolean): Boolean = !isStreaming

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
        if (expanded) {
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
    var hovered by remember { mutableStateOf(false) }
    val shouldAutoCollapse = shouldAutoCollapseTimelineToolGroup(items)
    LaunchedEffect(items.map(ToolEventItem::status), userSetExpansion) {
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
    val activeTool = activeTimelineTool(items)
    val groupGlyph = timelineToolGroupGlyph(items)
    val groupTint = timelineToolGroupTint(items)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .background(if (hovered) AppHoverBackground else Color.Transparent, RoundedCornerShape(7.dp))
                .clickable {
                    userSetExpansion = true
                    expanded = !expanded
                }
                .padding(horizontal = 10.dp, vertical = TOOL_EVENT_ROW_VERTICAL_PADDING_DP.dp),
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
            AnimatedContent(
                targetState = toolGroupHeadline(items),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = tween(durationMillis = TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS),
                        initialOffsetY = { height -> height / 2 },
                    ) + fadeIn(tween(durationMillis = TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS)))
                        .togetherWith(
                            slideOutVertically(
                                animationSpec = tween(durationMillis = TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS),
                                targetOffsetY = { height -> -height / 2 },
                            ) + fadeOut(tween(durationMillis = TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS)),
                        )
                },
                label = "tool-group-headline",
            ) { headline ->
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppMuted,
                        fontSize = TOOL_GROUP_TITLE_FONT_SIZE_SP.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (hovered) ToolEventChevron(chevronRotation)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(durationMillis = TOOL_GROUP_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = TOOL_GROUP_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = TOOL_GROUP_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = TOOL_GROUP_COLLAPSE_DURATION_MILLIS)),
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { TimelineToolTextRow(it, isFailure = false) }
            }
        }
    }
}

/**
 * 无边框的单个工具文本行，可按需展开完整输出或错误内容。
 */
@Composable
private fun TimelineToolTextRow(
    item: ToolEventItem,
    isFailure: Boolean,
) {
    val hasDetails = toolEventHasDetails(item) || item.errorMessage?.isNotBlank() == true
    val glyph = timelineToolGlyph(item)
    val running = shouldAnimateTimelineToolGlyph(item.status)
    var expanded by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    var userSetExpansion by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    LaunchedEffect(item.status, item.resultDisplay, item.resultPreview) {
        if (!userSetExpansion && shouldAutoExpandRunningTerminalOutput(item)) {
            expanded = true
        }
    }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(
            durationMillis = if (expanded) TOOL_ROW_EXPAND_DURATION_MILLIS else TOOL_ROW_COLLAPSE_DURATION_MILLIS,
        ),
        label = "tool-text-chevron",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .background(if (hovered) AppHoverBackground else Color.Transparent, RoundedCornerShape(7.dp))
                .clickable(enabled = hasDetails) {
                    userSetExpansion = true
                    expanded = !expanded
                }
                .padding(horizontal = 10.dp, vertical = TOOL_EVENT_ROW_VERTICAL_PADDING_DP.dp),
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
                    .weight(1f)
                    .padding(start = 7.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isFailure) AppDanger else AppText,
                    fontSize = TOOL_ROW_FONT_SIZE_SP.sp,
                    fontFamily = if (isTerminalToolEvent(item)) FontFamily.Monospace else FontFamily.Default,
                ),
            )
            if (hasDetails && hovered) ToolEventChevron(chevronRotation)
        }
        AnimatedVisibility(
            visible = expanded && hasDetails,
            enter = expandVertically(tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = TOOL_ROW_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = TOOL_ROW_COLLAPSE_DURATION_MILLIS)),
        ) {
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                timelineToolExpandedInput(item)?.let { input ->
                    Text(
                        text = input,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AppMuted,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                        ),
                    )
                }
                toolEventOutputText(item)?.let { output ->
                    ToolEventOutputPane(
                        text = output,
                        backgroundColor = Color(0xFF17181A),
                        textColor = AppMuted,
                    )
                }
                item.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                    ToolEventOutputPane(
                        text = error,
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
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "timeline-reasoning-glyph-breathe",
    )
    Canvas(
        modifier = Modifier
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
                val ringRadius = size.minDimension * 0.28f
                val ringTopLeft = Offset(size.width * 0.5f - ringRadius, size.height * 0.5f - ringRadius)
                drawCircle(
                    color = tint.copy(alpha = 0.45f),
                    radius = ringRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawArc(
                    color = tint,
                    startAngle = animatedProgress * 360f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
            }

            TimelineToolGlyph.SUCCESS -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.18f, size.height * 0.54f),
                    Offset(size.width * 0.42f, size.height * 0.76f),
                    stroke * 1.35f,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.42f, size.height * 0.76f),
                    Offset(size.width * 0.83f, size.height * 0.25f),
                    stroke * 1.35f,
                    StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 以固定最大高度显示工具原始输出，并仅在内容溢出时展示高对比度滚动条。
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
            .background(backgroundColor, RoundedCornerShape(6.dp)),
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

/**
 * 绘制与展开状态同步旋转的轻量箭头。
 */
@Composable
private fun ToolEventChevron(rotation: Float) {
    Canvas(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = 1.8.dp.toPx()
        drawLine(AppMuted, Offset(size.width * 0.22f, size.height * 0.34f), Offset(size.width * 0.5f, size.height * 0.64f), stroke, StrokeCap.Round)
        drawLine(AppMuted, Offset(size.width * 0.5f, size.height * 0.64f), Offset(size.width * 0.78f, size.height * 0.34f), stroke, StrokeCap.Round)
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
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.65f)),
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
internal fun toolEventChevronRotation(expanded: Boolean): Float = if (expanded) 180f else 0f

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
