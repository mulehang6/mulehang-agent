package com.agent.app.chat.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.LocalDesktopPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
/** 离线图表从 SVG 生成、页面加载到最终回退的可观察状态。 */
internal sealed interface DiagramPreviewState {
    /** PlantUML 正在使用本地引擎生成 SVG。 */
    data object GeneratingSvg : DiagramPreviewState

    /** JCEF 会话已创建或正在创建，仍等待本地页面握手。 */
    data object BrowserLoading : DiagramPreviewState

    /** 本地 HTML 已加载，仍等待 Mermaid 或 SVG 渲染完成。 */
    data object PageLoaded : DiagramPreviewState

    /** 页面通过标题握手确认图表已经可见，并保留页面上报的可选宽高比。 */
    data class Ready(
        val aspectRatio: Float? = null,
    ) : DiagramPreviewState

    /** 任一可恢复失败都会回退为原始代码块。 */
    data class Failed(
        val failure: DiagramPreviewFailure,
    ) : DiagramPreviewState
}

/** 将 JCEF 的异步回调折叠为不会倒退的图表状态。 */
internal fun diagramPreviewStateAfterBrowserStatus(
    current: DiagramPreviewState,
    status: DiagramBrowserStatus,
): DiagramPreviewState = when (status) {
    is DiagramBrowserStatus.Ready -> DiagramPreviewState.Ready(status.aspectRatio)
    DiagramBrowserStatus.PageLoaded -> when (current) {
        is DiagramPreviewState.Ready,
        is DiagramPreviewState.Failed,
        -> current

        else -> DiagramPreviewState.PageLoaded
    }

    is DiagramBrowserStatus.Failed -> DiagramPreviewState.Failed(status.failure)
}

/** 为仍未完成的浏览器页面生成稳定的八秒超时回退。 */
internal fun diagramPreviewTimeout(
    state: DiagramPreviewState,
): DiagramPreviewState.Failed? = when (state) {
    DiagramPreviewState.BrowserLoading,
    DiagramPreviewState.PageLoaded,
    -> DiagramPreviewState.Failed(
        DiagramPreviewFailure(
            kind = DiagramFailureKind.TIMEOUT,
            detail = "8 秒内未收到离线图表页面的完成信号。",
        ),
    )

    else -> null
}

/** 判断当前状态是否已允许用户操作页面内的图表缩放。 */
internal fun isDiagramPreviewReady(state: DiagramPreviewState): Boolean = state is DiagramPreviewState.Ready

/** 为新建或重新打开的图表渲染创建初始异步状态。 */
private fun initialDiagramPreviewState(kind: AssistantDiagramKind): DiagramPreviewState =
    if (kind == AssistantDiagramKind.PLANT_UML) {
        DiagramPreviewState.GeneratingSvg
    } else {
        DiagramPreviewState.BrowserLoading
    }

/**
 * 显示已闭合 Markdown 图表。两种图表最终都交给 Compose 的 SVG 画布绘制。
 */
