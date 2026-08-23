package com.agent.app.chat.component

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证常驻 Mermaid 工作页与 JVM 之间的消息编码和回包隔离。 */
class MermaidWorkerProtocolTest {

    /** 源码仅以 Base64URL 传入固定页面，不进入 JavaScript 字面量或工作页 URL。 */
    @Test
    fun dispatchScriptEncodesSourceWithoutLeakingRawDiagramText() {
        val source = "flowchart TD\\n用户[带引号 \"的标签\"] --> 完成"

        val script = mermaidRenderScript(
            requestId = 17,
            source = source,
            isDark = true,
        )

        assertTrue(script.startsWith("window.mulehangRender({requestId:17,"))
        assertTrue(script.contains("themePolicy:'auto'"))
        assertFalse(script.contains(source))
        assertFalse(script.contains("\"的标签"))
    }

    /** 页面 ready 和 SVG 成功回包会恢复为带代次、请求 ID 的强类型消息。 */
    @Test
    fun parsesReadyAndSuccessfulSvgResponse() {
        val svg = "<svg viewBox=\"0 0 1 1\"><path d=\"M0 0\"/></svg>"

        assertEquals(
            MermaidWorkerMessage.Ready(generation = 7),
            parseMermaidWorkerMessage("ready|7"),
        )
        assertEquals(
            MermaidWorkerMessage.Response(
                generation = 7,
                requestId = 11,
                result = DiagramRenderResult.Success(svg),
            ),
            parseMermaidWorkerMessage("7|11|success|${encodeBase64Url(svg)}"),
        )
    }

    /** 纯矢量策略不能接受 HTML 标签时，回包明确映射为可恢复的轮廓化失败。 */
    @Test
    fun mapsUnsupportedVectorContentToRecoverableFailure() {
        val message = parseMermaidWorkerMessage(
            "9|12|failure|unsupported|${encodeBase64Url("图表使用了 HTML 标签")}",
        )

        val response = assertIs<MermaidWorkerMessage.Response>(message)
        val failure = assertIs<DiagramRenderResult.Failure>(response.result)
        assertEquals(DiagramFailureKind.SVG_TEXT_OUTLINE, failure.failure.kind)
        assertEquals("图表使用了 HTML 标签", failure.failure.detail)
    }

    /** 损坏字段不会被错当作当前请求的回包。 */
    @Test
    fun ignoresMalformedWorkerMessages() {
        assertNull(parseMermaidWorkerMessage("ready|not-a-generation"))
        assertNull(parseMermaidWorkerMessage("7|11|success|%%%"))
        assertNull(parseMermaidWorkerMessage("7|11|unknown|payload"))
    }

    /** 生成协议允许使用的无填充 Base64URL 负载。 */
    private fun encodeBase64Url(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}
