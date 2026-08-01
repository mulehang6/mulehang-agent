package com.agent.app.chat.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

/**
 * 显示一张远程 Markdown 图片，并在加载或失败时提供不会留白的可读反馈。
 */
@Composable
internal fun AssistantMarkdownImage(
    alt: String,
    url: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppPanelBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.7f)),
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = alt.ifBlank { "Markdown 图片" },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 360.dp),
            contentScale = ContentScale.Fit,
            loading = { AssistantImageStatus("正在加载图片…") },
            error = { AssistantImageStatus(if (alt.isBlank()) "图片加载失败" else "图片加载失败：$alt") },
        )
    }
}

/** 显示图片异步状态，避免桌面端图片请求失败时留下空白区域。 */
@Composable
private fun AssistantImageStatus(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
        )
    }
}

/**
 * 显示扩展 Markdown 的一项定义列表，保持术语和释义的视觉层级。
 */
@Composable
internal fun AssistantDefinitionListItem(
    term: String,
    definition: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = term,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AppText,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = definition,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AppMuted,
                lineHeight = 22.sp,
            ),
        )
    }
}

/**
 * 显示只允许颜色白名单的 HTML span，不执行任何脚本或任意 CSS。
 */
@Composable
internal fun AssistantSafeHtmlSpan(
    content: String,
    colorName: String,
) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = safeHtmlColor(colorName),
            lineHeight = 22.sp,
        ),
    )
}

/** 显示允许 `align` 属性的 div，其内容仍沿用 Markdown renderer。 */
@Composable
internal fun AssistantSafeHtmlBlock(
    content: String,
    alignment: AssistantHtmlAlignment,
) {
    val contentAlignment = when (alignment) {
        AssistantHtmlAlignment.Start -> Alignment.TopStart
        AssistantHtmlAlignment.Center -> Alignment.TopCenter
        AssistantHtmlAlignment.End -> Alignment.TopEnd
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = contentAlignment,
    ) {
        Box(modifier = Modifier.widthIn(max = 720.dp)) {
            AssistantMarkdownText(content)
        }
    }
}

/**
 * 用本地 JLaTeXMath 将 LaTeX 绘制为位图；不会创建浏览器、WebView 或网络请求。
 */
@Composable
internal fun AssistantMathFormula(
    source: String,
    display: Boolean,
) {
    val renderScale = LocalDensity.current.density
    var rendered by remember(source, display, renderScale) { mutableStateOf<Result<RenderedLatexFormula>?>(null) }
    LaunchedEffect(source, display, renderScale) {
        rendered = runCatching {
            withContext(Dispatchers.Default) { renderLatexFormula(source, display, renderScale) }
        }
    }
    when (val result = rendered) {
        null -> Text(
            text = "正在排版数学公式…",
            style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
        )

        else -> result.getOrNull()?.let { formula ->
            RenderedLatexFormulaView(formula, display)
        } ?: Text(
            text = if (display) "${'$'}${'$'}$source${'$'}${'$'}" else "${'$'}$source${'$'}",
            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
        )
    }
}

/** 显示已在后台生成的公式位图，并保持紧凑的自然尺寸。 */
@Composable
private fun RenderedLatexFormulaView(
    formula: RenderedLatexFormula,
    display: Boolean,
) {
    val imageModifier = Modifier.size(
        width = formulaImageDimensionDp(formula.widthPx, formula.renderScale),
        height = formulaImageDimensionDp(formula.heightPx, formula.renderScale),
    )
    if (display) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = AppPanelBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.62f)),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = formula.bitmap,
                    contentDescription = "数学公式",
                    modifier = imageModifier,
                )
            }
        }
    } else {
        Image(
            bitmap = formula.bitmap,
            contentDescription = "数学公式",
            modifier = imageModifier,
        )
    }
}

/**
 * 显示原生可点击的 HTML details 块；视觉上保持为低干扰的折叠条目。
 */
@Composable
internal fun AssistantMarkdownDetails(
    summary: String,
    content: String,
) {
    var expanded by remember(summary, content) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppPanelBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.7f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppText,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Canvas(modifier = Modifier.size(18.dp)) {
                    val top = size.height * if (expanded) 0.62f else 0.38f
                    val bottom = size.height * if (expanded) 0.38f else 0.62f
                    val stroke = 1.8.dp.toPx()
                    drawLine(
                        color = AppMuted,
                        start = Offset(size.width * 0.22f, top),
                        end = Offset(size.width * 0.5f, bottom),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = AppMuted,
                        start = Offset(size.width * 0.5f, bottom),
                        end = Offset(size.width * 0.78f, top),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    AssistantMarkdownText(content)
                }
            }
        }
    }
}

/**
 * 在正文之后显示所有脚注定义；正文引用以 Unicode 上标序号替代原始 `[^id]` 标记。
 */
@Composable
internal fun AssistantFootnoteSection(footnotes: List<AssistantFootnote>) {
    if (footnotes.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = AppLine.copy(alpha = 0.72f))
        Text(
            text = "脚注",
            style = MaterialTheme.typography.labelMedium.copy(
                color = AppMuted,
                fontWeight = FontWeight.Medium,
            ),
        )
        footnotes.forEachIndexed { index, footnote ->
            Text(
                text = "${index + 1}. ${footnote.content}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}

/** 将经解析器白名单验证的 CSS 颜色名称映射为应用可用的 Compose 颜色。 */
private fun safeHtmlColor(colorName: String): Color = when (colorName.lowercase()) {
    "red" -> Color(0xFFFF6B6B)
    "green" -> Color(0xFF87C995)
    "blue" -> Color(0xFF8CB8FF)
    "orange" -> Color(0xFFFFB86B)
    "yellow" -> Color(0xFFF2D67A)
    "purple" -> Color(0xFFC4A5FF)
    "gray", "grey" -> AppMuted
    else -> AppText
}

/** JLaTeXMath 输出的不可变公式位图及其像素尺寸。 */
private data class RenderedLatexFormula(
    val bitmap: ImageBitmap,
    val widthPx: Int,
    val heightPx: Int,
    val renderScale: Float,
)

/** 在工作线程中把 TeX 源码绘制到透明位图。 */
private fun renderLatexFormula(
    source: String,
    display: Boolean,
    renderScale: Float,
): RenderedLatexFormula {
    val image = renderLatexFormulaImage(source, display, renderScale)
    val bytes = ByteArrayOutputStream().use { output ->
        ImageIO.write(image, "png", output)
        output.toByteArray()
    }
    return RenderedLatexFormula(
        bitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap(),
        widthPx = image.width,
        heightPx = image.height,
        renderScale = renderScale,
    )
}

/** 将 LaTeX 公式绘制为使用深色主题高对比度前景色的 AWT 位图。 */
internal fun renderLatexFormulaImage(
    source: String,
    display: Boolean,
    renderScale: Float = 1f,
): BufferedImage {
    require(renderScale > 0f) { "Formula render scale must be positive" }
    val formula = TeXFormula(source)
    val icon = formula.createTeXIcon(
        TeXConstants.STYLE_DISPLAY,
        (if (display) 22f else 18f) * renderScale,
    )
    icon.setForeground(LATEX_FOREGROUND)
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        icon.paintIcon(null, graphics, 0, 0)
    } finally {
        graphics.dispose()
    }
    return image
}

/** 将按显示密度生成的位图像素尺寸转换为布局 dp，避免 Compose 再次缩放。 */
internal fun formulaImageDimensionDp(pixelSize: Int, renderScale: Float) =
    (pixelSize / renderScale).dp

private val LATEX_FOREGROUND = java.awt.Color(0xE7, 0xEA, 0xF0)
