package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals

/** 覆盖外层会话树的分支压缩与深度上限。 */
class TaskSidebarTreeTest {
    /** 克隆形成的单子节点长链保持平直，不随会话数量持续右移。 */
    @Test
    fun `linear conversation chain keeps the same indentation`() {
        var indent = 0
        repeat(20) {
            indent = nextTaskTreeIndent(indent, hasMultipleChildren = false, justBranched = false)
        }

        assertEquals(0, indent)
    }

    /** 真正分叉及分叉后的首段会增加层级，后续单链保持稳定。 */
    @Test
    fun `conversation indentation grows only around a branch`() {
        val branchChildren = nextTaskTreeIndent(0, hasMultipleChildren = true, justBranched = false)
        val firstSegment = nextTaskTreeIndent(branchChildren, hasMultipleChildren = false, justBranched = true)
        val continuation = nextTaskTreeIndent(firstSegment, hasMultipleChildren = false, justBranched = false)

        assertEquals(1, branchChildren)
        assertEquals(2, firstSegment)
        assertEquals(2, continuation)
    }

    /** 多层嵌套分叉最多占用固定层数。 */
    @Test
    fun `conversation indentation is capped`() {
        var indent = 0
        repeat(20) {
            indent = nextTaskTreeIndent(indent, hasMultipleChildren = true, justBranched = true)
        }

        assertEquals(TASK_TREE_MAX_INDENT_LEVEL, indent)
    }
}
