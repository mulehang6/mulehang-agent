package com.agent.app.chat.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.LocalDesktopUiScalePercent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
/** 离线图表从后台 SVG 生成到最终回退的可观察状态。 */
internal sealed interface DiagramPreviewState {
    /** PlantUML 正在使用本地引擎生成 SVG。 */
    data object GeneratingSvg : DiagramPreviewState

    /** Mermaid 正在通过共享的不可见 JCEF 工作器生成 SVG。 */
    data object GeneratingMermaid : DiagramPreviewState

    /** 后台渲染器已返回图表 SVG，并保留可选宽高比。 */
    data class Ready(
        val aspectRatio: Float? = null,
    ) : DiagramPreviewState

    /** 任一可恢复失败都会回退为原始代码块。 */
    data class Failed(
        val failure: DiagramPreviewFailure,
    ) : DiagramPreviewState
}

/** 判断当前状态是否已允许用户操作页面内的图表缩放。 */
internal fun isDiagramPreviewReady(state: DiagramPreviewState): Boolean = state is DiagramPreviewState.Ready

/** 为新建或重新打开的图表渲染创建初始异步状态。 */
private fun initialDiagramPreviewState(kind: AssistantDiagramKind): DiagramPreviewState =
    if (kind == AssistantDiagramKind.PLANT_UML) {
        DiagramPreviewState.GeneratingSvg
    } else {
        DiagramPreviewState.GeneratingMermaid
    }

/**
 * 显示已闭合 Markdown 图表。两种图表最终都交给 Compose 的 SVG 画布绘制。
 */
