package com.agent.app.chat.state

import com.agent.app.ui.buildWorkspaceLabel
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ConversationState
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset

/**
 * 附件在 composer 中的展示状态。
 */
data class ChatAttachmentUiState(
    val path: String,
    val name: String,
)

/**
 * 当前轮次挂起中的提问卡片状态。
 */
data class PendingQuestionUiState(
    val requestId: String,
    val question: String,
    val options: List<String>,
    val allowFreeText: Boolean,
)

/**
 * 当前轮次挂起中的审批卡片状态。
 */
data class PendingApprovalUiState(
    val requestId: String,
    val toolName: String,
    val summary: String,
    val targetPath: String?,
    val payloadPreview: String?,
)

/**
 * 单个对话线程的窗口级展示状态。
 */
data class ChatConversationUiState(
    val id: String,
    val title: String,
    val workspacePath: String,
    val items: List<ConversationItem> = emptyList(),
    val attachments: List<ChatAttachmentUiState> = emptyList(),
    val history: List<AgentConversationHistoryMessage> = emptyList(),
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val executionState: ExecutionState = ExecutionState.Idle,
    val streamingAssistantItemIndex: Int? = null,
    val streamingReasoningItemIndex: Int? = null,
    val streamingAssistantHistoryIndex: Int? = null,
    val contextUsageFraction: Float = 0.72f,
    val pendingQuestion: PendingQuestionUiState? = null,
    val pendingApproval: PendingApprovalUiState? = null,
) {
    /**
     * 将对话线程折叠为旧的会话状态模型，兼容现有测试和渲染辅助函数。
     */
    fun toConversationState(activeProfileId: String?): ConversationState = ConversationState(
        items = items,
        executionState = executionState,
        activeProfileId = activeProfileId,
        streamingAssistantItemIndex = streamingAssistantItemIndex,
        streamingReasoningItemIndex = streamingReasoningItemIndex,
    )
}

/**
 * 同一工作目录下的对话分组。
 */
data class WorkspaceConversationGroupUiState(
    val workspacePath: String,
    val label: String,
    val conversations: List<ChatConversationUiState>,
)

/**
 * 原型侧栏中的 task 分组。
 */
enum class ChatTaskGroup {
    RUNNING,
    DONE,
}

/**
 * 原型侧栏中的单个 task 展示模型。
 */
data class ChatTaskListItemUiState(
    val id: String,
    val title: String,
    val subtitle: String,
    val stats: String,
    val group: ChatTaskGroup,
)

/**
 * 原型侧栏中的 task 分组展示模型。
 */
data class ChatTaskSectionUiState(
    val group: ChatTaskGroup,
    val title: String,
    val tasks: List<ChatTaskListItemUiState>,
)

/**
 * 整个聊天窗口的 UI 状态。
 */
data class ChatWindowUiState(
    val tasks: List<ChatConversationUiState>,
    val activeTaskId: String,
    val draft: String = "",
    val selectedProfileId: String? = null,
    val permissionPreset: PermissionPreset = PermissionPreset.DEFAULT,
) {
    /**
     * 当前激活的对话线程。
     */
    val activeConversation: ChatConversationUiState
        get() = activeConversationOrNull ?: error("Workspace is not selected.")

    /**
     * 当前激活的对话线程；未选择工作区时返回 null。
     */
    val activeConversationOrNull: ChatConversationUiState?
        get() = tasks.firstOrNull { it.id == activeTaskId }

    /**
     * 与旧测试兼容的活动会话 id 别名。
     */
    val activeConversationId: String
        get() = activeTaskId

    /**
     * 与旧 workspace-first 辅助逻辑兼容的按工作目录分组视图。
     */
    val workspaceGroups: List<WorkspaceConversationGroupUiState>
        get() = tasks
            .groupBy { it.workspacePath }
            .map { (workspacePath, conversations) ->
                WorkspaceConversationGroupUiState(
                    workspacePath = workspacePath,
                    label = buildWorkspaceLabel(workspacePath),
                    conversations = conversations,
                )
            }

    /**
     * 当前激活线程所属的工作目录标签。
     */
    val activeWorkspaceLabel: String
        get() = activeConversationOrNull?.workspacePath?.let(::buildWorkspaceLabel) ?: "请选择工作区"

    /**
     * 原型 task-first 侧栏展示数据。
     */
    val taskSections: List<ChatTaskSectionUiState>
        get() = listOf(
            ChatTaskSectionUiState(
                group = ChatTaskGroup.RUNNING,
                title = "Running",
                tasks = tasks
                    .filter { taskGroupFor(it) == ChatTaskGroup.RUNNING }
                    .map(::toTaskListItem),
            ),
            ChatTaskSectionUiState(
                group = ChatTaskGroup.DONE,
                title = "Done",
                tasks = tasks
                    .filter { taskGroupFor(it) == ChatTaskGroup.DONE }
                    .map(::toTaskListItem),
            ),
        )
}

/**
 * 根据当前会话是否仍在执行，推导原型侧栏中的 task 分组。
 */
internal fun taskGroupFor(conversation: ChatConversationUiState): ChatTaskGroup =
    if (
        conversation.executionState == ExecutionState.Running ||
        conversation.executionState == ExecutionState.WaitingForUserInput ||
        conversation.executionState == ExecutionState.WaitingForApproval ||
        (conversation.items.isEmpty() && conversation.executionState == ExecutionState.Idle)
    ) {
        ChatTaskGroup.RUNNING
    } else {
        ChatTaskGroup.DONE
    }

/**
 * 将真实会话映射为原型侧栏中的 task 列表项。
 */
internal fun toTaskListItem(conversation: ChatConversationUiState): ChatTaskListItemUiState {
    val title = conversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE }
    val subtitle = buildTaskSubtitle(conversation)
    val stats = buildTaskStats(conversation)
    return ChatTaskListItemUiState(
        id = conversation.id,
        title = title,
        subtitle = subtitle,
        stats = stats,
        group = taskGroupFor(conversation),
    )
}

/**
 * 从真实会话中提炼 task 副标题，优先展示最近的用户意图。
 */
internal fun buildTaskSubtitle(conversation: ChatConversationUiState): String =
    conversation.items
        .asReversed()
        .filterIsInstance<ChatMessageItem>()
        .firstOrNull { it.message.role == ChatRole.User }
        ?.message
        ?.content
        ?.lineSequence()
        ?.firstOrNull(String::isNotBlank)
        ?.trim()
        ?.take(TASK_SUBTITLE_MAX_LENGTH)
        ?: conversation.workspacePath

/**
 * 为 task 列表生成轻量统计文案。
 */
internal fun buildTaskStats(conversation: ChatConversationUiState): String = buildString {
    append(conversation.items.size)
    append(" items")
    if (conversation.attachments.isNotEmpty()) {
        append(" · ")
        append(conversation.attachments.size)
        append(" files")
    }
}

private const val TASK_SUBTITLE_MAX_LENGTH = 52
