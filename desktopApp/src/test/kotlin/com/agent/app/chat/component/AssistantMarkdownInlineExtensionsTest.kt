package com.agent.app.chat.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import com.agent.app.design.AppAccent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证 CommonMark 渲染器之外的行内 Markdown 扩展。 */
class AssistantMarkdownInlineExtensionsTest {

    /** 主内容高亮应使用低饱和灰蓝，而不是醒目的偏黄底色。 */
    @Test
    fun `should use a muted highlight background in the main content area`() {
        assertEquals(Color(0xFF3A414A), AssistantMarkdownHighlightBackground)
    }

    /** 行内代码应直接使用强调色文字，且不再附带灰色包裹框。 */
    @Test
    fun `should render inline code as blue text without a background wrapper`() {
        assertEquals(AppAccent, assistantMarkdownStringStyle().codeStyle?.color)
        assertEquals(Color.Unspecified, assistantMarkdownStringStyle().codeStyle?.background)
    }

    /** 扩展 Markdown 与反引号代码混用时，也必须移除反引号并复用无底色的蓝色样式。 */
    @Test
    fun `should style inline code in extension markdown without a background wrapper`() {
        val rendered = renderAssistantMarkdownInlineExtensions("==重点== 和 `gradlew.bat help`")

        assertEquals("重点 和 gradlew.bat help", rendered.text)
        assertTrue(rendered.spanStyles.any {
            it.item.color == AppAccent && it.item.background == Color.Unspecified
        })
    }

    /** 仅含反引号代码的 CommonMark 文本仍应交由完整 Markdown 渲染器处理。 */
    @Test
    fun `should not route plain markdown with inline code through the extension renderer`() {
        assertEquals(false, containsAssistantMarkdownInlineExtensions("**说明** `gradlew.bat help`"))
    }

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
