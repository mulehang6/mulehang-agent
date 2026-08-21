package com.agent.app.chat.component

import java.awt.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler.ErrorCode
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private val DIAGRAM_LOGGER = LoggerFactory.getLogger(DiagramBrowserRuntime::class.java)

/** 离线图表预览可恢复失败的具体类别。 */
internal enum class DiagramFailureKind(
    val label: String,
) {
    RESOURCE_MISSING("本地资源缺失"),
    JCEF_INITIALIZATION("JCEF 初始化"),
    PAGE_LOAD("离线页面加载"),
    MERMAID_SYNTAX("Mermaid 语法"),
    PLANT_UML_RENDER("PlantUML 生成"),
    BROWSER_SESSION("浏览器会话"),
    TIMEOUT("图表加载超时"),
}

/** 包含给用户的稳定类别和只写入日志的诊断细节的预览失败。 */
internal data class DiagramPreviewFailure(
    val kind: DiagramFailureKind,
    val detail: String,
    val cause: Throwable? = null,
) {
    /** 返回不暴露底层路径或浏览器异常的代码回退文案。 */
    fun fallbackMessage(): String = "离线图表预览失败（${kind.label}），已显示为代码。"
}

/** 浏览器页面向 Compose 预览报告的终态及失败类别。 */
internal sealed class DiagramBrowserStatus {
    /** 本地 HTML 主页面已结束加载，仍等待图表脚本报告最终状态。 */
    data object PageLoaded : DiagramBrowserStatus()

    /** 本地页面已完成图表渲染，并可选上报 SVG 的宽高比。 */
    data class Ready(
        val aspectRatio: Float? = null,
    ) : DiagramBrowserStatus()

    /** 本地页面无法加载或渲染时附带明确类别。 */
    data class Failed(
        val failure: DiagramPreviewFailure,
    ) : DiagramBrowserStatus()
}

/** 一个只在后台生成离线 SVG 的图表浏览器会话。 */
internal data class DiagramBrowserSession(
    val browser: CefBrowser,
) {
    /** 仅在兼容 JCEF 嵌入路径显式访问时创建原生组件。 */
    @Suppress("unused")
    val component: Component by lazy { browser.uiComponent }
}

/** 创建独立浏览器实例的成功或可恢复失败结果。 */
internal sealed class DiagramBrowserSessionResult {
    /** 成功创建可用于后台 SVG 生成的浏览器会话。 */
    data class Ready(
        val session: DiagramBrowserSession,
    ) : DiagramBrowserSessionResult()

    /** 创建前或创建中失败后返回代码块回退所需的信息。 */
    data class Failed(
        val failure: DiagramPreviewFailure,
    ) : DiagramBrowserSessionResult()
}

/** 在 JCEF 初始化链路中保留可恢复失败类别的内部异常。 */
private class DiagramRuntimeException(
    val failure: DiagramPreviewFailure,
) : IllegalStateException(failure.detail, failure.cause)

/** 记录图表回退原因，便于区分资源、浏览器和语法问题。 */
internal fun logDiagramPreviewFailure(failure: DiagramPreviewFailure) {
    val message = "离线图表预览失败 [${failure.kind.name}]：${failure.detail}"
    if (failure.cause == null) {
        DIAGRAM_LOGGER.warn(message)
    } else {
        DIAGRAM_LOGGER.warn(message, failure.cause)
    }
}

/**
 * 复用一个 JCEF 客户端承载全部图表，同时只允许它访问安装包或开发 classpath 解出的本地图表资源。
 */
