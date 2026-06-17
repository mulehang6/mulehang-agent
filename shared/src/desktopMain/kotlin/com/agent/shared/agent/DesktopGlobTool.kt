package com.agent.shared.agent

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 按 glob 在磁盘上搜索文件。
 */
class DesktopGlobTool {
    /**
     * glob 搜索参数。
     */
    data class Args(
        val pattern: String,
        val path: String = ".",
        val maxResults: Int = 50,
    )

    /**
     * 执行一次 glob 搜索并返回按行分隔的绝对路径。
     */
    fun execute(args: Args): String {
        val root = Paths.get(args.path).toAbsolutePath().normalize()
        val matchers = buildMatchers(root, args.pattern)
        val limit = args.maxResults.coerceIn(1, 200)
        val results = mutableListOf<String>()

        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .sorted()
                .forEach { file ->
                    if (results.size >= limit) {
                        return@forEach
                    }
                    val relative = root.relativize(file)
                    if (matchers.any { it.matches(relative) }) {
                        results += file.toString()
                    }
                }
        }
        return results.joinToString(separator = System.lineSeparator())
    }

    /**
     * 将 bare pattern 转成递归匹配形式，贴近 paicli 的默认体验。
     */
    private fun normalizePattern(pattern: String): String {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) {
            return "**"
        }
        return if (trimmed.contains('/') || trimmed.contains('\\')) {
            trimmed.replace('\\', '/')
        } else {
            "**/$trimmed"
        }
    }

    /**
     * 构建兼容根目录与递归目录的 glob 匹配器集合。
     */
    private fun buildMatchers(root: Path, pattern: String): List<java.nio.file.PathMatcher> {
        val normalized = normalizePattern(pattern)
        val candidates = linkedSetOf(normalized)
        if (normalized.startsWith("**/")) {
            candidates += normalized.removePrefix("**/")
        }
        return candidates.map { root.fileSystem.getPathMatcher("glob:$it") }
    }
}
