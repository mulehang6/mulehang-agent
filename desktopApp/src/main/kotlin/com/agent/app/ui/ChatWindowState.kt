package com.agent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.agent.AgentConversationHistoryMessage
import com.agent.shared.agent.AgentConversationHistoryPart
import com.agent.shared.agent.AgentRunRequest
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelCapabilitiesResolver
import com.agent.shared.state.AppError
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ConversationItem
import com.agent.shared.state.ConversationState
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.PermissionPreset
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
 * 窗口级状态持有者，负责多会话、composer 控件和流式消息归并。
 */
class ChatWindowState(
    private val sendMessageUseCase: SendMessageUseCase,
    snapshot: AppSessionSnapshot,
    projectPath: String = "",
    private val toolInteractionCoordinator: DesktopToolInteractionCoordinator = DesktopToolInteractionCoordinator(),
    private val onWorkspaceSelected: (String) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeRunJob: Job? = null
    private var activeRunConversationId: String? = null
    private var pendingQuestionConversationId: String? = null
    private var pendingApprovalConversationId: String? = null
    private var snapshot by mutableStateOf(snapshot)

    /**
     * 当前窗口可选的全部 profile。
     */
    val availableProfiles: List<ConfigProfile>
        get() = snapshot.profiles

    /**
     * 当前窗口的完整 UI 状态。
     */
    var ui by mutableStateOf(
        initialUiState(snapshot = snapshot, projectPath = projectPath),
    )
        private set

    /**
     * 兼容旧渲染逻辑的活动会话状态投影。
     */
    val state: ConversationState
        get() = ui.activeConversation.toConversationState(activeProfile?.id)

    /**
     * 当前激活 profile。
     */
    val activeProfile: ConfigProfile?
        get() = snapshot.profiles.firstOrNull { it.id == ui.selectedProfileId } ?: snapshot.activeProfile

    /**
     * 当前失败状态对应的 UI 可见错误文本。
     */
    val errorMessage: String?
        get() = (ui.activeConversationOrNull?.executionState as? ExecutionState.Failed)?.error?.let { error ->
            "${error.title}: ${error.message}"
        }

    /**
     * 更新配置快照，但保留已有工作区、会话和输入状态。
     */
    fun updateSessionSnapshot(snapshot: AppSessionSnapshot) {
        this.snapshot = snapshot
        val selectedProfileId = ui.selectedProfileId
            ?.takeIf { profileId -> snapshot.profiles.any { it.id == profileId } }
            ?: snapshot.activeProfile?.id
            ?: snapshot.profiles.firstOrNull()?.id
        val selectedProfile = snapshot.profiles.firstOrNull { it.id == selectedProfileId } ?: snapshot.activeProfile
        ui = ui.copy(
            selectedProfileId = selectedProfileId,
            tasks = ui.tasks.map { conversation ->
                conversation.withRecalculatedContextUsage(selectedProfile?.let(::contextWindowFor))
            },
        )
    }

    /**
     * 更新当前输入框草稿。
     */
    fun updateDraft(value: String) {
        ui = ui.copy(draft = value)
    }

    /**
     * 切换当前激活对话。
     */
    fun selectConversation(conversationId: String) {
        if (findConversationOrNull(conversationId) != null) {
            ui = ui.copy(activeTaskId = conversationId)
        }
    }

    /**
     * 在指定工作目录下新建对话并切换焦点。
     */
    fun createConversationForWorkspace(workspacePath: String) {
        onWorkspaceSelected(workspacePath)
        val conversation = newConversation(workspacePath, activeContextWindow())
        val updatedTasks = if (shouldReplaceActiveEmptyConversation(workspacePath)) {
            ui.tasks.map { existing ->
                if (existing.id == ui.activeTaskId) {
                    conversation
                } else {
                    existing
                }
            }
        } else {
            listOf(conversation) + ui.tasks
        }
        ui = ui.copy(
            tasks = updatedTasks,
            activeTaskId = conversation.id,
            draft = "",
        )
    }

    /**
     * 为当前会话挂载附件。
     */
    fun attachFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        mutateActiveConversation { conversation ->
            val attachments = conversation.attachments + paths.map { path ->
                ChatAttachmentUiState(
                    path = path,
                    name = path.substringAfterLast('\\').substringAfterLast('/'),
                )
            }
            conversation.copy(
                attachments = attachments.distinctBy { it.path },
                contextUsageFraction = estimateContextUsage(
                    items = conversation.items,
                    attachmentCount = attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        }
    }

    /**
     * 调整当前会话的权限档位。
     */
    fun updatePermission(permissionPreset: PermissionPreset) {
        ui = ui.copy(permissionPreset = permissionPreset)
    }

    /**
     * 切换当前 profile。
     */
    fun selectProfile(profileId: String) {
        val selectedProfile = snapshot.profiles.firstOrNull { it.id == profileId }
        if (selectedProfile != null) {
            ui = ui.copy(
                selectedProfileId = profileId,
                tasks = ui.tasks.map { conversation ->
                    conversation.withRecalculatedContextUsage(contextWindowFor(selectedProfile))
                },
            )
        }
    }

    /**
     * 调整当前活动会话的推理强度档位。
     */
    fun updateReasoningEffort(reasoningEffort: ReasoningEffort) {
        mutateActiveConversation { conversation ->
            conversation.copy(reasoningEffort = reasoningEffort)
        }
    }

    /**
     * 兼容旧调用方式的直接发送入口。
     */
    fun send(message: String) {
        updateDraft(message)
        sendDraft()
    }

    /**
     * 取消当前正在执行的轮次，并恢复到可继续输入的空闲态。
     */
    fun cancelActiveRun() {
        activeRunJob?.cancel()
        activeRunJob = null
        clearPendingOwnership(ui.activeTaskId)
        mutateActiveConversation { conversation ->
            if (conversation.executionState.isStoppable()) {
                conversation.copy(
                    executionState = ExecutionState.Idle,
                    pendingQuestion = null,
                    pendingApproval = null,
                )
            } else {
                conversation
            }
        }
    }

    /**
     * 回答当前挂起问题，并恢复同一轮 agent 执行。
     */
    fun answerPendingQuestion(answer: String) {
        if (!toolInteractionCoordinator.submitQuestion(answer)) return
        val targetConversationId = resolvePendingQuestionConversationId() ?: return
        pendingQuestionConversationId = null
        mutateConversation(targetConversationId) { conversation ->
            conversation.copy(
                pendingQuestion = null,
                executionState = ExecutionState.Running,
            )
        }
    }

    /**
     * 提交当前挂起审批，并恢复同一轮 agent 执行。
     */
    fun answerPendingApproval(approved: Boolean) {
        if (!toolInteractionCoordinator.submitApproval(approved)) return
        val targetConversationId = resolvePendingApprovalConversationId() ?: return
        pendingApprovalConversationId = null
        mutateConversation(targetConversationId) { conversation ->
            conversation.copy(
                pendingApproval = null,
                executionState = ExecutionState.Running,
            )
        }
    }

    /**
     * 发送当前草稿，并把流式结果归入当前活动会话。
     */
    fun sendDraft() {
        val prompt = ui.draft.trim()
        if (prompt.isBlank()) return

        if (ui.activeConversationOrNull == null) {
            ui = ui.copy(draft = prompt)
            return
        }

        val targetConversationId = ui.activeTaskId
        if (activeRunConversationId != null) {
            mutateConversation(targetConversationId) { conversation ->
                conversation.copy(
                    executionState = ExecutionState.Failed(
                        AppError(
                            title = "已有任务在执行",
                            message = "请等待当前任务完成，或先停止当前任务再启动新的 task。",
                        ),
                    ),
                )
            }
            return
        }

        val profile = activeProfile
        if (profile == null) {
            mutateActiveConversation { conversation ->
                conversation.copy(
                    executionState = ExecutionState.Failed(
                        AppError(
                            title = "缺少可用配置",
                            message = "请先在 settings.json 中配置并启用至少一个 profile。",
                        ),
                    ),
                )
            }
            return
        }

        val sourceConversation = findConversation(targetConversationId)
        val requestHistory = sourceConversation.history
        val reasoningEffort = supportedReasoningEffort(
            profile = profile,
            conversation = sourceConversation,
        )
        mutateConversation(targetConversationId) { conversation ->
            val nextItems = conversation.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt))
            conversation.copy(
                title = conversation.title.takeUnless { it == DEFAULT_CONVERSATION_TITLE }
                    ?: buildConversationTitle(prompt),
                items = nextItems,
                attachments = emptyList(),
                history = conversation.history + AgentConversationHistoryMessage.User(content = prompt),
                executionState = ExecutionState.Running,
                streamingAssistantItemIndex = null,
                streamingReasoningItemIndex = null,
                streamingAssistantHistoryIndex = null,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = 0,
                    contextWindow = contextWindowFor(profile),
                ),
            )
        }
        ui = ui.copy(draft = "")

        activeRunConversationId = targetConversationId
        activeRunJob = scope.launch {
            try {
                sendMessageUseCase(
                    AgentRunRequest(
                        prompt = prompt,
                        profile = profile,
                        history = requestHistory,
                        reasoningEffort = reasoningEffort,
                        workspacePath = sourceConversation.workspacePath,
                        permissionPreset = ui.permissionPreset,
                    ),
                ).collect { event ->
                    applyAgentEvent(targetConversationId, event)
                }
            } catch (_: CancellationException) {
                clearPendingOwnership(targetConversationId)
                mutateConversation(targetConversationId) { conversation ->
                    if (conversation.executionState.isStoppable()) {
                        conversation.copy(
                            executionState = ExecutionState.Idle,
                            pendingQuestion = null,
                            pendingApproval = null,
                        )
                    } else {
                        conversation
                    }
                }
            } catch (exception: Exception) {
                mutateConversation(targetConversationId) { conversation ->
                    val reason = exception.message ?: "执行过程中发生未知错误。"
                    val withToolFailure = attachFailureToTimeline(conversation, reason)
                    withToolFailure.copy(
                        executionState = ExecutionState.Failed(
                            AppError(
                                title = "发送失败",
                                message = reason,
                            ),
                        ),
                    )
                }
            } finally {
                if (activeRunConversationId == targetConversationId) {
                    activeRunConversationId = null
                }
                activeRunJob = null
            }
        }
    }

    /**
     * 查找指定对话，供测试或布局辅助调用。
     */
    fun findConversation(conversationId: String): ChatConversationUiState =
        findConversationOrNull(conversationId)
            ?: error("Conversation $conversationId not found.")

    /**
     * 仅当当前 profile 支持所选档位时才将 reasoning effort 送入执行链路。
     */
    private fun supportedReasoningEffort(
        profile: ConfigProfile,
        conversation: ChatConversationUiState,
    ): ReasoningEffort? {
        val capabilities = ModelCapabilitiesResolver.resolve(profile)
        val variants = capabilities.variants.values
        return conversation.reasoningEffort.takeIf { effort ->
            variants.any { variant -> variant.reasoningEffort == effort }
        } ?: capabilities.defaultReasoningEffort
    }

    /**
     * 当前 profile 的上下文窗口；显式配置优先，其次使用 provider/model 默认能力。
     */
    private fun activeContextWindow(): Int? = activeProfile?.let(::contextWindowFor)

    /**
     * 解析指定 profile 的上下文窗口。
     */
    private fun contextWindowFor(profile: ConfigProfile): Int? =
        resolveContextWindow(profile)

    /**
     * 将 agent 事件应用到指定活动会话。
     */
    private fun applyAgentEvent(conversationId: String, event: AgentStreamEvent) {
        when (event) {
            AgentStreamEvent.Started -> mutateConversation(conversationId) { conversation ->
                conversation.copy(executionState = ExecutionState.Running)
            }

            is AgentStreamEvent.TextDelta -> mutateConversation(conversationId) { conversation ->
                appendAssistantTextHistory(
                    appendAssistantDelta(conversation, event.text),
                    event.text,
                )
            }

            is AgentStreamEvent.ToolCallStarted -> mutateConversation(conversationId) { conversation ->
                appendAssistantToolCallHistory(
                    appendToolEvent(
                        conversation = conversation,
                        toolName = event.name,
                        status = ToolEventStatus.Started,
                        preview = event.argumentsPreview,
                    ),
                    id = event.toolCallId,
                    name = event.name,
                    argumentsPreview = event.argumentsPreview,
                )
            }

            is AgentStreamEvent.ToolCallFinished -> mutateConversation(conversationId) { conversation ->
                appendAssistantToolResultHistory(
                    appendToolEvent(
                        conversation = conversation,
                        toolName = event.name,
                        status = ToolEventStatus.Finished,
                        preview = event.resultPreview,
                    ),
                    id = event.toolCallId,
                    name = event.name,
                    resultPreview = event.resultPreview,
                )
            }

            is AgentStreamEvent.QuestionRequested -> mutateConversation(conversationId) { conversation ->
                pendingQuestionConversationId = conversationId
                pendingApprovalConversationId = null
                conversation.copy(
                    pendingQuestion = PendingQuestionUiState(
                        requestId = event.request.requestId,
                        question = event.request.question,
                        options = event.request.options,
                        allowFreeText = event.request.allowFreeText,
                    ),
                    pendingApproval = null,
                    executionState = ExecutionState.WaitingForUserInput,
                )
            }

            is AgentStreamEvent.ApprovalRequested -> mutateConversation(conversationId) { conversation ->
                pendingApprovalConversationId = conversationId
                pendingQuestionConversationId = null
                conversation.copy(
                    pendingApproval = PendingApprovalUiState(
                        requestId = event.request.requestId,
                        toolName = event.request.toolName,
                        summary = event.request.summary,
                        targetPath = event.request.targetPath,
                        payloadPreview = event.request.payloadPreview,
                    ),
                    pendingQuestion = null,
                    executionState = ExecutionState.WaitingForApproval,
                )
            }

            is AgentStreamEvent.Status -> mutateConversation(conversationId) { conversation ->
                appendToolEvent(
                    conversation = conversation,
                    toolName = "status",
                    status = ToolEventStatus.Status,
                    preview = event.message,
                )
            }

            is AgentStreamEvent.ReasoningDelta -> mutateConversation(conversationId) { conversation ->
                appendAssistantReasoningHistory(
                    appendReasoningDelta(
                        conversation = conversation,
                        summary = event.summary,
                        rawText = event.rawText,
                    ),
                    summary = event.summary,
                    rawText = event.rawText,
                )
            }

            is AgentStreamEvent.ReasoningCompleted -> mutateConversation(conversationId) { conversation ->
                completeAssistantReasoningHistory(
                    completeReasoning(
                        conversation = conversation,
                        summary = event.summary,
                        rawText = event.rawText,
                    ),
                    summary = event.summary,
                    rawText = event.rawText,
                )
            }

            is AgentStreamEvent.Completed -> mutateConversation(conversationId) { conversation ->
                clearPendingOwnership(conversationId)
                completeAssistantMessage(conversation, event.text).copy(
                    pendingQuestion = null,
                    pendingApproval = null,
                )
            }

            is AgentStreamEvent.Failed -> mutateConversation(conversationId) { conversation ->
                clearPendingOwnership(conversationId)
                val closedConversation = closeStreamingReasoning(conversation)
                val withToolFailure = attachFailureToTimeline(closedConversation, event.reason)
                withToolFailure.copy(
                    executionState = ExecutionState.Failed(
                        AppError(
                            title = "Agent 执行失败",
                            message = event.reason,
                        ),
                    ),
                    streamingAssistantItemIndex = null,
                    streamingReasoningItemIndex = null,
                    streamingAssistantHistoryIndex = null,
                    pendingQuestion = null,
                    pendingApproval = null,
                )
            }
        }
    }

    /**
     * 在指定对话上执行原子更新。
     */
    private fun mutateConversation(
        conversationId: String,
        transform: (ChatConversationUiState) -> ChatConversationUiState,
    ) {
        ui = ui.copy(
            tasks = ui.tasks.map { conversation ->
                if (conversation.id == conversationId) {
                    transform(conversation)
                } else {
                    conversation
                }
            },
        )
    }

    /**
     * 更新当前活动会话。
     */
    private fun mutateActiveConversation(transform: (ChatConversationUiState) -> ChatConversationUiState) {
        mutateConversation(ui.activeTaskId, transform)
    }

    /**
     * 找到当前挂起问题所属的会话；记录缺失时退回到真正挂起该问题的线程。
     */
    private fun resolvePendingQuestionConversationId(): String? =
        pendingQuestionConversationId
            ?: ui.tasks.firstOrNull { it.pendingQuestion != null }?.id

    /**
     * 找到当前挂起审批所属的会话；记录缺失时退回到真正挂起该审批的线程。
     */
    private fun resolvePendingApprovalConversationId(): String? =
        pendingApprovalConversationId
            ?: ui.tasks.firstOrNull { it.pendingApproval != null }?.id

    /**
     * 当指定会话结束或失败后，清理挂起请求的归属记录。
     */
    private fun clearPendingOwnership(conversationId: String) {
        if (pendingQuestionConversationId == conversationId) {
            pendingQuestionConversationId = null
        }
        if (pendingApprovalConversationId == conversationId) {
            pendingApprovalConversationId = null
        }
    }

    /**
     * 判断新建会话时是否应覆盖当前空白占位会话，避免侧栏出现两个“新对话”。
     */
    private fun shouldReplaceActiveEmptyConversation(workspacePath: String): Boolean {
        val activeConversation = ui.activeConversationOrNull ?: return false
        return activeConversation.workspacePath == workspacePath && activeConversation.isEmptyDefaultConversation()
    }

    /**
     * 查找指定对话，如果不存在则返回空。
     */
    private fun findConversationOrNull(conversationId: String): ChatConversationUiState? =
        ui.tasks.firstOrNull { it.id == conversationId }

    /**
     * 将文本增量拼接到当前正在生成的助手消息。
     */
    private fun appendAssistantDelta(
        conversation: ChatConversationUiState,
        delta: String,
    ): ChatConversationUiState {
        if (delta.isEmpty()) {
            return conversation
        }
        val normalizedConversation = closeStreamingReasoning(conversation)
        val currentIndex = normalizedConversation.streamingAssistantItemIndex
        return if (currentIndex == null) {
            val nextItems = normalizedConversation.items + ChatMessageItem(ChatMessage(ChatRole.Assistant, delta))
            normalizedConversation.copy(
                items = nextItems,
                streamingAssistantItemIndex = normalizedConversation.items.size,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = normalizedConversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        } else {
            val existingItem =
                normalizedConversation.items[currentIndex] as? ChatMessageItem ?: return normalizedConversation
            val updatedItems = normalizedConversation.items.toMutableList()
            updatedItems[currentIndex] = existingItem.copy(
                message = existingItem.message.copy(content = existingItem.message.content + delta),
            )
            normalizedConversation.copy(
                items = updatedItems,
                contextUsageFraction = estimateContextUsage(
                    items = updatedItems,
                    attachmentCount = normalizedConversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        }
    }

    /**
     * 将工具或状态事件追加到时间线。
     */
    private fun appendToolEvent(
        conversation: ChatConversationUiState,
        toolName: String,
        status: ToolEventStatus,
        preview: String?,
    ): ChatConversationUiState {
        val normalizedConversation = closeStreamingReasoning(conversation)
        val nextItems = normalizedConversation.items + ToolEventItem(
            toolName = toolName,
            status = status,
            preview = preview,
        )
        return normalizedConversation.copy(
            items = nextItems,
            contextUsageFraction = estimateContextUsage(
                items = nextItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = activeContextWindow(),
            ),
        )
    }

    /**
     * 将 agent 失败原因就地附加到时间线中最后一个尚未被 Finished 闭合的 Started 工具事件；
     * 如果不存在这样的工具事件，则追加一条独立的 Failed 工具事件，
     * 使错误信息在工具事件卡片内展示而非面板顶部。
     *
     * 通过检查最后一个 Started 事件之后是否存在 Finished 事件来避免误伤历史轮次
     * 中已经成功完成的工具调用。
     */
    private fun attachFailureToTimeline(
        conversation: ChatConversationUiState,
        reason: String,
    ): ChatConversationUiState {
        val lastStartedIndex = conversation.items.indexOfLast { item ->
            item is ToolEventItem && item.status == ToolEventStatus.Started
        }
        val hasFinishedAfterLastStarted = lastStartedIndex >= 0 && conversation.items
            .drop(lastStartedIndex + 1)
            .any { it is ToolEventItem && it.status == ToolEventStatus.Finished }
        return if (lastStartedIndex >= 0 && !hasFinishedAfterLastStarted) {
            val updatedItems = conversation.items.toMutableList()
            val started = updatedItems[lastStartedIndex] as ToolEventItem
            updatedItems[lastStartedIndex] = started.copy(
                status = ToolEventStatus.Failed,
                errorMessage = reason,
            )
            conversation.copy(items = updatedItems)
        } else {
            val nextItems = conversation.items + ToolEventItem(
                toolName = "error",
                status = ToolEventStatus.Failed,
                errorMessage = reason,
            )
            conversation.copy(
                items = nextItems,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = conversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        }
    }

    /**
     * 将思考增量拼接到当前思考块。
     */
    private fun appendReasoningDelta(
        conversation: ChatConversationUiState,
        summary: String?,
        rawText: String?,
    ): ChatConversationUiState {
        if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) {
            return conversation
        }
        val currentIndex = conversation.streamingReasoningItemIndex
        return if (currentIndex == null) {
            val nextItems = conversation.items + ReasoningItem(
                summaryText = summary,
                rawText = rawText ?: summary,
                expanded = true,
                isStreaming = true,
            )
            conversation.copy(
                items = nextItems,
                streamingReasoningItemIndex = conversation.items.size,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = conversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        } else {
            val existingItem = conversation.items[currentIndex] as? ReasoningItem ?: return conversation
            val updatedItems = conversation.items.toMutableList()
            updatedItems[currentIndex] = existingItem.copy(
                summaryText = existingItem.summaryText.orEmpty().appendNullable(summary),
                rawText = existingItem.rawText.orEmpty().appendNullable(rawText ?: summary),
                expanded = true,
                isStreaming = true,
            )
            conversation.copy(items = updatedItems)
        }
    }

    /**
     * 收到 reasoning 完整事件后收尾当前思考块。
     */
    private fun completeReasoning(
        conversation: ChatConversationUiState,
        summary: String?,
        rawText: String?,
    ): ChatConversationUiState {
        val currentIndex = conversation.streamingReasoningItemIndex
            ?: conversation.items.indexOfLast { item -> item is ReasoningItem }.takeIf { it >= 0 }
            ?: run {
                val nextItems = conversation.items + ReasoningItem(
                    summaryText = summary,
                    rawText = rawText ?: summary,
                    expanded = true,
                    isStreaming = false,
                )
                return conversation.copy(
                    items = nextItems,
                    contextUsageFraction = estimateContextUsage(
                        items = nextItems,
                        attachmentCount = conversation.attachments.size,
                        contextWindow = activeContextWindow(),
                    ),
                )
            }
        val existingItem = conversation.items[currentIndex] as? ReasoningItem ?: return conversation
        val updatedItems = conversation.items.toMutableList()
        updatedItems[currentIndex] = existingItem.copy(
            summaryText = summary ?: existingItem.summaryText,
            rawText = rawText ?: existingItem.rawText,
            expanded = true,
            isStreaming = false,
        )
        return conversation.copy(
            items = updatedItems,
            streamingReasoningItemIndex = null,
        )
    }

    /**
     * 在完成时补齐最终正文，并清理流式状态。
     */
    private fun completeAssistantMessage(
        conversation: ChatConversationUiState,
        finalText: String,
    ): ChatConversationUiState {
        val normalizedConversation = closeStreamingReasoning(conversation)
        val currentIndex = normalizedConversation.streamingAssistantItemIndex ?: run {
            val nextItems = appendCompletedAssistantIfNeeded(normalizedConversation.items, finalText)
            return finalizeAssistantTextHistory(normalizedConversation, finalText).copy(
                items = nextItems,
                executionState = ExecutionState.Idle,
                streamingAssistantItemIndex = null,
                streamingAssistantHistoryIndex = null,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = normalizedConversation.attachments.size,
                    contextWindow = activeContextWindow(),
                ),
            )
        }
        val existingItem =
            normalizedConversation.items[currentIndex] as? ChatMessageItem ?: return normalizedConversation.copy(
                executionState = ExecutionState.Idle,
                streamingAssistantItemIndex = null,
                streamingAssistantHistoryIndex = null,
            )
        val finalizedItem = if (finalText.isNotBlank() && existingItem.message.content != finalText) {
            existingItem.copy(
                message = existingItem.message.copy(content = finalText),
            )
        } else {
            existingItem
        }
        val updatedItems = normalizedConversation.items.toMutableList().apply {
            if (currentIndex == lastIndex) {
                this[currentIndex] = finalizedItem
            } else {
                removeAt(currentIndex)
                add(finalizedItem)
            }
        }
        return finalizeAssistantTextHistory(normalizedConversation, finalText).copy(
            items = updatedItems,
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
            streamingAssistantHistoryIndex = null,
            contextUsageFraction = estimateContextUsage(
                items = updatedItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = activeContextWindow(),
            ),
        )
    }

    /**
     * 当底层只返回完成文本时补一条助手消息。
     */
    private fun appendCompletedAssistantIfNeeded(
        items: List<ConversationItem>,
        finalText: String,
    ): List<ConversationItem> {
        if (finalText.isBlank()) {
            return items
        }
        return items + ChatMessageItem(ChatMessage(ChatRole.Assistant, finalText))
    }

    /**
     * 在进入工具或正文阶段前关闭仍处于流式中的思考块。
     */
    private fun closeStreamingReasoning(source: ChatConversationUiState): ChatConversationUiState {
        val reasoningIndex = source.streamingReasoningItemIndex ?: return source
        val reasoningItem = source.items[reasoningIndex] as? ReasoningItem ?: return source.copy(
            streamingReasoningItemIndex = null,
        )
        if (!reasoningItem.isStreaming) {
            return source.copy(streamingReasoningItemIndex = null)
        }
        val updatedItems = source.items.toMutableList()
        updatedItems[reasoningIndex] = reasoningItem.copy(isStreaming = false, expanded = true)
        return source.copy(
            items = updatedItems,
            streamingReasoningItemIndex = null,
        )
    }
}

/**
 * 使用指定上下文窗口重算会话占比。
 */
private fun ChatConversationUiState.withRecalculatedContextUsage(contextWindow: Int?): ChatConversationUiState =
    copy(
        contextUsageFraction = estimateContextUsage(
            items = items,
            attachmentCount = attachments.size,
            contextWindow = contextWindow,
        ),
    )

/**
 * 基于当前项目路径和 profile 快照建立初始 UI 状态。
 */
private fun initialUiState(
    snapshot: AppSessionSnapshot,
    projectPath: String,
): ChatWindowUiState {
    if (projectPath.isBlank()) {
        return ChatWindowUiState(
            tasks = emptyList(),
            activeTaskId = "",
            selectedProfileId = snapshot.activeProfile?.id ?: snapshot.profiles.firstOrNull()?.id,
        )
    }
    val initialConversation = newConversation(
        workspacePath = projectPath,
        contextWindow = snapshot.activeProfile?.let(::resolveContextWindow),
    )
    return ChatWindowUiState(
        tasks = listOf(initialConversation),
        activeTaskId = initialConversation.id,
        selectedProfileId = snapshot.activeProfile?.id ?: snapshot.profiles.firstOrNull()?.id,
    )
}

/**
 * 创建一条空白会话。
 */
private fun newConversation(
    workspacePath: String,
    contextWindow: Int?,
): ChatConversationUiState = ChatConversationUiState(
    id = UUID.randomUUID().toString(),
    title = DEFAULT_CONVERSATION_TITLE,
    workspacePath = workspacePath,
    contextUsageFraction = estimateContextUsage(
        items = emptyList(),
        attachmentCount = 0,
        contextWindow = contextWindow,
    ),
)

/**
 * 解析 profile 的上下文窗口；profile 显式配置优先，其次使用模型能力默认值。
 */
private fun resolveContextWindow(profile: ConfigProfile): Int? =
    profile.limit?.context ?: ModelCapabilitiesResolver.resolve(profile).limit?.context

/**
 * 依据已有消息和附件粗略估计上下文占用比例；没有 context 窗口时回退为 0。
 */
private fun estimateContextUsage(
    items: List<ConversationItem>,
    attachmentCount: Int,
    contextWindow: Int?,
): Float {
    val window = contextWindow?.takeIf { it > 0 } ?: return 0f
    val estimatedTokens = items.sumOf(::estimateTokens) + attachmentCount * ATTACHMENT_TOKEN_ESTIMATE
    return (estimatedTokens.toFloat() / window).coerceIn(0f, 1f)
}

/**
 * 粗略估算单个时间线项的 token 数，后续可被 provider usage 或 tokenizer 替换。
 */
private fun estimateTokens(item: ConversationItem): Int = when (item) {
    is ChatMessageItem -> estimateTextTokens(item.message.content)
    is ReasoningItem -> estimateTextTokens(item.rawText ?: item.displayText)
    is ToolEventItem -> estimateTextTokens(item.preview.orEmpty()) + estimateTextTokens(item.toolName)
}

/**
 * 使用常见的 4 字符约 1 token 经验值估算文本 token 数。
 */
private fun estimateTextTokens(text: String): Int =
    (text.length + CHARS_PER_TOKEN_ESTIMATE - 1) / CHARS_PER_TOKEN_ESTIMATE

/**
 * 仅在有值时追加文本片段。
 */
private fun String.appendNullable(next: String?): String = if (next.isNullOrEmpty()) this else this + next

/**
 * 根据首条用户消息生成本地短标题；后续可替换为无工具 LLM title executor。
 */
internal fun buildConversationTitle(prompt: String): String {
    val firstLine = prompt
        .lineSequence()
        .map { line -> line.trim().replace(Regex("\\s+"), " ") }
        .firstOrNull(String::isNotBlank)
        .orEmpty()
    return firstLine.take(CONVERSATION_TITLE_MAX_LENGTH).ifBlank { DEFAULT_CONVERSATION_TITLE }
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

/**
 * 判断会话是否仍是未使用过的默认空会话。
 */
private fun ChatConversationUiState.isEmptyDefaultConversation(): Boolean =
    title == DEFAULT_CONVERSATION_TITLE &&
            items.isEmpty() &&
            attachments.isEmpty() &&
            history.isEmpty() &&
            pendingQuestion == null &&
            pendingApproval == null &&
            executionState == ExecutionState.Idle

/**
 * 新对话的默认标题。
 */
private const val DEFAULT_CONVERSATION_TITLE = "新对话"

private const val CONVERSATION_TITLE_MAX_LENGTH = 24

private const val TASK_SUBTITLE_MAX_LENGTH = 52

private const val CHARS_PER_TOKEN_ESTIMATE = 4

private const val ATTACHMENT_TOKEN_ESTIMATE = 64

/**
 * 确保当前会话存在一个可追加的流式助手历史消息。
 */
private fun ensureStreamingAssistantHistory(conversation: ChatConversationUiState): ChatConversationUiState {
    val currentIndex = conversation.streamingAssistantHistoryIndex
    if (currentIndex != null && conversation.history.getOrNull(currentIndex) is AgentConversationHistoryMessage.Assistant) {
        return conversation
    }
    val nextHistory = conversation.history + AgentConversationHistoryMessage.Assistant()
    return conversation.copy(
        history = nextHistory,
        streamingAssistantHistoryIndex = nextHistory.lastIndex,
    )
}

/**
 * 向当前流式助手历史中追加文本，并与最后一个文本 part 合并。
 */
private fun appendAssistantTextHistory(
    conversation: ChatConversationUiState,
    delta: String,
): ChatConversationUiState {
    if (delta.isEmpty()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val last = parts.lastOrNull()
        if (last is AgentConversationHistoryPart.Text) {
            parts.dropLast(1) + last.copy(text = last.text + delta)
        } else {
            parts + AgentConversationHistoryPart.Text(text = delta)
        }
    }
}

/**
 * 向当前流式助手历史中追加 reasoning，并与最后一个 reasoning part 合并。
 */
private fun appendAssistantReasoningHistory(
    conversation: ChatConversationUiState,
    summary: String?,
    rawText: String?,
): ChatConversationUiState {
    if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val last = parts.lastOrNull()
        if (last is AgentConversationHistoryPart.Reasoning) {
            val mergedSummary = last.summary.orEmpty().appendNullable(summary).takeIf { it.isNotBlank() }
            val mergedRawText = last.rawText.orEmpty().appendNullable(rawText).takeIf { it.isNotBlank() }
            parts.dropLast(1) + last.copy(
                summary = mergedSummary,
                rawText = mergedRawText,
            )
        } else {
            parts + AgentConversationHistoryPart.Reasoning(
                summary = summary,
                rawText = rawText,
            )
        }
    }
}

/**
 * 用完整 reasoning 收尾当前 assistant history 中最后一个 reasoning part。
 */
private fun completeAssistantReasoningHistory(
    conversation: ChatConversationUiState,
    summary: String?,
    rawText: String?,
): ChatConversationUiState {
    if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val reasoningIndex = parts.indexOfLast { part -> part is AgentConversationHistoryPart.Reasoning }
        if (reasoningIndex >= 0) {
            val last = parts[reasoningIndex] as AgentConversationHistoryPart.Reasoning
            parts.toMutableList().apply {
                this[reasoningIndex] = last.copy(
                    summary = summary ?: last.summary,
                    rawText = rawText ?: last.rawText,
                )
            }
        } else {
            parts + AgentConversationHistoryPart.Reasoning(
                summary = summary,
                rawText = rawText,
            )
        }
    }
}

/**
 * 追加 assistant tool call 历史片段。
 */
private fun appendAssistantToolCallHistory(
    conversation: ChatConversationUiState,
    id: String?,
    name: String,
    argumentsPreview: String?,
): ChatConversationUiState = updateAssistantHistoryParts(conversation) { parts ->
    parts + AgentConversationHistoryPart.ToolCall(
        id = id,
        name = name,
        argumentsPreview = argumentsPreview,
    )
}

/**
 * 追加 assistant tool result 历史片段。
 */
private fun appendAssistantToolResultHistory(
    conversation: ChatConversationUiState,
    id: String?,
    name: String,
    resultPreview: String?,
): ChatConversationUiState = updateAssistantHistoryParts(conversation) { parts ->
    parts + AgentConversationHistoryPart.ToolResult(
        id = id,
        name = name,
        resultPreview = resultPreview,
    )
}

/**
 * 在 assistant message 完成时用最终正文收尾文本 part，避免和流式增量重复累加。
 */
private fun finalizeAssistantTextHistory(
    conversation: ChatConversationUiState,
    finalText: String,
): ChatConversationUiState {
    if (finalText.isBlank()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val last = parts.lastOrNull()
        if (last is AgentConversationHistoryPart.Text) {
            parts.dropLast(1) + last.copy(text = finalText)
        } else {
            parts + AgentConversationHistoryPart.Text(text = finalText)
        }
    }
}

/**
 * 更新当前流式 assistant history 的 parts 列表。
 */
private fun updateAssistantHistoryParts(
    conversation: ChatConversationUiState,
    transform: (List<AgentConversationHistoryPart>) -> List<AgentConversationHistoryPart>,
): ChatConversationUiState {
    val normalizedConversation = ensureStreamingAssistantHistory(conversation)
    val historyIndex = normalizedConversation.streamingAssistantHistoryIndex ?: return normalizedConversation
    val assistant = normalizedConversation.history[historyIndex] as? AgentConversationHistoryMessage.Assistant
        ?: return normalizedConversation
    val updatedHistory = normalizedConversation.history.toMutableList()
    updatedHistory[historyIndex] = assistant.copy(parts = transform(assistant.parts))
    return normalizedConversation.copy(history = updatedHistory)
}
