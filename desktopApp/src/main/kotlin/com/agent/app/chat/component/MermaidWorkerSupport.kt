package com.agent.app.chat.component

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import org.cef.browser.CefBrowser
import org.slf4j.LoggerFactory

/** 已完成固定页面与 JS 桥接握手的 Mermaid 浏览器会话。 */
internal data class MermaidWorkerSessionState(
    val browser: CefBrowser,
    val generation: Long,
)

/** 当前等待固定工作页回包的渲染请求。 */
internal data class ActiveMermaidWorkerRequest(
    val generation: Long,
    val requestId: Long,
    val response: CompletableDeferred<DiagramRenderResult>,
)

/** 浏览器失败时需要同步结束的就绪等待和活动请求。 */
internal data class MermaidWorkerFailureCompletion(
    val ready: CompletableDeferred<MermaidWorkerSessionState>?,
    val active: ActiveMermaidWorkerRequest?,
)

/** 只有浏览器、页面或等待超时才触发一次工作器重建。 */
internal fun DiagramPreviewFailure.isMermaidWorkerFailure(): Boolean = kind in setOf(
    DiagramFailureKind.JCEF_INITIALIZATION,
    DiagramFailureKind.PAGE_LOAD,
    DiagramFailureKind.BROWSER_SESSION,
    DiagramFailureKind.TIMEOUT,
)

/** 按 JBR 标准目录布局查找指定运行时随附的 Windows JCEF helper。 */
internal fun locateMermaidJcefHelperPath(runtimeHome: Path): Path? {
    val helperPath = runtimeHome
        .toAbsolutePath()
        .normalize()
        .resolve("bin")
        .resolve("jcef_helper.exe")
    return helperPath.takeIf(Files::isRegularFile)
}

/** Mermaid 后台工作器的最小 Chromium 启动参数。 */
internal val MERMAID_JCEF_ARGUMENTS = arrayOf(
    "--disable-background-networking",
    "--disable-component-update",
    "--disable-default-apps",
    "--disable-domain-reliability",
    "--disable-sync",
    "--metrics-recording-only",
    "--no-first-run",
    "--no-pings",
)

/** Mermaid 工作器阶段日志分类器。 */
internal val MERMAID_WORKER_LOGGER = LoggerFactory.getLogger("com.agent.app.chat.component.MermaidSvgRenderer")
