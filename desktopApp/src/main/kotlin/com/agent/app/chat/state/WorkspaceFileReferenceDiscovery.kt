package com.agent.app.chat.state

import java.nio.file.Files
import java.nio.file.Path

/** `@` 浏览器中的一个工作区内文件候选。 */
internal data class WorkspaceFileReference(
    val absolutePath: String,
    val relativePath: String,
)

/**
 * 在真实工作区根目录下列出可引用的普通文件。目录遍历不跟随目录链接，且每个候选都会再次按
 * 真实路径检查，避免浏览器把工作区外文件伪装成项目文件。
 */
internal fun discoverWorkspaceFileReferences(
    workspacePath: String,
    query: String,
    maxResults: Int = MAX_WORKSPACE_REFERENCE_RESULTS,
): List<WorkspaceFileReference> {
    if (workspacePath.isBlank() || maxResults <= 0) return emptyList()
    val root = runCatching { Path.of(workspacePath).toRealPath() }.getOrNull() ?: return emptyList()
    val normalizedQuery = query.trim().lowercase()
    return runCatching {
        Files.walk(root).use { paths ->
            paths.iterator().asSequence()
                .filter(Files::isRegularFile)
                .filter { file -> !file.hasIgnoredReferenceSegment(root) }
                .mapNotNull { file -> file.toWorkspaceReferenceOrNull(root) }
                .filter { reference ->
                    normalizedQuery.isBlank() || reference.relativePath.lowercase().contains(normalizedQuery)
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { reference -> reference.relativePath })
                .take(maxResults)
                .toList()
        }
    }.getOrDefault(emptyList())
}

/** 隐藏 Git 元数据、依赖与构建目录，避免 `@` 菜单被生成文件淹没。 */
private fun Path.hasIgnoredReferenceSegment(root: Path): Boolean = root.relativize(this).any { segment ->
    segment.toString() in IGNORED_REFERENCE_DIRECTORY_NAMES
}

/** 将候选路径转换为经真实路径边界确认的 UI 模型。 */
private fun Path.toWorkspaceReferenceOrNull(root: Path): WorkspaceFileReference? {
    val realPath = runCatching(::toRealPath).getOrNull() ?: return null
    if (!realPath.startsWith(root)) return null
    return WorkspaceFileReference(
        absolutePath = realPath.toString(),
        relativePath = root.relativize(realPath).toString().replace('\\', '/'),
    )
}

private const val MAX_WORKSPACE_REFERENCE_RESULTS = 200
private val IGNORED_REFERENCE_DIRECTORY_NAMES = setOf(".git", "node_modules", "build", "out", "dist")
