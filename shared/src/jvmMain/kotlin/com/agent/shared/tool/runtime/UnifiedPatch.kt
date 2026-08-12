package com.agent.shared.tool.runtime

import com.agent.shared.tool.model.FileChangeKind
import com.agent.shared.tool.model.FileDiffLineKind
import com.agent.shared.tool.model.FileDiffLinePreview
import com.agent.shared.tool.model.FileDiffPreview

/**
 * 解析 Kilo 兼容的补丁文本，并将单个文件操作应用到内存内容。
 *
 * 支持 `*** Add File`、`*** Update File` 和 `*** Delete File`；更新块使用不含行号的
 * `@@` 上下文，避免模型把 read_file 的行号误写入补丁。
 */
object UnifiedPatch {
    /** 解析完整的 `*** Begin Patch` 文本，返回按原顺序排列的文件操作。 */
    fun parse(patchText: String): List<Operation> {
        val lines = patchText.replace("\r\n", "\n").trim().split("\n")
        require(lines.firstOrNull() == BEGIN && lines.lastOrNull() == END) {
            "PATCH_FORMAT_INVALID: 补丁必须以 '$BEGIN' 开始并以 '$END' 结束。示例：$BEGIN\\n*** Add File: notes.txt\\n+内容\\n$END"
        }
        val operations = mutableListOf<Operation>()
        var index = 1
        while (index < lines.lastIndex) {
            val header = lines[index]
            val kind = when {
                header.startsWith(ADD_PREFIX) -> FileChangeKind.CREATED
                header.startsWith(UPDATE_PREFIX) -> FileChangeKind.MODIFIED
                header.startsWith(DELETE_PREFIX) -> FileChangeKind.DELETED
                else -> throw IllegalArgumentException("PATCH_FORMAT_INVALID: 无法识别操作 '$header'。")
            }
            val prefix = when (kind) {
                FileChangeKind.CREATED -> ADD_PREFIX
                FileChangeKind.MODIFIED -> UPDATE_PREFIX
                FileChangeKind.DELETED -> DELETE_PREFIX
            }
            val path = header.removePrefix(prefix).trim().also {
                require(it.isNotEmpty()) { "PATCH_PATH_INVALID: 文件路径不能为空。" }
            }
            index += 1
            val bodyStart = index
            while (index < lines.lastIndex && !isOperationBoundary(lines[index])) index += 1
            val body = lines.subList(bodyStart, index)
            operations += Operation(kind, path, body)
        }
        require(operations.isNotEmpty()) { "PATCH_FORMAT_INVALID: 补丁至少包含一个文件操作。" }
        require(operations.map(Operation::path).distinct().size == operations.size) {
            "PATCH_PATH_DUPLICATE: 同一补丁不能重复操作同一个文件。"
        }
        return operations
    }

    /** 将已解析的单个操作应用到当前文本，并生成供 UI 与审批器使用的精确预览。 */
    fun apply(path: String, current: String?, operation: Operation): AppliedPatch {
        val source = current?.normalizeLines().orEmpty()
        return when (operation.kind) {
            FileChangeKind.CREATED -> applyAdd(path, current, operation)
            FileChangeKind.MODIFIED -> applyUpdate(path, source, operation)
            FileChangeKind.DELETED -> applyDelete(path, current, operation)
        }
    }

    /** 一个已解析但尚未落盘的文件操作。 */
    data class Operation(
        val kind: FileChangeKind,
        val path: String,
        val body: List<String>,
    )

    /** 已通过上下文检查的候选内容；删除操作以 [delete] 标记。 */
    data class AppliedPatch(
        val content: String?,
        val delete: Boolean,
        val preview: FileDiffPreview,
    )

