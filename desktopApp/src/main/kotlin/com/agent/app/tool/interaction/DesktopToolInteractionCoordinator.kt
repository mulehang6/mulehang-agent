package com.agent.app.tool.interaction

import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.QuestionRequest
import kotlinx.coroutines.CompletableDeferred

/**
 * 用户对单次工具审批的选择。
 */
enum class ApprovalResponse {
    /** 仅允许当前这一次调用。 */
    APPROVE_ONCE,

    /** 允许本会话后续同名工具调用。 */
    APPROVE_TOOL_TYPE,

    /** 拒绝当前调用并结束正在执行的 agent 轮次。 */
    REJECT_AND_STOP,
}

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
    private var pendingApprovalRequest: ApprovalRequest? = null
    private val autoApprovedToolNames = mutableSetOf<String>()

    /**
     * 返回当前会话中已被持续允许的工具类型。
     */
    override fun isApprovalAutoApproved(request: ApprovalRequest): Boolean = synchronized(lock) {
        request.toolName in autoApprovedToolNames
    }

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
            if (request.toolName in autoApprovedToolNames) {
                return true
            } else {
                check(pendingApproval == null) { "已有未完成的审批请求: ${request.requestId}" }
                pendingApproval = deferred
                pendingApprovalRequest = request
            }
        }
        return try {
            deferred.await()
        } finally {
            synchronized(lock) {
                if (pendingApproval === deferred) {
                    pendingApproval = null
                    pendingApprovalRequest = null
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
     * 提交细分的审批选择，并释放当前工具调用。
     */
    fun submitApproval(response: ApprovalResponse): Boolean = synchronized(lock) {
        val deferred = pendingApproval ?: return@synchronized false
        if (response == ApprovalResponse.APPROVE_TOOL_TYPE) {
            pendingApprovalRequest?.toolName?.let(autoApprovedToolNames::add)
        }
        deferred.complete(response != ApprovalResponse.REJECT_AND_STOP)
    }

}
