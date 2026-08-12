package com.agent.shared.chat.model

import com.agent.shared.tool.model.FileDiffPreview

/**
 * 时间线中的工具调用或状态事件项。
 *
 * [errorMessage] 仅在 [status] 为 [ToolEventStatus.Failed] 时携带失败原因，
 * 用于在工具事件卡片内就地展示错误，而非在面板顶部单独展示。
 * [operationIntent] 为终端调用时由模型提供的简短操作说明。
 * [resultPreview] 保存回灌给后续模型上下文的紧凑工具输出。
 * [resultDisplay] 保存供用户展开查看的完整工具输出。
 * [fileDiffs] 保存原生补丁工具生成的结构化文件 Diff，供时间线按编辑器式画布展示。
 */
data class ToolEventItem(
    val toolName: String,
    val status: ToolEventStatus,
    val preview: String? = null,
    val errorMessage: String? = null,
    val operationIntent: String? = null,
    val toolCallId: String? = null,
    val resultPreview: String? = null,
    val resultDisplay: String? = null,
    val fileDiffs: List<FileDiffPreview> = emptyList(),
) : ConversationItem {
    override val kind: ConversationItem.Kind = ConversationItem.Kind.ToolEvent
}
