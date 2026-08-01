package com.agent.app.chat.component

import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证 CommonMark 渲染器之外的行内 Markdown 扩展。 */
class AssistantMarkdownInlineExtensionsTest {

    /** 下划线、高亮、上下标标记应被移除，并改以对应的 Compose 文本样式呈现。 */
    @Test
    fun `should render underline highlight subscript and superscript extensions`() {
        val rendered = renderAssistantMarkdownInlineExtensions(
            """
            - <u>下划线</u>
            - ==高亮==
            - H~2~O
            - X^2^
            """.trimIndent(),
        )

        assertEquals("• 下划线\n• 高亮\n• H2O\n• X2", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
        assertTrue(rendered.spanStyles.any { it.item.background == AssistantMarkdownHighlightBackground })
        assertTrue(rendered.spanStyles.any { it.item.baselineShift == BaselineShift.Subscript })
        assertTrue(rendered.spanStyles.any { it.item.baselineShift == BaselineShift.Superscript })
    }
}
