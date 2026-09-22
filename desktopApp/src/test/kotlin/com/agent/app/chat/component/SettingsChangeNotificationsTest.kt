package com.agent.app.chat.component

import com.agent.shared.settings.model.AgentHookCommand
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.settings.model.AgentHookMatcher
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.settings.model.AgentResourceSettings
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import com.agent.shared.settings.model.SettingsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 设置变更通知的会话生命周期和浮层定位回归测试。 */
class SettingsChangeNotificationsTest {

    /** 仅 MCP 变化时只生成 MCP 保存通知。 */
    @Test
    fun `should notify only saved MCP changes`() {
        val original = SettingsDocument()
        val updated = original.copy(agentResources = original.agentResources.copy(mcpServers = listOf(validMcp())))

        assertEquals(
            listOf("已保存 MCP 服务修改。"),
            savedExtensionChangeMessages(original, updated, saveSucceeded = true),
        )
    }

    /** 仅 Hooks 变化时只生成 Hooks 保存通知。 */
    @Test
    fun `should notify only saved hooks changes`() {
        val original = SettingsDocument()
        val updated = original.copy(hooks = validHooks())

        assertEquals(
            listOf("已保存 Agent Hooks 修改。"),
            savedExtensionChangeMessages(original, updated, saveSucceeded = true),
        )
    }

    /** 两类配置同时变化时通知顺序固定为 MCP 后 Hooks。 */
    @Test
    fun `should notify MCP before hooks when both changed`() {
        val original = SettingsDocument()
        val updated = original.copy(
            agentResources = AgentResourceSettings(mcpServers = listOf(validMcp())),
            hooks = validHooks(),
        )

        assertEquals(
            listOf("已保存 MCP 服务修改。", "已保存 Agent Hooks 修改。"),
            savedExtensionChangeMessages(original, updated, saveSucceeded = true),
        )
    }

    /** 撤销到基线、无变化或保存失败均不得生成通知。 */
    @Test
    fun `should not notify reverted unchanged or failed saves`() {
        val baseline = SettingsDocument(agentResources = AgentResourceSettings(mcpServers = listOf(validMcp())))
        val changed = SettingsDocument()

        assertTrue(savedExtensionChangeMessages(baseline, baseline, saveSucceeded = true).isEmpty())
        assertTrue(savedExtensionChangeMessages(baseline, baseline.copy(), saveSucceeded = true).isEmpty())
        assertTrue(savedExtensionChangeMessages(baseline, changed, saveSucceeded = false).isEmpty())
    }

    /** 收起单条提示不影响历史；打开独立通知页后标记已读。 */
    @Test
    fun `should retain dismissed notification in session history`() {
        val notifications = SettingsChangeNotifications()
        val entry = notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "全局设置：已添加 Skills 目录：tools")

        assertEquals(entry, notifications.transientEntry)
        notifications.dismissTransient()
        assertNull(notifications.transientEntry)
        assertEquals(listOf(entry), notifications.entries)

        assertTrue(notifications.hasUnreadEntries)
        assertEquals(1, notifications.unreadCount)
        notifications.markAllRead()

        assertFalse(notifications.hasUnreadEntries)
        assertNull(notifications.transientEntry)
        assertEquals(listOf(entry), notifications.entries)
    }

    /** 旧通知的倒计时不得关闭后来出现的通知，固定生命周期为十秒。 */
    @Test
    fun `should dismiss only matching transient notification after ten seconds`() {
        val notifications = SettingsChangeNotifications()
        val first = notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "first")
        val second = notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "second")

        notifications.dismissTransient(first.id)

        assertEquals(second, notifications.transientEntry)
        assertEquals(10.seconds, SETTINGS_NOTIFICATION_AUTO_DISMISS_DURATION)
        notifications.dismissTransient(second.id)
        assertNull(notifications.transientEntry)
        assertEquals(listOf(first, second), notifications.entries)
    }

    /** 标记已读只改变提示状态，不删除会话内记录。 */
    @Test
    fun `should dismiss notification history without clearing entries`() {
        val notifications = SettingsChangeNotifications()
        val entry = notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "全局设置：已添加 MCP 服务：filesystem")

        notifications.markAllRead()

        assertFalse(notifications.hasUnreadEntries)
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
        assertFalse(notifications.hasUnreadEntries)
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

    /** 新记录在已读游标之后重新点亮未读状态。 */
    @Test
    fun `should mark later notifications unread after opening history page`() {
        val notifications = SettingsChangeNotifications()
        notifications.record(SettingsChangeNotificationCategory.EXTENSIONS, "first")
        notifications.markAllRead()

        notifications.record(SettingsChangeNotificationCategory.AI_SERVICES, "second")

        assertTrue(notifications.hasUnreadEntries)
        assertEquals(1, notifications.unreadCount)
    }

    /** 通知不暴露绝对路径，范围只使用稳定的配置层级名称。 */
    @Test
    fun `should render stable scope labels for notification messages`() {
        assertEquals("全局设置", settingsChangeScopeLabel(ConfigLayer.USER))
        assertEquals("项目设置", settingsChangeScopeLabel(ConfigLayer.PROJECT))
        assertEquals("环境设置", settingsChangeScopeLabel(ConfigLayer.ENVIRONMENT))
    }

    /** 构造保存通知测试使用的最小有效 MCP。 */
    private fun validMcp(): McpServerSettings = McpServerSettings(
        id = "local",
        transport = McpServerTransport.STDIO,
        command = listOf("npx"),
    )

    /** 构造保存通知测试使用的最小有效 Hooks 文档片段。 */
    private fun validHooks(): AgentHookSettings = AgentHookSettings(
        hooks = mapOf(
            AgentHookEvent.USER_PROMPT_SUBMIT to listOf(
                AgentHookMatcher(hooks = listOf(AgentHookCommand(command = "echo ok"))),
            ),
        ),
    )
}
