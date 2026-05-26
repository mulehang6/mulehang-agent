package com.agent.shared.state

/**
 * UI 可消费的错误模型。
 */
data class AppError(
    val title: String,
    val message: String,
)
