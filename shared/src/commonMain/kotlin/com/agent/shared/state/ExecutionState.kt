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
     * 执行失败。
     */
    data class Failed(val error: AppError) : ExecutionState
}
