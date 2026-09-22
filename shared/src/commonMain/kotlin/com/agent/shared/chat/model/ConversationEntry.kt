package com.agent.shared.chat.model

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.tool.model.FileDiffPreview
import com.agent.shared.tool.model.QuestionAnswer

/** 新会话采用的条目树格式版本。 */
const val CURRENT_CONVERSATION_TREE_FORMAT_VERSION: Int = 1

/**
 * 会话中的稳定条目节点。
 *
 * 条目只保存父指针；一个会话内的全部条目共同组成可保留兄弟分支的森林。
 */
sealed interface ConversationEntry {
    /** 条目的稳定标识。 */
    val id: String

    /** 父条目标识；为空时表示根节点。 */
    val parentId: String?

    /** 条目创建时间，用于树内稳定排序。 */
    val createdAt: Long

    /** 标准用户或助手消息。 */
    data class Message(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val message: ChatMessage,
        val inputParts: List<UserInputPart> = if (message.role == ChatRole.User) {
            listOf(UserInputPart.Text(message.content))
        } else {
            emptyList()
        },
    ) : ConversationEntry

    /** 助手推理块，可在流式阶段原地更新。 */
    data class Reasoning(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val summaryText: String? = null,
        val rawText: String? = null,
        val expanded: Boolean = true,
        val isStreaming: Boolean = true,
        val startedAtMillis: Long = createdAt,
        val durationMillis: Long? = null,
    ) : ConversationEntry

    /** 助手发起的一次工具调用。 */
    data class ToolCall(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val toolName: String,
        val preview: String? = null,
        val operationIntent: String? = null,
        val toolCallId: String? = null,
        val resultDisplay: String? = null,
        val fileDiffs: List<FileDiffPreview> = emptyList(),
    ) : ConversationEntry

    /** 工具调用返回的结果。 */
    data class ToolResult(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val toolName: String,
        val status: ToolEventStatus,
        val errorMessage: String? = null,
        val toolCallId: String? = null,
        val resultPreview: String? = null,
        val resultDisplay: String? = null,
        val fileDiffs: List<FileDiffPreview> = emptyList(),
    ) : ConversationEntry

    /** 用户已提交的一组交互式问题答案。 */
    data class Answers(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val answers: List<QuestionAnswer>,
    ) : ConversationEntry

    /** 从离开的分支生成并注入模型上下文的摘要。 */
    data class BranchSummary(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val fromEntryId: String,
        val summary: String,
        val details: String? = null,
        val inputTokens: Long? = null,
        val outputTokens: Long? = null,
    ) : ConversationEntry

    /** 作用于目标条目的用户标签；标签不进入模型上下文。 */
    data class Label(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val targetEntryId: String,
        val label: String,
    ) : ConversationEntry

    /** 路径上选用的模型配置变更。 */
    data class ModelChange(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val profileId: String?,
    ) : ConversationEntry

    /** 路径上选用的推理强度变更。 */
    data class ReasoningEffortChange(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val reasoningEffort: String,
    ) : ConversationEntry

    /** 项目保留的自定义展示事件。 */
    data class Custom(
        override val id: String,
        override val parentId: String?,
        override val createdAt: Long,
        val type: String,
        val text: String,
        val inputParts: List<UserInputPart> = listOf(UserInputPart.Text(text)),
    ) : ConversationEntry
}

/** 活动路径投影得到的界面时间线、模型历史和路径设置。 */
data class ConversationEntryProjection(
    val path: List<ConversationEntry>,
    val timeline: List<ConversationItem>,
    val history: List<AgentConversationHistoryMessage>,
    val profileId: String?,
    val reasoningEffort: String?,
    val labels: Map<String, String>,
)

/** 复制单条祖先路径后得到的新条目集合与 leaf。 */
data class CopiedConversationPath(
    val entries: List<ConversationEntry>,
    val activeEntryId: String?,
)

/**
 * 返回从根到 [leafId] 的单一路径；孤儿节点从自身起算，环路在首次重复处截断。
 */