internal object DiagramBrowserRuntime {
    private val lock = Any()
    // JCEF 的数值 browser identifier 在 createImmediately 前后并不稳定，必须直接以浏览器对象绑定回调。
    private val statusCallbacks = ConcurrentHashMap<CefBrowser, (DiagramBrowserStatus) -> Unit>()
    private val wheelCallbacks = ConcurrentHashMap<CefBrowser, (DiagramBrowserWheelInput) -> Unit>()
    private val wheelInputBatchers = ConcurrentHashMap<CefBrowser, DiagramBrowserWheelBatcher>()
    private val renderedSvgCallbacks = ConcurrentHashMap<CefBrowser, (String?) -> Unit>()
    private val renderedSvgCache = ConcurrentHashMap<CefBrowser, String>()
    private val resourceDirectory by lazy {
        DiagramBrowserResourcePolicy.resolveDiagramResourceDirectory()
            ?: throw DiagramRuntimeException(
                DiagramPreviewFailure(
                    kind = DiagramFailureKind.RESOURCE_MISSING,
                    detail = "安装包资源与开发 classpath 中均未找到完整的 Mermaid 图表资源。",
                ),
            )
    }
    private val wheelInputRouter by lazy {
        CefMessageRouter.create(
            CefMessageRouter.CefMessageRouterConfig(
                DIAGRAM_WHEEL_QUERY_FUNCTION,
                DIAGRAM_WHEEL_QUERY_CANCEL_FUNCTION,
            ),
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser,
                    frame: CefFrame,
                    queryId: Long,
                    request: String,
                    persistent: Boolean,
                    callback: CefQueryCallback,
                ): Boolean {
                    if (!frame.isMain || persistent) return false
                    val input = parseDiagramBrowserWheelInput(request) ?: return false
                    publishWheelInput(browser, input)
                    callback.success("")
                    return true
                }
            },
        )
    }
    private val renderedSvgRouter by lazy {
        CefMessageRouter.create(
            CefMessageRouter.CefMessageRouterConfig(
                DIAGRAM_SVG_QUERY_FUNCTION,
                DIAGRAM_SVG_QUERY_CANCEL_FUNCTION,
            ),
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser,
                    frame: CefFrame,
                    queryId: Long,
                    request: String,
                    persistent: Boolean,
                    callback: CefQueryCallback,
                ): Boolean {
                    if (!frame.isMain || persistent) return false
                    val svg = request.takeIf(String::isNotBlank)
                    renderedSvgCallbacks.remove(browser)?.invoke(svg)
                        ?: svg?.let { renderedSvgCache[browser] = it }
                    callback.success("")
                    return true
                }
            },
        )
    }

    private var sharedClient: CefClient? = null
    private var initializationFailure: DiagramPreviewFailure? = null

    /** 创建一个只加载本地图表文档的浏览器会话。 */
    fun createSession(
        kind: AssistantDiagramKind,
        source: String,
        isDark: Boolean,
        onStatus: (DiagramBrowserStatus) -> Unit,
    ): DiagramBrowserSessionResult = try {
        val documentUrl = diagramDocumentUrl(kind, source, isDark)
        val browser = getSharedClient().createBrowser(
            documentUrl,
            CefRendering.DEFAULT,
            false,
        )
        statusCallbacks[browser] = onStatus
        try {
            browser.createImmediately()
            DiagramBrowserSessionResult.Ready(
                DiagramBrowserSession(browser = browser),
            )
        } catch (error: Throwable) {
            statusCallbacks.remove(browser)
            runCatching { browser.close(true) }
            throw error
        }
    } catch (error: DiagramRuntimeException) {
        logDiagramPreviewFailure(error.failure)
        DiagramBrowserSessionResult.Failed(error.failure)
    } catch (error: Throwable) {
        val failure = DiagramPreviewFailure(
            kind = DiagramFailureKind.BROWSER_SESSION,
            detail = error.message ?: "无法创建离线图表浏览器实例。",
            cause = error,
        )
        logDiagramPreviewFailure(failure)
        DiagramBrowserSessionResult.Failed(failure)
    }

    /** 关闭被 Compose 移除的图表浏览器，保留共享客户端给其他图表使用。 */
    fun closeSession(session: DiagramBrowserSession) {
        statusCallbacks.remove(session.browser)
        wheelCallbacks.remove(session.browser)
        wheelInputBatchers.remove(session.browser)
        renderedSvgCallbacks.remove(session.browser)
        renderedSvgCache.remove(session.browser)
        if (!session.browser.isClosed) {
            session.browser.close(true)
        }
    }

    /** 等待离线页面把已生成的 SVG 发送回 JVM，并在等待期间保持浏览器会话不变。 */
    internal suspend fun awaitRenderedSvg(browser: CefBrowser): String? = suspendCancellableCoroutine { continuation ->
        if (browser.isClosed) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val callback: (String?) -> Unit = { svg ->
            if (continuation.isActive) continuation.resume(svg)
        }
        renderedSvgCallbacks[browser] = callback
        val cachedSvg = renderedSvgCache.remove(browser)
        if (cachedSvg != null) {
            renderedSvgCallbacks.remove(browser, callback)
            continuation.resume(cachedSvg)
            return@suspendCancellableCoroutine
        }
        runCatching {
            browser.executeJavaScript(
                "window.mulehangDiagram?.requestSvg?.();",
                "",
                0,
            )
        }.onFailure {
            renderedSvgCallbacks.remove(browser, callback)
            if (continuation.isActive) continuation.resume(null)
        }
        continuation.invokeOnCancellation {
            renderedSvgCallbacks.remove(browser, callback)
        }
    }

    /** 注册当前图表页面经 MessageRouter 上报的滚轮输入回调。 */
    internal fun registerWheelCallback(
        browser: CefBrowser,
        callback: (DiagramBrowserWheelInput) -> Unit,
    ) {
        wheelInputBatchers.remove(browser)
        wheelCallbacks[browser] = callback
    }

    /** 仅在回调仍属于当前 Compose 图表时移除它，避免清除新会话绑定。 */
    internal fun unregisterWheelCallback(
        browser: CefBrowser,
        callback: (DiagramBrowserWheelInput) -> Unit,
    ) {
        if (wheelCallbacks.remove(browser, callback)) {
            wheelInputBatchers.remove(browser)
        }
    }

    /** 初始化一次 JCEF 和共享客户端，并为其安装本地资源访问策略。 */
    private fun getSharedClient(): CefClient = synchronized(lock) {
        sharedClient ?: initializationFailure?.let { failure ->
            throw DiagramRuntimeException(failure)
        } ?: try {
            ensureJcefStartup()
            CefApp.getInstance(JCEF_ARGUMENTS, jcefSettings(), null)
                .createClient()
                .also(::configureClient)
                .also { client -> sharedClient = client }
        } catch (error: Throwable) {
            val failure = DiagramPreviewFailure(
                kind = DiagramFailureKind.JCEF_INITIALIZATION,
                detail = error.message ?: "JCEF 无法初始化。",
                cause = error,
            )
            initializationFailure = failure
            throw DiagramRuntimeException(failure)
        }
    }

    /**
     * 启动 JCEF 原生运行时，再创建 [CefApp]。
     *
     * JBR 的 JCEF 实现会把 [CefApp] 的初始化挂在 `startup` 完成信号之后；仅调用
     * `getInstance` 会令浏览器对象永远停留在尚未创建原生实例的状态。
     */
    private fun ensureJcefStartup() {
        check(CefApp.startup(JCEF_ARGUMENTS)) { "JCEF 原生运行时启动失败。" }
    }

    /** 为 Windows JBR 的 JCEF helper 配置 Chromium 子进程路径。 */
    private fun jcefSettings(): CefSettings = CefSettings().apply {
        browser_subprocess_path = resolveJcefHelperPath().toString()
    }

    /**
     * 返回随当前 JBR 打包的 JCEF helper。
     *
     * 未设置此路径时，JCEF 会把 Chromium 的 `--type=gpu-process` 等参数交给主 Java
     * 进程，导致子进程在启动前失败。
     */
    private fun resolveJcefHelperPath(): Path {
        return locateJcefHelperPath(Path.of(System.getProperty("java.home")))
            ?: error("当前 JBR 中未找到 JCEF 子进程 helper。")
    }

    /** 按 JBR 标准目录布局查找指定运行时随附的 Windows JCEF helper。 */
    internal fun locateJcefHelperPath(runtimeHome: Path): Path? {
        val helperPath = runtimeHome
            .toAbsolutePath()
            .normalize()
            .resolve("bin")
            .resolve(JCEF_HELPER_FILE)
        return helperPath.takeIf { candidate -> Files.isRegularFile(candidate) }
    }

    /** 让共享客户端报告页面状态、释放已关闭浏览器，并阻断一切外部请求。 */
    private fun configureClient(client: CefClient) {
        client.addMessageRouter(wheelInputRouter)
        client.addMessageRouter(renderedSvgRouter)
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(browser: CefBrowser, title: String) {
                when {
                    title == DIAGRAM_READY_TITLE -> publishStatus(browser, DiagramBrowserStatus.Ready())
                    title.startsWith(DIAGRAM_READY_TITLE_WITH_ASPECT_RATIO_PREFIX) -> {
                        publishStatus(
                            browser,
                            DiagramBrowserStatus.Ready(diagramReadyAspectRatio(title)),
                        )
                    }
                    title.startsWith(DIAGRAM_ERROR_TITLE_PREFIX) -> {
                        publishStatus(browser, DiagramBrowserStatus.Failed(diagramPageFailure(title)))
                    }
                }
            }

            override fun onConsoleMessage(
                browser: CefBrowser,
                level: CefSettings.LogSeverity,
                message: String,
                source: String,
                line: Int,
            ): Boolean {
                if (message.startsWith(DIAGRAM_CONSOLE_ERROR_PREFIX)) {
                    publishStatus(browser, DiagramBrowserStatus.Failed(diagramPageFailure(message)))
                }
                return false
            }
        })
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(
                browser: CefBrowser,
                frame: CefFrame,
                httpStatusCode: Int,
            ) {
                if (frame.isMain) {
                    publishStatus(browser, DiagramBrowserStatus.PageLoaded)
                }
            }

            override fun onLoadError(
                browser: CefBrowser,
                frame: CefFrame,
                errorCode: ErrorCode,
                errorText: String,
                failedUrl: String,
            ) {
                if (frame.isMain) {
                    publishStatus(
                        browser,
                        DiagramBrowserStatus.Failed(
                            DiagramPreviewFailure(
                                kind = DiagramFailureKind.PAGE_LOAD,
                                detail = "页面加载失败：$errorCode，$errorText（$failedUrl）",
                            ),
                        ),
                    )
                }
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforeClose(browser: CefBrowser) {
                statusCallbacks.remove(browser)
                wheelCallbacks.remove(browser)
                wheelInputBatchers.remove(browser)
                renderedSvgCallbacks.remove(browser)
                renderedSvgCache.remove(browser)
            }
        })
        client.addRequestHandler(DiagramBrowserResourcePolicy.localOnlyRequestHandler)
    }

    /** 在 Swing 事件线程通知当前浏览器对应的 Compose 状态。 */
    private fun publishStatus(
        browser: CefBrowser,
        status: DiagramBrowserStatus,
    ) {
        SwingUtilities.invokeLater {
            statusCallbacks[browser]?.invoke(status)
        }
    }

    /** 将浏览器滚轮输入合并后切回 Swing 线程，避免消息积压反复驱动 Compose 时间线。 */
    private fun publishWheelInput(
        browser: CefBrowser,
        input: DiagramBrowserWheelInput,
    ) {
        val batcher = wheelInputBatchers.computeIfAbsent(browser) { DiagramBrowserWheelBatcher() }
        if (!batcher.enqueue(input)) return
        SwingUtilities.invokeLater {
            val pendingInputs = batcher.drain()
            val callback = wheelCallbacks[browser] ?: return@invokeLater
            pendingInputs.forEach(callback)
        }
    }

    /** 生成带安全 Base64URL 片段的本地 HTML 文档地址。 */
    private fun diagramDocumentUrl(
        kind: AssistantDiagramKind,
        source: String,
        isDark: Boolean,
    ): String {
        val encodedSource = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(source.toByteArray(StandardCharsets.UTF_8))
        val theme = if (isDark) "dark" else "light"
        val themePolicy = if (kind == AssistantDiagramKind.MERMAID && shouldApplyMermaidAutomaticTheme(source)) {
            "auto"
        } else {
            "source"
        }
        return resourceDirectory.resolve(DiagramBrowserResourcePolicy.DIAGRAM_HTML_FILE).toUri().toString() +
            "?kind=${kind.browserKind}&theme=$theme&themePolicy=$themePolicy#$encodedSource"
    }

    /** 保留资源策略的稳定测试入口，实际实现位于独立策略文件。 */
    internal fun locateDiagramResourceDirectory(
        packageResourcesDirectory: Path?,
        classpathDiagramPage: java.net.URL?,
    ): Path? = DiagramBrowserResourcePolicy.locateDiagramResourceDirectory(
        packageResourcesDirectory,
        classpathDiagramPage,
    )

    /** 保留 Mermaid 资源路径的稳定测试入口。 */
    internal fun diagramMermaidEntryRelativePath(): Path =
        DiagramBrowserResourcePolicy.diagramMermaidEntryRelativePath()

    private val AssistantDiagramKind.browserKind: String
        get() = when (this) {
            AssistantDiagramKind.PLANT_UML -> "plantuml"
            AssistantDiagramKind.MERMAID -> "mermaid"
        }

    private const val DIAGRAM_READY_TITLE = "mulehang-diagram-ready"
    private const val DIAGRAM_READY_TITLE_WITH_ASPECT_RATIO_PREFIX = "$DIAGRAM_READY_TITLE:"
    private const val DIAGRAM_ERROR_TITLE_PREFIX = "mulehang-diagram-error:"
    private const val DIAGRAM_CONSOLE_ERROR_PREFIX = "mulehang-diagram-error:"
    private const val DIAGRAM_WHEEL_QUERY_FUNCTION = "mulehangDiagramWheelQuery"
    private const val DIAGRAM_WHEEL_QUERY_CANCEL_FUNCTION = "mulehangDiagramWheelQueryCancel"
    private const val DIAGRAM_SVG_QUERY_FUNCTION = "mulehangDiagramSvgQuery"
    private const val DIAGRAM_SVG_QUERY_CANCEL_FUNCTION = "mulehangDiagramSvgQueryCancel"
    private const val JCEF_HELPER_FILE = "jcef_helper.exe"
    private val JCEF_ARGUMENTS = arrayOf(
        "--disable-background-networking",
        "--disable-component-update",
        "--disable-default-apps",
        "--disable-domain-reliability",
        "--disable-sync",
        "--metrics-recording-only",
        "--no-first-run",
        "--no-pings",
    )
}
