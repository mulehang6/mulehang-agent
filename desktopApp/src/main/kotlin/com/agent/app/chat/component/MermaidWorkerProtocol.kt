package com.agent.app.chat.component

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64

/** Mermaid 固定工作页与 JVM 之间的受控消息协议。 */
internal sealed interface MermaidWorkerMessage {
    /** 页面已完成 JS 查询桥接注入，可接受渲染命令。 */
    data class Ready(
        val generation: Long,
    ) : MermaidWorkerMessage

    /** 页面返回一张图的 SVG 或可恢复失败。 */
    data class Response(
        val generation: Long,
        val requestId: Long,
        val result: DiagramRenderResult,
    ) : MermaidWorkerMessage
}

/** 构造固定工作页地址，源码不出现在 URL、历史记录或日志中。 */
internal fun mermaidWorkerDocumentUrl(resourceDirectory: Path, generation: Long): String =
    resourceDirectory.resolve(DiagramBrowserResourcePolicy.MERMAID_WORKER_FILE).toUri().toString() +
        "?generation=$generation"

/** 构造只包含 Base64URL 字段的 Mermaid 渲染命令。 */
internal fun mermaidRenderScript(
    requestId: Long,
    source: String,
    isDark: Boolean,
): String {
    val encodedSource = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(source.toByteArray(StandardCharsets.UTF_8))
    val themePolicy = if (shouldApplyMermaidAutomaticTheme(source)) "auto" else "source"
    return "window.mulehangRender({requestId:$requestId,source:'$encodedSource',isDark:$isDark,themePolicy:'$themePolicy'});"
}

/** 解析 ready、成功和失败消息；损坏消息会被安全地忽略。 */
internal fun parseMermaidWorkerMessage(request: String): MermaidWorkerMessage? {
    val fields = request.split(MERMAID_MESSAGE_FIELD_SEPARATOR, limit = 5)
    if (fields.getOrNull(0) == MERMAID_MESSAGE_STATUS_READY) {
        return fields.getOrNull(1)?.toLongOrNull()?.let(MermaidWorkerMessage::Ready)
    }
    val generation = fields.getOrNull(0)?.toLongOrNull() ?: return null
    val requestId = fields.getOrNull(1)?.toLongOrNull() ?: return null
    return when (fields.getOrNull(2)) {
        MERMAID_MESSAGE_STATUS_SUCCESS -> fields.getOrNull(3)
            ?.decodeMermaidWorkerPayload()
            ?.takeIf(String::isNotBlank)
            ?.let { svg -> MermaidWorkerMessage.Response(generation, requestId, DiagramRenderResult.Success(svg)) }

        MERMAID_MESSAGE_STATUS_FAILURE -> {
            val failureKind = fields.getOrNull(3).toMermaidDiagramFailureKind()
            val detail = fields.getOrNull(4)
                ?.decodeMermaidWorkerPayload()
                ?.takeIf(String::isNotBlank)
                ?: "Mermaid 工作器未返回可用 SVG。"
            MermaidWorkerMessage.Response(
                generation,
                requestId,
                DiagramRenderResult.Failure(DiagramPreviewFailure(failureKind, detail)),
            )
        }

        else -> null
    }
}

/** 将工作页受控的失败类别映射为既有可恢复失败。 */
private fun String?.toMermaidDiagramFailureKind(): DiagramFailureKind = when (this) {
    MERMAID_WORKER_FAILURE_SYNTAX -> DiagramFailureKind.MERMAID_SYNTAX
    MERMAID_WORKER_FAILURE_RESOURCE -> DiagramFailureKind.RESOURCE_MISSING
    MERMAID_WORKER_FAILURE_UNSUPPORTED -> DiagramFailureKind.SVG_TEXT_OUTLINE
    else -> DiagramFailureKind.BROWSER_SESSION
}

/** 解码 worker 的 Base64URL 字段，损坏回包不会影响后续请求。 */
private fun String.decodeMermaidWorkerPayload(): String? = runCatching {
    val padding = "=".repeat((4 - length % 4) % 4)
    String(Base64.getUrlDecoder().decode(this + padding), StandardCharsets.UTF_8)
}.getOrNull()

/** MessageRouter 的 JavaScript 查询入口。 */
internal const val MERMAID_RENDER_QUERY_FUNCTION = "mulehangMermaidRenderQuery"

/** MessageRouter 的 JavaScript 查询取消入口。 */
internal const val MERMAID_RENDER_QUERY_CANCEL_FUNCTION = "mulehangMermaidRenderQueryCancel"

/** 协议中不会出现在 Base64URL 负载里的字段分隔符。 */
internal const val MERMAID_MESSAGE_FIELD_SEPARATOR = "|"

/** 工作页 ready 消息的状态字段。 */
internal const val MERMAID_MESSAGE_STATUS_READY = "ready"

/** 工作页成功消息的状态字段。 */
internal const val MERMAID_MESSAGE_STATUS_SUCCESS = "success"

/** 工作页失败消息的状态字段。 */
internal const val MERMAID_MESSAGE_STATUS_FAILURE = "failure"

/** 工作页的 Mermaid 语法失败类别。 */
internal const val MERMAID_WORKER_FAILURE_SYNTAX = "syntax"

/** 工作页本地资源失败类别。 */
internal const val MERMAID_WORKER_FAILURE_RESOURCE = "resource"

/** 工作页无法满足纯矢量标签约束的失败类别。 */
internal const val MERMAID_WORKER_FAILURE_UNSUPPORTED = "unsupported"
