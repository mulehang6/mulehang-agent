package com.agent.shared.tool.runtime

import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.ToolRisk

/** AUTO 模式的独立快速审批模型接口。 */
fun interface ToolApprovalAgent {
    /** 对已通过硬性规则的请求给出放行、人工确认或拒绝。 */
    fun decide(request: ApprovalRequest): ApprovalDecision
}

/** 带来源与原因的 AUTO 审核结果，供日志、审计和人工回退判断使用。 */
data class ApprovalReview(
    val decision: ApprovalDecision,
    val source: String,
    val reason: String,
)

/** 可选的详细审核能力；保留 [ToolApprovalAgent] 为函数接口以兼容已有测试替身。 */
interface DetailedToolApprovalAgent : ToolApprovalAgent {
    /** 返回已脱敏的审核来源和理由，不能包含凭据或完整补丁。 */
    fun review(request: ApprovalRequest): ApprovalReview

    override fun decide(request: ApprovalRequest): ApprovalDecision = review(request).decision
}

/** 审批模型的稳定输出；无效或失败时必须落到 [ASK]。 */
enum class ApprovalDecision {
    ALLOW,
    ASK,
    DENY,
}

/**
 * 将实际 LLM 调用封装在 [review] 中，并在任何失败或越权结果时 fail closed。
 * [profile] 必须来自 FasterModelResolver，而不是当前执行模型。
 */
class ConfiguredToolApprovalAgent(
    val profile: ConfigProfile,
    private val review: (ConfigProfile, ApprovalRequest) -> ApprovalDecision,
) : DetailedToolApprovalAgent {
    override fun review(request: ApprovalRequest): ApprovalReview = when (request.risk) {
        ToolRisk.DANGEROUS -> ApprovalReview(ApprovalDecision.DENY, "hard-rule", "dangerous_risk")
        else -> runCatching { review(profile, request) }
            .fold(
                onSuccess = { ApprovalReview(it, "configured-reviewer", "model_decision") },
                onFailure = { ApprovalReview(ApprovalDecision.ASK, "manual-fallback", "reviewer_failed") },
            )
    }
}

/** 未配置审批模型时保持人工确认，不能把 AUTO 误降级为无确认执行。 */
object ManualFallbackToolApprovalAgent : DetailedToolApprovalAgent {
    override fun review(request: ApprovalRequest): ApprovalReview =
        ApprovalReview(ApprovalDecision.ASK, "manual-fallback", "reviewer_unconfigured")
}
