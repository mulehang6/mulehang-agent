package com.agent.app.chat.component

import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.conversationEntryPath

/** Pi 会话树提供的五档筛选。 */
internal enum class ConversationEntryFilter(val label: String, val description: String) {
    DEFAULT("默认", "隐藏设置与标签记录"),
    NO_TOOLS("隐藏工具", "同时隐藏工具调用与结果"),
    USER_ONLY("仅用户", "只显示用户消息"),
    LABELED_ONLY("已标记", "只显示已添加标签的条目"),
    ALL("全部", "显示所有条目，包括设置记录"),
}

/** 会话条目在 Pi 式扁平树中的一行展示信息。 */
internal data class ConversationEntryTreeRow(
    val entry: ConversationEntry,
    val indent: Int,
    val visibleParentId: String?,
    val visibleChildIds: List<String>,
    val isLastSibling: Boolean,
    val showConnector: Boolean,
    val foldable: Boolean,
)

/**
 * 按 Pi 的规则把条目图压成可读列表。
 *
 * 单子节点链保持相同缩进；只有真正出现兄弟分支时才增加层级。分支后的第一段
 * 单链额外缩进一次，用于让分支归属清晰，后续线性条目不再继续向右漂移。
 */
internal fun flattenConversationEntryTree(
    entries: List<ConversationEntry>,
    visibleIds: Set<String>,
    activeEntryId: String?,
    collapsedIds: Set<String> = emptySet(),
): List<ConversationEntryTreeRow> {
    val byId = entries.associateBy(ConversationEntry::id)
    val displayEntries = entries.filter { it.id in visibleIds }
    val displayIds = displayEntries.mapTo(mutableSetOf(), ConversationEntry::id)
    val effectiveParents = displayEntries.associate { entry ->
        entry.id to nearestVisibleParentId(entry, byId, displayIds)
    }
    val children = displayEntries.groupBy { effectiveParents[it.id] }
    val activePathIds = conversationEntryPath(entries, activeEntryId)
        .mapTo(mutableSetOf(), ConversationEntry::id)
    val stableComparator = compareByDescending<ConversationEntry> { it.id in activePathIds }
        .thenBy(ConversationEntry::createdAt)
        .thenBy(ConversationEntry::id)
    val result = mutableListOf<ConversationEntryTreeRow>()
    val emitted = mutableSetOf<String>()
    val rootedIds = mutableSetOf<String>()

    /** 标记自然根可达的节点；折叠只影响显示，不会把后代误判成孤儿根。 */
    fun markRooted(entryId: String) {
        if (!rootedIds.add(entryId)) return
        children[entryId].orEmpty().forEach { child -> markRooted(child.id) }
    }

    /** 递归加入一段树；损坏环路在首次重复处截断。 */
    fun append(
        entry: ConversationEntry,
        indent: Int,
        justBranched: Boolean,
        isLastSibling: Boolean,
        showConnector: Boolean,
        ancestors: Set<String>,
    ) {
        if (entry.id in ancestors || !emitted.add(entry.id)) return
        val childEntries = children[entry.id].orEmpty()
            .filterNot { it.id in ancestors }
            .sortedWith(stableComparator)
        val parentSiblingCount = children[effectiveParents[entry.id]].orEmpty().size
        result += ConversationEntryTreeRow(
            entry = entry,
            indent = indent.coerceAtMost(CONVERSATION_ENTRY_TREE_MAX_INDENT_LEVEL),
            visibleParentId = effectiveParents[entry.id],
            visibleChildIds = childEntries.map(ConversationEntry::id),
            isLastSibling = isLastSibling,
            showConnector = showConnector,
            foldable = childEntries.isNotEmpty() &&
                    (
                            effectiveParents[entry.id] == null ||
                                    parentSiblingCount > 1
                            ),
        )
        if (entry.id in collapsedIds) return

        val multipleChildren = childEntries.size > 1
        childEntries.forEachIndexed { index, child ->
            val childIndent = when {
                multipleChildren -> indent + 1
                justBranched && indent > 0 -> indent + 1
                else -> indent
            }.coerceAtMost(CONVERSATION_ENTRY_TREE_MAX_INDENT_LEVEL)
            append(
                entry = child,
                indent = childIndent,
                justBranched = multipleChildren,
                isLastSibling = index == childEntries.lastIndex,
                showConnector = childIndent > indent || multipleChildren,
                ancestors = ancestors + entry.id,
            )
        }
    }

    val roots = children[null].orEmpty().sortedWith(stableComparator)
    roots.forEach { root -> markRooted(root.id) }
    roots.forEachIndexed { index, root ->
        append(root, 0, false, index == roots.lastIndex, false, emptySet())
    }
    // 孤儿环路没有自然根节点；仍把每个未发出的连通分量作为根显示。
    displayEntries.sortedWith(stableComparator).filterNot { it.id in rootedIds }.forEach { orphan ->
        append(
            entry = orphan,
            indent = 0,
            justBranched = false,
            isLastSibling = true,
            showConnector = false,
            ancestors = emptySet(),
        )
    }
    return result
}