    /** 新建文件只接受以加号开头的内容行。 */
    private fun applyAdd(path: String, current: String?, operation: Operation): AppliedPatch {
        require(current == null) { "PATCH_TARGET_EXISTS: 新建目标已存在，请改用 *** Update File。" }
        val content = operation.body.map { line ->
            require(line.startsWith('+')) { "PATCH_ADD_INVALID: Add File 的每行必须以 + 开头。" }
            line.drop(1)
        }.joinToString("\n")
        return applied(
            path = path,
            kind = FileChangeKind.CREATED,
            beforeLines = emptyList(),
            afterLines = content.toPreviewLines(),
            content = content,
            delete = false,
            operation = operation,
        )
    }

    /** 更新文件时将每个无行号 hunk 在精确上下文处替换。 */
    private fun applyUpdate(path: String, source: List<String>, operation: Operation): AppliedPatch {
        require(source.isNotEmpty() || operation.body.isNotEmpty()) { "PATCH_CONTEXT_MISSING: 空文件更新必须提供 hunk。" }
        val target = source.toMutableList()
        var searchStart = 0
        splitHunks(operation.body).forEach { hunk -> searchStart = applyHunk(target, hunk, searchStart) }
        return applied(
            path = path,
            kind = FileChangeKind.MODIFIED,
            beforeLines = source,
            afterLines = target,
            content = target.joinToString("\n"),
            delete = false,
            operation = operation,
        )
    }

    /** 删除文件不接受正文，并要求目标确实存在。 */
    private fun applyDelete(path: String, current: String?, operation: Operation): AppliedPatch {
        require(current != null) { "PATCH_TARGET_MISSING: 删除目标不存在。" }
        require(operation.body.isEmpty()) { "PATCH_DELETE_INVALID: Delete File 后不能包含内容。" }
        return applied(
            path = path,
            kind = FileChangeKind.DELETED,
            beforeLines = current.toPreviewLines(),
            afterLines = emptyList(),
            content = null,
            delete = true,
            operation = operation,
        )
    }

    /** 将 hunk 的旧内容定位到当前目标中，再替换为新内容。 */
    private fun applyHunk(target: MutableList<String>, hunk: UpdateHunk, searchStart: Int): Int {
        require(hunk.lines.isNotEmpty()) { "PATCH_HUNK_INVALID: @@ 后必须包含至少一行变更或上下文。" }
        require(hunk.lines.any { it.startsWith('+') || it.startsWith('-') }) { "PATCH_HUNK_INVALID: hunk 没有实际变更。" }
        val oldLines = hunk.lines.filter { it.startsWith(' ') || it.startsWith('-') }.map { it.drop(1) }
        val newLines = hunk.lines.filter { it.startsWith(' ') || it.startsWith('+') }.map { it.drop(1) }
        val anchoredStart = hunk.changeContext?.let { anchor ->
            target.indexOfSequence(listOf(anchor), searchStart).takeIf { it >= 0 }?.plus(1)
                ?: throw IllegalArgumentException("PATCH_CONTEXT_CONFLICT: 找不到 @@ 指定的上下文 '$anchor'。")
        } ?: searchStart
        if (oldLines.isEmpty()) {
            target.addAll(target.size, newLines)
            return target.size
        }
        val position = target.indexOfSequence(oldLines, anchoredStart, hunk.endOfFile)
        require(position >= 0) { "PATCH_CONTEXT_CONFLICT: 上下文与最新文件不匹配，请重新 read_file 后再试。" }
        repeat(oldLines.size) { target.removeAt(position) }
        target.addAll(position, newLines)
        return position + newLines.size
    }

