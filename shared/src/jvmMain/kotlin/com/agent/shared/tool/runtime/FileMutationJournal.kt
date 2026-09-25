package com.agent.shared.tool.runtime

import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** 单次文件恢复的结果；冲突文件保持原样。 */
data class FileRestoreSummary(val restored: List<String>, val skipped: List<String>)

/** 将受管补丁写入和用户轮次绑定，回退时按修改顺序逆向恢复。 */
class FileMutationJournal(private val database: DesktopPersistenceDatabase) {
    /** 仅在整个补丁批次成功落盘后记录修改；失败会由补丁工具恢复文件。 */
    fun recordPatch(conversationId: String, pending: DesktopReadWriteTools.PendingPatchBatch) {
        if (conversationId.isBlank() || pending.files.isEmpty()) return
        database.write { queries ->
            val checkpoint = queries.selectLatestCheckpoint(conversationId, "USER_TURN").executeAsOneOrNull()
                ?: error("文件修改前缺少 USER_TURN 回退点。")
            var order = queries.selectNextFileMutationOrder(conversationId).executeAsOne()
            val now = System.currentTimeMillis()
            pending.files.forEach { file ->
                val before = file.originalContent?.toByteArray(StandardCharsets.UTF_8)
                val after = if (file.delete) ByteArray(0) else file.nextContent.orEmpty().toByteArray(StandardCharsets.UTF_8)
                queries.insertFileMutation(
                    id = UUID.randomUUID().toString(),
                    conversation_id = conversationId,
                    run_id = null,
                    user_turn_checkpoint_id = checkpoint.id,
                    invocation_id = null,
                    transaction_order = order++,
                    absolute_path = file.target.path.toAbsolutePath().normalize().toString(),
                    before_sha256 = before?.let(::sha256),
                    after_sha256 = sha256(after),
                    before_content = before,
                    is_new_file = before == null,
                    after_exists = !file.delete,
                    created_at = now,
                )
            }
        }
    }

    /** 恢复目标用户消息及后续轮次的受管文件；哈希不匹配时跳过该路径。 */
    fun restoreFromUserTurn(conversationId: String, userEntryId: String): FileRestoreSummary {
        val rows = database.read { queries ->
            val checkpoint = queries.selectUserTurnCheckpoint(conversationId, userEntryId)
                .executeAsOneOrNull() ?: return@read emptyList()
            queries.selectFileMutationsFromUserTurn(conversationId, checkpoint.id).executeAsList()
        }
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val blocked = mutableSetOf<Path>()
        rows.forEach { row ->
            val path = Path.of(row.absolute_path).toAbsolutePath().normalize()
            if (path in blocked) return@forEach
            val result = runCatching {
                val exists = Files.exists(path)
                val current = if (exists) Files.readAllBytes(path) else ByteArray(0)
                if (exists != row.after_exists || sha256(current) != row.after_sha256) {
                    false
                } else {
                    if (row.is_new_file) Files.delete(path)
                    else writeAtomically(path, requireNotNull(row.before_content))
                    true
                }
            }.getOrDefault(false)
            if (result) restored += row.absolute_path
            else {
                skipped += row.absolute_path
                blocked.add(path)
            }
        }
        return FileRestoreSummary(restored, skipped)
    }

    /** 同目录临时文件替换，避免恢复过程中留下半写入内容。 */
    private fun writeAtomically(path: Path, content: ByteArray) {
        val parent = path.parent ?: error("无法恢复没有父目录的文件。")
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".mulehang-restore-", ".tmp")
        try {
            Files.write(temporary, content)
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** 返回内容字节的 SHA-256 十六进制摘要。 */
    private fun sha256(content: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(content).joinToString("") { byte -> "%02x".format(byte) }
}
