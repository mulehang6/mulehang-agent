package com.agent.app.chat.component

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler.ErrorCode
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import kotlin.time.Duration.Companion.milliseconds

/** 后台 Mermaid 渲染的成功 SVG 或可以回退为源码的失败。 */
internal sealed interface DiagramRenderResult {
    /** 官方 Mermaid 已在离线 JCEF 中生成并固化样式的矢量 SVG。 */
    data class Success(
        val svg: String,
    ) : DiagramRenderResult

    /** 渲染器、页面或 Mermaid 自身报告的可恢复失败。 */
    data class Failure(
        val failure: DiagramPreviewFailure,
    ) : DiagramRenderResult
}

/** 唯一的 Mermaid 渲染入口；首次调用才创建并复用不可见的离线 JCEF 工作器。 */
internal suspend fun renderMermaidToSvg(
    source: String,
    isDark: Boolean,
): DiagramRenderResult = MermaidSvgRenderer.render(source, isDark)

/**
 * 以单个不可见 JCEF 浏览器串行运行官方 Mermaid 页面。
 *
 * 调用方取消只会停止等待，不会取消进程级队列中的工作器任务，避免切换会话后留下半初始化浏览器。
 */
private object MermaidSvgRenderer {
    private val renderMutex = Mutex()
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleLock = Any()
    private val requestIds = AtomicLong()
    private val workerGenerations = AtomicLong()
    private val resourceDirectory by lazy {
        DiagramBrowserResourcePolicy.resolveDiagramResourceDirectory()
            ?: throw MermaidRendererException(
                DiagramPreviewFailure(
                    kind = DiagramFailureKind.RESOURCE_MISSING,
                    detail = "安装包资源与开发 classpath 中均未找到完整的 Mermaid 图表资源。",
                ),
            )
    }
    private val responseRouter by lazy {
        CefMessageRouter.create(
            CefMessageRouter.CefMessageRouterConfig(
                MERMAID_RENDER_QUERY_FUNCTION,
                MERMAID_RENDER_QUERY_CANCEL_FUNCTION,
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
                    parseMermaidWorkerMessage(request)?.let { message ->
                        publishWorkerMessage(browser, message)
                    }
                    callback.success("")
                    return true
                }
            },
        )
    }

    private var sharedClient: CefClient? = null
    private var initializationFailure: DiagramPreviewFailure? = null
    private var workerBrowser: CefBrowser? = null
    private var workerReady: CompletableDeferred<MermaidWorkerSessionState>? = null
    private var readySession: MermaidWorkerSessionState? = null
    private var activeRequest: ActiveMermaidWorkerRequest? = null
    private val requestGate = MermaidWorkerRequestGate()

    /** 将调用请求交给应用生命周期内的串行任务，避免 UI 协程取消工作器初始化。 */
    suspend fun render(
        source: String,
        isDark: Boolean,
    ): DiagramRenderResult {
        val completion = CompletableDeferred<DiagramRenderResult>()
        renderScope.launch {
            completion.complete(
                runCatching {
                    renderMutex.withLock { renderQueued(source, isDark) }
                }.getOrElse { error ->
                    DiagramRenderResult.Failure(
                        DiagramPreviewFailure(
                            kind = DiagramFailureKind.BROWSER_SESSION,
                            detail = error.message ?: "无法运行 Mermaid 后台工作器。",
                            cause = error,
                        ),
                    )
                },
            )
        }
        return completion.await()
    }

    /** 以冷启动或已就绪工作器对应的总时限执行一次可自动恢复的渲染。 */
    private suspend fun renderQueued(
        source: String,
        isDark: Boolean,
    ): DiagramRenderResult {
        val isColdStart = !hasReadyWorker()
        val timeoutMillis = if (isColdStart) COLD_RENDER_TIMEOUT_MILLIS else WARM_RENDER_TIMEOUT_MILLIS
        return try {
            withTimeout(timeoutMillis.milliseconds) {
                renderWithSingleRecovery(source, isDark)
            }
        } catch (error: TimeoutCancellationException) {
            discardWorker()
            DiagramRenderResult.Failure(
                DiagramPreviewFailure(
                    kind = DiagramFailureKind.TIMEOUT,
                    detail = if (isColdStart) {
                        "${COLD_RENDER_TIMEOUT_MILLIS / 1_000} 秒内未完成 Mermaid 工作器启动和 SVG 渲染。"
                    } else {
                        "${WARM_RENDER_TIMEOUT_MILLIS / 1_000} 秒内未收到 Mermaid 工作器的 SVG。"
                    },
                    cause = error,
                ),
            )
        }
    }

    /** 工作器失败时只重建一次，并让统一超时预算限制总等待时间。 */
    private suspend fun renderWithSingleRecovery(
        source: String,
        isDark: Boolean,
    ): DiagramRenderResult {
        var hasRebuiltWorker = false
        while (true) {
            val session = try {
                awaitWorkerReady()
            } catch (error: MermaidRendererException) {
                if (hasRebuiltWorker) return DiagramRenderResult.Failure(error.failure)
                hasRebuiltWorker = true
                discardWorker()
                continue
            }
            val result = renderInReadyWorker(session, source, isDark)
            if (result is DiagramRenderResult.Failure && result.failure.isMermaidWorkerFailure() && !hasRebuiltWorker) {
                hasRebuiltWorker = true
                discardWorker()
                continue
            }
            return result
        }
    }

    /** 等待固定工作页通过 JS 查询桥接回报就绪。 */
    private suspend fun awaitWorkerReady(): MermaidWorkerSessionState {
        val ready = withContext(Dispatchers.Swing) { getOrCreateWorkerReady() }
        return try {
            ready.await()
        } catch (error: MermaidRendererException) {
            throw error
        } catch (error: TimeoutCancellationException) {
            throw error
        } catch (error: Throwable) {
            throw MermaidRendererException(
                DiagramPreviewFailure(
                    kind = DiagramFailureKind.BROWSER_SESSION,
                    detail = error.message ?: "Mermaid 工作器未能进入就绪状态。",
                    cause = error,
                ),
            )
        }
    }

    /** 已就绪浏览器直接复用；否则创建浏览器，等待生命周期回调加载固定页面。 */
    private fun getOrCreateWorkerReady(): CompletableDeferred<MermaidWorkerSessionState> = synchronized(lifecycleLock) {
        readySession?.let { session -> return CompletableDeferred(session) }
        workerReady?.let { ready -> return ready }
        val generation = workerGenerations.incrementAndGet()
        val ready = CompletableDeferred<MermaidWorkerSessionState>()
        val browser = getSharedClient().createBrowser("about:blank", CefRendering.DEFAULT, false)
        workerBrowser = browser
        workerReady = ready
        logWorkerPhase(generation, null, "创建浏览器")
        browser.createImmediately()
        ready
    }

    /** 判断浏览器是否已完成页面和桥接的双重就绪。 */
    private fun hasReadyWorker(): Boolean = synchronized(lifecycleLock) {
        readySession?.browser?.isClosed == false
    }

    /** 向已就绪页面派发独立源码，并在后台完成 SVG 文字轮廓化。 */
    private suspend fun renderInReadyWorker(
        session: MermaidWorkerSessionState,
        source: String,
        isDark: Boolean,
    ): DiagramRenderResult {
        val requestId = requestIds.incrementAndGet()
        val response = CompletableDeferred<DiagramRenderResult>()
        return try {
            withContext(Dispatchers.Swing) {
                beginRequest(session, requestId, source, isDark, response)
            }
            prepareReturnedSvg(response.await())
        } catch (error: TimeoutCancellationException) {
            throw error
        } catch (error: MermaidRendererException) {
            DiagramRenderResult.Failure(error.failure)
        } catch (error: Throwable) {
            DiagramRenderResult.Failure(
                DiagramPreviewFailure(
                    kind = DiagramFailureKind.BROWSER_SESSION,
                    detail = error.message ?: "Mermaid 工作器无法派发渲染请求。",
                    cause = error,
                ),
            )
        } finally {
            clearActiveRequest(requestId)
        }
    }

    /** 使用 Base64URL 把源码安全地作为 JavaScript 对象字段传入固定页面。 */
    private fun beginRequest(
        session: MermaidWorkerSessionState,
        requestId: Long,
        source: String,
        isDark: Boolean,
        response: CompletableDeferred<DiagramRenderResult>,
    ) {
        synchronized(lifecycleLock) {
            if (readySession != session || workerBrowser !== session.browser || session.browser.isClosed) {
                throw MermaidRendererException(
                    DiagramPreviewFailure(
                        kind = DiagramFailureKind.BROWSER_SESSION,
                        detail = "Mermaid 工作器在请求派发前已失效。",
                    ),
                )
            }
            requestGate.activate(requestId)
            activeRequest = ActiveMermaidWorkerRequest(session.generation, requestId, response)
        }
        logWorkerPhase(session.generation, requestId, "派发渲染请求")
        session.browser.executeJavaScript(
            mermaidRenderScript(requestId, source, isDark),
            session.browser.url,
            0,
        )
    }

    /** 把成功 SVG 转成路径，保证 Skia SVGDOM 能画出所有标签。 */
    private suspend fun prepareReturnedSvg(result: DiagramRenderResult): DiagramRenderResult = when (result) {
        is DiagramRenderResult.Failure -> result
        is DiagramRenderResult.Success -> withContext(Dispatchers.Default) {
            runCatching { outlineDiagramSvgText(result.svg) }
                .fold(
                    onSuccess = DiagramRenderResult::Success,
                    onFailure = { error ->
                        DiagramRenderResult.Failure(
                            DiagramPreviewFailure(
                                kind = DiagramFailureKind.SVG_TEXT_OUTLINE,
                                detail = error.message ?: "Mermaid SVG 标签无法转换为矢量路径。",
                                cause = error,
                            ),
                        )
                    },
                )
        }
    }

    /** JCEF 客户端只在首张 Mermaid 图表时创建，并持续复用到应用退出。 */
    private fun getSharedClient(): CefClient = synchronized(lifecycleLock) {
        sharedClient ?: initializationFailure?.let { failure ->
            throw MermaidRendererException(failure)
        } ?: try {
            ensureJcefStartup()
            CefApp.getInstance(MERMAID_JCEF_ARGUMENTS, jcefSettings(), null)
                .createClient()
                .also(::configureClient)
                .also { client -> sharedClient = client }
        } catch (error: MermaidRendererException) {
            throw error
        } catch (error: Throwable) {
            val failure = DiagramPreviewFailure(
                kind = DiagramFailureKind.JCEF_INITIALIZATION,
                detail = error.message ?: "JCEF 无法初始化。",
                cause = error,
            )
            initializationFailure = failure
            throw MermaidRendererException(failure)
        }
    }

    /** 启动 JBR 中的 JCEF 原生运行时。 */
    private fun ensureJcefStartup() {
        check(CefApp.startup(MERMAID_JCEF_ARGUMENTS)) { "JCEF 原生运行时启动失败。" }
    }

    /** Windows 使用发布到运行时目录的 helper；其他平台沿用 JCEF 自身的默认路径解析。 */
    private fun jcefSettings(): CefSettings = CefSettings().apply {
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            browser_subprocess_path = resolveJcefHelperPath().toString()
        }
    }

    /** 在当前 JBR 的标准位置查找 JCEF helper，缺失时保持可恢复失败。 */
    private fun resolveJcefHelperPath(): Path = locateMermaidJcefHelperPath(Path.of(System.getProperty("java.home")))
        ?: error("当前 JBR 中未找到 JCEF 子进程 helper。")

    /** 为共享客户端安装本地资源限制、工作器回包通道和浏览器生命周期保护。 */
    private fun configureClient(client: CefClient) {
        client.addMessageRouter(responseRouter)
        client.addRequestHandler(DiagramBrowserResourcePolicy.localOnlyRequestHandler)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    currentWorkerGeneration(browser)?.let { generation ->
                        logWorkerPhase(generation, null, "固定工作页加载完成（$httpStatusCode）")
                    }
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
                    completeBrowserFailure(
                        browser,
                        DiagramPreviewFailure(
                            kind = DiagramFailureKind.PAGE_LOAD,
                            detail = "Mermaid 工作器页面加载失败：$errorCode，$errorText（$failedUrl）。",
                        ),
                    )
                }
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser) {
                currentWorkerGeneration(browser)?.let { generation ->
                    logWorkerPhase(generation, null, "浏览器已创建，加载固定工作页")
                    browser.loadURL(mermaidWorkerDocumentUrl(resourceDirectory, generation))
                }
            }

            override fun onBeforeClose(browser: CefBrowser) {
                completeBrowserFailure(
                    browser,
                    DiagramPreviewFailure(
                        kind = DiagramFailureKind.BROWSER_SESSION,
                        detail = "Mermaid 后台工作器意外关闭。",
                    ),
                )
                synchronized(lifecycleLock) {
                    if (workerBrowser === browser) workerBrowser = null
                }
            }
        })
    }

    /** 接收页面就绪或指定请求的 SVG 回包，并拒绝旧代次消息。 */
    private fun publishWorkerMessage(browser: CefBrowser, message: MermaidWorkerMessage) {
        when (message) {
            is MermaidWorkerMessage.Ready -> completeWorkerReady(browser, message.generation)
            is MermaidWorkerMessage.Response -> publishWorkerResponse(browser, message)
        }
    }

    /** 将浏览器页面的 ready 消息转换为可派发请求的会话。 */
    private fun completeWorkerReady(browser: CefBrowser, generation: Long) {
        val ready = synchronized(lifecycleLock) {
            if (workerBrowser !== browser || currentWorkerGeneration(browser) != generation || readySession != null) {
                null
            } else {
                MermaidWorkerSessionState(browser, generation).also { session ->
                    readySession = session
                    workerReady?.complete(session)
                }
            }
        }
        if (ready != null) logWorkerPhase(generation, null, "JS 查询桥接已就绪")
    }

    /** 只接受当前代次和当前请求 ID 的渲染回包。 */
    private fun publishWorkerResponse(browser: CefBrowser, message: MermaidWorkerMessage.Response) {
        val deferred = synchronized(lifecycleLock) {
            val active = activeRequest
            if (
                workerBrowser !== browser ||
                active?.generation != message.generation ||
                active.requestId != message.requestId ||
                !requestGate.accepts(message.requestId)
            ) {
                null
            } else {
                activeRequest = null
                requestGate.clear(message.requestId)
                active.response
            }
        }
        deferred?.complete(message.result)
        if (deferred != null) logWorkerPhase(message.generation, message.requestId, "收到 SVG 回包")
    }

    /** 让当前就绪等待和活动请求同时收到浏览器级失败。 */
    private fun completeBrowserFailure(browser: CefBrowser, failure: DiagramPreviewFailure) {
        val completion = synchronized(lifecycleLock) {
            if (workerBrowser !== browser) {
                null
            } else {
                val ready = workerReady.also { workerReady = null }
                readySession = null
                val active = activeRequest?.also {
                    requestGate.clear(it.requestId)
                    activeRequest = null
                }
                MermaidWorkerFailureCompletion(ready, active)
            }
        } ?: return
        completion.ready?.completeExceptionally(MermaidRendererException(failure))
        completion.active?.response?.complete(DiagramRenderResult.Failure(failure))
    }

    /** 清除已完成、超时或取消请求的活动槽位。 */
    private fun clearActiveRequest(requestId: Long) {
        synchronized(lifecycleLock) {
            if (activeRequest?.requestId == requestId) {
                requestGate.clear(requestId)
                activeRequest = null
            }
        }
    }

    /** 关闭当前浏览器但保留 JCEF 客户端，以便在同一进程中重建一次工作器。 */
    private suspend fun discardWorker() {
        withContext(Dispatchers.Swing) {
            val browser = synchronized(lifecycleLock) {
                val discardedReady = workerReady.also { workerReady = null }
                readySession = null
                requestGate.clearAll()
                activeRequest = null
                workerBrowser.also { workerBrowser = null }.also {
                    discardedReady?.completeExceptionally(
                        MermaidRendererException(
                            DiagramPreviewFailure(
                                kind = DiagramFailureKind.BROWSER_SESSION,
                                detail = "Mermaid 工作器正在重建。",
                            ),
                        ),
                    )
                }
            }
            if (browser != null && !browser.isClosed) browser.close(true)
        }
    }

    /** 返回指定浏览器当前所属的工作器代次。 */
    private fun currentWorkerGeneration(browser: CefBrowser): Long? = synchronized(lifecycleLock) {
        readySession?.takeIf { it.browser === browser }?.generation
            ?: workerReady?.takeIf { workerBrowser === browser }?.let { workerGenerations.get() }
    }

    /** 写入可按工作器代次和请求 ID 关联的阶段诊断日志。 */
    private fun logWorkerPhase(generation: Long, requestId: Long?, phase: String) {
        MERMAID_WORKER_LOGGER.debug("Mermaid 工作器 [generation={}, request={}] {}", generation, requestId, phase)
    }

    /** 让初始化错误保持既有的可恢复失败类型。 */
    private class MermaidRendererException(
        val failure: DiagramPreviewFailure,
    ) : IllegalStateException(failure.detail, failure.cause)

    private const val COLD_RENDER_TIMEOUT_MILLIS = 20_000L
    private const val WARM_RENDER_TIMEOUT_MILLIS = 8_000L
}
