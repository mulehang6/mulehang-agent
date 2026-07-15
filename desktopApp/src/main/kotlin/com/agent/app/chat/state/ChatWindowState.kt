package com.agent.app.chat.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationState
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.resolver.ModelCapabilitiesResolver
import com.agent.shared.tool.model.PermissionPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
                    val withToolFailure = attachFailureToTimeline(
                        conversation = conversation,
                        reason = reason,
                        contextWindow = activeContextWindow(),
                    )
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
            is AgentStreamEvent.QuestionRequested -> {
                pendingQuestionConversationId = conversationId
                pendingApprovalConversationId = null
            }

            is AgentStreamEvent.ApprovalRequested -> {
                pendingApprovalConversationId = conversationId
                pendingQuestionConversationId = null
            }

            is AgentStreamEvent.Completed,
            is AgentStreamEvent.Failed,
                -> clearPendingOwnership(conversationId)

            else -> Unit
        }
        val contextWindow = activeContextWindow()
        mutateConversation(conversationId) { conversation ->
            reduceAgentEvent(conversation, event, contextWindow)
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

}