@Composable
internal fun AssistantDiagramPreview(
    kind: AssistantDiagramKind,
    source: String,
) {
    val palette = LocalDesktopPalette.current
    val globalScalePercent = LocalDesktopUiScalePercent.current
    var displayMode by remember(kind, source, palette.isDark) {
        mutableStateOf(DiagramPreviewDisplayMode.RENDERED)
    }
    var previewState by remember(kind, source, palette.isDark) {
        mutableStateOf(initialDiagramPreviewState(kind))
    }
    var renderedSvg by remember(kind, source, palette.isDark) {
        mutableStateOf<String?>(null)
    }
    var zoomPercent by remember(kind, source, palette.isDark) {
        mutableStateOf(DIAGRAM_DEFAULT_ZOOM_PERCENT)
    }
    var zoomInput by remember(kind, source, palette.isDark) {
        mutableStateOf(TextFieldValue(DIAGRAM_DEFAULT_ZOOM_PERCENT.toString()))
    }
    LaunchedEffect(kind, source, palette.isDark, displayMode) {
        if (displayMode != DiagramPreviewDisplayMode.RENDERED) {
            return@LaunchedEffect
        }
        renderedSvg = null
        previewState = initialDiagramPreviewState(kind)
        try {
            if (kind == AssistantDiagramKind.PLANT_UML) {
                val svg = withContext(Dispatchers.Default) {
                    renderPlantUmlToSvg(source, palette.isDark)
                }
                renderedSvg = svg
                previewState = DiagramPreviewState.Ready(diagramSvgAspectRatio(svg))
                return@LaunchedEffect
            }
            previewState = DiagramPreviewState.GeneratingMermaid
            when (val result = renderMermaidToSvg(source, palette.isDark)) {
                is DiagramRenderResult.Success -> {
                    renderedSvg = result.svg
                    previewState = DiagramPreviewState.Ready(diagramSvgAspectRatio(result.svg))
                }

                is DiagramRenderResult.Failure -> {
                    logDiagramPreviewFailure(result.failure)
                    previewState = DiagramPreviewState.Failed(result.failure)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val failure = if (error is SvgTextOutliningException) {
                DiagramPreviewFailure(
                    kind = DiagramFailureKind.SVG_TEXT_OUTLINE,
                    detail = error.message ?: "SVG 标签无法转换为矢量路径。",
                    cause = error,
                )
            } else {
                DiagramPreviewFailure(
                    kind = if (kind == AssistantDiagramKind.PLANT_UML) {
                        DiagramFailureKind.PLANT_UML_RENDER
                    } else {
                        DiagramFailureKind.BROWSER_SESSION
                    },
                    detail = error.message ?: "离线图表无法生成 SVG。",
                    cause = error,
                )
            }
            logDiagramPreviewFailure(failure)
            previewState = DiagramPreviewState.Failed(failure)
        }
    }

    val updateZoom: (Int) -> Unit = { requestedPercent ->
        val normalizedPercent = normalizeDiagramZoomPercent(requestedPercent)
        zoomPercent = normalizedPercent
        val normalizedText = normalizedPercent.toString()
        zoomInput = TextFieldValue(normalizedText, selection = TextRange(normalizedText.length))
    }

    when (displayMode) {
        DiagramPreviewDisplayMode.SOURCE -> DiagramSourceSurface(
            kind = kind,
            source = source,
            zoomPercent = zoomPercent,
            zoomInput = zoomInput,
            onZoomInputChange = { zoomInput = it },
            onZoomChange = updateZoom,
            onDisplayModeChange = { displayMode = it },
        )

        DiagramPreviewDisplayMode.RENDERED -> when (val state = previewState) {
            is DiagramPreviewState.Failed -> DiagramCodeFallback(kind, source, state.failure.fallbackMessage())
            DiagramPreviewState.GeneratingSvg -> DiagramLoading("正在生成 PlantUML 图表…")
            DiagramPreviewState.GeneratingMermaid,
            is DiagramPreviewState.Ready,
            -> if (state is DiagramPreviewState.Ready && renderedSvg != null) {
                DiagramSvgSurface(
                    kind = kind,
                    source = source,
                    svg = renderedSvg!!,
                    zoomPercent = zoomPercent,
                    globalScalePercent = globalScalePercent,
                    zoomInput = zoomInput,
                    onZoomInputChange = { zoomInput = it },
                    onZoomChange = updateZoom,
                    onDisplayModeChange = { displayMode = it },
                )
            } else {
                DiagramLoading(
                    "正在生成 Mermaid 图表…",
                )
            }
        }
    }
}

/** 在后台渲染完成前显示明确的状态，避免出现无提示空框。 */
@Composable
private fun DiagramLoading(message: String) {
    Text(
        text = message,
        style = JewelTheme.defaultTextStyle.copy(color = LocalDesktopPalette.current.muted),
    )
}

/** 在与渲染视图相同的图表 Island 中显示原始围栏源码。 */
@Composable
private fun DiagramSourceSurface(
    kind: AssistantDiagramKind,
    source: String,
    zoomPercent: Int,
    zoomInput: TextFieldValue,
    onZoomInputChange: (TextFieldValue) -> Unit,
    onZoomChange: (Int) -> Unit,
    onDisplayModeChange: (DiagramPreviewDisplayMode) -> Unit,
) {
    val palette = LocalDesktopPalette.current
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 12.dp,
        solidColor = palette.panelBackground,
        borderColor = palette.line,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DiagramPreviewToolbar(
                displayMode = DiagramPreviewDisplayMode.SOURCE,
                enabled = false,
                zoomPercent = zoomPercent,
                zoomInput = zoomInput,
                onZoomInputChange = onZoomInputChange,
                onZoomChange = onZoomChange,
                onDisplayModeChange = onDisplayModeChange,
            )
            Column(modifier = Modifier.padding(8.dp)) {
                AssistantCodeBlock(
                    language = kind.fenceLanguage,
                    source = source,
                )
            }
        }
    }
}

/** 在浏览器或语法失败时保留原始围栏内容和 Jewel 的代码交互。 */
@Composable
private fun DiagramCodeFallback(
    kind: AssistantDiagramKind,
    source: String,
    message: String,
) {
    Text(
        text = message,
        style = JewelTheme.defaultTextStyle.copy(color = LocalDesktopPalette.current.muted),
    )
    AssistantCodeBlock(language = kind.fenceLanguage, source = source)
}
