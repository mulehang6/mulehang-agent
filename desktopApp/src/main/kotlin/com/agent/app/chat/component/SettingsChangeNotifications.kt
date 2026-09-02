package com.agent.app.chat.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.settings.model.ConfigLayer

/** 本次应用会话内设置变更通知的最大保留数量。 */
internal const val SETTINGS_CHANGE_NOTIFICATION_LIMIT = 100

/** 设置变更的可见分类，仅覆盖扩展和 AI 服务。 */
internal enum class SettingsChangeNotificationCategory {
    EXTENSIONS,
    AI_SERVICES,
}

/** 将配置层级转为不依赖本地路径的通知范围文案。 */
internal fun settingsChangeScopeLabel(layer: ConfigLayer): String = when (layer) {
    ConfigLayer.USER -> "全局设置"
    ConfigLayer.PROJECT -> "项目设置"
    ConfigLayer.ENVIRONMENT -> "环境设置"
}

/** 一条不含敏感配置内容的设置变更记录。 */
internal data class SettingsChangeNotification(
    val id: Long,
    val category: SettingsChangeNotificationCategory,
    val message: String,
)

/** 管理单条提示与总历史卡片的会话级状态。 */
@Stable
internal class SettingsChangeNotifications {
    private val mutableEntries = mutableStateListOf<SettingsChangeNotification>()
    private var nextId by mutableLongStateOf(0L)

    /** 最新操作触发的单条提示；关闭时保留历史，仅隐藏该卡片。 */
    var transientEntryId by mutableStateOf<Long?>(null)
        private set

    /** 是否显示由通知图标打开的完整历史。 */
    var historyVisible by mutableStateOf(false)
        private set

    /** 按创建顺序保存的会话内通知。 */
    val entries: List<SettingsChangeNotification>
        get() = mutableEntries

    /** 当前可作为单条提示展示的通知。 */
    val transientEntry: SettingsChangeNotification?
        get() = transientEntryId?.let { id -> mutableEntries.firstOrNull { entry -> entry.id == id } }

    /** 追加变更记录，并展示最新单条提示。 */
    fun record(category: SettingsChangeNotificationCategory, message: String): SettingsChangeNotification {
        val entry = SettingsChangeNotification(
            id = nextId + 1,
            category = category,
            message = message,
        )
        nextId = entry.id
        mutableEntries += entry
        while (mutableEntries.size > SETTINGS_CHANGE_NOTIFICATION_LIMIT) {
            mutableEntries.removeAt(0)
        }
        transientEntryId = entry.id
        historyVisible = false
        return entry
    }

    /** 隐藏单条提示，不删除其历史。 */
    fun dismissTransient() {
        transientEntryId = null
    }

    /** 切换总历史，并避免和单条提示重叠显示。 */
    fun toggleHistory() {
        historyVisible = !historyVisible
        if (historyVisible) transientEntryId = null
    }

    /** 从总历史中彻底移除一条消息。 */
    fun remove(id: Long) {
        mutableEntries.removeAll { entry -> entry.id == id }
        if (transientEntryId == id) transientEntryId = null
    }

    /** 清空整个会话的设置变更历史。 */
    fun clear() {
        mutableEntries.clear()
        transientEntryId = null
        historyVisible = false
    }
}
