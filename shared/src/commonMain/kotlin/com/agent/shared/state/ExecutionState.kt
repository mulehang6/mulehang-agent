package com.agent.shared.state

/**
 * 当前会话执行状态。
 */
sealed interface ExecutionState {
    /**
     * 空闲态。
     */
    data object Idle : ExecutionState

    /**
     * 执行中。
     */
    data object Running : ExecutionState

    /**
     * 正在等待用户回答 ask_user。
     */
    data object WaitingForUserInput : ExecutionState

    /**
     * 正在等待用户审批危险操作。
     */
    data object WaitingForApproval : ExecutionState

    /**
     * 执行失败。
     */
    data class Failed(val error: AppError) : ExecutionState
}
