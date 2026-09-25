package com.agent.app.chat.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.agent.app.chat.media.SessionMediaStore
import com.agent.app.tool.interaction.ApprovalResponse
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.app.chat.persistence.TaskPersistenceCoordinator
import com.agent.app.platform.ClipboardPngImage
import com.agent.app.platform.ToastActivationTarget
import com.agent.shared.agent.api.ConversationTitleGenerator
import com.agent.shared.agent.api.BranchSummaryGenerator
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.koog.AgentRunRecoveryRepository
import com.agent.shared.agent.status.AgentTodoRepository
import com.agent.shared.agent.status.AgentStatusRepository
import com.agent.shared.chat.attention.ConversationAttentionEvent
import com.agent.shared.chat.attention.ConversationAttentionRepository
import com.agent.shared.tool.interaction.InteractionRequestRepository
import com.agent.shared.agent.resource.AgentPromptCommand
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentResourceSnapshot
import com.agent.shared.chat.model.ConversationState
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.resolver.ModelCapabilitiesResolver
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.QuestionAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * 窗口状态门面，装配会话、附件、执行与标题协作者。
 */
class ChatWindowState(
    internal val sendMessageUseCase: SendMessageUseCase,
    snapshot: AppSessionSnapshot,
    projectPath: String = "",
    internal val toolInteractionCoordinator: DesktopToolInteractionCoordinator = DesktopToolInteractionCoordinator(),
    internal val onWorkspaceSelected: (String) -> Unit = {},
    internal val persistenceCoordinator: TaskPersistenceCoordinator? = null,
    internal val todoRepository: AgentTodoRepository? = null,
    internal val statusRepository: AgentStatusRepository? = null,
    internal val recoveryRepository: AgentRunRecoveryRepository? = null,
    internal val attentionRepository: ConversationAttentionRepository? = null,
    internal val interactionRequestRepository: InteractionRequestRepository? = null,
    internal val onAttentionNotification: (ConversationAttentionEvent) -> Unit = {},
    internal val branchSummaryGenerator: BranchSummaryGenerator? = null,
    internal val conversationTitleGenerator: ConversationTitleGenerator? = null,
    internal val clock: () -> Long = System::currentTimeMillis,
    internal val workspaceDirectoryExists: (String) -> Boolean = { path -> path.isNotBlank() },
    internal val resourceSnapshotProvider: (String) -> AgentResourceSnapshot? = { null },
    internal val resourceReloader: suspend (String) -> AgentResourceSnapshot? = { null },
    internal val sessionMediaStore: SessionMediaStore? = null,
    internal val onSessionClosed: (String, String) -> Unit = { _, _ -> },
    internal val resourceDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal var activeRunJob: Job? = null
    internal var activeRunConversationId: String? = null
    internal var activeRunRequest: AgentRunRequest? = null
    internal var pauseRequestedConversationId: String? = null
    internal val resourceReloadInProgress: Boolean get() = resourceController.reloadInProgress
    internal var pendingQuestionConversationId: String? = null
    internal var pendingApprovalConversationId: String? = null
    internal val conversationTitleJobs = mutableMapOf<String, Job>()
    internal val conversationTitleGenerationVersions = mutableMapOf<String, Int>()
    internal var snapshot by mutableStateOf(snapshot)
    internal var resourceSnapshot by mutableStateOf(AgentResourceSnapshot.empty())
    internal var runtimeResourceDiagnostics by mutableStateOf(emptyList<AgentResourceDiagnostic>())
    private var pendingToastActivation: ToastActivationTarget? = null
    private var attentionNavigationSerial = 0L

    /** 当前窗口可选的全部 profile。 */
    val availableProfiles: List<ConfigProfile>
        get() = snapshot.profiles

    /** 当前资源快照中可由 composer 命令浏览器展示的命令。 */
    val availablePromptCommands: List<AgentPromptCommand>
        get() = resourceSnapshot.commands

    /** 当前资源快照的版本号，保留给扩展中心的重载状态与后续可观测性展示。 */
    @Suppress("unused")
    val resourceVersion: Long
        get() = resourceSnapshot.version

    /** 扩展中心展示的本轮已发现包；是否生效取决于其启用状态。 */
    val extensionPackages
        get() = resourceSnapshot.packages

    /** 扩展中心展示的本轮已加载 Skills。 */
    val loadedSkills
        get() = resourceSnapshot.skills

    /** 扩展中心展示的资源解析、冲突与不支持能力诊断。 */
    val resourceDiagnostics
        get() = (resourceSnapshot.diagnostics + runtimeResourceDiagnostics).distinct()

    /** 扩展中心展示的受控 MCP 服务声明。 */
    val mcpServers
        get() = resourceSnapshot.mcpServers

    /** 当前窗口的完整 UI 状态。 */
    var ui by mutableStateOf(
        initialUiState(snapshot = snapshot, projectPath = projectPath),
    )
        internal set

    /** 兼容旧渲染逻辑的活动会话状态投影。 */
    val state: ConversationState
        get() = ui.activeConversationOrNull?.toConversationState(activeProfile?.id)
            ?: ConversationState(
                items = emptyList(),
                executionState = ui.newConversationError?.let(ExecutionState::Failed) ?: ExecutionState.Idle,
                activeProfileId = activeProfile?.id,
            )

    /** 当前输入区附件由内存草稿持有，不能进入会话快照。 */
    val activeDraftAttachments: List<ChatAttachmentUiState>
        get() = draftController.activeAttachments

    /** 当前输入目标的上下文估计；新会话也按所选模型窗口计算。 */
    val activeContextUsageFraction: Float
        get() {
            val conversation = ui.activeConversationOrNull ?: return estimateContextUsage(
                emptyList(),
                activeDraftAttachments.size,
                activeContextWindow(),
            )
            val storedEstimate = conversation.contextUsageFraction
            if (activeDraftAttachments.isEmpty()) return storedEstimate
            return maxOf(
                storedEstimate,
                estimateContextUsage(conversation.items, activeDraftAttachments.size, activeContextWindow()),
            )
        }

    /** 保存当前输入区后切换到工作区的新会话页。 */
    internal fun showNewConversation(workspacePath: String) = draftController.showNewConversation(workspacePath)

    /** 保存当前输入区后切换到真实会话，可指定回退后恢复的输入。 */
    internal fun showExistingConversation(conversationId: String, restored: ComposerDraft? = null) =
        draftController.showExistingConversation(conversationId, restored)

    /** 替换活动草稿的附件并触发 composer 重绘。 */
    internal fun updateDraftAttachments(attachments: List<ChatAttachmentUiState>) = draftController.updateAttachments(attachments)

    /** 发送成功接受后只消费来源草稿，其他会话和工作区草稿保持原样。 */
    internal fun consumeDraftAfterSend(source: ActiveConversationTarget) = draftController.consumeAfterSend(source)

    /** 删除真实会话时同时移除它的未发送输入。 */
    internal fun forgetConversationDraft(conversationId: String) = draftController.forgetConversation(conversationId)

    /** 当前激活 profile。 */
    val activeProfile: ConfigProfile?
        get() {
            val conversationProfileId = ui.activeConversationOrNull?.profileId
            return snapshot.profiles.firstOrNull { it.id == conversationProfileId }
                ?: snapshot.profiles.firstOrNull { it.id == ui.selectedProfileId }
                ?: snapshot.activeProfile
        }

    /** 当前失败状态对应的 UI 可见错误文本。 */
    val errorMessage: String?
        get() = ((ui.activeConversationOrNull?.executionState as? ExecutionState.Failed)?.error
            ?: ui.newConversationError)?.let { error ->
            "${error.title}: ${error.message}"
        }

    private val workspaceController = ChatWorkspaceController(this)
    private val resourceController = ChatResourceController(this)
    private val draftController = ChatDraftController(this)
    private val attachmentController = ChatAttachmentController(this)
    internal val runController = ChatRunController(this)
    private val recoveryController = ChatRunRecoveryController(this)
    internal val attentionController = ChatAttentionController(this)
    private val titleController = ChatTitleController(this)
    internal val conversationTreeController = ConversationTreeController(this)

    /** 更新配置快照，但保留已有工作区、会话和输入状态。 */
    fun updateSessionSnapshot(snapshot: AppSessionSnapshot) = applySessionSnapshot(snapshot)

    /** 更新当前输入框草稿。 */
    fun updateDraft(
        value: String,
        selectionStart: Int = value.length,
    ) = draftController.updateDraft(value, selectionStart)

    /** 将命令浏览器中的选择插回 composer，而不是直接运行，用户仍可补充参数后再发送。 */
    fun insertPromptCommand(command: AgentPromptCommand) {
        val draft = ui.draft
        val selection = ui.draftSelectionStart.coerceIn(0, draft.length)
        val slashStart = draft.lastIndexOf('/', startIndex = (selection - 1).coerceAtLeast(0))
            .takeIf { index -> index >= 0 && draft.substring(index + 1, selection).none(Char::isWhitespace) }
            ?: selection
        val replacement = "/${command.name} "
        val nextDraft = draft.replaceRange(slashStart, selection, replacement)
        updateDraft(nextDraft, slashStart + replacement.length)
    }

    /** 当前没有 Agent 任务占用 MCP 连接时才允许重载资源。 */
    val canReloadAgentResources: Boolean
        get() = resourceController.canReload

    /** 手动重载当前工作区或仅用户级资源；运行期间拒绝重载，避免中途断开 MCP 工具。 */
    suspend fun reloadAgentResources(): Boolean = resourceController.reload()

    /** 预先占用重载槽位，再异步执行重载，避免新任务插入重载与旧连接替换之间。 */
    internal fun startResourceReload(onSuccess: () -> Unit = {}): Boolean = resourceController.startReload(onSuccess)

    /** 进入应用或切换工作区时读取当前发布快照，不触发手动 reload。 */
    fun refreshActiveResourceSnapshot() = resourceController.refreshActiveSnapshot()

    /**
     * 获取指定工作区当前已发布的资源快照。provider 返回 null 时保持普通聊天可用，不注入
     * 可能属于另一个工作区的旧资源。
     */
    internal fun refreshResourceSnapshotFor(workspacePath: String): AgentResourceSnapshot =
        resourceController.refreshFor(workspacePath)

    /** 发送后的资源读取离开 UI 线程，读取完成后才发布到当前界面。 */
    internal suspend fun loadRunResourceSnapshot(workspacePath: String): AgentResourceSnapshot =
        resourceController.loadForRun(workspacePath)

    /** 调整当前会话的权限档位。 */
    fun updatePermission(permissionPreset: PermissionPreset) {
        if (ui.activeConversationOrNull == null) {
            ui = ui.copy(permissionPreset = permissionPreset)
            return
        }
        mutateActiveConversation { conversation ->
            conversation.copy(permissionPreset = permissionPreset)
        }
    }

    /** 切换当前 profile。 */
    fun selectProfile(profileId: String) {
        val selectedProfile = snapshot.profiles.firstOrNull { it.id == profileId } ?: return
        ui = ui.copy(selectedProfileId = profileId)
        if (ui.activeConversationOrNull == null) {
            ui = ui.copy(newReasoningEffort = defaultReasoningEffortFor(selectedProfile))
            return
        }
        mutateActiveConversation { conversation ->
            val nextEffort = resolvedReasoningEffort(
                profile = selectedProfile,
                preferredEffort = conversation.reasoningEffort,
            ) ?: conversation.reasoningEffort
            conversationTreeController
                .recordReasoningEffortChange(
                    conversationTreeController.recordModelChange(conversation, profileId),
                    nextEffort,
                )
                .withRecalculatedContextUsage(contextWindowFor(selectedProfile))
        }
    }

    /** 调整当前活动会话的推理强度档位。 */
    fun updateReasoningEffort(reasoningEffort: ReasoningEffort) {
        if (ui.activeConversationOrNull == null) {
            ui = ui.copy(newReasoningEffort = reasoningEffort)
            return
        }
        mutateActiveConversation { conversation ->
            conversationTreeController.recordReasoningEffortChange(conversation, reasoningEffort)
        }
    }

    /** 查找指定对话，供测试或布局辅助调用。 */
    fun findConversation(conversationId: String): ChatConversationUiState =
        findConversationOrNull(conversationId)
            ?: error("Conversation $conversationId not found.")

    /** 加载真实会话；新会话页保持未落库且不生成 UUID。 */
    fun restoreTasks(tasks: List<ChatConversationUiState>) {
        invalidateAllConversationTitleGenerations()
        val unreadAttention = attentionRepository?.unread().orEmpty()
        ui = ui.copy(
            tasks = tasks.map(::withAgentStatus).map(::withRestoredPendingInteraction)
                .map { attentionController.hydrate(it, unreadAttention) },
            activeTaskId = "",
            newWorkspacePath = ui.newWorkspacePath.ifBlank {
                tasks.firstOrNull { it.archivedAt == null }?.workspacePath.orEmpty()
            },
        )
        pendingToastActivation?.let(::activateToast)
    }

    /** 热启动立即定位；冷启动先排队，待真实会话加载后再处理。 */
    internal fun activateToast(target: ToastActivationTarget) {
        if (ui.tasks.none { it.id == target.conversationId }) {
            pendingToastActivation = target
            return
        }
        pendingToastActivation = null
        showExistingConversation(target.conversationId)
        target.entryId?.let { entryId ->
            attentionNavigationSerial++
            ui = ui.copy(attentionNavigation = AttentionNavigationRequest(
                serial = attentionNavigationSerial,
                conversationId = target.conversationId,
                entryId = entryId,
            ))
        }
    }

    /** 时间线已消费 Toast 定位请求后清除瞬时状态。 */
    fun clearAttentionNavigation(serial: Long) {
        if (ui.attentionNavigation?.serial == serial) ui = ui.copy(attentionNavigation = null)
    }

    /** 从专用关系表读取当前 TODO 和最近一次实际发送的模型状态消息。 */
    internal fun withAgentStatus(conversation: ChatConversationUiState): ChatConversationUiState = conversation.copy(
        agentTodos = todoRepository?.list(conversation.id).orEmpty(),
        agentStatus = statusRepository?.latest(conversation.id),
    )

    /** 保存或加载失败时更新侧栏可见的简短提示。 */
    fun setPersistenceError(message: String) {
        ui = ui.copy(persistenceErrorMessage = message)
    }

    /** 在窗口退出前强制写入最新任务快照。 */
    fun flushPersistence(onFlushed: () -> Unit) {
        persistenceCoordinator?.flush(ui.tasks, onFlushed) ?: onFlushed()
    }

    /** 仅当当前 profile 支持所选档位时才将 reasoning effort 送入执行链路。 */
    internal fun supportedReasoningEffort(
        profile: ConfigProfile,
        conversation: ChatConversationUiState,
    ): ReasoningEffort? = resolvedReasoningEffort(profile, conversation.reasoningEffort)

    /** 保留受当前 profile 支持的档位；否则回退到该 profile 的默认档位。 */
    internal fun resolvedReasoningEffort(
        profile: ConfigProfile,
        preferredEffort: ReasoningEffort,
    ): ReasoningEffort? {
        val capabilities = ModelCapabilitiesResolver.resolve(profile)
        return preferredEffort.takeIf { effort ->
            effort in capabilities.reasoningEfforts
        } ?: capabilities.defaultReasoningEffort
    }

    /** 当前 profile 的上下文窗口；显式配置优先，其次使用 provider/model 默认能力。 */
    internal fun activeContextWindow(): Int? = activeProfile?.let(::contextWindowFor)

    /** 按会话保存的 profile 解析可用配置；旧会话回退到窗口默认配置。 */
    internal fun profileForConversation(conversation: ChatConversationUiState): ConfigProfile? =
        snapshot.profiles.firstOrNull { it.id == conversation.profileId }
            ?: snapshot.profiles.firstOrNull { it.id == ui.selectedProfileId }
            ?: snapshot.activeProfile

    /** 根据会话绑定的 profile 解析上下文窗口。 */
    internal fun contextWindowForConversation(conversation: ChatConversationUiState): Int? =
        snapshot.profiles.firstOrNull { it.id == conversation.profileId }?.let(::contextWindowFor)
            ?: activeContextWindow()

    /** 解析指定 profile 的上下文窗口。 */
    internal fun contextWindowFor(profile: ConfigProfile): Int? =
        resolveContextWindow(profile)

    /** 在指定对话上执行原子更新。 */
    internal fun mutateConversation(
        conversationId: String,
        schedulePersistence: Boolean = true,
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
        if (schedulePersistence) persistenceCoordinator?.schedule(ui.tasks)
    }

    /** 更新当前活动会话。 */
    internal fun mutateActiveConversation(transform: (ChatConversationUiState) -> ChatConversationUiState) {
        mutateConversation(ui.activeTaskId, transform = transform)
    }

    /** 查找指定对话，如果不存在则返回空。 */
    internal fun findConversationOrNull(conversationId: String): ChatConversationUiState? =
        ui.tasks.firstOrNull { it.id == conversationId }
    /** 切换当前激活对话。 */
    fun selectConversation(conversationId: String) = workspaceController.selectConversation(conversationId)

    /** 重命名指定对话；空白标题保持原样，避免产生无法辨识的侧栏条目。 */
    fun renameConversation(conversationId: String, title: String) = workspaceController.renameConversation(conversationId, title)

    /** 删除指定对话；删除当前对话时优先复用已有空白对话，避免重复创建占位项。 */
    fun deleteConversation(conversationId: String) {
        conversationTreeController.deleteConversation(conversationId)
    }

    /** 在指定工作目录下新建对话并切换焦点。 */
    fun createConversationForWorkspace(workspacePath: String) = workspaceController.createConversationForWorkspace(workspacePath)

    /** 返回仍未关联工作目录的旧版历史数量。 */
    val legacyUnlinkedHistoryCount: Int
        get() = workspaceController.legacyUnlinkedHistoryCount

    /** 将旧版无来源隐藏历史批量恢复到用户明确选择的目录。 */
    fun restoreLegacyUnlinkedHistory(workspacePath: String): String? = workspaceController.restoreLegacyUnlinkedHistory(workspacePath)

    /** 返回当前会话不可执行时应展示的工作目录说明。 */
    fun workspaceIssue(conversation: ChatConversationUiState): String? = workspaceController.workspaceIssue(conversation)

    /** 返回指定目录不可执行时的用户可读原因。 */
    fun workspaceIssueForPath(workspacePath: String): String? = workspaceController.workspaceIssueForPath(workspacePath)

    /** 更新一个工作区下所有任务的显示名称与目录；目标目录已有历史时合并。 */
    fun editWorkspace(
        previousPath: String,
        name: String,
        path: String,
    ): String? = workspaceController.editWorkspace(previousPath, name, path)

    /** 将一个已关联工作区完整迁移至新目录，并在需要时与目标目录历史合并。 */
    fun relinkWorkspace(previousPath: String, path: String): String? = workspaceController.relinkWorkspace(previousPath, path)

    /**
     * 解除工作区目录关联，并删除不含历史的默认占位任务。
     *
     * 若正在删除当前工作区，则切换到最近使用且仍可访问的其他工作区的新任务；没有候选时回到欢迎页。
     */
    fun disconnectWorkspace(workspacePath: String) = workspaceController.disconnectWorkspace(workspacePath)

    /** 为一条未关联或失效历史任务重新选择可执行工作目录。 */
    fun relinkConversationWorkspace(conversationId: String, workspacePath: String): String? = workspaceController.relinkConversationWorkspace(conversationId, workspacePath)

    /** 为当前会话挂载附件。 */
    fun attachFiles(paths: List<String>) = attachmentController.attachFiles(paths)

    /**
     * 将工作区内的文本文件作为 `@` 引用插入。真实路径与工作区真实路径比较，符号链接也不能借此
     * 逃出项目边界。
     */
    fun attachWorkspaceFile(path: String): String? = attachmentController.attachWorkspaceFile(path)

    /** 把剪贴板 PNG 存入会话媒体库并在插入点写入稳定的“图 N” token。 */
    fun addClipboardImage(image: ClipboardPngImage): String? = attachmentController.addClipboardImage(image)

    /** 从当前会话输入区移除指定路径的附件并重新估算上下文占用。 */
    fun removeAttachment(path: String) = attachmentController.removeAttachment(path)

    /** 兼容旧调用方式的直接发送入口。 */
    fun send(message: String) = runController.send(message)

    /** 取消当前正在执行的轮次，并恢复到可继续输入的空闲态。 */
    fun cancelActiveRun() = runController.cancelActiveRun()

    /** 暂停当前运行并保留可继续的 Koog 恢复点。 */
    fun pauseActiveRun() = recoveryController.pauseActiveRun()

    /** 从检查点继续当前暂停或中断的会话。 */
    fun resumeActiveRun() = recoveryController.resumeActiveRun()

    /** 回答当前挂起问题，并恢复同一轮 agent 执行。 */
    fun answerPendingQuestion(answer: String) = runController.answerPendingQuestion(answer)

    /** 一次提交当前批量问题的完整回答，并恢复发起问题的同一轮 Agent。 */
    fun answerPendingQuestions(answers: List<QuestionAnswer>) = runController.answerPendingQuestions(answers)

    /** 提交当前挂起审批；拒绝时停止当前 agent 轮次，其余选择恢复同一轮执行。 */
    fun answerPendingApproval(response: ApprovalResponse) = runController.answerPendingApproval(response)

    /** 兼容既有二元审批调用。 */
    fun answerPendingApproval(approved: Boolean) = runController.answerPendingApproval(approved)

    /** 发送当前草稿，并把流式结果归入当前活动会话。 */
    fun sendDraft() = runController.sendDraft()

    /** 当指定会话结束或失败后，清理挂起请求的归属记录。 */
    internal fun clearPendingOwnership(conversationId: String) = runController.clearPendingOwnership(conversationId)

    /** 以独立、无工具的标题生成器更新首条用户消息所在会话。 */
    internal fun requestConversationTitle(
        conversationId: String,
        firstUserMessage: String,
        profile: ConfigProfile,
    ) = titleController.requestConversationTitle(conversationId, firstUserMessage, profile)

    /** 判断会话是否能从首条用户消息显式重新生成标题。 */
    fun canRegenerateConversationTitle(conversationId: String): Boolean =
        titleController.canRegenerateConversationTitle(conversationId)

    /** 启动与任务运行状态相互独立的标题重新生成。 */
    fun regenerateConversationTitle(conversationId: String): Boolean =
        titleController.regenerateConversationTitle(conversationId)

    /** 取消标题任务，并递增版本以阻止迟到结果覆盖当前状态。 */
    internal fun invalidateConversationTitleGeneration(conversationId: String) = titleController.invalidateConversationTitleGeneration(conversationId)

    /** 恢复持久化任务前，取消所有窗口内尚未完成的标题生成请求。 */
    internal fun invalidateAllConversationTitleGenerations() = titleController.invalidateAllConversationTitleGenerations()

}
