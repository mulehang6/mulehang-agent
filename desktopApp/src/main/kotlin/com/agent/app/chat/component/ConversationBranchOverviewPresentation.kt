package com.agent.app.chat.component

import com.agent.shared.chat.model.ConversationEntry

/** 分支概览中一个可精确切换的真实分支末端。 */
internal data class ConversationBranchOverviewItem(
    val leafEntryId: String,
    val depth: Int,
    val skippedEntryCount: Int,
    val preview: String,
    val isActiveLeaf: Boolean,
    val isHeadLeaf: Boolean,
)

/**
 * 把完整条目图压缩为 pi-web 风格的分支末端列表。
 *
 * 线性链不制造伪分支；只有图中出现真实兄弟节点或多个根时才返回项目。每个末端使用
 * 最近分叉之后的首条有效内容作为预览，并把中间线性条目汇总为跳过数量。
 */
internal fun buildConversationBranchOverview(
    entries: List<ConversationEntry>,
    activeEntryId: String?,
    headEntryId: String?,
): List<ConversationBranchOverviewItem> {
    val structuralEntries = entries.filterNot { it is ConversationEntry.Label }
    val byId = structuralEntries.associateBy(ConversationEntry::id)
    val allById = entries.associateBy(ConversationEntry::id)
    val structuralIds = byId.keys
    val parentById = structuralEntries.associate { entry ->
        entry.id to nearestVisibleParentId(entry, allById, structuralIds)
    }
    val childrenByParent = structuralEntries.groupBy { entry -> parentById[entry.id] }
    val roots = childrenByParent[null].orEmpty()
    val hasRealBranch = roots.size > 1 || childrenByParent.values.any { children -> children.size > 1 }
    if (!hasRealBranch) return emptyList()

    val leaves = structuralEntries.filter { entry -> childrenByParent[entry.id].isNullOrEmpty() }
    return leaves.map { leaf ->
        val path = buildList {
            val reversed = mutableListOf<ConversationEntry>()
            val visited = mutableSetOf<String>()
            var current: ConversationEntry? = leaf
            while (current != null && visited.add(current.id)) {
                reversed += current
                current = parentById[current.id]?.let(byId::get)
            }
            addAll(reversed.asReversed())
        }
        val lastBranchIndex = path.indexOfLast { entry -> childrenByParent[entry.id].orEmpty().size > 1 }
        val segment = path.drop(lastBranchIndex + 1).ifEmpty { listOf(leaf) }
        val previewEntry = segment.firstOrNull { entry ->
            (entry is ConversationEntry.Message || entry is ConversationEntry.Custom) &&
                    entryPreview(entry).isNotBlank()
        } ?: segment.firstOrNull { entryPreview(it).isNotBlank() } ?: leaf
        val branchDepth = path.count { entry -> childrenByParent[entry.id].orEmpty().size > 1 } +
                if (roots.size > 1) 1 else 0
        ConversationBranchOverviewItem(
            leafEntryId = leaf.id,
            depth = branchDepth.coerceAtMost(CONVERSATION_BRANCH_OVERVIEW_MAX_INDENT),
            skippedEntryCount = (segment.size - 1).coerceAtLeast(0),
            preview = entryPreview(previewEntry).ifBlank { entryKindLabel(previewEntry) },
            isActiveLeaf = leaf.id == activeEntryId,
            isHeadLeaf = leaf.id == headEntryId,
        )
    }.sortedWith(
        compareByDescending<ConversationBranchOverviewItem> { it.isActiveLeaf }
            .thenByDescending { it.isHeadLeaf }
            .thenBy { item -> entries.firstOrNull { it.id == item.leafEntryId }?.createdAt ?: Long.MAX_VALUE },
    )
}

/** 分支概览只按真实分叉缩进，并限制极深会话对正文宽度的挤压。 */
internal const val CONVERSATION_BRANCH_OVERVIEW_MAX_INDENT = 4
