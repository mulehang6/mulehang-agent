package com.agent.app.tool.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.design.AppDanger
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.shared.tool.model.FileChangeKind
import com.agent.shared.tool.model.FileDiffLineKind
import com.agent.shared.tool.model.FileDiffLinePreview
import com.agent.shared.tool.model.FileDiffPreview
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** 编辑器式 unified Diff 预览：保留行号、增删语义和可展开的上下文。 */
@Composable
internal fun EditorDiffPreview(diff: FileDiffPreview) {
    var expandedContextRuns by remember(diff.path, diff.editorLines) { mutableStateOf(emptySet<Int>()) }
    val rows = editorDiffRows(diff.editorLines, expandedContextRuns)
    Column {
        EditorDiffFileHeader(diff)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppLine, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(AppPanelBackground, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .horizontalScroll(rememberScrollState()),
        ) {
            rows.forEach { row ->
                when (row) {
                    is EditorDiffRow.Line -> EditorDiffLine(row.line)
                    is EditorDiffRow.CollapsedContext -> CollapsedContextLine(
                        row = row,
                        onExpand = { expandedContextRuns = expandedContextRuns + row.runIndex },
                    )
                }
            }
        }
    }
}

/** 文件级标题为编辑器画布提供变更类型和路径上下文。 */
@Composable
private fun EditorDiffFileHeader(diff: FileDiffPreview) {
    val tint = when (diff.kind) {
        FileChangeKind.CREATED -> AppSuccess
        FileChangeKind.MODIFIED -> AppMuted
        FileChangeKind.DELETED -> AppDanger
    }
    val label = when (diff.kind) {
        FileChangeKind.CREATED -> "新增"
        FileChangeKind.MODIFIED -> "修改"
        FileChangeKind.DELETED -> "删除"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppPanelBackground, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .border(1.dp, AppLine, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = JewelTheme.defaultTextStyle.copy(color = tint, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(8.dp))
        Text(
            text = diff.path,
            style = JewelTheme.defaultTextStyle.copy(color = AppText, fontFamily = FontFamily.Monospace),
        )
    }
}

/** 绘制单个 Diff 行，增删符号位于专用 gutter，代码文本不包含协议前缀。 */
@Composable
private fun EditorDiffLine(line: FileDiffLinePreview) {
    val tint = when (line.kind) {
        FileDiffLineKind.CONTEXT -> Color.Transparent
        FileDiffLineKind.REMOVED -> AppDanger.copy(alpha = 0.18f)
        FileDiffLineKind.ADDED -> AppSuccess.copy(alpha = 0.18f)
    }
    val marker = when (line.kind) {
        FileDiffLineKind.CONTEXT -> " "
        FileDiffLineKind.REMOVED -> "−"
        FileDiffLineKind.ADDED -> "+"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(marker, Modifier.width(18.dp), style = editorDiffTextStyle(color = AppMuted))
        EditorLineNumber(line.oldLineNumber)
        EditorLineNumber(line.newLineNumber)
        Text(line.content, style = editorDiffTextStyle(color = AppText), modifier = Modifier.padding(start = 8.dp, end = 12.dp))
    }
}

/** 绘制可点击的折叠上下文行；点击仅展开当前区段。 */
@Composable
private fun CollapsedContextLine(row: EditorDiffRow.CollapsedContext, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppLine.copy(alpha = 0.42f))
            .clickable(onClick = onExpand)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text("⋯ ${row.hiddenLineCount} 行未变更", style = editorDiffTextStyle(color = AppMuted))
    }
}

/** 两侧行号列保持固定宽度，使代码列在连续行间稳定对齐。 */
@Composable
private fun EditorLineNumber(number: Int?) {
    Text(
        text = number?.toString().orEmpty(),
        modifier = Modifier.width(DIFF_GUTTER_WIDTH),
        style = editorDiffTextStyle(color = AppMuted),
    )
}

/** Diff 的等宽字体与紧凑行高，接近 IDE 编辑器而非普通正文。 */
@Composable
private fun editorDiffTextStyle(color: Color) = JewelTheme.defaultTextStyle.copy(
    color = color,
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.sp,
)

/** 编辑器画布中的可视行，折叠项不包含或泄露正文。 */
internal sealed interface EditorDiffRow {
    data class Line(val line: FileDiffLinePreview) : EditorDiffRow
    data class CollapsedContext(val runIndex: Int, val hiddenLineCount: Int) : EditorDiffRow
}

/**
 * 默认只展示每段上下文的前后各三行；中间未改动内容折叠为一行。
 *
 * [expandedContextRuns] 只影响对应区段，不会展开同一文件其他修改附近的上下文。
 */
internal fun editorDiffRows(
    lines: List<FileDiffLinePreview>,
    expandedContextRuns: Set<Int> = emptySet(),
): List<EditorDiffRow> = buildList {
    var index = 0
    var contextRun = 0
    while (index < lines.size) {
        if (lines[index].kind != FileDiffLineKind.CONTEXT) {
            add(EditorDiffRow.Line(lines[index]))
            index += 1
            continue
        }
        val start = index
        while (index < lines.size && lines[index].kind == FileDiffLineKind.CONTEXT) index += 1
        val context = lines.subList(start, index)
        if (context.size <= MAX_VISIBLE_CONTEXT_LINES || contextRun in expandedContextRuns) {
            context.forEach { add(EditorDiffRow.Line(it)) }
        } else {
            context.take(CONTEXT_EDGE_LINES).forEach { add(EditorDiffRow.Line(it)) }
            add(EditorDiffRow.CollapsedContext(contextRun, context.size - CONTEXT_EDGE_LINES * 2))
            context.takeLast(CONTEXT_EDGE_LINES).forEach { add(EditorDiffRow.Line(it)) }
        }
        contextRun += 1
    }
}

private val DIFF_GUTTER_WIDTH = 42.dp
private const val CONTEXT_EDGE_LINES = 3
private const val MAX_VISIBLE_CONTEXT_LINES = CONTEXT_EDGE_LINES * 2