fun conversationEntryPath(
    entries: List<ConversationEntry>,
    leafId: String?,
): List<ConversationEntry> {
    if (leafId == null) return emptyList()
    val byId = entries.associateBy(ConversationEntry::id)
    val visited = mutableSetOf<String>()
    val reversed = buildList {
        var current = byId[leafId]
        while (current != null && visited.add(current.id)) {
            add(current)
            current = current.parentId?.let(byId::get)
        }
    }
    return reversed.asReversed()
}

/** 返回当前活动路径上的最新标签，空标签用于移除已有标签。 */
fun conversationEntryLabels(path: List<ConversationEntry>): Map<String, String> = buildMap {
    path.filterIsInstance<ConversationEntry.Label>().forEach { entry ->
        if (entry.label.isBlank()) remove(entry.targetEntryId) else put(entry.targetEntryId, entry.label)
    }
}

/** 将活动 leaf 的祖先路径投影为现有界面和 Agent 网关可消费的结构。 */
fun projectConversationEntries(
    entries: List<ConversationEntry>,
    leafId: String?,
): ConversationEntryProjection {
    val path = conversationEntryPath(entries, leafId)
    val pathIds = path.mapTo(mutableSetOf(), ConversationEntry::id)
    val timeline = mutableListOf<ConversationItem>()
    val history = mutableListOf<AgentConversationHistoryMessage>()
    var profileId: String? = null
    var reasoningEffort: String? = null

    path.forEach { entry ->
        when (entry) {
            is ConversationEntry.Message -> {
                timeline += ChatMessageItem(entry.message)
                if (entry.message.role == ChatRole.User) {
                    history += AgentConversationHistoryMessage.User(entry.message.content, entry.inputParts)
                } else if (entry.message.role == ChatRole.Assistant) {
                    history.appendAssistantPart(AgentConversationHistoryPart.Text(entry.message.content))
                }
            }

            is ConversationEntry.Reasoning -> {
                timeline += ReasoningItem(
                    summaryText = entry.summaryText,
                    rawText = entry.rawText,
                    expanded = entry.expanded,
                    isStreaming = entry.isStreaming,
                    startedAtMillis = entry.startedAtMillis,
                    durationMillis = entry.durationMillis,
                )
                history.appendAssistantPart(
                    AgentConversationHistoryPart.Reasoning(entry.summaryText, entry.rawText),
                )
            }

            is ConversationEntry.ToolCall -> {
                timeline += ToolEventItem(
                    toolName = entry.toolName,
                    status = ToolEventStatus.Started,
                    preview = entry.preview,
                    operationIntent = entry.operationIntent,
                    toolCallId = entry.toolCallId,
                    resultDisplay = entry.resultDisplay,
                    fileDiffs = entry.fileDiffs,
                )
                history.appendAssistantPart(
                    AgentConversationHistoryPart.ToolCall(entry.toolCallId, entry.toolName, entry.preview),
                )
            }

            is ConversationEntry.ToolResult -> {
                timeline.completeToolEvent(entry)
                history.appendAssistantPart(
                    AgentConversationHistoryPart.ToolResult(entry.toolCallId, entry.toolName, entry.resultPreview),
                )
            }

            is ConversationEntry.Answers -> timeline += AnsweredQuestionsItem(entry.answers)
            is ConversationEntry.BranchSummary -> history += AgentConversationHistoryMessage.User(
                content = branchSummaryContext(entry.summary),
            )

            is ConversationEntry.ModelChange -> profileId = entry.profileId
            is ConversationEntry.ReasoningEffortChange -> reasoningEffort = entry.reasoningEffort
            is ConversationEntry.Custom -> {
                timeline += ChatMessageItem(ChatMessage(ChatRole.User, entry.text))
                history += AgentConversationHistoryMessage.User(entry.text, entry.inputParts)
            }

            is ConversationEntry.Label -> Unit
        }
    }
    return ConversationEntryProjection(
        path = path,
        timeline = timeline,
        history = history,
        profileId = profileId,
        reasoningEffort = reasoningEffort,
        labels = conversationEntryLabels(
            entries.filter { entry ->
                entry is ConversationEntry.Label && entry.targetEntryId in pathIds
            },
        ),
    )
}