/** 找到过滤后最近的可见父条目；隐藏的中间记录不会制造额外缩进。 */
internal fun nearestVisibleParentId(
    entry: ConversationEntry,
    byId: Map<String, ConversationEntry>,
    visibleIds: Set<String>,
): String? {
    var parentId = entry.parentId
    val visited = mutableSetOf<String>()
    while (parentId != null && visited.add(parentId)) {
        if (parentId in visibleIds) return parentId
        parentId = byId[parentId]?.parentId
    }
    return null
}

/** 按当前筛选与搜索返回直接匹配的节点，视觉父级随后重建为最近可见祖先。 */
internal fun visibleConversationEntryIds(
    entries: List<ConversationEntry>,
    query: String,
    filter: ConversationEntryFilter,
    labeledIds: Set<String>,
): Set<String> {
    val labels = allEntryLabels(entries)
    val normalizedQuery = query.trim()
    return entries.asSequence()
        .filter { entryMatchesFilter(it, filter, labeledIds) }
        .filter { entry ->
            normalizedQuery.isEmpty() || buildString {
                append(entryKindLabel(entry))
                append(' ')
                append(entryPreview(entry))
                labels[entry.id]?.let { append(' ').append(it) }
            }.contains(normalizedQuery, ignoreCase = true)
        }
        .mapTo(linkedSetOf(), ConversationEntry::id)
}

/** 判断条目是否属于当前 Pi 筛选档。 */
private fun entryMatchesFilter(
    entry: ConversationEntry,
    filter: ConversationEntryFilter,
    labeledIds: Set<String>,
): Boolean = when (filter) {
    ConversationEntryFilter.DEFAULT -> entry !is ConversationEntry.Label &&
            entry !is ConversationEntry.ModelChange &&
            entry !is ConversationEntry.ReasoningEffortChange

    ConversationEntryFilter.NO_TOOLS -> entry !is ConversationEntry.Label &&
            entry !is ConversationEntry.ModelChange &&
            entry !is ConversationEntry.ReasoningEffortChange &&
            entry !is ConversationEntry.ToolCall &&
            entry !is ConversationEntry.ToolResult

    ConversationEntryFilter.USER_ONLY ->
        entry is ConversationEntry.Message && entry.message.role == ChatRole.User

    ConversationEntryFilter.LABELED_ONLY -> entry.id in labeledIds
    ConversationEntryFilter.ALL -> true
}

/** 从目标条目向上找到当前筛选中最近的可见选择。 */
internal fun nearestVisibleConversationEntryId(
    entries: List<ConversationEntry>,
    entryId: String?,
    visibleIds: Set<String> = entries.mapTo(mutableSetOf(), ConversationEntry::id),
): String? {
    val byId = entries.associateBy(ConversationEntry::id)
    val visited = mutableSetOf<String>()
    var current = entryId?.let(byId::get)
    while (current != null && visited.add(current.id)) {
        if (current.id in visibleIds) return current.id
        current = current.parentId?.let(byId::get)
    }
    return null
}

