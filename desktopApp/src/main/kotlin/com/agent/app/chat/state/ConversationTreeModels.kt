package com.agent.app.chat.state

/** 离开当前分支时采用的摘要策略。 */
internal enum class BranchSummaryMode {
    NONE,
    AUTOMATIC,
    CUSTOM,
}

/** 一次条目树导航的摘要选择。 */
internal data class BranchNavigationSummary(
    val mode: BranchSummaryMode = BranchSummaryMode.NONE,
    val customPrompt: String = "",
)

/** 树操作的可展示结果。 */
internal data class ConversationTreeOperationResult(
    val succeeded: Boolean,
    val message: String? = null,
)
