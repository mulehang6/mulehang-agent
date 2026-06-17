package com.agent.app.ui

import com.agent.shared.agent.ApprovalRequest
import com.agent.shared.agent.DesktopToolInteractionBridge
import com.agent.shared.agent.QuestionRequest
import kotlinx.coroutines.CompletableDeferred

/**
 * 桌面 UI 与工具执行链之间的挂起恢复协调器。
 *
 * 它只负责等待和回填，不直接驱动 UI；具体展示状态仍由 `AgentStreamEvent`
 * 进入 `ChatWindowState` 后维护。
 */
class DesktopToolInteractionCoordinator : DesktopToolInteractionBridge {
    private val lock = Any()
    private var pendingQuestion: CompletableDeferred<String>? = null
    private var pendingApproval: CompletableDeferred<Boolean>? = null

    /**
     * 挂起当前工具调用，直到 UI 提交问题答案。
     */
    override suspend fun requestQuestion(request: QuestionRequest): String {
        val deferred = CompletableDeferred<String>()
        synchronized(lock) {
            check(pendingQuestion == null) { "已有未完成的问题请求: ${request.requestId}" }
            pendingQuestion = deferred
        }
        return try {
            deferred.await()
        } finally {
            synchronized(lock) {
                if (pendingQuestion === deferred) {
                    pendingQuestion = null
                }
            }
        }
    }

    /**
     * 挂起当前工具调用，直到 UI 提交审批结果。
     */
    override suspend fun requestApproval(request: ApprovalRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(lock) {
            check(pendingApproval == null) { "已有未完成的审批请求: ${request.requestId}" }
            pendingApproval = deferred
        }
        return try {
            deferred.await()
        } finally {
            synchronized(lock) {
                if (pendingApproval === deferred) {
                    pendingApproval = null
                }
            }
        }
    }

    /**
     * 提交问题答案并恢复当前轮次。
     */
    fun submitQuestion(answer: String): Boolean = synchronized(lock) {
        pendingQuestion?.complete(answer) ?: false
    }

    /**
     * 提交审批结果并恢复当前轮次。
     */
    fun submitApproval(approved: Boolean): Boolean = synchronized(lock) {
        pendingApproval?.complete(approved) ?: false
    }
}