/** 标签按钮的展示与可用状态，避免写入没有语义的空标签记录。 */
internal fun conversationEntryLabelAction(
    currentLabel: String?,
    draft: String,
): ConversationEntryLabelAction {
    val normalizedDraft = draft.trim()
    return when {
        currentLabel == null && normalizedDraft.isEmpty() -> ConversationEntryLabelAction("添加标签", false)
        currentLabel == null -> ConversationEntryLabelAction("添加标签", true)
        normalizedDraft.isEmpty() -> ConversationEntryLabelAction("清除标签", true)
        normalizedDraft == currentLabel -> ConversationEntryLabelAction("保存标签", false)
        else -> ConversationEntryLabelAction("保存标签", true)
    }
}

/** 标签按钮的用户可见文案和可提交状态。 */
internal data class ConversationEntryLabelAction(
    val label: String,
    val enabled: Boolean,
)

/** 汇总完整条目图上每个目标条目的最新标签。 */
internal fun allEntryLabels(entries: List<ConversationEntry>): Map<String, String> = buildMap {
    entries.filterIsInstance<ConversationEntry.Label>()
        .sortedBy(ConversationEntry.Label::createdAt)
        .forEach { entry ->
            if (entry.label.isBlank()) remove(entry.targetEntryId) else put(entry.targetEntryId, entry.label)
        }
}

/** 返回条目的紧凑类型标签。 */
internal fun entryKindLabel(entry: ConversationEntry): String = when (entry) {
    is ConversationEntry.Message -> if (entry.message.role == ChatRole.User) "用户" else "助手"
    is ConversationEntry.Reasoning -> "推理"
    is ConversationEntry.ToolCall -> "工具调用"
    is ConversationEntry.ToolResult -> "工具结果"
    is ConversationEntry.Answers -> "回答"
    is ConversationEntry.BranchSummary -> "分支摘要"
    is ConversationEntry.Label -> "标签"
    is ConversationEntry.ModelChange -> "模型"
    is ConversationEntry.ReasoningEffortChange -> "推理强度"
    is ConversationEntry.Custom -> entry.type
}

/** 返回可搜索、可复制的条目文本。 */
internal fun entryPreview(entry: ConversationEntry): String = when (entry) {
    is ConversationEntry.Message -> entry.message.content
    is ConversationEntry.Reasoning -> entry.summaryText?.takeIf(String::isNotBlank)
        ?: entry.rawText?.takeIf(String::isNotBlank).orEmpty()

    is ConversationEntry.ToolCall -> "${entry.toolName} ${entry.preview.orEmpty()}"
    is ConversationEntry.ToolResult -> entry.resultDisplay ?: entry.resultPreview ?: entry.errorMessage.orEmpty()
    is ConversationEntry.Answers -> entry.answers.joinToString("；") { "${it.question}：${it.answer}" }
    is ConversationEntry.BranchSummary -> entry.summary
    is ConversationEntry.Label -> entry.label
    is ConversationEntry.ModelChange -> entry.profileId ?: "默认模型"
    is ConversationEntry.ReasoningEffortChange -> entry.reasoningEffort
    is ConversationEntry.Custom -> entry.text
}

/** 返回一行树连接符；连接符宽度只取决于真实分支层级。 */
internal fun conversationEntryConnector(row: ConversationEntryTreeRow): String = when {
    row.indent == 0 -> ""
    !row.showConnector -> "   ".repeat(row.indent)
    else -> "│  ".repeat((row.indent - 1).coerceAtLeast(0)) +
            if (row.isLastSibling) "└─ " else "├─ "
}

/** 内部条目树最多占用的视觉分支层数，超过后仍保留关系但不再继续挤压正文。 */
internal const val CONVERSATION_ENTRY_TREE_MAX_INDENT_LEVEL = 6
