package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.ConversationState
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.FileDiffPreview
import com.agent.shared.tool.model.QuestionPrompt
import com.agent.shared.tool.model.normalizeQuestionPrompts

/** composer 中有序文件或图片 token 的种类。 */
enum class ChatAttachmentKind {
    FILE_SNAPSHOT,
    IMAGE,
}

/**
 * 附件在 composer 中的展示状态。
 *
 * [token] 是插入到文本框的可见占位符。发送前按 token 在草稿中的位置重建
 * [UserInputPart] 顺序，因此图片、文件和前后文字不会被附件条的显示顺序篡改。
 */
data class ChatAttachmentUiState(
    val path: String,
    val name: String,
    val token: String = "@$name",
    val kind: ChatAttachmentKind = ChatAttachmentKind.FILE_SNAPSHOT,
    val snapshotContent: String? = null,
    val mimeType: String? = null,
    val mediaId: String? = null,
    val imageLabel: String? = null,
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
    val diff: FileDiffPreview? = null,
    /** 当前审批中全部文件的预览；单文件历史仍使用 [diff]。 */
    val diffs: List<FileDiffPreview> = diff?.let(::listOf).orEmpty(),
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
    /** 外层会话树中的直接父会话。 */
    val parentConversationId: String? = null,
    /** fork 创建时所选的源条目。 */
    val forkedFromEntryId: String? = null,
    /** 内部条目树当前活动 leaf。 */
    val activeEntryId: String? = null,
    /** 内部条目树持久主线的末端；普通导航只改变 [activeEntryId]。 */
    val headEntryId: String? = null,
    /** 归档时间；为空表示显示在活跃侧栏。 */
    val archivedAt: Long? = null,
    /** 0 表示旧线性快照，正数表示条目树会话。 */
    val treeFormatVersion: Int = 0,
    /** 会话的完整条目图，包含当前路径以外的兄弟分支。 */
    val entries: List<ConversationEntry> = emptyList(),
    val items: List<ConversationItem> = emptyList(),
    val attachments: List<ChatAttachmentUiState> = emptyList(),
    val history: List<AgentConversationHistoryMessage> = emptyList(),
    /** 此会话绑定的 provider/model profile；为空时按当前配置回退。 */
    val profileId: String? = null,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    /** 此会话独立保存的工具执行权限。 */
    val permissionPreset: PermissionPreset = PermissionPreset.DEFAULT,
    /** 当前等待阶段，仅用于实时展示，不写入会话历史。 */
    val progressMessage: String? = null,
    val executionState: ExecutionState = ExecutionState.Idle,
    val streamingAssistantItemIndex: Int? = null,
    val streamingReasoningItemIndex: Int? = null,
    val streamingAssistantHistoryIndex: Int? = null,
    /** 当前流式助手消息的稳定条目标识。 */
    val streamingAssistantEntryId: String? = null,
    /** 当前流式推理块的稳定条目标识。 */
    val streamingReasoningEntryId: String? = null,
    val contextUsageFraction: Float = 0.72f,
    /** 任务最后被操作的时间戳（毫秒），侧栏"已完成"分组按它倒序展示。 */
    val updatedAt: Long = 0L,
    val pendingQuestion: PendingQuestionUiState? = null,
    val pendingApproval: PendingApprovalUiState? = null,
    /** 显式重新生成标题的瞬时状态；不参与任务执行状态和持久化。 */
    val titleRegenerationInProgress: Boolean = false,
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
    val parentConversationId: String? = null,
    val subtreeUpdatedAt: Long = 0L,
    val treeFormatVersion: Int = 0,
    val titleRegenerationInProgress: Boolean = false,
)

