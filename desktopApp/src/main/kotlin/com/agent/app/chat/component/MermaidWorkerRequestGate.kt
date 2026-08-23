package com.agent.app.chat.component

/**
 * 记录共享 Mermaid 工作器当前允许完成的请求。
 *
 * 调用方在同一把生命周期锁内使用它，从而让超时或换页后的旧响应无法完成新请求。
 */
internal class MermaidWorkerRequestGate {
    private var activeRequestId: Long? = null

    /** 激活唯一的当前请求；串行渲染器不允许覆盖尚未结束的请求。 */
    fun activate(requestId: Long) {
        check(requestId > 0) { "Mermaid 请求 ID 必须为正数。" }
        check(activeRequestId == null) { "Mermaid 工作器已有未结束请求。" }
        activeRequestId = requestId
    }

    /** 仅当回包仍属于当前请求时才允许它改变渲染结果。 */
    fun accepts(requestId: Long): Boolean = activeRequestId == requestId

    /** 结束指定请求；超时、取消和正常回包都只会清理自己的 ID。 */
    fun clear(requestId: Long) {
        if (activeRequestId == requestId) activeRequestId = null
    }

    /** 浏览器被重建时丢弃所有活动标记。 */
    fun clearAll() {
        activeRequestId = null
    }
}
