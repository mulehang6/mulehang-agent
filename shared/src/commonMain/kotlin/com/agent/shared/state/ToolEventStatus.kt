package com.agent.shared.state

/**
 * 工具相关时间线事件的状态。
 */
enum class ToolEventStatus {
    /**
     * 工具开始调用。
     */
    Started,

    /**
     * 工具已返回结果。
     */
    Finished,

    /**
     * 非工具的中间状态文本。
     */
    Status,
}
