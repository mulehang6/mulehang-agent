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
    val risk: ToolRisk = ToolRisk.UNKNOWN,
    val diff: FileDiffPreview? = null,
    /** 一次补丁包含的全部文件 Diff；单文件调用继续使用 [diff] 兼容既有界面与历史。 */
    val diffs: List<FileDiffPreview> = diff?.let(::listOf).orEmpty(),
)

/** 描述工具调用的安全敏感度，供审批器与界面共同使用。 */
enum class ToolRisk {
    READ_ONLY,
    WORKSPACE_WRITE,
    EXTERNAL_WRITE,
    COMMAND,
    DANGEROUS,
    UNKNOWN,
}

/** 文件变更种类。 */
@Serializable
enum class FileChangeKind {
    CREATED,
    MODIFIED,
    DELETED,
}

/** 编辑器式 unified Diff 中一行的语义，供 UI 决定 gutter、颜色和行号。 */
@Serializable
enum class FileDiffLineKind {
    CONTEXT,
    REMOVED,
    ADDED,
}

/** 单栏编辑器 Diff 的一行；不存在的一侧行号以 null 表示。 */
@Serializable
data class FileDiffLinePreview(
    val kind: FileDiffLineKind,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val content: String,
)

/**
 * 在写入前生成的统一 diff 预览。
 *
 * [collapsedUnchangedLineCount] 表示 UI 默认隐藏的未改动行数，避免大文件占满审批卡。
 */
@Serializable
data class FileDiffPreview(
    val path: String,
    val kind: FileChangeKind,
    val unifiedDiff: String,
    val collapsedUnchangedLineCount: Int,
    /** 面向审批 UI 的结构化行，避免把 patch 协议原文当作编辑器内容显示。 */
    val editorLines: List<FileDiffLinePreview> = emptyList(),
)
