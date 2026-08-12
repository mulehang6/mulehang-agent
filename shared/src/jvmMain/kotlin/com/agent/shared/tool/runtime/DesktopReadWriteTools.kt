package com.agent.shared.tool.runtime

import com.agent.shared.tool.model.FileDiffPreview
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 封装分页读取与 Kilo 兼容补丁的预览、审批前置和可回滚落盘。 */
class DesktopReadWriteTools(private val fileSupport: DesktopFileToolSupport) {
    /** 读取文本文件的一个受限分页。 */
    fun readFile(path: String, offset: Int = 1, limit: Int = DEFAULT_READ_LINE_LIMIT): String {
        require(offset >= 1) { "offset 必须从 1 开始。" }
        val target = fileSupport.resolveForRead(path).path
        require(!isBinary(target)) { "拒绝读取二进制文件: $target" }
        val safeLimit = limit.coerceIn(1, MAX_READ_LINE_LIMIT)
        val lines = Files.readAllLines(target, StandardCharsets.UTF_8)
        val slice = lines.drop(offset - 1).take(safeLimit)
        val remaining = (lines.size - (offset - 1 + slice.size)).coerceAtLeast(0)
        return buildString {
            slice.forEachIndexed { index, line -> appendLine("${offset + index}: $line") }
            if (remaining > 0) append("… 已截断，剩余 $remaining 行。")
        }.trimEnd()
    }

    /** 列出目录中的一级条目，并按名称排序和截断。 */
    fun listDir(path: String, maxResults: Int = DEFAULT_DIRECTORY_LIMIT): String {
        val target = fileSupport.resolveForRead(path).path
        require(Files.isDirectory(target)) { "目标不是目录: $target" }
        val safeLimit = maxResults.coerceIn(1, MAX_DIRECTORY_LIMIT)
        Files.list(target).use { stream ->
            val entries = stream.map { it.fileName.toString() }.sorted().toList()
            return entries.take(safeLimit).joinToString(System.lineSeparator()).let { result ->
                if (entries.size > safeLimit) "$result${System.lineSeparator()}… 已截断，剩余 ${entries.size - safeLimit} 项。" else result
            }
        }
    }

    /** 解析所有文件操作，校验路径和内容，并返回复用同一预览的待审批批次。 */
    fun previewPatch(patchText: String): PendingPatchBatch {
        val operations = UnifiedPatch.parse(patchText)
        val pending = operations.map { operation ->
            val resolved = fileSupport.resolveForWrite(operation.path)
            val current = resolved.path.takeIf(Files::exists)?.let { target ->
                require(!isBinary(target)) { "PATCH_BINARY_REJECTED: 拒绝修改二进制文件: $target" }
                Files.readString(target, StandardCharsets.UTF_8)
            }
            val applied = UnifiedPatch.apply(resolved.path.toString(), current, operation)
            PendingFilePatch(
                target = resolved,
                originalContent = current,
                nextContent = applied.content,
                delete = applied.delete,
                preview = applied.preview,
            )
        }
        return PendingPatchBatch(pending)
    }

    /** 审批完成后提交整个批次；任何失败都会恢复先前已写入的文件。 */
    fun applyPatch(pending: PendingPatchBatch): String {
        pending.files.forEach(::ensureUnchangedSincePreview)
        val applied = mutableListOf<PendingFilePatch>()
        try {
            pending.files.forEach { file ->
                applyOne(file)
                applied += file
            }
        } catch (error: Exception) {
            applied.asReversed().forEach { file -> runCatching { restore(file) } }
            throw IllegalStateException("PATCH_APPLY_FAILED: 批量写入失败，已尝试回滚已完成的文件。", error)
        }
        return buildString {
            append("PATCH_APPLIED: ").append(pending.files.size).append(" 个文件")
            pending.files.forEach { file -> append("\n- ").append(file.preview.kind).append(' ').append(file.preview.path) }
        }
    }

    /** 校验审批等待期间没有其他进程覆盖文件。 */
    private fun ensureUnchangedSincePreview(file: PendingFilePatch) {
        val latest = file.target.path.takeIf(Files::exists)?.let { Files.readString(it, StandardCharsets.UTF_8) }
        check(latest == file.originalContent) { "PATCH_STALE: 文件在审批期间已变化，请重新生成 diff。" }
    }

    /** 落盘单个操作，文件替换使用同目录临时文件。 */
    private fun applyOne(file: PendingFilePatch) {
        if (file.delete) {
            Files.deleteIfExists(file.target.path)
            return
        }
        val parent = file.target.path.parent ?: error("PATCH_PATH_INVALID: 目标没有父目录。")
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".mulehang-", ".tmp")
        try {
            Files.writeString(temporary, file.nextContent.orEmpty(), StandardCharsets.UTF_8)
            Files.move(temporary, file.target.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** 将失败批次中已经落盘的文件恢复为预览时内容。 */
    private fun restore(file: PendingFilePatch) {
        if (file.originalContent == null) {
            Files.deleteIfExists(file.target.path)
        } else {
            applyOne(file.copy(nextContent = file.originalContent, delete = false))
        }
    }

    /** 一个待提交文件及其审批前原始快照。 */
    data class PendingFilePatch(
        val target: DesktopFileToolSupport.ResolvedToolPath,
        val originalContent: String?,
        val nextContent: String?,
        val delete: Boolean,
        val preview: FileDiffPreview,
    )

    /** 一个不可分割的多文件审批批次。 */
    data class PendingPatchBatch(val files: List<PendingFilePatch>) {
        /** 所有用于审批 UI 的文件 Diff。 */
        val previews: List<FileDiffPreview> = files.map(PendingFilePatch::preview)

        /** 批次只要含有工作区外目标，就必须按外部写入策略处理。 */
        val containsExternalWrite: Boolean = files.any { !it.target.isInsideWorkspace }
    }

    /** 通过 NUL 字节快速识别二进制文件。 */
    private fun isBinary(path: java.nio.file.Path): Boolean = Files.newInputStream(path).use { input ->
        input.readNBytes(BINARY_SAMPLE_BYTES).any { it == 0.toByte() }
    }

    private companion object {
        const val DEFAULT_READ_LINE_LIMIT = 2_000
        const val MAX_READ_LINE_LIMIT = 5_000
        const val DEFAULT_DIRECTORY_LIMIT = 200
        const val MAX_DIRECTORY_LIMIT = 1_000
        const val BINARY_SAMPLE_BYTES = 8_192
    }
}
