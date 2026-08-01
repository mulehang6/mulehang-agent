@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
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
import com.agent.app.chat.presentation.toolEventOutputChunks
import com.agent.app.chat.presentation.toolEventOutputText
import com.agent.app.chat.presentation.shouldExpandToolEventByDefault
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.AppDanger
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
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
import com.halilibo.richtext.ui.WithStyle
import com.halilibo.richtext.ui.string.RichTextStringStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM

/**
 * 完整会话时间线，按顺序渲染所有用户消息、助手回答、思考块和工具事件。
 */
@Composable
internal fun ConversationTimeline(
    conversation: ChatConversationUiState,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        conversation.items.forEachIndexed { index, item ->
            when (item) {
                is ChatMessageItem -> {
                    if (item.message.role == ChatRole.User) {
                        UserMessageCard(item.message.content)
                    } else {
                        AssistantMessageBlock(
                            content = item.message.content,
                            isStreaming = index == conversation.streamingAssistantItemIndex,
                        )
                    }
                }

                is ReasoningItem -> TimelineReasoningItem(item)
                is ToolEventItem -> TimelineToolEvent(item)
            }
        }
        if (conversation.executionState == ExecutionState.Running) {
            buildSecondaryStatus(conversation)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                )
            }
        }
        if (failedState != null && !hasFailedToolEvent) {
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

/**
 * 单条用户消息卡片。
 */
@Composable
private fun UserMessageCard(content: String) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth * 0.8f)
                .wrapContentWidth(),
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
                BasicRichText(modifier = Modifier.fillMaxWidth()) {
                    WithStyle(
                        style = RichTextStyle(
                            stringStyle = RichTextStringStyle(
                                linkStyle = assistantMarkdownLinkStyle(),
                            ),
                        ),
                    ) {
                        Markdown(content = normalizedContent)
                    }
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
        )
    } else {
        MaterialTheme.typography.titleSmall.copy(
            color = AppMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildReasoningHeadline(item),
            modifier = Modifier.clickable { expanded = !expanded },
            style = headlineStyle,
        )
        if (expanded) {
            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                ),
            )
        }
    }
}

/**
 * 时间线中的工具事件条目。
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
    val outputChunks = remember(item.resultPreview, item.resultDisplay) { toolEventOutputChunks(item) }
    val outputListState = rememberLazyListState()
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
                    toolEventOutputText(item)?.let {
                        if (outputChunks.isEmpty()) {
                            Text(
                                text = "无输出内容",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF17181A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = AppMuted,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                ),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .background(Color(0xFF17181A), RoundedCornerShape(6.dp)),
                            ) {
                                LazyColumn(
                                    state = outputListState,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    items(outputChunks) { chunk ->
                                        Text(
                                            text = chunk,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = AppMuted,
                                                fontSize = 14.sp,
                                                lineHeight = 21.sp,
                                            ),
                                        )
                                    }
                                }
                                if (
                                    shouldShowToolOutputScrollbar(
                                        canScrollForward = outputListState.canScrollForward ||
                                            outputListState.canScrollBackward,
                                    )
                                ) {
                                    CompositionLocalProvider(
                                        LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                                            unhoverColor = Color(0xFF747983),
                                            hoverColor = Color(0xFFB8BEC8),
                                        ),
                                    ) {
                                        VerticalScrollbar(
                                            adapter = rememberScrollbarAdapter(outputListState),
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .padding(vertical = 4.dp, horizontal = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AppDanger,
                                lineHeight = 20.sp,
                            ),
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
internal fun shouldShowToolOutputScrollbar(canScrollForward: Boolean): Boolean = canScrollForward