    /** 将正文按 Kilo 的 `@@` 分隔为多个 hunk。 */
    private fun splitHunks(body: List<String>): List<UpdateHunk> {
        require(body.firstOrNull()?.startsWith(HUNK) == true) { "PATCH_HUNK_INVALID: Update File 必须以 @@ 开始。" }
        val hunks = mutableListOf<MutableUpdateHunk>()
        body.forEach { line ->
            if (line.startsWith(HUNK)) {
                hunks.add(MutableUpdateHunk(changeContext = line.drop(2).trim().ifBlank { null }))
            } else if (line == END_OF_FILE) {
                val current = hunks.lastOrNull()
                    ?: throw IllegalArgumentException("PATCH_HUNK_INVALID: End of File 前缺少 @@ 标记。")
                current.endOfFile = true
            } else {
                require(line.startsWith(' ') || line.startsWith('+') || line.startsWith('-')) {
                    "PATCH_HUNK_INVALID: hunk 行必须以空格、+ 或 - 开头。"
                }
                hunks.lastOrNull()?.lines?.add(line)
                    ?: throw IllegalArgumentException("PATCH_HUNK_INVALID: hunk 缺少 @@ 标记。")
            }
        }
        return hunks.map { UpdateHunk(it.changeContext, it.lines, it.endOfFile) }
    }

    /** 将结果映射为 Diff 预览。 */
    private fun applied(
        path: String,
        kind: FileChangeKind,
        beforeLines: List<String>,
        afterLines: List<String>,
        content: String?,
        delete: Boolean,
        operation: Operation,
    ): AppliedPatch = AppliedPatch(
        content = content,
        delete = delete,
        preview = FileDiffPreview(
            path = path,
            kind = kind,
            unifiedDiff = renderPreview(operation),
            collapsedUnchangedLineCount = operation.body.count { it.startsWith(' ') },
            editorLines = buildEditorLines(beforeLines, afterLines),
        ),
    )

    /** 用标准 unified 标记渲染 UI 可折叠的预览，不泄露未涉及的文件内容。 */
    private fun renderPreview(operation: Operation): String = buildString {
        appendLine("*** ${operation.kind.name.lowercase()} File: ${operation.path}")
        operation.body.forEach(::appendLine)
    }.trimEnd()

    /**
     * 从变更前后文本构造可直接交给 Compose 的统一 Diff 行。
     *
     * 常见文件使用 LCS 保留准确的上下文与行号；超出矩阵预算时退化为共同前后缀加中段替换，
     * 避免审批预览因大文件消耗不可控内存。
     */
    private fun buildEditorLines(before: List<String>, after: List<String>): List<FileDiffLinePreview> {
        if (before.isEmpty()) return after.mapIndexed { index, content ->
            FileDiffLinePreview(FileDiffLineKind.ADDED, null, index + 1, content)
        }
        if (after.isEmpty()) return before.mapIndexed { index, content ->
            FileDiffLinePreview(FileDiffLineKind.REMOVED, index + 1, null, content)
        }
        return if (before.size.toLong() * after.size <= MAX_DIFF_MATRIX_CELLS) {
            buildLcsEditorLines(before, after)
        } else {
            buildLargeFileEditorLines(before, after)
        }
    }

