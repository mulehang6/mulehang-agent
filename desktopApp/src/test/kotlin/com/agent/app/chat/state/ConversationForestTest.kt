package com.agent.app.chat.state

import com.agent.shared.agent.api.ReasoningEffort
import kotlin.test.Test
import kotlin.test.assertEquals

/** 覆盖侧栏会话森林的孤儿处理与子树活动排序。 */
class ConversationForestTest {
    /** 子节点最近活动时间会提升整棵根树的排序权重。 */
    @Test
    fun rootsSortByMostRecentActivityInWholeSubtree() {
        val oldRoot = conversation("old-root", null, 1L)
        val recentChild = conversation("recent-child", oldRoot.id, 100L)
        val middleRoot = conversation("middle-root", null, 50L)

        val roots = buildConversationForest(listOf(middleRoot, oldRoot, recentChild))

        assertEquals(listOf(oldRoot.id, middleRoot.id), roots.map { it.task.id })
        assertEquals(recentChild.id, roots.first().children.single().task.id)
    }

    /** 视图中找不到父会话的节点会提升为根。 */
    @Test
    fun missingParentPromotesConversationToRoot() {
        val orphan = conversation("orphan", "archived-parent", 10L)

        val roots = buildConversationForest(listOf(orphan))

        assertEquals(listOf(orphan.id), roots.map { it.task.id })
    }

    /** 创建用于森林测试的最小会话。 */
    private fun conversation(id: String, parentId: String?, updatedAt: Long): ChatConversationUiState =
        ChatConversationUiState(
            id = id,
            title = id,
            workspacePath = "D:/workspace",
            parentConversationId = parentId,
            reasoningEffort = ReasoningEffort.MEDIUM,
            updatedAt = updatedAt,
        )
}
