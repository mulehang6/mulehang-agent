package com.agent.app.chat.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.app.chat.persistence.TaskPersistenceCoordinator
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.ConversationTitleGenerator
import com.agent.shared.agent.api.ConversationTitleRequest
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.AnsweredQuestionsItem
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
import com.agent.shared.tool.model.QuestionAnswer
import com.agent.shared.tool.model.QuestionPrompt
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
    private val persistenceCoordinator: TaskPersistenceCoordinator? = null,
    private val conversationTitleGenerator: ConversationTitleGenerator? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val workspaceDirectoryExists: (String) -> Boolean = { path -> path.isNotBlank() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeRunJob: Job? = null
    private var activeRunConversationId: String? = null
    private var pendingQuestionConversationId: String? = null
    private var pendingApprovalConversationId: String? = null
    private val conversationTitleJobs = mutableMapOf<String, Job>()
    private val conversationTitleGenerationVersions = mutableMapOf<String, Int>()
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
        get() {
            val conversationProfileId = ui.activeConversationOrNull?.profileId
            return snapshot.profiles.firstOrNull { it.id == conversationProfileId }
                ?: snapshot.profiles.firstOrNull { it.id == ui.selectedProfileId }
                ?: snapshot.activeProfile
        }

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
        ui = ui.copy(
            selectedProfileId = selectedProfileId,
            tasks = ui.tasks.map { conversation ->
                val boundProfile = conversation.profileId?.let { profileId ->
                    snapshot.profiles.firstOrNull { it.id == profileId }
                }
                // 没有显式绑定 profile 的会话代表"跟随窗口默认"，需要沿用与
                // profileForConversation 相同的回退链，否则新快照的默认档位能力
                // 不会传导到这些会话的 reasoning effort 和上下文窗口。
                val effectiveProfile = boundProfile
                    ?: snapshot.profiles.firstOrNull { it.id == selectedProfileId }
                    ?: snapshot.activeProfile
                conversation
                    .copy(
                        profileId = conversation.profileId?.takeIf { boundProfile != null },
                        reasoningEffort = effectiveProfile?.let { profile ->
                            resolvedReasoningEffort(profile, conversation.reasoningEffort)
                        } ?: conversation.reasoningEffort,
                    )
                    .withRecalculatedContextUsage(effectiveProfile?.let(::contextWindowFor))
            },
        )
        persistenceCoordinator?.schedule(ui.tasks)
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
     * 重命名指定对话；空白标题保持原样，避免产生无法辨识的侧栏条目。
     */
    fun renameConversation(conversationId: String, title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return
        invalidateConversationTitleGeneration(conversationId)
        mutateConversation(conversationId) { conversation ->
            conversation.copy(
                title = normalizedTitle,
                titleState = ConversationTitleState.GENERATED,
            )
        }
    }

    /**
     * 删除指定对话；删除当前对话时优先复用已有空白对话，避免重复创建占位项。
     */
    fun deleteConversation(conversationId: String) {
        val deletedConversation = findConversationOrNull(conversationId) ?: return
        invalidateConversationTitleGeneration(conversationId)
        if (activeRunConversationId == conversationId) {
            activeRunJob?.cancel()
            activeRunJob = null
            activeRunConversationId = null
        }
        clearPendingOwnership(conversationId)
        val isDeletingActiveConversation = ui.activeTaskId == conversationId
        val remainingTasks = ui.tasks.filterNot { it.id == conversationId }
        val replacementConversation = if (isDeletingActiveConversation) {
            remainingTasks.firstOrNull {
                it.workspacePath == deletedConversation.workspacePath && it.isEmptyDefaultConversation()
            } ?: remainingTasks.firstOrNull(ChatConversationUiState::isEmptyDefaultConversation) ?: newConversation(
                workspacePath = deletedConversation.workspacePath,
                contextWindow = contextWindowForConversation(deletedConversation),
                profileId = deletedConversation.profileId,
                reasoningEffort = deletedConversation.reasoningEffort,
                permissionPreset = deletedConversation.permissionPreset,
            )
        } else {
            null
        }
        ui = ui.copy(
            tasks = if (replacementConversation != null && replacementConversation !in remainingTasks) {
                listOf(replacementConversation) + remainingTasks
            } else {
                remainingTasks
            },
            activeTaskId = replacementConversation?.id ?: ui.activeTaskId,
            draft = if (isDeletingActiveConversation) "" else ui.draft,
        )
        persistenceCoordinator?.schedule(ui.tasks)
    }

    /**
     * 在指定工作目录下新建对话并切换焦点。
     */
    fun createConversationForWorkspace(workspacePath: String) {
        val normalizedPath = workspacePath.trim()
        onWorkspaceSelected(normalizedPath)
        val restoredTasks = restoreDetachedWorkspaceHistory(
            conversations = ui.tasks,
            workspacePath = normalizedPath,
        )
        val reusableConversation = restoredTasks.firstOrNull { conversation ->
            conversation.workspacePath == normalizedPath && conversation.isEmptyDefaultConversation()
        }
        if (reusableConversation != null) {
            ui = ui.copy(
                tasks = restoredTasks,
                activeTaskId = reusableConversation.id,
                draft = "",
            )
            persistenceCoordinator?.schedule(ui.tasks)
            return
        }
        val preferenceSource = ui.activeConversationOrNull
        val selectedProfile = activeProfile
        val conversation = newConversation(
            workspacePath = normalizedPath,
            contextWindow = selectedProfile?.let(::contextWindowFor),
            profileId = preferenceSource?.profileId ?: selectedProfile?.id,
            reasoningEffort = preferenceSource?.reasoningEffort
                ?: selectedProfile?.let(::defaultReasoningEffortFor)
                ?: ReasoningEffort.MEDIUM,
            permissionPreset = preferenceSource?.permissionPreset ?: PermissionPreset.DEFAULT,
        ).copy(workspaceName = workspaceNameFor(normalizedPath, restoredTasks))
        val updatedTasks = if (shouldReplaceActiveEmptyConversation(normalizedPath)) {
            restoredTasks.map { existing ->
                if (existing.id == ui.activeTaskId) {
                    conversation
                } else {
                    existing
                }
            }
        } else {
            listOf(conversation) + restoredTasks
        }
        ui = ui.copy(
            tasks = updatedTasks,
            activeTaskId = conversation.id,
            draft = "",
        )
        persistenceCoordinator?.schedule(ui.tasks)
    }

    /** 返回尚无来源目录的旧版隐藏历史数量，供界面在批量恢复前请求确认。 */
    val legacyUnlinkedHistoryCount: Int
        get() = ui.tasks.count { conversation ->
            conversation.workspacePath.isBlank() &&
                    conversation.detachedWorkspacePath == null &&
                    !conversation.isEmptyDefaultConversation()
        }

    /** 将旧版无来源隐藏历史批量恢复到用户明确选择的目录。 */
    fun restoreLegacyUnlinkedHistory(workspacePath: String): String? {
        val normalizedPath = workspacePath.trim()
        if (!workspaceDirectoryExists(normalizedPath)) return "请选择存在的工作目录。"
        if (legacyUnlinkedHistoryCount == 0) return null
        val targetWorkspaceName = workspaceNameFor(normalizedPath, ui.tasks)
        ui = ui.copy(
            tasks = ui.tasks.map { conversation ->
                if (
                    conversation.workspacePath.isBlank() &&
                    conversation.detachedWorkspacePath == null &&
                    !conversation.isEmptyDefaultConversation()
                ) {
                    conversation.copy(
                        workspacePath = normalizedPath,
                        workspaceName = targetWorkspaceName,
                        detachedWorkspacePath = null,
                        detachedWorkspaceName = null,
                        updatedAt = clock(),
                    )
                } else {
                    conversation
                }
            },
        )
        persistenceCoordinator?.schedule(ui.tasks)
        return null
    }

    /** 返回当前会话不可执行时应展示的工作目录说明。 */
    fun workspaceIssue(conversation: ChatConversationUiState): String? =
        workspaceIssueForPath(conversation.workspacePath)

    /** 返回指定目录不可执行时的用户可读原因。 */
    fun workspaceIssueForPath(workspacePath: String): String? = when {
        workspacePath.isBlank() -> "此历史任务尚未关联工作目录。"
        !workspaceDirectoryExists(workspacePath) -> "工作目录已不存在：$workspacePath"
        else -> null
    }

    /** 更新一个工作区下所有任务的显示名称与目录；目标目录已有历史时合并。 */
    fun editWorkspace(
        previousPath: String,
        name: String,
        path: String,
    ): String? {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return "工作区名称不能为空。"
        return migrateWorkspace(
            previousPath = previousPath,
            path = path,
            explicitWorkspaceName = normalizedName,
        )
    }

    /** 将一个已关联工作区完整迁移至新目录，并在需要时与目标目录历史合并。 */
    fun relinkWorkspace(previousPath: String, path: String): String? = migrateWorkspace(
        previousPath = previousPath,
        path = path,
        explicitWorkspaceName = null,
    )

    /** 迁移工作区的内部实现；显式名称会同步覆盖已合并目标组的显示名称。 */
    private fun migrateWorkspace(
        previousPath: String,
        path: String,
        explicitWorkspaceName: String?,
    ): String? {
        val normalizedPath = path.trim()
        if (previousPath.isBlank()) return "未关联历史请逐条重新关联工作目录。"
        if (!workspaceDirectoryExists(normalizedPath)) return "请选择存在的工作目录。"
        val targetWorkspaceName = explicitWorkspaceName
            ?: workspaceNameFor(normalizedPath, ui.tasks)
        val migratedTasks = ui.tasks.map { conversation ->
            if (conversation.workspacePath == previousPath) {
                conversation.copy(
                    workspacePath = normalizedPath,
                    workspaceName = targetWorkspaceName,
                    detachedWorkspacePath = null,
                    detachedWorkspaceName = null,
                    updatedAt = clock(),
                )
            } else if (explicitWorkspaceName != null && conversation.workspacePath == normalizedPath) {
                conversation.copy(
                    workspaceName = targetWorkspaceName,
                    updatedAt = clock(),
                )
            } else {
                conversation
            }
        }
        ui = ui.copy(
            tasks = restoreDetachedWorkspaceHistory(migratedTasks, normalizedPath),
        )
        persistenceCoordinator?.schedule(ui.tasks)
        return null
    }

    /**
     * 解除工作区目录关联，并删除不含历史的默认占位任务。
     *
     * 若正在删除当前工作区，则切换到最近使用且仍可访问的其他工作区的新任务；没有候选时回到欢迎页。
     */
    fun disconnectWorkspace(workspacePath: String) {
        val activeConversation = ui.activeConversationOrNull
        val isDisconnectingActiveWorkspace = activeConversation?.workspacePath == workspacePath
        val fallbackWorkspacePath = if (isDisconnectingActiveWorkspace) {
            findRecentAvailableWorkspacePath(excludedWorkspacePath = workspacePath)
        } else {
            null
        }
        val removedTaskIds = ui.tasks
            .filter { it.workspacePath == workspacePath && it.isEmptyDefaultConversation() }
            .map(ChatConversationUiState::id)
            .toSet()
        val retainedTasks = ui.tasks
            .filterNot { it.id in removedTaskIds }
            .map { conversation ->
                if (conversation.workspacePath == workspacePath) {
                    conversation.copy(
                        workspacePath = "",
                        workspaceName = null,
                        detachedWorkspacePath = workspacePath,
                        detachedWorkspaceName = conversation.workspaceName,
                        updatedAt = clock(),
                    )
                } else {
                    conversation
                }
            }
        val fallbackConversation = fallbackWorkspacePath?.let { fallbackPath ->
            retainedTasks.firstOrNull { conversation ->
                conversation.workspacePath == fallbackPath && conversation.isEmptyDefaultConversation()
            } ?: newConversation(
                workspacePath = fallbackPath,
                contextWindow = activeProfile?.let(::contextWindowFor),
                profileId = activeConversation?.profileId ?: activeProfile?.id,
                reasoningEffort = activeConversation?.reasoningEffort
                    ?: activeProfile?.let(::defaultReasoningEffortFor)
                    ?: ReasoningEffort.MEDIUM,
                permissionPreset = activeConversation?.permissionPreset ?: PermissionPreset.DEFAULT,
            ).copy(
                workspaceName = retainedTasks.firstOrNull { it.workspacePath == fallbackPath }?.workspaceName,
            )
        }
        val updatedTasks = if (fallbackConversation != null && fallbackConversation !in retainedTasks) {
            listOf(fallbackConversation) + retainedTasks
        } else {
            retainedTasks
        }
        ui = ui.copy(
            tasks = updatedTasks,
            activeTaskId = if (isDisconnectingActiveWorkspace) {
                fallbackConversation?.id.orEmpty()
            } else if (ui.activeTaskId in removedTaskIds) {
                updatedTasks.firstOrNull()?.id.orEmpty()
            } else {
                ui.activeTaskId
            },
            draft = if (isDisconnectingActiveWorkspace) "" else ui.draft,
        )
        persistenceCoordinator?.schedule(ui.tasks)
    }

    /** 为一条未关联或失效历史任务重新选择可执行工作目录。 */
    fun relinkConversationWorkspace(conversationId: String, workspacePath: String): String? {
        val normalizedPath = workspacePath.trim()
        if (!workspaceDirectoryExists(normalizedPath)) return "请选择存在的工作目录。"
        val targetWorkspaceName = workspaceNameFor(normalizedPath, ui.tasks)
        val relinkedTasks = ui.tasks.map { conversation ->
            if (conversation.id == conversationId) {
                conversation.copy(
                    workspacePath = normalizedPath,
                    workspaceName = targetWorkspaceName,
                    detachedWorkspacePath = null,
                    detachedWorkspaceName = null,
                    executionState = ExecutionState.Idle,
                    updatedAt = clock(),
                )
            } else {
                conversation
            }
        }
        ui = ui.copy(tasks = restoreDetachedWorkspaceHistory(relinkedTasks, normalizedPath))
        persistenceCoordinator?.schedule(ui.tasks)
        return null
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
                    contextWindow = contextWindowForConversation(conversation),
                ),
            )
        }
    }

    /**
     * 从当前会话输入区移除指定路径的附件并重新估算上下文占用。
     */
    fun removeAttachment(path: String) {
        mutateActiveConversation { conversation ->
            val attachments = conversation.attachments.filterNot { it.path == path }
            conversation.copy(
                attachments = attachments,
                contextUsageFraction = estimateContextUsage(
                    items = conversation.items,
                    attachmentCount = attachments.size,
                    contextWindow = contextWindowForConversation(conversation),
                ),
            )
        }
    }

    /**
     * 调整当前会话的权限档位。
     */
    fun updatePermission(permissionPreset: PermissionPreset) {
        mutateActiveConversation { conversation ->
            conversation.copy(permissionPreset = permissionPreset)
        }
    }

    /**
     * 切换当前 profile。
     */
    fun selectProfile(profileId: String) {
        val selectedProfile = snapshot.profiles.firstOrNull { it.id == profileId } ?: return
        ui = ui.copy(selectedProfileId = profileId)
        mutateActiveConversation { conversation ->
            conversation
                .copy(
                    profileId = profileId,
                    reasoningEffort = resolvedReasoningEffort(
                        profile = selectedProfile,
                        preferredEffort = conversation.reasoningEffort,
                    ) ?: conversation.reasoningEffort,
                )
                .withRecalculatedContextUsage(contextWindowFor(selectedProfile))
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
        val targetConversationId = resolvePendingQuestionConversationId() ?: return
        val pending = findConversation(targetConversationId).pendingQuestion ?: return
        val question = pending.effectiveQuestions.singleOrNull()?.question ?: return
        submitPendingQuestionAnswers(
            answers = listOf(QuestionAnswer(question = question, answer = answer)),
            toolResponse = answer,
        )
    }

    /**
     * 一次提交当前批量问题的完整回答，并恢复发起问题的同一轮 Agent。
     */
    fun answerPendingQuestions(answers: List<QuestionAnswer>) {
        submitPendingQuestionAnswers(
            answers = answers,
            toolResponse = formatQuestionAnswers(answers),
        )
    }

    /**
     * 写入问答记录、解除挂起并向等待中的工具调用提交指定文本结果。
     */
    private fun submitPendingQuestionAnswers(
        answers: List<QuestionAnswer>,
        toolResponse: String,
    ) {
        val targetConversationId = resolvePendingQuestionConversationId() ?: return
        val pending = findConversation(targetConversationId).pendingQuestion ?: return
        if (!isCompleteQuestionAnswerSet(pending = pending, answers = answers)) return
        if (!toolInteractionCoordinator.submitQuestion(toolResponse)) return
        pendingQuestionConversationId = null
        mutateConversation(targetConversationId) { conversation ->
            conversation.copy(
                items = conversation.items + AnsweredQuestionsItem(answers = answers),
                pendingQuestion = null,
                executionState = ExecutionState.Running,
            )
        }
    }

    /**
     * 验证答案必须按当前问卷顺序完整覆盖，且每项都包含非空文本。
     */
    private fun isCompleteQuestionAnswerSet(
        pending: PendingQuestionUiState,
        answers: List<QuestionAnswer>,
    ): Boolean = pending.effectiveQuestions.map(QuestionPrompt::question) == answers.map(QuestionAnswer::question) &&
            answers.all { it.answer.isNotBlank() }

    /**
     * 将批量回答编码为稳定的纯文本，供挂起中的 Agent 工具调用继续读取。
     */
    private fun formatQuestionAnswers(answers: List<QuestionAnswer>): String = answers.joinToString("\n\n") { answer ->
        "Question: ${answer.question}\nAnswer: ${answer.answer.trim()}"
    }

    /**
     * 提交当前挂起审批；拒绝时停止当前 agent 轮次，其余选择恢复同一轮执行。
     */
    fun answerPendingApproval(response: ApprovalResponse) {
        if (!toolInteractionCoordinator.submitApproval(response)) return
        if (response == ApprovalResponse.REJECT_AND_STOP) {
            cancelActiveRun()
            return
        }
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
     * 兼容既有二元审批调用。
     */
    fun answerPendingApproval(approved: Boolean) {
        answerPendingApproval(
            if (approved) ApprovalResponse.APPROVE_ONCE else ApprovalResponse.REJECT_AND_STOP,
        )
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

        val sourceConversation = findConversation(targetConversationId)
        workspaceIssue(sourceConversation)?.let { message ->
            mutateConversation(targetConversationId) { conversation ->
                conversation.copy(
                    executionState = ExecutionState.Failed(
                        AppError(
                            title = "工作目录不可用",
                            message = message,
                        ),
                    ),
                )
            }
            return
        }
        val profile = profileForConversation(sourceConversation)
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

        val requestHistory = sourceConversation.history
        val shouldGenerateConversationTitle = sourceConversation.title == DEFAULT_CONVERSATION_TITLE &&
                sourceConversation.history.none { message -> message is AgentConversationHistoryMessage.User } &&
                conversationTitleGenerator != null
        val reasoningEffort = supportedReasoningEffort(
            profile = profile,
            conversation = sourceConversation,
        )
        mutateConversation(targetConversationId) { conversation ->
            val nextItems = conversation.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt))
            conversation.copy(
                title = conversation.title.takeUnless { it == DEFAULT_CONVERSATION_TITLE }
                    ?: buildConversationTitle(prompt),
                titleState = if (shouldGenerateConversationTitle) {
                    ConversationTitleState.GENERATING
                } else {
                    conversation.titleState
                },
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
        if (shouldGenerateConversationTitle) {
            requestConversationTitle(
                conversationId = targetConversationId,
                firstUserMessage = prompt,
                profile = profile,
            )
        }

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
                        permissionPreset = sourceConversation.permissionPreset,
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
                        contextWindow = contextWindowForConversation(conversation),
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
     * 用数据库加载的任务替换初始占位任务；空数据库保持当前可用会话。
     */
    fun restoreTasks(tasks: List<ChatConversationUiState>) {
        if (tasks.isEmpty()) return
        invalidateAllConversationTitleGenerations()
        val restoredPreferenceSource = tasks.first()
        val restoredProfile = profileForConversation(restoredPreferenceSource)
        val newConversation = ui.tasks.firstOrNull { it.isEmptyDefaultConversation() }
            ?: newConversation(
                workspacePath = restoredPreferenceSource.workspacePath,
                contextWindow = restoredProfile?.let(::contextWindowFor),
                profileId = restoredPreferenceSource.profileId ?: restoredProfile?.id,
                reasoningEffort = restoredProfile?.let { profile ->
                    resolvedReasoningEffort(profile, restoredPreferenceSource.reasoningEffort)
                } ?: restoredPreferenceSource.reasoningEffort,
                permissionPreset = restoredPreferenceSource.permissionPreset,
            )
        ui = ui.copy(
            tasks = listOf(newConversation) + tasks.filterNot(ChatConversationUiState::isEmptyDefaultConversation),
            activeTaskId = newConversation.id,
            draft = "",
        )
    }

    /**
     * 保存或加载失败时更新侧栏可见的简短提示。
     */
    fun setPersistenceError(message: String) {
        ui = ui.copy(persistenceErrorMessage = message)
    }

    /**
     * 在窗口退出前强制写入最新任务快照。
     */
    fun flushPersistence(onFlushed: () -> Unit) {
        persistenceCoordinator?.flush(ui.tasks, onFlushed) ?: onFlushed()
    }

    /**
     * 仅当当前 profile 支持所选档位时才将 reasoning effort 送入执行链路。
     */
    private fun supportedReasoningEffort(
        profile: ConfigProfile,
        conversation: ChatConversationUiState,
    ): ReasoningEffort? = resolvedReasoningEffort(profile, conversation.reasoningEffort)

    /**
     * 保留受当前 profile 支持的档位；否则回退到该 profile 的默认档位。
     */
    private fun resolvedReasoningEffort(
        profile: ConfigProfile,
        preferredEffort: ReasoningEffort,
    ): ReasoningEffort? {
        val capabilities = ModelCapabilitiesResolver.resolve(profile)
        return preferredEffort.takeIf { effort ->
            effort in capabilities.reasoningEfforts
        } ?: capabilities.defaultReasoningEffort
    }

    /**
     * 当前 profile 的上下文窗口；显式配置优先，其次使用 provider/model 默认能力。
     */
    private fun activeContextWindow(): Int? = activeProfile?.let(::contextWindowFor)

    /**
     * 按会话保存的 profile 解析可用配置；旧会话回退到窗口默认配置。
     */
    private fun profileForConversation(conversation: ChatConversationUiState): ConfigProfile? =
        snapshot.profiles.firstOrNull { it.id == conversation.profileId }
            ?: snapshot.profiles.firstOrNull { it.id == ui.selectedProfileId }
            ?: snapshot.activeProfile

    /**
     * 根据会话绑定的 profile 解析上下文窗口。
     */
    private fun contextWindowForConversation(conversation: ChatConversationUiState): Int? =
        snapshot.profiles.firstOrNull { it.id == conversation.profileId }?.let(::contextWindowFor)
            ?: activeContextWindow()

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
        val contextWindow = findConversationOrNull(conversationId)
            ?.let(::contextWindowForConversation)
            ?: activeContextWindow()
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
                    transform(conversation).copy(updatedAt = clock())
                } else {
                    conversation
                }
            },
        )
        persistenceCoordinator?.schedule(ui.tasks)
    }

    /**
     * 更新当前活动会话。
     */
    private fun mutateActiveConversation(transform: (ChatConversationUiState) -> ChatConversationUiState) {
        mutateConversation(ui.activeTaskId, transform)
    }

    /**
     * 以独立、无工具的标题生成器更新首条用户消息所在会话。
     */
    private fun requestConversationTitle(
        conversationId: String,
        firstUserMessage: String,
        profile: ConfigProfile,
    ) {
        val titleGenerator = conversationTitleGenerator ?: return
        invalidateConversationTitleGeneration(conversationId)
        val generationVersion = conversationTitleGenerationVersions.getValue(conversationId)
        conversationTitleJobs[conversationId] = scope.launch {
            val generatedTitle = try {
                normalizeGeneratedConversationTitle(
                    rawTitle = titleGenerator.generate(
                        ConversationTitleRequest(
                            firstUserMessage = firstUserMessage,
                            profile = profile,
                        ),
                    ),
                    fallbackTitle = buildConversationTitle(firstUserMessage),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (conversationTitleGenerationVersions[conversationId] != generationVersion) return@launch
            conversationTitleJobs.remove(conversationId)
            mutateConversation(conversationId) { conversation ->
                if (generatedTitle == null) {
                    conversation.copy(titleState = ConversationTitleState.FAILED)
                } else {
                    conversation.copy(
                        title = generatedTitle,
                        titleState = ConversationTitleState.GENERATED,
                    )
                }
            }
        }
    }

    /**
     * 取消标题任务，并递增版本以阻止迟到结果覆盖当前状态。
     */
    private fun invalidateConversationTitleGeneration(conversationId: String) {
        conversationTitleJobs.remove(conversationId)?.cancel()
        conversationTitleGenerationVersions[conversationId] =
            (conversationTitleGenerationVersions[conversationId] ?: 0) + 1
    }

    /**
     * 恢复持久化任务前，取消所有窗口内尚未完成的标题生成请求。
     */
    private fun invalidateAllConversationTitleGenerations() {
        conversationTitleJobs.values.forEach(Job::cancel)
        conversationTitleJobs.clear()
        conversationTitleGenerationVersions.keys.toList().forEach(::invalidateConversationTitleGeneration)
    }

    /**
     * 清洗模型格式标记，保证侧栏始终获得单行、有限长度的可读标题。
     */
    private fun normalizeGeneratedConversationTitle(
        rawTitle: String,
        fallbackTitle: String,
    ): String {
        val normalizedTitle = rawTitle
            .replace(Regex("[*_`]+"), "")
            .lineSequence()
            .joinToString(separator = " ") { line -> line.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’')
            .take(CONVERSATION_TITLE_MAX_LENGTH)
            .trim()
        return normalizedTitle.ifBlank { fallbackTitle }
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
     * 在移除当前工作区前，从其他仍可访问的工作区中找出最近更新的目录。
     *
     * 相同更新时间沿用任务列表顺序，保证选择结果稳定。
     */
    private fun findRecentAvailableWorkspacePath(excludedWorkspacePath: String): String? = ui.tasks
        .asSequence()
        .filter { conversation ->
            conversation.workspacePath.isNotBlank() &&
                    conversation.workspacePath != excludedWorkspacePath &&
                    workspaceDirectoryExists(conversation.workspacePath)
        }
        .maxByOrNull(ChatConversationUiState::updatedAt)
        ?.workspacePath

    /** 解析关联或恢复工作区时应使用的名称，优先保留目标组已有的用户命名。 */
    private fun workspaceNameFor(
        workspacePath: String,
        conversations: List<ChatConversationUiState>,
    ): String = conversations.firstNotNullOfOrNull { conversation ->
        conversation.workspaceName
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeIf { conversation.workspacePath == workspacePath }
    } ?: conversations.firstNotNullOfOrNull { conversation ->
        conversation.detachedWorkspaceName
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeIf {
                conversation.workspacePath.isBlank() && conversation.detachedWorkspacePath == workspacePath
            }
    }
        ?: buildWorkspaceLabel(workspacePath)

    /** 将解除前目录与当前所选目录精确匹配的隐藏历史恢复到同一个工作区。 */
    private fun restoreDetachedWorkspaceHistory(
        conversations: List<ChatConversationUiState>,
        workspacePath: String,
    ): List<ChatConversationUiState> {
        if (conversations.none { it.workspacePath.isBlank() && it.detachedWorkspacePath == workspacePath }) {
            return conversations
        }
        val targetWorkspaceName = workspaceNameFor(workspacePath, conversations)
        return conversations.map { conversation ->
            when {
                conversation.workspacePath.isBlank() && conversation.detachedWorkspacePath == workspacePath -> {
                    conversation.copy(
                        workspacePath = workspacePath,
                        workspaceName = targetWorkspaceName,
                        detachedWorkspacePath = null,
                        detachedWorkspaceName = null,
                        updatedAt = clock(),
                    )
                }

                conversation.workspacePath == workspacePath && conversation.workspaceName.isNullOrBlank() -> {
                    conversation.copy(workspaceName = targetWorkspaceName)
                }

                else -> conversation
            }
        }
    }

    /**
     * 查找指定对话，如果不存在则返回空。
     */
    private fun findConversationOrNull(conversationId: String): ChatConversationUiState? =
        ui.tasks.firstOrNull { it.id == conversationId }

}
