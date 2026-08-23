package com.agent.app.chat.component

import org.slf4j.LoggerFactory

/** 离线图表预览可恢复失败的具体类别。 */
internal enum class DiagramFailureKind(
    val label: String,
) {
    RESOURCE_MISSING("本地资源缺失"),
    JCEF_INITIALIZATION("JCEF 初始化"),
    PAGE_LOAD("离线页面加载"),
    MERMAID_SYNTAX("Mermaid 语法"),
    PLANT_UML_RENDER("PlantUML 生成"),
    SVG_TEXT_OUTLINE("SVG 文字转换"),
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

/** 记录图表回退原因，便于区分资源、浏览器和语法问题。 */
internal fun logDiagramPreviewFailure(failure: DiagramPreviewFailure) {
    val message = "离线图表预览失败 [${failure.kind.name}]：${failure.detail}"
    if (failure.cause == null) {
        DIAGRAM_FAILURE_LOGGER.warn(message)
    } else {
        DIAGRAM_FAILURE_LOGGER.warn(message, failure.cause)
    }
}

private val DIAGRAM_FAILURE_LOGGER = LoggerFactory.getLogger("com.agent.app.chat.component.DiagramPreviewFailure")
