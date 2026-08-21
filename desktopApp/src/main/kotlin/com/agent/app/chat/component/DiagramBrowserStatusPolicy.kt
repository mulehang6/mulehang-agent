package com.agent.app.chat.component

/** 从页面标题的可选宽高比字段读取正的有限值。 */
internal fun diagramReadyAspectRatio(title: String): Float? =
    title.removePrefix("mulehang-diagram-ready:")
        .toFloatOrNull()
        ?.takeIf { it.isFinite() && it > 0f }

/** 将本地页面写入的受限错误标题映射为稳定的 Kotlin 失败类别。 */
internal fun diagramPageFailure(title: String): DiagramPreviewFailure {
    val pageCategory = title.removePrefix("mulehang-diagram-error:")
    val kind = when (pageCategory) {
        "resource-missing" -> DiagramFailureKind.RESOURCE_MISSING
        "mermaid-syntax" -> DiagramFailureKind.MERMAID_SYNTAX
        else -> DiagramFailureKind.PAGE_LOAD
    }
    return DiagramPreviewFailure(
        kind = kind,
        detail = "离线图表页面报告失败：$pageCategory",
    )
}
