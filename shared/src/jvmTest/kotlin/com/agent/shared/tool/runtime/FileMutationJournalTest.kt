package com.agent.shared.tool.runtime

import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** 验证受管补丁回退的逆序、创建文件和哈希冲突保护。 */
class FileMutationJournalTest {
    /** 连续修改同一文件时必须逆序恢复发送前内容。 */
    @Test
    fun `restores consecutive managed patches in reverse order`() = runTest {
        val workspace = Files.createTempDirectory("mulehang-file-restore")
        val file = workspace.resolve("notes.txt")
        Files.writeString(file, "first\n")
        DesktopPersistenceDatabase.open(workspace.resolve("data.db")).use { database ->
            prepareTurn(database, workspace.toString())
            val journal = FileMutationJournal(database)
            val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))
            apply(tools, journal, "notes.txt", "first", "second")
            apply(tools, journal, "notes.txt", "second", "third")

            val summary = journal.restoreFromUserTurn("conversation", "turn")

            assertEquals("first\n", Files.readString(file))
            assertEquals(2, summary.restored.size)
            assertTrue(summary.skipped.isEmpty())
        }
    }

    /** 未变化的新文件可删除，外部改动后的文件则保持原样。 */
    @Test
    fun `deletes untouched new file and skips hash conflict`() = runTest {
        val workspace = Files.createTempDirectory("mulehang-file-conflict")
        DesktopPersistenceDatabase.open(workspace.resolve("data.db")).use { database ->
            prepareTurn(database, workspace.toString())
            val journal = FileMutationJournal(database)
            val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))
            val created = tools.previewPatch("*** Begin Patch\n*** Add File: new.txt\n+created\n*** End Patch")
            tools.applyPatch(created) { journal.recordPatch("conversation", it) }
            val changed = tools.previewPatch("*** Begin Patch\n*** Add File: changed.txt\n+original\n*** End Patch")
            tools.applyPatch(changed) { journal.recordPatch("conversation", it) }
            Files.writeString(workspace.resolve("changed.txt"), "external")

            val summary = journal.restoreFromUserTurn("conversation", "turn")

            assertFalse(Files.exists(workspace.resolve("new.txt")))
            assertEquals("external", Files.readString(workspace.resolve("changed.txt")))
            assertEquals(1, summary.skipped.size)
        }
    }

    /** 创建可关联文件修改的永久用户轮次检查点。 */
    private suspend fun prepareTurn(database: DesktopPersistenceDatabase, workspacePath: String) {
        SqliteTaskRepository(database).saveUserTurn(
            tasks = listOf(PersistedTask(
                id = "conversation",
                title = "测试",
                workspacePath = workspacePath,
                reasoningEffort = "MEDIUM",
                contextUsageFraction = 0f,
                executionState = "IDLE",
                executionErrorTitle = null,
                executionErrorMessage = null,
                attachmentsJson = "[]",
                timeline = emptyList(),
                history = emptyList(),
            )),
            before = null,
            conversationId = "conversation",
            userEntryId = "turn",
        )
    }

    /** 通过真实预览和写入流程应用一条单文件更新。 */
    private fun apply(
        tools: DesktopReadWriteTools,
        journal: FileMutationJournal,
        path: String,
        before: String,
        after: String,
    ) {
        val pending = tools.previewPatch(
            "*** Begin Patch\n*** Update File: $path\n@@\n-$before\n+$after\n*** End Patch",
        )
        tools.applyPatch(pending) { journal.recordPatch("conversation", it) }
    }
}
