package com.agent.shared.agent

/**
 * 桌面工具层与 UI 层之间的挂起恢复桥。
 */
interface DesktopToolInteractionBridge {
    /**
     * 请求用户回答一个问题，并在提交后恢复。
     */
    suspend fun requestQuestion(request: QuestionRequest): String

    /**
     * 请求用户确认一次危险操作。
     */
    suspend fun requestApproval(request: ApprovalRequest): Boolean
}

/**
 * 默认的拒绝式交互桥；在 UI 未接入时避免工具静默阻塞。
 */
object RejectingDesktopToolInteractionBridge : DesktopToolInteractionBridge {
    /**
     * 默认情况下不支持提问式挂起。
     */
    override suspend fun requestQuestion(request: QuestionRequest): String {
        error("当前运行环境尚未接入 ask_user 交互桥。问题: ${request.question}")
    }

    /**
     * 默认情况下拒绝危险操作。
     */
    override suspend fun requestApproval(request: ApprovalRequest): Boolean = false
}
