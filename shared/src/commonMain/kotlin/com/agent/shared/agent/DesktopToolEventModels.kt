package com.agent.shared.agent

/**
 * `ask_user` 发起的问题请求。
 */
data class QuestionRequest(
    val requestId: String,
    val toolCallId: String,
    val question: String,
    val options: List<String>,
    val allowFreeText: Boolean = true,
)

/**
 * 需要用户审批的危险操作请求。
 */
data class ApprovalRequest(
    val requestId: String,
    val toolName: String,
    val summary: String,
    val targetPath: String? = null,
    val payloadPreview: String? = null,
)
