package com.agent.app.chat.component

import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest

/** 为后台图表浏览器解析本地页面，并阻止所有外部资源请求。 */
internal object DiagramBrowserResourcePolicy {
    internal const val COMPOSE_APP_RESOURCES_DIRECTORY_PROPERTY = "compose.application.resources.dir"
    internal const val DIAGRAM_RESOURCE_DIRECTORY = "diagram"
    internal const val MERMAID_WORKER_FILE = "mermaid-worker.html"

    /** 本地图表页面可以使用的请求拦截器。 */
    val localOnlyRequestHandler = object : CefRequestHandlerAdapter() {
        override fun onBeforeBrowse(
            browser: CefBrowser,
            frame: CefFrame,
            request: CefRequest,
            userGesture: Boolean,
            isRedirect: Boolean,
        ): Boolean = !isAllowedResourceUrl(request.url)

        override fun onOpenURLFromTab(
            browser: CefBrowser,
            frame: CefFrame,
            targetUrl: String,
            userGesture: Boolean,
        ): Boolean = true

        override fun getResourceRequestHandler(
            browser: CefBrowser,
            frame: CefFrame,
            request: CefRequest,
            isNavigation: Boolean,
            isDownload: Boolean,
            requestInitiator: String,
            disableDefaultHandling: BoolRef,
        ): CefResourceRequestHandler = localOnlyResourceRequestHandler
    }

    /** 在资源加载阶段再次阻止非文件协议。 */
    private val localOnlyResourceRequestHandler = object : CefResourceRequestHandlerAdapter() {
        override fun onBeforeResourceLoad(
            browser: CefBrowser,
            frame: CefFrame,
            request: CefRequest,
        ): Boolean = !isAllowedResourceUrl(request.url)
    }

    /** 先查找安装包资源，再回退到开发 classpath 的本地目录。 */
    fun resolveDiagramResourceDirectory(): Path? {
        val packageResourcesDirectory = System.getProperty(COMPOSE_APP_RESOURCES_DIRECTORY_PROPERTY)
            ?.let { value -> runCatching { Path.of(value) }.getOrNull() }
        val classpathDiagramPage = DiagramBrowserResourcePolicy::class.java.classLoader
            .getResource("$DIAGRAM_RESOURCE_DIRECTORY/$MERMAID_WORKER_FILE")
        return locateDiagramResourceDirectory(packageResourcesDirectory, classpathDiagramPage)
    }

    /** 判断给定目录是否包含完整的页面和 Mermaid 运行时。 */
    fun locateDiagramResourceDirectory(
        packageResourcesDirectory: Path?,
        classpathDiagramPage: URL?,
    ): Path? {
        packageResourcesDirectory
            ?.toAbsolutePath()
            ?.normalize()
            ?.resolve(DIAGRAM_RESOURCE_DIRECTORY)
            ?.takeIf(::hasCompleteDiagramResources)
            ?.let { return it }

        val developmentDirectory = classpathDiagramPage
            ?.takeIf { pageUrl -> pageUrl.protocol.equals("file", ignoreCase = true) }
            ?.let { pageUrl -> runCatching { Path.of(pageUrl.toURI()).parent }.getOrNull() }
            ?.toAbsolutePath()
            ?.normalize()
        return developmentDirectory?.takeIf(::hasCompleteDiagramResources)
    }

    /** 返回页面引用的 Mermaid 入口相对于图表资源目录的路径。 */
    fun diagramMermaidEntryRelativePath(): Path = Path.of("mermaid", "mermaid.min.js")

    /** 判断一个目录是否能在无网络环境中承载图表页面。 */
    private fun hasCompleteDiagramResources(directory: Path): Boolean =
        Files.isRegularFile(directory.resolve(MERMAID_WORKER_FILE)) &&
            Files.isRegularFile(directory.resolve(diagramMermaidEntryRelativePath()))

    /** 判断请求是否只指向给定的本地图表资源目录。 */
    private fun isAllowedResourceUrl(url: String): Boolean =
        isAllowedDiagramResourceUrl(url, resolveDiagramResourceDirectoryForRequest())

    /** 请求拦截期间只使用已解析目录，解析失败时返回 null 以拒绝一切请求。 */
    private fun resolveDiagramResourceDirectoryForRequest(): Path? =
        resolveDiagramResourceDirectory()?.toAbsolutePath()?.normalize()
}

/** 判断 [url] 是否只会访问 [resourceDirectory] 内的离线图表文件，目录缺失时拒绝访问。 */
internal fun isAllowedDiagramResourceUrl(
    url: String,
    resourceDirectory: Path?,
): Boolean {
    val normalizedResourceDirectory = resourceDirectory?.toAbsolutePath()?.normalize() ?: return false
    if (url == "about:blank") return true
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("file", ignoreCase = true)) return false
    val resourceUri = runCatching {
        URI(uri.scheme, uri.authority, uri.path, null, null)
    }.getOrNull() ?: return false
    val requestedPath = runCatching { Path.of(resourceUri).toAbsolutePath().normalize() }.getOrNull() ?: return false
    return requestedPath.startsWith(normalizedResourceDirectory)
}
