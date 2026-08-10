package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ConversationState
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.QuestionPrompt
import com.agent.shared.tool.model.normalizeQuestionPrompts

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
    val question: String = "",
    val options: List<String> = emptyList(),
    val questions: List<QuestionPrompt> = emptyList(),
    val allowFreeText: Boolean,
) {
    /**
     * 返回批量题目；旧状态会按原有单题字段回退。
     */
    val effectiveQuestions: List<QuestionPrompt>
        get() = normalizeQuestionPrompts(
            questions.ifEmpty { listOf(QuestionPrompt(question = question, options = options)) },
        )
}

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
 * 会话标题的异步生成生命周期；该状态只影响当前窗口展示，不改变持久化标题。
 */
enum class ConversationTitleState {
    NOT_REQUESTED,
    GENERATING,
    GENERATED,
    FAILED,
}

/**
 * 单个对话线程的窗口级展示状态。
 */
data class ChatConversationUiState(
    val id: String,
    val title: String,
    val titleState: ConversationTitleState = ConversationTitleState.NOT_REQUESTED,
    val workspacePath: String,
    /** 用户设置的工作区显示名；为空时使用路径末级名称。 */
    val workspaceName: String? = null,
    /** 解除关联前的工作目录；空路径历史可借此在重新选择同一路径时恢复。 */
    val detachedWorkspacePath: String? = null,
    /** 解除关联前的工作区显示名。 */
    val detachedWorkspaceName: String? = null,
    val items: List<ConversationItem> = emptyList(),
    val attachments: List<ChatAttachmentUiState> = emptyList(),
    val history: List<AgentConversationHistoryMessage> = emptyList(),
    /** 此会话绑定的 provider/model profile；为空时按当前配置回退。 */
    val profileId: String? = null,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    /** 此会话独立保存的工具执行权限。 */
    val permissionPreset: PermissionPreset = PermissionPreset.DEFAULT,
    val executionState: ExecutionState = ExecutionState.Idle,
    val streamingAssistantItemIndex: Int? = null,
    val streamingReasoningItemIndex: Int? = null,
    val streamingAssistantHistoryIndex: Int? = null,
    val contextUsageFraction: Float = 0.72f,
    /** 任务最后被操作的时间戳（毫秒），侧栏"已完成"分组按它倒序展示。 */
    val updatedAt: Long = 0L,
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
 * 侧栏任务右侧的即时状态标识。
 */
enum class ChatTaskStatus {
    NEW,
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
    val group: ChatTaskGroup,
    val status: ChatTaskStatus,
    val titleState: ConversationTitleState,
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
 * 侧栏中的工作区分组，内部再按任务状态拆分。
 */
data class WorkspaceTaskSectionUiState(
    val workspacePath: String,
    val label: String,
    val sections: List<ChatTaskSectionUiState>,
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
    val persistenceErrorMessage: String? = null,
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
            .filter { it.workspacePath.isNotBlank() }
            .groupBy { it.workspacePath }
            .map { (workspacePath, conversations) ->
                WorkspaceConversationGroupUiState(
                    workspacePath = workspacePath,
                    label = buildWorkspaceLabel(workspacePath, conversations.firstOrNull()?.workspaceName),
                    conversations = conversations,
                )
            }

    /**
     * 当前激活线程所属的工作目录标签。
     */
    val activeWorkspaceLabel: String
        get() = activeConversationOrNull?.let { conversation ->
            buildWorkspaceLabel(conversation.workspacePath, conversation.workspaceName)
        } ?: "请选择工作区"

    /**
     * 原型 task-first 侧栏展示数据。
     */
    val taskSections: List<ChatTaskSectionUiState>
        get() = listOf(
            ChatTaskSectionUiState(
                group = ChatTaskGroup.RUNNING,
                title = "进行中",
                tasks = tasks
                    .filter { taskGroupFor(it) == ChatTaskGroup.RUNNING }
                    .map(::toTaskListItem),
            ),
            ChatTaskSectionUiState(
                group = ChatTaskGroup.DONE,
                title = "已完成",
                tasks = tasks
                    .filter { taskGroupFor(it) == ChatTaskGroup.DONE }
                    .sortedByDescending { it.updatedAt }
                    .map(::toTaskListItem),
            ),
        )

    /**
     * 供侧栏使用的工作区优先分组视图。
     */
    val workspaceTaskSections: List<WorkspaceTaskSectionUiState>
        get() = tasks
            .filter { it.workspacePath.isNotBlank() }
            .groupBy { it.workspacePath }
            .map { (workspacePath, conversations) ->
                WorkspaceTaskSectionUiState(
                    workspacePath = workspacePath,
                    label = buildWorkspaceLabel(workspacePath, conversations.firstOrNull()?.workspaceName),
                    sections = listOf(
                        ChatTaskSectionUiState(
                            group = ChatTaskGroup.RUNNING,
                            title = "进行中",
                            tasks = conversations
                                .filter { taskGroupFor(it) == ChatTaskGroup.RUNNING }
                                .map(::toTaskListItem),
                        ),
                        ChatTaskSectionUiState(
                            group = ChatTaskGroup.DONE,
                            title = "已完成",
                            tasks = conversations
                                .filter { taskGroupFor(it) == ChatTaskGroup.DONE }
                                .sortedByDescending { it.updatedAt }
                                .map(::toTaskListItem),
                        ),
                    ),
                )
            }
}

/**
 * 判断当前执行状态是否可被 composer 停止，覆盖运行、等待输入和等待审批。
 */
internal fun ExecutionState.isStoppable(): Boolean =
    this == ExecutionState.Running ||
            this == ExecutionState.WaitingForUserInput ||
            this == ExecutionState.WaitingForApproval

/**
 * 将工作目录映射为侧栏分组标题。
 */
internal fun buildWorkspaceLabel(path: String, workspaceName: String? = null): String =
    workspaceName?.trim()?.takeIf(String::isNotBlank)
        ?: path.trimEnd('\\', '/').substringAfterLast('\\').substringAfterLast('/')
            .ifBlank { "未关联历史" }

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
 * 新建空白会话使用虚线占位标识，执行中与完成态使用各自的状态标识。
 */
internal fun taskStatusFor(conversation: ChatConversationUiState): ChatTaskStatus = when {
    conversation.isEmptyDefaultConversation() -> ChatTaskStatus.NEW
    taskGroupFor(conversation) == ChatTaskGroup.RUNNING -> ChatTaskStatus.RUNNING
    else -> ChatTaskStatus.DONE
}

/**
 * 将真实会话映射为原型侧栏中的 task 列表项。
 */
internal fun toTaskListItem(conversation: ChatConversationUiState): ChatTaskListItemUiState {
    val title = conversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE }
    val subtitle = buildTaskSubtitle(conversation)
    return ChatTaskListItemUiState(
        id = conversation.id,
        title = title,
        subtitle = subtitle,
        group = taskGroupFor(conversation),
        status = taskStatusFor(conversation),
        titleState = conversation.titleState,
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
        ?: buildWorkspaceLabel(conversation.workspacePath, conversation.workspaceName)

private const val TASK_SUBTITLE_MAX_LENGTH = 52
