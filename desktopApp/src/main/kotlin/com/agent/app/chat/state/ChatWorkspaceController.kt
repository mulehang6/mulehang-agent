package com.agent.app.chat.state

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.model.PermissionPreset

/** 管理会话与工作区的关联、迁移和历史恢复。 */
internal class ChatWorkspaceController(private val window: ChatWindowState) {
    /**
     * 切换当前激活对话。
     */
    fun selectConversation(conversationId: String) {
        with(window) {
            if (findConversationOrNull(conversationId) != null) {
                showExistingConversation(conversationId)
                refreshResourceSnapshotFor(ui.activeConversationOrNull?.workspacePath.orEmpty())
            }
        }
    }

    /**
     * 重命名指定对话；空白标题保持原样，避免产生无法辨识的侧栏条目。
     */
    fun renameConversation(conversationId: String, title: String) {
        with(window) {
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
    }

    /**
     * 删除指定对话；删除当前对话时优先复用已有空白对话，避免重复创建占位项。
     */
    fun deleteConversation(conversationId: String) {
        with(window) {
            val deletedConversation = findConversationOrNull(conversationId) ?: return
            onSessionClosed(conversationId, deletedConversation.workspacePath)
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
    }

    /** 在指定工作目录下切换到进程内新会话页，直到首次发送才创建真实会话。 */
    fun createConversationForWorkspace(workspacePath: String) {
        with(window) {
            val normalizedPath = workspacePath.trim()
            onWorkspaceSelected(normalizedPath)
            val restoredTasks = restoreDetachedWorkspaceHistory(
                conversations = ui.tasks,
                workspacePath = normalizedPath,
            )
            if (restoredTasks != ui.tasks) {
                ui = ui.copy(tasks = restoredTasks)
                persistenceCoordinator?.schedule(ui.tasks)
            }
            showNewConversation(normalizedPath)
        }
    }

    /** 返回尚无来源目录的旧版隐藏历史数量，供界面在批量恢复前请求确认。 */
    val legacyUnlinkedHistoryCount: Int
        get() = with(window) { ui.tasks.count { conversation ->
            conversation.workspacePath.isBlank() &&
                    conversation.detachedWorkspacePath == null &&
                    !conversation.isEmptyDefaultConversation()
        }

    }
    /** 将旧版无来源隐藏历史批量恢复到用户明确选择的目录。 */
    fun restoreLegacyUnlinkedHistory(workspacePath: String): String? {
        with(window) {
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
    }

    /** 返回当前会话不可执行时应展示的工作目录说明。 */
    fun workspaceIssue(conversation: ChatConversationUiState): String? {
        return with(window) {
            workspaceIssueForPath(conversation.workspacePath)
        }
    }

    /** 返回指定目录不可执行时的用户可读原因。 */
    fun workspaceIssueForPath(workspacePath: String): String? {
        return with(window) {
            when {
            workspacePath.isBlank() -> "此历史任务尚未关联工作目录。"
            !workspaceDirectoryExists(workspacePath) -> "工作目录已不存在：$workspacePath"
            else -> null
        }
        }
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
        with(window) {
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
    }

    /**
     * 解除工作区目录关联，并删除不含历史的默认占位任务。
     *
     * 若正在删除当前工作区，则切换到最近使用且仍可访问的其他工作区的新任务；没有候选时回到欢迎页。
     */
    fun disconnectWorkspace(workspacePath: String) {
        with(window) {
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
            ui = ui.copy(
                tasks = retainedTasks,
            )
            if (isDisconnectingActiveWorkspace || ui.activeTaskId in removedTaskIds ||
                (ui.activeTaskId.isBlank() && ui.newWorkspacePath == workspacePath)
            ) showNewConversation(fallbackWorkspacePath.orEmpty())
            persistenceCoordinator?.schedule(ui.tasks)
        }
    }

    /** 为一条未关联或失效历史任务重新选择可执行工作目录。 */
    fun relinkConversationWorkspace(conversationId: String, workspacePath: String): String? {
        with(window) {
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
    }

    /**
     * 判断新建会话时是否应覆盖当前空白占位会话，避免侧栏出现两个“新对话”。
     */
    private fun shouldReplaceActiveEmptyConversation(workspacePath: String): Boolean {
        with(window) {
            val activeConversation = ui.activeConversationOrNull ?: return false
            return activeConversation.workspacePath == workspacePath && activeConversation.isEmptyDefaultConversation()
        }
    }

    /**
     * 在移除当前工作区前，从其他仍可访问的工作区中找出最近更新的目录。
     *
     * 相同更新时间沿用任务列表顺序，保证选择结果稳定。
     */
    private fun findRecentAvailableWorkspacePath(excludedWorkspacePath: String): String? {
        return with(window) {
            ui.tasks
            .asSequence()
            .filter { conversation ->
                conversation.workspacePath.isNotBlank() &&
                        conversation.workspacePath != excludedWorkspacePath &&
                        workspaceDirectoryExists(conversation.workspacePath)
            }
            .maxByOrNull(ChatConversationUiState::updatedAt)
            ?.workspacePath
                ?: ui.newWorkspacePath.takeIf { path ->
                    path.isNotBlank() && path != excludedWorkspacePath && workspaceDirectoryExists(path)
                }
        }
    }

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
        with(window) {
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
    }

}
