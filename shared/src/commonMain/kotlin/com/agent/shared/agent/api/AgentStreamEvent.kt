package com.agent.shared.agent.api

import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.FileDiffPreview
import com.agent.shared.tool.model.QuestionRequest
import com.agent.shared.agent.status.AgentStatusSnapshot

/**
 * UI 可消费的 agent 流式事件。
 */
sealed interface AgentStreamEvent {
    /**
     * 执行开始。
     */
    data object Started : AgentStreamEvent

    /**
     * 助手正文的文本增量。
     */
    data class TextDelta(val text: String) : AgentStreamEvent

    /**
     * 工具调用开始。
     */
    data class ToolCallStarted(
        val toolCallId: String? = null,
        val name: String,
        val argumentsPreview: String? = null,
        val operationIntent: String? = null,
        /** 完整结构化调用参数，供工具审计和不确定结果恢复使用。 */
        val argumentsJson: String? = null,
    ) : AgentStreamEvent

    /**
     * 工具调用结束。
     */
    data class ToolCallFinished(
        val toolCallId: String? = null,
        val name: String,
        val resultPreview: String? = null,
        val resultDisplay: String? = null,
    ) : AgentStreamEvent

    /**
     * 原生文件工具生成了可直接渲染的结构化 Diff。
     *
     * 该事件在工具完成前发送，让 AUTO 放行的补丁也能在执行后时间线中显示同一份预览。
     */
    data class ToolFileDiffPreviewed(
        val name: String,
        val diffs: List<FileDiffPreview>,
    ) : AgentStreamEvent

    /**
     * 单个工具调用失败；agent 运行继续，不应与整个执行失败混淆。
     */
    data class ToolCallFailed(
        val toolCallId: String? = null,
        val name: String,
        val reason: String,
    ) : AgentStreamEvent

    /** 暂停时无法确认副作用的工具调用，由合成结果接续而不重跑原调用。 */
    data class ToolCallInterrupted(
        val toolCallId: String?,
        val name: String,
        val argumentsJson: String,
        val partialOutput: String,
        val reason: String,
    ) : AgentStreamEvent

    /**
     * 工具运行期间产生的实时输出增量。
     */
    data class ToolOutputDelta(
        val toolCallId: String? = null,
        val name: String,
        val text: String,
        val stream: ToolOutputStream,
    ) : AgentStreamEvent

    /**
     * 工具输出所属的进程流。
     */
    enum class ToolOutputStream {
        Stdout,
        Stderr,
    }

    /**
     * 工具请求用户回答问题。
     */
    data class QuestionRequested(val request: QuestionRequest) : AgentStreamEvent

    /**
     * 工具请求用户审批危险操作。
     */
    data class ApprovalRequested(val request: ApprovalRequest) : AgentStreamEvent

    /**
     * 非正文的中间状态文本。
     */
    data class Status(val message: String) : AgentStreamEvent

    /** 代码生成且已保存的模型状态文本；普通时间线不显示。 */
    data class StatusSnapshotUpdated(
        val snapshot: AgentStatusSnapshot,
        val modelMessageText: String,
    ) : AgentStreamEvent

    /** Provider 实际 token 用量；缺失字段由界面继续沿用估算。 */
    data class UsageUpdated(
        val inputTokens: Long?,
        val outputTokens: Long?,
        val contextWindow: Int?,
    ) : AgentStreamEvent

    /**
     * 思考内容的流式增量。
     */
    data class ReasoningDelta(
        val summary: String?,
        val rawText: String?,
    ) : AgentStreamEvent

    /**
     * 思考内容的完整收尾事件。
     */
    data class ReasoningCompleted(
        val summary: String?,
        val rawText: String?,
    ) : AgentStreamEvent

    /**
     * 执行完成。
     */
    data class Completed(val text: String) : AgentStreamEvent

    /**
     * 执行失败。
     */
    data class Failed(val reason: String) : AgentStreamEvent
}
