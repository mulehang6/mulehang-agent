package com.agent.shared.agent.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import com.agent.shared.agent.api.BranchSummaryGenerator
import com.agent.shared.agent.api.BranchSummaryRequest
import com.agent.shared.agent.api.GeneratedBranchSummary
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.agent.prompt.buildPromptParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 通过一次无工具 Koog 请求生成 Pi 语义的分支摘要。 */
class KoogBranchSummaryGenerator(
    private val executionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val agentRunner: suspend (BranchSummaryRequest) -> GeneratedBranchSummary = ::runBranchSummaryAgent,
) : BranchSummaryGenerator {
    /** 异常与取消原样交给调用方，确保失败时不会提交 leaf 迁移。 */
    override suspend fun generate(request: BranchSummaryRequest): GeneratedBranchSummary =
        withContext(executionDispatcher) { agentRunner(request) }
}

/** 创建一次性无工具摘要 Agent，并返回它的纯文本结果。 */
internal suspend fun runBranchSummaryAgent(request: BranchSummaryRequest): GeneratedBranchSummary {
    val agent = AIAgent
        .builder()
        .promptExecutor(buildPromptExecutor(request.profile))
        .llmModel(buildLlmModel(request.profile))
        .toolRegistry(ToolRegistry.EMPTY)
        .prompt(buildBranchSummaryPrompt(request))
        .functionalStrategy(branchSummaryStrategy)
        .build()
    return agent.run(branchSummaryUserPrompt(request), null)
}

/** 构造只允许输出摘要正文的独立 prompt。 */
internal fun buildBranchSummaryPrompt(request: BranchSummaryRequest): Prompt = Prompt.build(
    id = "mulehang-branch-summary",
    params = buildPromptParams(request.profile, reasoningEffort = null),
) {
    system(branchSummarySystemPrompt())
}

/** 返回分支摘要的固定系统约束。 */
internal fun branchSummarySystemPrompt(): String = """
    You are a context summarization assistant. Summarize the abandoned branch of a software-engineering
    conversation so another model can continue from a different point without losing important context.

    Preserve the user's goals, decisions, constraints, completed work, verification results, failures,
    unresolved questions, and relevant file or tool outcomes. Be concise but concrete. Do not call tools,
    do not add facts, and output only the summary body without a preamble or Markdown fence.
""".trimIndent()

/** 把离开分支和可选自定义指令封装为单条摘要请求。 */
internal fun branchSummaryUserPrompt(request: BranchSummaryRequest): String = buildString {
    appendLine("The following entries belong to the branch that is being left:")
    appendLine()
    appendLine(request.branchContent)
    request.customInstructions?.trim()?.takeIf(String::isNotBlank)?.let { instructions ->
        appendLine()
        appendLine("Additional summarization instructions:")
        append(instructions)
    }
}

/** 单轮请求摘要并提取纯文本，不进入普通 Agent 循环。 */
private val branchSummaryStrategy = functionalStrategy<String, GeneratedBranchSummary>("branch_summary") { prompt ->
    requestLLM(prompt).toGeneratedBranchSummary()
}

/** 从无工具响应中提取摘要正文和 provider 返回的用量。 */
private fun Message.Assistant.toGeneratedBranchSummary(): GeneratedBranchSummary = GeneratedBranchSummary(
    summary = parts.filterIsInstance<MessagePart.Text>().joinToString(separator = "\n") { it.text },
    inputTokens = metaInfo.inputTokensCount?.toLong(),
    outputTokens = metaInfo.outputTokensCount?.toLong(),
)
