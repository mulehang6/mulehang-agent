package com.agent.shared.agent.api

import com.agent.shared.settings.model.ConfigProfile

/** 生成分支摘要所需的隔离输入。 */
data class BranchSummaryRequest(
    /** 从旧 leaf 到共同祖先之间、即将离开的分支内容。 */
    val branchContent: String,
    /** 用户提供的附加摘要指令；为空时采用默认结构。 */
    val customInstructions: String? = null,
    /** 摘要请求使用的会话模型配置。 */
    val profile: ConfigProfile,
)

/** 模型生成的摘要正文及该次独立请求的用量。 */
data class GeneratedBranchSummary(
    val summary: String,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
)

/** 独立于普通 Agent 运行、且不允许注册工具的分支摘要入口。 */
fun interface BranchSummaryGenerator {
    /** 生成可写入分支摘要条目的纯文本。 */
    suspend fun generate(request: BranchSummaryRequest): GeneratedBranchSummary
}
