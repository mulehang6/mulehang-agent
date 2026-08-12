package com.agent.shared.tool.interaction

import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.FileDiffPreview
import com.agent.shared.tool.model.QuestionRequest

/**
 * 桌面工具层与 UI 层之间的挂起恢复桥。
 */
interface DesktopToolInteractionBridge {
    /**
     * 判断同类工具是否已获得本轮会话内的持续授权。
     *
     * 默认保持逐次确认，避免非桌面实现意外放宽权限。
     */
    fun isApprovalAutoApproved(request: ApprovalRequest): Boolean = false

    /**
     * 将工具执行期间产生的输出同步转发给 UI。
     *
     * 工具实现运行在非协程调用栈中，因此该回调不能挂起。
     */
    fun onToolOutputChunk(
        toolName: String,
        text: String,
        isErrorStream: Boolean,
    ) = Unit

    /**
     * 将原生文件操作的结构化 Diff 转发给执行时间线。
     *
     * 回调发生在补丁审批和落盘之前，因此审批卡与执行完成卡可复用同一份安全预览。
     */
    fun onFileDiffPreview(
        toolName: String,
        diffs: List<FileDiffPreview>,
    ) = Unit

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
