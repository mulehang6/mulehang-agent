package com.agent.shared.agent.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.Prompt
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.runtime.ApprovalDecision
import com.agent.shared.tool.runtime.ApprovalReview
import com.agent.shared.tool.runtime.DetailedToolApprovalAgent
import kotlinx.coroutines.runBlocking

/** 使用独立快速模型进行无工具、单轮 AUTO 审批，并记录可审计的降级原因。 */
class KoogToolApprovalAgent(private val profile: ConfigProfile) : DetailedToolApprovalAgent {
    override fun review(request: ApprovalRequest): ApprovalReview {
        if (request.risk == com.agent.shared.tool.model.ToolRisk.DANGEROUS) {
            return ApprovalReview(ApprovalDecision.DENY, "hard-rule", "dangerous_risk")
        }
        return runCatching { runBlocking { runApprovalOnlyAgent(profile, request) } }
            .fold(
                onSuccess = { output ->
                    output.toApprovalDecisionOrNull()?.let { decision ->
                        ApprovalReview(decision, "auto-reviewer", "model_decision")
                    } ?: ApprovalReview(ApprovalDecision.ASK, "manual-fallback", "invalid_model_output")
                },
                onFailure = { ApprovalReview(ApprovalDecision.ASK, "manual-fallback", "reviewer_failed") },
            )
    }
}

/** 请求审批模型只返回 ALLOW、ASK 或 DENY。 */
private suspend fun runApprovalOnlyAgent(profile: ConfigProfile, request: ApprovalRequest): String {
    val agent = AIAgent.builder()
        .promptExecutor(buildPromptExecutor(profile))
        .llmModel(buildLlmModel(profile))
        .toolRegistry(ToolRegistry.EMPTY)
        .prompt(buildToolApprovalPrompt())
        .functionalStrategy(approvalOnlyStrategy)
        .build()
    val input = "tool=${request.toolName}\nrisk=${request.risk}\npath=${request.targetPath.orEmpty()}\nintent=${request.summary}\ndiff=${request.diff?.unifiedDiff?.take(8_000).orEmpty()}"
    return agent.run(input, null)
}

private val approvalOnlyStrategy = functionalStrategy<String, String>("tool_approval") { request ->
    requestLLM(request).approvalText()
}

/** 构建与普通 agent 隔离的审核系统提示。 */
private fun buildToolApprovalPrompt(): Prompt = Prompt.build(
    id = "mulehang-tool-approval",
) {
    system("You are a security approval reviewer. Reply with exactly ALLOW, ASK, or DENY. Never use tools. Ask for human confirmation when uncertain, when a path is external, or when content is sensitive.")
}

private fun Message.Assistant.approvalText(): String =
    parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }

private fun String.toApprovalDecisionOrNull(): ApprovalDecision? = when (trim().uppercase()) {
    "ALLOW" -> ApprovalDecision.ALLOW
    "ASK" -> ApprovalDecision.ASK
    "DENY" -> ApprovalDecision.DENY
    else -> null
}