@Composable
internal fun AssistantDiagramPreview(
    kind: AssistantDiagramKind,
    source: String,
    onDiagramWheel: (Float) -> Unit = {},
) {
    val palette = LocalDesktopPalette.current
    var displayMode by remember(kind, source, palette.isDark) {
        mutableStateOf(DiagramPreviewDisplayMode.RENDERED)
    }
    var session by remember(kind, source, palette.isDark) { mutableStateOf<DiagramBrowserSession?>(null) }
    var browserStatus by remember(kind, source, palette.isDark) { mutableStateOf<DiagramBrowserStatus?>(null) }
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
    val latestDisplayMode by rememberUpdatedState(displayMode)
    val latestPreviewState by rememberUpdatedState(previewState)
    val latestSession by rememberUpdatedState(session)
    val activeSession = session

    LaunchedEffect(kind, source, palette.isDark, displayMode) {
        if (displayMode != DiagramPreviewDisplayMode.RENDERED) {
            session = null
            browserStatus = null
            return@LaunchedEffect
        }
        session = null
        browserStatus = null
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
            previewState = DiagramPreviewState.BrowserLoading
            when (
                val result = withContext(Dispatchers.Swing) {
                    DiagramBrowserRuntime.createSession(
                        kind = kind,
                        source = source,
                        isDark = palette.isDark,
                        onStatus = { status -> browserStatus = status },
                    )
                }
            ) {
                is DiagramBrowserSessionResult.Ready -> {
                    if (
                        latestDisplayMode != DiagramPreviewDisplayMode.RENDERED ||
                        latestPreviewState is DiagramPreviewState.Failed
                    ) {
                        DiagramBrowserRuntime.closeSession(result.session)
                    } else {
                        session = result.session
                    }
                }
                is DiagramBrowserSessionResult.Failed -> previewState = DiagramPreviewState.Failed(result.failure)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val failure = DiagramPreviewFailure(
                kind = if (kind == AssistantDiagramKind.PLANT_UML) {
                    DiagramFailureKind.PLANT_UML_RENDER
                } else {
                    DiagramFailureKind.BROWSER_SESSION
                },
                detail = error.message ?: "离线图表无法生成 SVG。",
                cause = error,
            )
            logDiagramPreviewFailure(failure)
            previewState = DiagramPreviewState.Failed(failure)
        }
    }

    LaunchedEffect(browserStatus, activeSession, displayMode) {
        if (displayMode != DiagramPreviewDisplayMode.RENDERED) return@LaunchedEffect
        val status = browserStatus ?: return@LaunchedEffect
        when (status) {
            DiagramBrowserStatus.PageLoaded -> {
                previewState = diagramPreviewStateAfterBrowserStatus(previewState, status)
            }

            is DiagramBrowserStatus.Failed -> {
                val nextState = diagramPreviewStateAfterBrowserStatus(previewState, status)
                previewState = nextState
                logDiagramPreviewFailure(status.failure)
                session = null
            }

            is DiagramBrowserStatus.Ready -> {
                val browserSession = activeSession ?: return@LaunchedEffect
                if (previewState is DiagramPreviewState.Ready) return@LaunchedEffect
                previewState = DiagramPreviewState.PageLoaded
                val svg = withContext(Dispatchers.Swing) {
                    DiagramBrowserRuntime.awaitRenderedSvg(browserSession.browser)
                }
                withContext(Dispatchers.Swing) {
                    DiagramBrowserRuntime.closeSession(browserSession)
                }
                if (latestSession != browserSession) return@LaunchedEffect
                session = null
                if (svg.isNullOrBlank()) {
                    val failure = DiagramPreviewFailure(
                        kind = DiagramFailureKind.BROWSER_SESSION,
                        detail = "离线图表页面未返回 SVG。",
                    )
                    logDiagramPreviewFailure(failure)
                    previewState = DiagramPreviewState.Failed(failure)
                } else {
                    renderedSvg = svg
                    previewState = DiagramPreviewState.Ready(diagramSvgAspectRatio(svg))
                }
            }
        }
    }

    if (activeSession != null) {
        DisposableEffect(activeSession) {
            onDispose {
                DiagramBrowserRuntime.closeSession(activeSession)
            }
        }
    }

    LaunchedEffect(activeSession, displayMode) {
        if (displayMode != DiagramPreviewDisplayMode.RENDERED) return@LaunchedEffect
        val timeoutSession = activeSession ?: return@LaunchedEffect
        delay(DIAGRAM_PREVIEW_TIMEOUT)
        val timeoutState = diagramPreviewTimeout(latestPreviewState) ?: return@LaunchedEffect
        if (latestSession == timeoutSession) {
            logDiagramPreviewFailure(timeoutState.failure)
            previewState = timeoutState
            session = null
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
            DiagramPreviewState.BrowserLoading,
            DiagramPreviewState.PageLoaded,
            is DiagramPreviewState.Ready,
            -> if (state is DiagramPreviewState.Ready && renderedSvg != null) {
                DiagramSvgSurface(
                    kind = kind,
                    source = source,
                    svg = renderedSvg!!,
                    zoomPercent = zoomPercent,
                    zoomInput = zoomInput,
                    onZoomInputChange = { zoomInput = it },
                    onZoomChange = updateZoom,
                    onDiagramWheel = onDiagramWheel,
                    onDisplayModeChange = { displayMode = it },
                )
            } else {
                DiagramLoading(
                    when (state) {
                        DiagramPreviewState.PageLoaded -> "正在整理离线图表…"
                        else -> "正在加载离线图表预览…"
                    },
                )
            }
        }
    }
}

/** 在浏览器会话与页面渲染完成前显示明确的状态，避免出现无提示空框。 */
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

private val DIAGRAM_PREVIEW_TIMEOUT = 8.seconds
