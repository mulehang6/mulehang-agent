package com.agent.app.chat.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.agent.api.BranchSummaryRequest
import com.agent.shared.agent.api.GeneratedBranchSummary
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.conversationEntryPath
import com.agent.shared.chat.model.copyConversationEntryPath
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * 管理会话外层父子树与单个会话内部条目树，窗口状态只保留对 UI 的门面。
 */
internal class ConversationTreeController(
    private val window: ChatWindowState,
) {
    /** 供弹窗禁用重复提交并展示摘要进行状态。 */
    var summaryInProgress: Boolean by mutableStateOf(false)
        private set

    /** 返回树会话中可作为独立新会话起点的全部用户消息。 */
    fun newSessionCandidates(conversationId: String): List<ConversationEntry.Message> =
        window.findConversationOrNull(conversationId)
            ?.takeIf { it.treeFormatVersion > 0 }
            ?.entries
            ?.filterIsInstance<ConversationEntry.Message>()
            ?.filter { it.message.role == ChatRole.User }
            .orEmpty()

    /** 从所选用户消息之前创建独立子会话，并把原输入恢复到 composer。 */
    fun createConversationFromUserEntry(
        conversationId: String,
        userEntryId: String,
    ): ConversationTreeOperationResult {
        val source = window.findConversationOrNull(conversationId)
            ?: return failure("会话不存在。")
        if (source.treeFormatVersion <= 0) return failure("旧版会话不支持从消息新建会话。")
        val selected = source.entries.firstOrNull { it.id == userEntryId } as? ConversationEntry.Message
            ?: return failure("请选择一条用户消息。")
        if (selected.message.role != ChatRole.User) return failure("只能从用户消息新建会话。")
        window.cancelRunIfOwnedBy(conversationId)
        val copied = copyConversationEntryPath(source.entries, selected.parentId, ::newId)
        val restoredDraft = draftFromInputParts(selected.inputParts)
        val child = newConversation(
            workspacePath = source.workspacePath,
            contextWindow = window.contextWindowForConversation(source),
            profileId = source.profileId,
            reasoningEffort = source.reasoningEffort,
            permissionPreset = source.permissionPreset,
        ).copy(
            title = derivedConversationTitle(source.title, "fork"),
            titleState = ConversationTitleState.GENERATED,
            workspaceName = source.workspaceName,
            parentConversationId = source.id,
            forkedFromEntryId = selected.id,
            entries = copied.entries,
            activeEntryId = copied.activeEntryId,
            headEntryId = copied.activeEntryId,
        ).withEntryProjection()
        window.ui = window.ui.copy(
            tasks = listOf(child) + window.ui.tasks,
            composerFocusRequestId = window.ui.composerFocusRequestId + 1L,
        )
        window.showExistingConversation(
            child.id,
            ComposerDraft(restoredDraft.text, restoredDraft.text.length, restoredDraft.attachments),
        )
        window.persistenceCoordinator?.schedule(window.ui.tasks)
        return success()
    }

    /**
     * 从一条真实用户消息开始编辑当前会话；只有重新发送时才会追加新的兄弟分支。
     */
    suspend fun editFromUserEntry(
        conversationId: String,
        userEntryId: String,
        summary: BranchNavigationSummary = BranchNavigationSummary(),
    ): ConversationTreeOperationResult {
        val source = window.findConversationOrNull(conversationId)
            ?: return failure("会话不存在。")
        if (source.treeFormatVersion <= 0) return failure("旧版会话不支持从此处编辑。")
        val selected = source.entries.firstOrNull { it.id == userEntryId } as? ConversationEntry.Message
            ?: return failure("请选择一条用户消息。")
        if (selected.message.role != ChatRole.User) return failure("只能从用户消息开始编辑。")
        val result = navigateToEntry(conversationId, userEntryId, summary)
        if (result.succeeded) {
            window.ui = window.ui.copy(composerFocusRequestId = window.ui.composerFocusRequestId + 1L)
        }
        return result
    }

    /** 回到指定用户消息发送前，并仅将该消息的原始输入恢复到对应草稿。 */
    suspend fun rollbackUserTurn(
        conversationId: String,
        userEntryId: String,
        restoreFiles: Boolean = false,
    ): ConversationTreeOperationResult {
        val source = window.findConversationOrNull(conversationId) ?: return failure("会话不存在。")
        val selected = source.entries.firstOrNull { it.id == userEntryId } as? ConversationEntry.Message
            ?: return failure("用户消息不存在。")
        if (selected.message.role != ChatRole.User) return failure("只能回退用户消息。")
        val coordinator = window.persistenceCoordinator ?: return failure("会话持久化不可用。")
        val runningJob = window.activeRunJob.takeIf { window.activeRunConversationId == conversationId }
        window.cancelRunIfOwnedBy(conversationId)
        runningJob?.join()
        val outcome = runCatching { coordinator.rollbackUserTurn(conversationId, userEntryId, restoreFiles) }
            .getOrElse { error -> return failure(error.message ?: "消息回退失败。") }
            ?: return failure("这条消息没有可用的回退点。")
        val restoredTasks = outcome.tasks.map(window::withAgentStatus)
        window.invalidateConversationTitleGeneration(conversationId)
        window.clearPendingOwnership(conversationId)
        val restoredDraft = draftFromInputParts(selected.inputParts)
        val draft = ComposerDraft(restoredDraft.text, restoredDraft.text.length, restoredDraft.attachments)
        window.ui = window.ui.copy(tasks = restoredTasks)
        if (restoredTasks.none { it.id == conversationId }) {
            window.showNewConversation(source.workspacePath)
            window.updateDraft(draft.text, draft.selectionStart)
            window.updateDraftAttachments(draft.attachments)
            window.forgetConversationDraft(conversationId)
        } else {
            window.showExistingConversation(conversationId, draft)
        }
        window.ui = window.ui.copy(composerFocusRequestId = window.ui.composerFocusRequestId + 1L)
        return if (outcome.fileSummary.skipped.isEmpty()) success()
        else success("会话已回退；${outcome.fileSummary.skipped.size} 个文件已变化，未覆盖。")
    }

    /** 克隆 root 到当前 leaf 的单一路径，不复制源会话的其他分支。 */
    fun cloneConversation(conversationId: String): ConversationTreeOperationResult {
        val source = window.findConversationOrNull(conversationId)
            ?: return failure("会话不存在。")
        if (source.treeFormatVersion <= 0) return failure("旧版会话不支持克隆。")
        window.cancelRunIfOwnedBy(conversationId)
        val copied = copyConversationEntryPath(source.entries, source.activeEntryId, ::newId)
        val child = newConversation(
            workspacePath = source.workspacePath,
            contextWindow = window.contextWindowForConversation(source),
            profileId = source.profileId,
            reasoningEffort = source.reasoningEffort,
            permissionPreset = source.permissionPreset,
        ).copy(
            title = derivedConversationTitle(source.title, "clone"),
            titleState = ConversationTitleState.GENERATED,
            workspaceName = source.workspaceName,
            parentConversationId = source.id,
            entries = copied.entries,
            activeEntryId = copied.activeEntryId,
            headEntryId = copied.activeEntryId,
        ).withEntryProjection()
        window.ui = window.ui.copy(
            tasks = listOf(child) + window.ui.tasks,
        )
        window.showExistingConversation(child.id, ComposerDraft())
        window.persistenceCoordinator?.schedule(window.ui.tasks)
        return success()
    }

    /** 判断导航是否会放弃当前 leaf 之后的一段分支，从而需要展示摘要选择。 */
    fun wouldLeaveActiveBranch(conversationId: String, entryId: String): Boolean {
        val conversation = window.findConversationOrNull(conversationId) ?: return false
        val selected = conversation.entries.firstOrNull { it.id == entryId } ?: return false
        return entriesToSummarize(conversation, destinationLeafId(selected)).isNotEmpty()
    }

    /** 判断当前活动 leaf 是否偏离会话持久末端。 */
    fun isAwayFromHead(conversationId: String): Boolean =
        window.findConversationOrNull(conversationId)?.let { conversation ->
            conversation.treeFormatVersion > 0 && conversation.activeEntryId != conversation.headEntryId
        } == true

    /**
     * 采用 Pi 语义导航条目：user/custom 回到父节点并恢复输入，其他条目直接成为 leaf。
     */
    suspend fun navigateToEntry(
        conversationId: String,
        entryId: String,
        summary: BranchNavigationSummary = BranchNavigationSummary(),
    ): ConversationTreeOperationResult {
        if (summaryInProgress) return failure("正在生成分支摘要，请稍后再试。")
        val source = window.findConversationOrNull(conversationId)
            ?: return failure("会话不存在。")
        if (source.treeFormatVersion <= 0) return failure("旧版会话没有条目树。")
        val selected = source.entries.firstOrNull { it.id == entryId }
            ?: return failure("条目不存在。")
        val destinationLeafId = destinationLeafId(selected)
        val restored = when (selected) {
            is ConversationEntry.Message -> selected.inputParts.takeIf { selected.message.role == ChatRole.User }
            is ConversationEntry.Custom -> selected.inputParts
            else -> null
        }?.let(::draftFromInputParts) ?: RestoredDraft("", emptyList())
        return navigateToLeaf(conversationId, source, destinationLeafId, summary, restored)
    }

    /** 精确切换到分支末端，不触发 user/custom 的重新编辑语义，并保留当前草稿。 */
    suspend fun switchToLeaf(
        conversationId: String,
        leafEntryId: String,
        summary: BranchNavigationSummary = BranchNavigationSummary(),
    ): ConversationTreeOperationResult {
        if (summaryInProgress) return failure("正在生成分支摘要，请稍后再试。")
        val source = window.findConversationOrNull(conversationId)
            ?: return failure("会话不存在。")
        if (source.treeFormatVersion <= 0) return failure("旧版会话没有条目树。")
        if (source.entries.none { it.id == leafEntryId }) return failure("分支末端不存在。")
        return navigateToLeaf(conversationId, source, leafEntryId, summary, restoredDraft = null)
    }

    /** 返回会话持久末端，保留 composer 草稿并沿用同一套离开分支摘要流程。 */
    suspend fun returnToHead(
        conversationId: String,
        summary: BranchNavigationSummary = BranchNavigationSummary(),
    ): ConversationTreeOperationResult {
        if (summaryInProgress) return failure("正在生成分支摘要，请稍后再试。")
        val source = window.findConversationOrNull(conversationId)
            ?: return failure("会话不存在。")
        val headEntryId = source.headEntryId ?: return success()
        if (source.entries.none { it.id == headEntryId }) return failure("会话末端已损坏。")
        return navigateToLeaf(conversationId, source, headEntryId, summary, restoredDraft = null)
    }

    /** 完成摘要生成、并发校验和投影提交；[restoredDraft] 为空时保持当前 composer。 */
    private suspend fun navigateToLeaf(
        conversationId: String,
        source: ChatConversationUiState,
        destinationLeafId: String?,
        summary: BranchNavigationSummary,
        restoredDraft: RestoredDraft?,
    ): ConversationTreeOperationResult {
        val entriesBeingLeft = entriesToSummarize(source, destinationLeafId)
        val isLeavingCurrentPath = entriesBeingLeft.isNotEmpty()
        val customInstructions = summary.customPrompt.trim().takeIf(String::isNotEmpty)
        if (isLeavingCurrentPath && summary.mode == BranchSummaryMode.CUSTOM && customInstructions == null) {
            return failure("自定义摘要提示不能为空。")
        }
        window.cancelRunIfOwnedBy(conversationId)
        val branchEntries = if (isLeavingCurrentPath && summary.mode != BranchSummaryMode.NONE) {
            entriesBeingLeft
        } else {
            emptyList()
        }
        val generatedSummary = if (branchEntries.isNotEmpty()) {
            generateBranchSummary(
                conversation = source,
                entries = branchEntries,
                customInstructions = customInstructions.takeIf { summary.mode == BranchSummaryMode.CUSTOM },
            ).getOrElse { error -> return failure(error.message ?: "分支摘要生成失败。") }
        } else {
            null
        }
        val current = window.findConversationOrNull(conversationId) ?: return failure("会话不存在。")
        if (current.activeEntryId != source.activeEntryId) return failure("会话路径已变化，请重新选择。")
        var nextEntries = current.entries
        var nextLeafId = destinationLeafId
        if (generatedSummary != null) {
            val summaryEntry = ConversationEntry.BranchSummary(
                id = newId(),
                parentId = destinationLeafId,
                createdAt = window.clock(),
                fromEntryId = source.activeEntryId.orEmpty(),
                summary = generatedSummary.summary,
                details = branchSummaryDetails(branchEntries, customInstructions),
                inputTokens = generatedSummary.inputTokens,
                outputTokens = generatedSummary.outputTokens,
            )
            nextEntries = nextEntries + summaryEntry
            nextLeafId = summaryEntry.id
        }
        val nextConversation = current.withEntryProjection(
            entries = nextEntries,
            activeEntryId = nextLeafId,
            headEntryId = current.headEntryId,
        ).copy(
            streamingAssistantEntryId = null,
            streamingReasoningEntryId = null,
        )
        window.ui = window.ui.copy(
            tasks = window.ui.tasks.map { if (it.id == conversationId) nextConversation else it },
        )
        window.showExistingConversation(
            conversationId,
            restoredDraft?.let { ComposerDraft(it.text, it.text.length, it.attachments) },
        )
        window.persistenceCoordinator?.schedule(window.ui.tasks)
        return success()
    }

    /** 为目标条目追加或清除标签。 */
    fun setEntryLabel(
        conversationId: String,
        entryId: String,
        label: String,
    ): ConversationTreeOperationResult {
        val source = window.findConversationOrNull(conversationId)
            ?: return failure("会话不存在。")
        if (source.treeFormatVersion <= 0 || source.entries.none { it.id == entryId }) {
            return failure("条目不存在或会话不支持标签。")
        }
        val normalizedLabel = label.trim()
        val currentLabel = source.entries.filterIsInstance<ConversationEntry.Label>()
            .lastOrNull { it.targetEntryId == entryId }
            ?.label
            ?.takeIf(String::isNotBlank)
        if (currentLabel == normalizedLabel.takeIf(String::isNotBlank)) return success()
        val entry = ConversationEntry.Label(
            id = newId(),
            parentId = source.activeEntryId,
            createdAt = window.clock(),
            targetEntryId = entryId,
            label = normalizedLabel,
        )
        window.mutateConversation(conversationId) { conversation ->
            conversation.withEntryProjection(conversation.entries + entry, conversation.activeEntryId)
        }
        return success()
    }

    /** 记录活动路径上的模型切换。 */
    fun recordModelChange(conversation: ChatConversationUiState, profileId: String?): ChatConversationUiState =
        recordModelChangeEntry(conversation, profileId, newId(), window.clock())

    /** 记录活动路径上的推理强度切换。 */
    fun recordReasoningEffortChange(
        conversation: ChatConversationUiState,
        effort: ReasoningEffort,
    ): ChatConversationUiState = recordReasoningEffortChangeEntry(conversation, effort, newId(), window.clock())

    /** 判断会话子树是否可以归档。 */
    fun canArchive(conversationId: String): Boolean {
        val subtreeIds = descendantConversationIds(conversationId)
        return subtreeIds.isNotEmpty() && window.ui.tasks
            .filter { it.id in subtreeIds }
            .none { it.executionState.isStoppable() || window.activeRunConversationId == it.id }
    }

    /** 判断单个会话是否满足永久删除的生命周期约束。 */
    fun canDelete(conversationId: String): Boolean {
        return deleteBlockReason(conversationId) == null
    }

    /** 返回会话不能永久删除的具体原因；为空表示可以进入确认流程。 */
    fun deleteBlockReason(conversationId: String): String? {
        val source = window.findConversationOrNull(conversationId) ?: return "会话不存在。"
        return null
    }

    /** 归档选中会话及全部后代，并为当前会话选择安全替代项。 */
    fun archiveConversation(conversationId: String): ConversationTreeOperationResult {
        if (!canArchive(conversationId)) return failure("运行中或等待交互的会话树不能归档。")
        val archivedIds = descendantConversationIds(conversationId)
        val archivedAt = window.clock()
        val target = window.findConversationOrNull(conversationId) ?: return failure("会话不存在。")
        var tasks = window.ui.tasks.map { conversation ->
            if (conversation.id in archivedIds) conversation.copy(archivedAt = archivedAt) else conversation
        }
        var activeTaskId = window.ui.activeTaskId
        var draft = window.ui.draft
        if (activeTaskId in archivedIds) {
            val replacement = tasks
                .filter { it.archivedAt == null && it.workspacePath == target.workspacePath }
                .maxByOrNull(ChatConversationUiState::updatedAt)
            activeTaskId = replacement?.id.orEmpty()
            draft = ""
        }
        window.ui = window.ui.copy(tasks = tasks)
        if (window.ui.activeTaskId in archivedIds) {
            if (activeTaskId.isBlank()) window.showNewConversation(target.workspacePath)
            else window.showExistingConversation(activeTaskId)
        }
        window.persistenceCoordinator?.schedule(window.ui.tasks)
        return success()
    }

    /** 只恢复选中会话；归档父节点缺失时它会在活跃视图中暂时成为根。 */
    fun restoreConversation(conversationId: String): ConversationTreeOperationResult {
        val source = window.findConversationOrNull(conversationId) ?: return failure("会话不存在。")
        if (source.archivedAt == null) return success()
        window.mutateConversation(conversationId) { it.copy(archivedAt = null) }
        return success()
    }

    /** 永久删除单个会话；取消其运行，直接子会话提升为根。 */
    fun deleteConversation(conversationId: String): ConversationTreeOperationResult {
        val source = window.findConversationOrNull(conversationId) ?: return failure("会话不存在。")
        deleteBlockReason(conversationId)?.let { reason -> return failure(reason) }
        window.cancelRunIfOwnedBy(conversationId)
        window.onSessionClosed(conversationId, source.workspacePath)
        window.invalidateConversationTitleGeneration(conversationId)
        window.clearPendingOwnership(conversationId)
        val deletingActive = window.ui.activeTaskId == conversationId
        window.ui = window.ui.copy(
            tasks = window.ui.tasks
                .filterNot { it.id == conversationId }
                .map { conversation ->
                    if (conversation.parentConversationId == conversationId) {
                        conversation.copy(parentConversationId = null)
                    } else {
                        conversation
                    }
                },
        )
        if (deletingActive) window.showNewConversation(source.workspacePath)
        window.forgetConversationDraft(conversationId)
        window.persistenceCoordinator?.schedule(window.ui.tasks)
        return success()
    }

    /** 返回会话本身及其全部后代，并对损坏环路做去重保护。 */
    private fun descendantConversationIds(conversationId: String): Set<String> {
        if (window.findConversationOrNull(conversationId) == null) return emptySet()
        val children = window.ui.tasks.groupBy(ChatConversationUiState::parentConversationId)
        val result = mutableSetOf<String>()
        val pending = ArrayDeque<String>().apply { add(conversationId) }
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (result.add(current)) children[current].orEmpty().forEach { pending.add(it.id) }
        }
        return result
    }

    /** 调用隔离的无工具模型摘要；失败和取消都不会提交 leaf 变化。 */
    private suspend fun generateBranchSummary(
        conversation: ChatConversationUiState,
        entries: List<ConversationEntry>,
        customInstructions: String?,
    ): Result<GeneratedBranchSummary> {
        val generator = window.branchSummaryGenerator
            ?: return Result.failure(IllegalStateException("当前环境没有可用的分支摘要服务。"))
        val profile = window.profileForConversation(conversation)
            ?: return Result.failure(IllegalStateException("当前会话没有可用的模型配置。"))
        summaryInProgress = true
        return try {
            val generated = generator.generate(
                BranchSummaryRequest(
                    branchContent = branchSummaryContent(entries),
                    customInstructions = customInstructions,
                    profile = profile,
                ),
            )
            val summary = generated.summary.trim()
            if (summary.isBlank()) {
                Result.failure(IllegalStateException("模型返回了空的分支摘要。"))
            } else {
                Result.success(generated.copy(summary = summary))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(IllegalStateException("分支摘要生成失败：${error.message ?: "未知错误"}", error))
        } finally {
            summaryInProgress = false
        }
    }

    /** 只选择旧 leaf 到新路径共同祖先之间真正被放弃的条目。 */
    private fun entriesToSummarize(
        conversation: ChatConversationUiState,
        destinationLeafId: String?,
    ): List<ConversationEntry> {
        val sourcePath = conversationEntryPath(conversation.entries, conversation.activeEntryId)
        val destinationIds =
            conversationEntryPath(conversation.entries, destinationLeafId).mapTo(mutableSetOf()) { it.id }
        val commonAncestorId = sourcePath.lastOrNull { it.id in destinationIds }?.id
        return if (commonAncestorId == null) {
            sourcePath
        } else {
            sourcePath.dropWhile { it.id != commonAncestorId }.drop(1)
        }
    }

    /** 按 Pi 语义将可选条目转换成真正的导航 leaf。 */
    private fun destinationLeafId(entry: ConversationEntry): String? = when (entry) {
        is ConversationEntry.Message -> if (entry.message.role == ChatRole.User) entry.parentId else entry.id
        is ConversationEntry.Custom -> entry.parentId
        else -> entry.id
    }

    /** 创建成功结果。 */
    private fun success(message: String? = null): ConversationTreeOperationResult =
        ConversationTreeOperationResult(true, message)

    /** 创建失败结果。 */
    private fun failure(message: String): ConversationTreeOperationResult =
        ConversationTreeOperationResult(false, message)

    /** 创建稳定条目标识。 */
    private fun newId(): String = UUID.randomUUID().toString()

}
