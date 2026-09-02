package com.agent.app.chat.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.agent.shared.settings.model.ConfigLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 设置变更通知的会话生命周期和浮层定位回归测试。 */
class SettingsChangeNotificationsTest {

    /** 收起单条提示不影响历史，打开历史时则不再保留单条浮层。 */
    @Test
    fun `should retain dismissed notification in session history`() {
        val notifications = SettingsChangeNotifications()
        val entry = notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "全局设置：已添加 Skills 目录：tools")

        assertEquals(entry, notifications.transientEntry)
        notifications.dismissTransient()
        assertNull(notifications.transientEntry)
        assertEquals(listOf(entry), notifications.entries)

        notifications.toggleHistory()

        assertTrue(notifications.historyVisible)
        assertNull(notifications.transientEntry)
        assertEquals(listOf(entry), notifications.entries)
    }

    /** 单条删除和清空全部都必须同步清理当前展示状态。 */
    @Test
    fun `should remove individual entries and clear all history`() {
        val notifications = SettingsChangeNotifications()
        val first = notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "全局设置：已启用扩展包：team-tools")
        val second = notifications.record(SettingsChangeNotificationCategory.AI_SERVICES, "项目设置：已新增 AI 服务：gateway")

        notifications.remove(second.id)

        assertEquals(listOf(first), notifications.entries)
        assertNull(notifications.transientEntry)
        notifications.clear()
        assertTrue(notifications.entries.isEmpty())
        assertFalse(notifications.historyVisible)
    }

    /** 历史只保留最近一百条，最早记录按创建顺序淘汰。 */
    @Test
    fun `should retain only the newest one hundred notifications`() {
        val notifications = SettingsChangeNotifications()
        repeat(SETTINGS_CHANGE_NOTIFICATION_LIMIT + 1) { index ->
            notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "全局设置：变更 $index")
        }

        assertEquals(SETTINGS_CHANGE_NOTIFICATION_LIMIT, notifications.entries.size)
        assertEquals(2L, notifications.entries.first().id)
        assertEquals(101L, notifications.entries.last().id)
        assertEquals(101L, notifications.transientEntry?.id)
    }

    /** 浮层向通知图标的左上侧展开，边缘不足时不得越过窗口。 */
    @Test
    fun `should anchor floating card beside notification icon within root bounds`() {
        val placement = settingsNotificationCardPlacement(
            rootSize = IntSize(1024, 720),
            anchor = Rect(left = 980f, top = 640f, right = 1020f, bottom = 680f),
            cardWidthPx = 360,
            edgePx = 12,
            gapPx = 8,
        )

        assertEquals(612, placement.leftPx)
        assertEquals(40, placement.bottomPx)
        assertEquals(668, placement.maxHeightPx)
        assertEquals(360.dp, settingsNotificationCardWidth(600.dp))
        assertEquals(276.dp, settingsNotificationCardWidth(300.dp))
    }

    /** 通知不暴露绝对路径，范围只使用稳定的配置层级名称。 */
    @Test
    fun `should render stable scope labels for notification messages`() {
        assertEquals("全局设置", settingsChangeScopeLabel(ConfigLayer.USER))
        assertEquals("项目设置", settingsChangeScopeLabel(ConfigLayer.PROJECT))
        assertEquals("环境设置", settingsChangeScopeLabel(ConfigLayer.ENVIRONMENT))
    }
}
