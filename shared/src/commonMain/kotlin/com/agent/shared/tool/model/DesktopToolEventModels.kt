package com.agent.shared.tool.model

import kotlinx.serialization.Serializable

/**
 * 单个待回答问题及其可选的内置候选项。
 */
@Serializable
data class QuestionPrompt(
    val question: String,
    val options: List<String> = emptyList(),
)

/**
 * 用户对单个问题的最终回答。
 */
data class QuestionAnswer(
    val question: String,
    val answer: String,
)

/**
 * `ask_user` 发起的问题请求。
 */
data class QuestionRequest(
    val requestId: String,
    val toolCallId: String,
    val questions: List<QuestionPrompt> = emptyList(),
    /** 兼容旧工具调用的单题文本；仅在 [questions] 为空时使用。 */
    val question: String = "",
    /** 兼容旧工具调用的单题候选项；仅在 [questions] 为空时使用。 */
    val options: List<String> = emptyList(),
    val allowFreeText: Boolean = true,
) {
    /**
     * 返回已过滤空题目、去重候选项且每题最多五项的有效题目。
     */
    val effectiveQuestions: List<QuestionPrompt>
        get() = normalizeQuestionPrompts(
            questions.ifEmpty { listOf(QuestionPrompt(question = question, options = options)) },
        )
}

/**
 * 清理工具请求中的题目和候选项，确保界面与模型看到一致的上限。
 */
fun normalizeQuestionPrompts(raw: List<QuestionPrompt>): List<QuestionPrompt> = raw
    .map { prompt ->
        prompt.copy(
            question = prompt.question.trim(),
            options = prompt.options
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .take(5),
        )
    }
    .filter { it.question.isNotEmpty() }

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
