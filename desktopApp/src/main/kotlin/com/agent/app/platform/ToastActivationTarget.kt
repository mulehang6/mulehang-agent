package com.agent.app.platform

import com.agent.shared.chat.attention.ConversationAttentionEvent
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Windows Toast 激活参数明确定位到事件、会话和条目。 */
internal data class ToastActivationTarget(
    val eventId: String,
    val conversationId: String,
    val entryId: String?,
) {
    /** 转为 Toast XML 的 launch 参数，不混入用户可见正文。 */
    fun toArguments(): String = listOf(
        "eventId" to eventId,
        "conversationId" to conversationId,
        "entryId" to entryId.orEmpty(),
    ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }

    companion object {
        /** 拒绝缺少精确会话或事件定位信息的系统激活。 */
        fun parse(arguments: String?): ToastActivationTarget? {
            if (arguments.isNullOrBlank()) return null
            val values = runCatching {
                arguments.split('&').associate { pair ->
                    val index = pair.indexOf('=')
                    require(index > 0) { "Toast 激活参数格式错误" }
                    pair.substring(0, index) to URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8)
                }
            }.getOrNull() ?: return null
            val eventId = values["eventId"]?.takeIf(String::isNotBlank) ?: return null
            val conversationId = values["conversationId"]?.takeIf(String::isNotBlank) ?: return null
            return ToastActivationTarget(eventId, conversationId, values["entryId"]?.takeIf(String::isNotBlank))
        }
    }
}

/** 从持久化关注事件生成系统通知的精确目标。 */
internal fun ConversationAttentionEvent.toToastActivationTarget(): ToastActivationTarget = ToastActivationTarget(
    eventId = id,
    conversationId = conversationId,
    entryId = entryId,
)
