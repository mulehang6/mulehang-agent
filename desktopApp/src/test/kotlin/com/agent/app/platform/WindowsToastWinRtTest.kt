package com.agent.app.platform

import com.agent.shared.chat.attention.ConversationAttentionEvent
import com.agent.shared.chat.attention.ConversationAttentionType
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.xml.sax.InputSource

/** Windows Toast XML 必须保留精确目标并接受用户文本中的特殊字符。 */
class WindowsToastWinRtTest {
    /** 解析出的 launch 与正文不应被 XML 转义污染。 */
    @Test
    fun `toast xml escapes content and preserves activation target`() {
        val event = ConversationAttentionEvent(
            id = "事件&1", conversationId = "会话/2", entryId = "条目=3",
            type = ConversationAttentionType.QUESTION,
            title = "审批 <文件>", body = "A&B \"测试\"", actionResolved = false,
            readAt = null, createdAt = 1,
        )
        val xml = WindowsToastWinRt.toastXml(event)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val target = ToastActivationTarget.parse(document.documentElement.getAttribute("launch"))
        assertEquals(event.id, target?.eventId)
        assertEquals(event.conversationId, target?.conversationId)
        assertEquals(event.entryId, target?.entryId)
        assertEquals("审批 <文件>", document.getElementsByTagName("text").item(0).textContent)
        assertEquals("A&B \"测试\"", document.getElementsByTagName("text").item(1).textContent)
    }
}
