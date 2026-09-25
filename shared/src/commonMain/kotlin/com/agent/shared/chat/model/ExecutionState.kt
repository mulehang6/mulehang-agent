package com.agent.shared.chat.model

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

    /** 用户暂停，等待从本轮恢复点继续。 */
    data object Paused : ExecutionState

    /** 应用重启后发现尚未终结的运行，等待继续。 */
    data object Interrupted : ExecutionState

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
