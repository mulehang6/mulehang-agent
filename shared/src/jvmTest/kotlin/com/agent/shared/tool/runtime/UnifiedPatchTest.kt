package com.agent.shared.tool.runtime

import com.agent.shared.tool.model.FileChangeKind
import com.agent.shared.tool.model.FileDiffLineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 验证 Kilo 补丁语法、上下文校验和预览内容。 */
class UnifiedPatchTest {
    /** Add File 应只接受以加号开头的文件内容。 */
    @Test
    fun `parses and applies add file`() {
        val operation = UnifiedPatch.parse(
            """
            *** Begin Patch
            *** Add File: notes.txt
            +hello
            +world
            *** End Patch
            """.trimIndent(),
        ).single()

        val applied = UnifiedPatch.apply("notes.txt", null, operation)

        assertEquals(FileChangeKind.CREATED, applied.preview.kind)
        assertEquals("hello\nworld", applied.content)
        assertEquals(listOf(1, 2), applied.preview.editorLines.map { it.newLineNumber })
        assertTrue(applied.preview.editorLines.all { it.kind == FileDiffLineKind.ADDED })
    }

    /** Update File 支持不含行号的多个 hunk。 */
    @Test
    fun `applies multiple update hunks`() {
        val operation = UnifiedPatch.parse(
            """
            *** Begin Patch
            *** Update File: sample.txt
            @@
            -one
            +first
            @@
             keep
            -two
            +second
            *** End Patch
            """.trimIndent(),
        ).single()

        val applied = UnifiedPatch.apply("sample.txt", "before\none\nkeep\ntwo", operation)

        assertEquals("before\nfirst\nkeep\nsecond", applied.content)
        assertEquals(1, applied.preview.collapsedUnchangedLineCount)
        assertEquals(
            listOf(FileDiffLineKind.CONTEXT, FileDiffLineKind.REMOVED, FileDiffLineKind.ADDED),
            applied.preview.editorLines.take(3).map { it.kind },
        )
        assertEquals(2, applied.preview.editorLines[1].oldLineNumber)
        assertEquals(2, applied.preview.editorLines[2].newLineNumber)
    }

    /** Kilo 的 `@@ 上下文` 与文件尾标记应限制替换位置。 */
    @Test
    fun `supports kilo hunk context and end of file`() {
        val operation = UnifiedPatch.parse(
            """
            *** Begin Patch
            *** Update File: sample.txt
            @@ second
            -target
            +updated
            *** End of File
            *** End Patch
            """.trimIndent(),
        ).single()

        val applied = UnifiedPatch.apply("sample.txt", "first\ntarget\nsecond\ntarget", operation)

        assertEquals("first\ntarget\nsecond\nupdated", applied.content)
    }

    /** Delete File 只能删除已有目标且不包含正文。 */
    @Test
    fun `parses delete file`() {
        val operation = UnifiedPatch.parse(
            """
            *** Begin Patch
            *** Delete File: old.txt
            *** End Patch
            """.trimIndent(),
        ).single()

        val applied = UnifiedPatch.apply("old.txt", "obsolete", operation)

        assertTrue(applied.delete)
        assertEquals(FileChangeKind.DELETED, applied.preview.kind)
        assertEquals(FileDiffLineKind.REMOVED, applied.preview.editorLines.single().kind)
        assertEquals(1, applied.preview.editorLines.single().oldLineNumber)
    }

    /** 过期上下文必须明确失败，不能覆盖用户刚保存的内容。 */
    @Test
    fun `rejects stale patch context`() {
        val operation = UnifiedPatch.parse(
            """
            *** Begin Patch
            *** Update File: sample.txt
            @@
            -before
            +after
            *** End Patch
            """.trimIndent(),
        ).single()

        val error = assertFailsWith<IllegalArgumentException> {
            UnifiedPatch.apply("sample.txt", "changed", operation)
        }

        assertTrue(error.message.orEmpty().startsWith("PATCH_CONTEXT_CONFLICT"))
    }

    /** 旧 unified hunk 必须给出稳定的协议错误。 */
    @Test
    fun `rejects legacy unified diff`() {
        val error = assertFailsWith<IllegalArgumentException> {
            UnifiedPatch.parse("@@ -1 +1 @@\n-before\n+after")
        }

        assertTrue(error.message.orEmpty().startsWith("PATCH_FORMAT_INVALID"))
    }
}