/** 使用 Pi 兼容的固定边界标记包装分支摘要。 */
fun branchSummaryContext(summary: String): String =
    "The following is a summary of a branch that this conversation came back from:\n\n<summary>\n$summary\n</summary>"

/**
 * 复制 root 到目标 leaf 的单一路径并生成全新条目标识，不携带源会话的兄弟分支。
 */
fun copyConversationEntryPath(
    entries: List<ConversationEntry>,
    leafId: String?,
    idFactory: () -> String,
): CopiedConversationPath {
    val path = conversationEntryPath(entries, leafId)
    val pathIds = path.mapTo(mutableSetOf(), ConversationEntry::id)
    val metadataLabels = entries.filterIsInstance<ConversationEntry.Label>()
        .filter { entry -> entry.id !in pathIds && entry.targetEntryId in pathIds }
    val entriesToCopy = path + metadataLabels
    val idMapping = entriesToCopy.associate { entry -> entry.id to idFactory() }
    val copied = entriesToCopy.map { entry ->
        entry.withIdentity(
            id = idMapping.getValue(entry.id),
            parentId = entry.parentId?.let(idMapping::get),
            idMapping = idMapping,
        )
    }
    return CopiedConversationPath(copied, leafId?.let(idMapping::get))
}

/** 返回替换稳定标识与父指针后的同类型条目。 */
private fun ConversationEntry.withIdentity(
    id: String,
    parentId: String?,
    idMapping: Map<String, String>,
): ConversationEntry = when (this) {
    is ConversationEntry.Message -> copy(id = id, parentId = parentId)
    is ConversationEntry.Reasoning -> copy(id = id, parentId = parentId)
    is ConversationEntry.ToolCall -> copy(id = id, parentId = parentId)
    is ConversationEntry.ToolResult -> copy(id = id, parentId = parentId)
    is ConversationEntry.Answers -> copy(id = id, parentId = parentId)
    is ConversationEntry.BranchSummary -> copy(
        id = id,
        parentId = parentId,
        fromEntryId = idMapping[fromEntryId] ?: fromEntryId,
    )
    is ConversationEntry.Label -> copy(
        id = id,
        parentId = parentId,
        targetEntryId = idMapping[targetEntryId] ?: targetEntryId,
    )
    is ConversationEntry.ModelChange -> copy(id = id, parentId = parentId)
    is ConversationEntry.ReasoningEffortChange -> copy(id = id, parentId = parentId)
    is ConversationEntry.Custom -> copy(id = id, parentId = parentId)
}

/** 将助手片段并入最后一条助手历史，必要时创建新的助手历史。 */
private fun MutableList<AgentConversationHistoryMessage>.appendAssistantPart(
    part: AgentConversationHistoryPart,
) {
    val assistant = lastOrNull() as? AgentConversationHistoryMessage.Assistant
    if (assistant == null) {
        add(AgentConversationHistoryMessage.Assistant(listOf(part)))
    } else {
        this[lastIndex] = assistant.copy(parts = assistant.parts + part)
    }
}

/** 用工具结果完成最近的匹配工具卡；没有匹配调用时追加独立结果卡。 */
private fun MutableList<ConversationItem>.completeToolEvent(result: ConversationEntry.ToolResult) {
    val matchingIndex = indices.lastOrNull { index ->
        val item = this[index] as? ToolEventItem ?: return@lastOrNull false
        item.status == ToolEventStatus.Started &&
                (result.toolCallId?.let { it == item.toolCallId } ?: (item.toolName == result.toolName))
    }
    val completed = ToolEventItem(
        toolName = result.toolName,
        status = result.status,
        errorMessage = result.errorMessage,
        toolCallId = result.toolCallId,
        resultPreview = result.resultPreview,
        resultDisplay = result.resultDisplay,
        fileDiffs = result.fileDiffs,
    )
    if (matchingIndex == null) {
        add(completed)
    } else {
        val call = this[matchingIndex] as ToolEventItem
        this[matchingIndex] = completed.copy(
            preview = call.preview,
            operationIntent = call.operationIntent,
            fileDiffs = result.fileDiffs.ifEmpty { call.fileDiffs },
            resultDisplay = result.resultDisplay ?: call.resultDisplay,
        )
    }
}