/** 侧栏或设置页中的一棵会话树节点。 */
data class ChatTaskTreeNodeUiState(
    val task: ChatTaskListItemUiState,
    val children: List<ChatTaskTreeNodeUiState>,
    val subtreeUpdatedAt: Long,
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
    /** 不再按运行状态拆段的完整会话森林。 */
    val roots: List<ChatTaskTreeNodeUiState> = emptyList(),
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
    /** composer 当前插入点，用于在 `@`、图片和命令选择时保持 token 顺序。 */
    val draftSelectionStart: Int = draft.length,
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
        get() = activeTasks
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
                tasks = activeTasks
                    .filter { taskGroupFor(it) == ChatTaskGroup.RUNNING }
                    .map(::toTaskListItem),
            ),
            ChatTaskSectionUiState(
                group = ChatTaskGroup.DONE,
                title = "已完成",
                tasks = activeTasks
                    .filter { taskGroupFor(it) == ChatTaskGroup.DONE }
                    .sortedByDescending { it.updatedAt }
                    .map(::toTaskListItem),
            ),
        )

    /**
     * 供侧栏使用的工作区优先分组视图。
     */
    val workspaceTaskSections: List<WorkspaceTaskSectionUiState>
        get() = activeTasks
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
                    roots = buildConversationForest(conversations),
                )
            }

    /** 当前未归档的全部会话。 */
    val activeTasks: List<ChatConversationUiState>
        get() = tasks.filter { it.archivedAt == null }

    /** 设置页中管理的全部归档会话。 */
    val archivedTasks: List<ChatConversationUiState>
        get() = tasks.filter { it.archivedAt != null }

    /** 设置页按工作区展示的归档会话森林。 */
    val archivedWorkspaceTaskSections: List<WorkspaceTaskSectionUiState>
        get() = archivedTasks
            .groupBy { it.workspacePath }
            .map { (workspacePath, conversations) ->
                WorkspaceTaskSectionUiState(
                    workspacePath = workspacePath,
                    label = buildWorkspaceLabel(workspacePath, conversations.firstOrNull()?.workspaceName),
                    sections = emptyList(),
                    roots = buildConversationForest(conversations),
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
internal fun taskStatusFor(conversation: ChatConversationUiState): ChatTaskStatus {
    val isBlankPlaceholder = conversation.isConversationContentEmpty() &&
            (
                    conversation.executionState == ExecutionState.Idle ||
                            conversation.title == DEFAULT_CONVERSATION_TITLE
                    )
    return when {
        isBlankPlaceholder -> ChatTaskStatus.NEW
        taskGroupFor(conversation) == ChatTaskGroup.RUNNING -> ChatTaskStatus.RUNNING
        else -> ChatTaskStatus.DONE
    }
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
        parentConversationId = conversation.parentConversationId,
        subtreeUpdatedAt = conversation.updatedAt,
        treeFormatVersion = conversation.treeFormatVersion,
        titleRegenerationInProgress = conversation.titleRegenerationInProgress,
    )
}

/**
 * 将同一视图中的会话组装为森林；缺失父节点会自然提升为根，整个子树按最近活动时间倒序。
 */
internal fun buildConversationForest(
    conversations: List<ChatConversationUiState>,
): List<ChatTaskTreeNodeUiState> {
    val byId = conversations.associateBy(ChatConversationUiState::id)
    val childrenByParent = conversations
        .filter { conversation -> conversation.parentConversationId in byId }
        .groupBy(ChatConversationUiState::parentConversationId)

    fun buildNode(conversation: ChatConversationUiState, ancestors: Set<String>): ChatTaskTreeNodeUiState {
        val nextAncestors = ancestors + conversation.id
        val children = childrenByParent[conversation.id]
            .orEmpty()
            .filterNot { child -> child.id in nextAncestors }
            .map { child -> buildNode(child, nextAncestors) }
            .sortedByDescending(ChatTaskTreeNodeUiState::subtreeUpdatedAt)
        val subtreeUpdatedAt = maxOf(conversation.updatedAt, children.maxOfOrNull { it.subtreeUpdatedAt } ?: 0L)
        return ChatTaskTreeNodeUiState(
            task = toTaskListItem(conversation).copy(subtreeUpdatedAt = subtreeUpdatedAt),
            children = children,
            subtreeUpdatedAt = subtreeUpdatedAt,
        )
    }

    return conversations
        .filter { conversation -> conversation.parentConversationId !in byId }
        .map { conversation -> buildNode(conversation, emptySet()) }
        .sortedByDescending(ChatTaskTreeNodeUiState::subtreeUpdatedAt)
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