    /** 使用最长公共子序列生成精确的新增、删除与上下文行。 */
    private fun buildLcsEditorLines(before: List<String>, after: List<String>): List<FileDiffLinePreview> {
        val lcs = Array(before.size + 1) { IntArray(after.size + 1) }
        for (oldIndex in before.indices.reversed()) {
            for (newIndex in after.indices.reversed()) {
                lcs[oldIndex][newIndex] = if (before[oldIndex] == after[newIndex]) {
                    lcs[oldIndex + 1][newIndex + 1] + 1
                } else {
                    maxOf(lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1])
                }
            }
        }
        val lines = mutableListOf<FileDiffLinePreview>()
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < before.size || newIndex < after.size) {
            when {
                oldIndex < before.size && newIndex < after.size && before[oldIndex] == after[newIndex] -> {
                    lines += FileDiffLinePreview(FileDiffLineKind.CONTEXT, oldIndex + 1, newIndex + 1, before[oldIndex])
                    oldIndex += 1
                    newIndex += 1
                }

                oldIndex < before.size && (newIndex == after.size || lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1]) -> {
                    lines += FileDiffLinePreview(FileDiffLineKind.REMOVED, oldIndex + 1, null, before[oldIndex])
                    oldIndex += 1
                }

                else -> {
                    lines += FileDiffLinePreview(FileDiffLineKind.ADDED, null, newIndex + 1, after[newIndex])
                    newIndex += 1
                }
            }
        }
        return lines
    }

    /** 大文件只保留共同前后缀，其余中段按替换展示，仍保证行号正确。 */
    private fun buildLargeFileEditorLines(before: List<String>, after: List<String>): List<FileDiffLinePreview> {
        val prefix = before.indices.zip(after.indices).takeWhile { (old, new) -> before[old] == after[new] }.count()
        val suffix = before.indices.reversed()
            .zip(after.indices.reversed())
            .takeWhile { (old, new) -> old >= prefix && new >= prefix && before[old] == after[new] }
            .count()
        return buildList {
            before.take(prefix).forEachIndexed { index, content ->
                add(FileDiffLinePreview(FileDiffLineKind.CONTEXT, index + 1, index + 1, content))
            }
            before.subList(prefix, before.size - suffix).forEachIndexed { index, content ->
                add(FileDiffLinePreview(FileDiffLineKind.REMOVED, prefix + index + 1, null, content))
            }
            after.subList(prefix, after.size - suffix).forEachIndexed { index, content ->
                add(FileDiffLinePreview(FileDiffLineKind.ADDED, null, prefix + index + 1, content))
            }
            before.takeLast(suffix).forEachIndexed { index, content ->
                val oldLine = before.size - suffix + index + 1
                val newLine = after.size - suffix + index + 1
                add(FileDiffLinePreview(FileDiffLineKind.CONTEXT, oldLine, newLine, content))
            }
        }
    }

    /** 归一化文本到无需保留尾部空行的按行表示。 */
    private fun String.normalizeLines(): List<String> = toPreviewLines()

    /** 空内容以零行表示，非空内容移除落盘格式无关的末尾换行后按行拆分。 */
    private fun String?.toPreviewLines(): List<String> = this
        ?.removeSuffix("\n")
        ?.takeIf(String::isNotEmpty)
        ?.split("\n")
        .orEmpty()

    /** 仅文件操作和补丁结束标记可终止当前操作正文。 */
    private fun isOperationBoundary(line: String): Boolean =
        line.startsWith(ADD_PREFIX) || line.startsWith(UPDATE_PREFIX) || line.startsWith(DELETE_PREFIX) || line == END

    /** 在列表中查找连续文本块。 */
    private fun List<String>.indexOfSequence(needle: List<String>, start: Int = 0, endOfFile: Boolean = false): Int {
        if (needle.size > size) return -1
        val candidates = if (endOfFile) listOf(size - needle.size) else (start.coerceAtLeast(0)..size - needle.size)
        return candidates.firstOrNull { index ->
            index + needle.size <= size && subList(index, index + needle.size) == needle
        } ?: -1
    }

    /** Kilo `@@ 标识` 的可选定位上下文及其操作行。 */
    private data class UpdateHunk(
        val changeContext: String?,
        val lines: List<String>,
        val endOfFile: Boolean,
    )

    /** 解析时使用的可变 hunk 累积器。 */
    private data class MutableUpdateHunk(
        val changeContext: String?,
        val lines: MutableList<String> = mutableListOf(),
        var endOfFile: Boolean = false,
    )

    private const val BEGIN = "*** Begin Patch"
    private const val END = "*** End Patch"
    private const val ADD_PREFIX = "*** Add File:"
    private const val UPDATE_PREFIX = "*** Update File:"
    private const val DELETE_PREFIX = "*** Delete File:"
    private const val HUNK = "@@"
    private const val END_OF_FILE = "*** End of File"
    private const val MAX_DIFF_MATRIX_CELLS = 250_000L
}
