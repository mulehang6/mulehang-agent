package com.agent.shared.tool.runtime

import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 按关键字或正则在项目文件中搜索内容。
 */
class DesktopGrepTool(
    private val ripgrepRunner: ((Args) -> String?)? = null,
) {
    /**
     * grep 搜索参数。
     */
    data class Args(
        val pattern: String,
        val path: String = ".",
        val glob: String? = null,
        val regex: Boolean = false,
        val caseSensitive: Boolean = true,
        val contextLines: Int = 0,
        val maxResults: Int = 50,
        val headLimit: Int = 20,
        val maxChars: Int = 24_000,
    )

    /**
     * 执行一次搜索，优先尝试 ripgrep，不可用时回退到 JVM 实现。
     */
    fun execute(args: Args): String = (ripgrepRunner?.invoke(args) ?: runRipgrep(args)) ?: runJvmFallback(args)

    /**
     * JVM 回退实现，保证在没有 rg 时仍可工作。
     */
    private fun runJvmFallback(args: Args): String {
        val root = Paths.get(args.path).toAbsolutePath().normalize()
        val matcher = buildMatcher(args)
        val globMatchers = args.glob?.trim()?.takeIf { it.isNotEmpty() }?.let {
            buildGlobMatchers(root, it)
        }
        val maxResults = args.maxResults.coerceIn(1, 200)
        val contextLines = args.contextLines.coerceAtLeast(0)
        val maxChars = args.maxChars.coerceAtLeast(200)
        val output = StringBuilder()
        var matchCount = 0
        var partialReason: String? = null

        Files.walk(root).use { stream ->
            val iterator = stream
                .filter { Files.isRegularFile(it) }
                .sorted()
                .iterator()
            while (iterator.hasNext()) {
                if (partialReason != null) {
                    break
                }
                val file = iterator.next()
                val relative = root.relativize(file)
                if (DesktopIgnoredPaths.containsIgnoredSegment(relative)) {
                    continue
                }
                if (globMatchers != null && globMatchers.none { it.matches(relative) }) {
                    continue
                }
                val lines = readTextLines(file) ?: continue
                for ((index, line) in lines.withIndex()) {
                    if (!matcher.matches(line)) {
                        continue
                    }
                    matchCount += 1
                    if (matchCount > maxResults) {
                        partialReason = "maxResults"
                        break
                    }
                    appendMatch(
                        output = output,
                        file = file,
                        lineNumber = index + 1,
                        lines = lines,
                        contextLines = contextLines,
                    )
                    if (output.length > maxChars) {
                        partialReason = "maxChars"
                        break
                    }
                }
            }
        }

        if (partialReason != null) {
            if (output.isNotEmpty() && output.last() != '\n') {
                output.appendLine()
            }
            output.append("partial=true reason=").append(partialReason)
        }
        return output.toString().trimEnd()
    }

    /** 使用 rg 的 JSON 流搜索；找不到可执行文件时返回 null 进入 JVM 降级。 */
    private fun runRipgrep(args: Args): String? {
        if (args.contextLines > 0 || args.maxResults < 200) return null
        val command = buildList {
            add("rg")
            add("--json")
            add("--no-messages")
            if (!args.caseSensitive) add("-i")
            if (!args.regex) add("-F")
            args.glob?.trim()?.takeIf(String::isNotEmpty)?.let { glob -> add("--glob"); add(glob) }
            add(args.pattern)
            add(args.path)
        }
        return runCatching {
            val result = DesktopProcessRunner().run(
                DesktopProcessRunner.Args(
                    command = command,
                    workingDirectory = java.io.File(args.path),
                    timeoutMillis = 30_000L,
                ),
            )
            renderRipgrepMatches(result.stdout, args)
        }.getOrNull()
    }

    /** 将 rg 的 NDJSON 转换为与 JVM 回退一致的模型可读 `path:line:text` 结果。 */
    private fun renderRipgrepMatches(output: String, args: Args): String {
        val maxBlocks = args.headLimit.coerceIn(1, 200)
        val maxChars = args.maxChars.coerceIn(200, 100_000)
        val rendered = output.lineSequence().mapNotNull { line ->
            val element = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@mapNotNull null
            if (element["type"]?.jsonPrimitive?.content != "match") return@mapNotNull null
            val data = element["data"]?.jsonObject ?: return@mapNotNull null
            val path = data["path"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
            val lineNumber = data["line_number"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val text = data["lines"]?.jsonObject?.get("text")?.jsonPrimitive?.content?.trimEnd('\n', '\r') ?: ""
            "$path:$lineNumber:$text"
        }.take(maxBlocks).joinToString("\n")
        return rendered.take(maxChars).let { visible ->
            if (visible.length < rendered.length) "$visible\npartial=true reason=maxChars" else visible
        }
    }

    /**
     * 构造字符串匹配器。
     */
    private fun buildMatcher(args: Args): LineMatcher {
        val regex = if (args.regex) {
            Regex(
                pattern = args.pattern,
                options = if (args.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE),
            )
        } else {
            val escaped = Regex.escape(args.pattern)
            Regex(
                pattern = escaped,
                options = if (args.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE),
            )
        }
        return LineMatcher(regex)
    }

    /**
     * 读取文本文件；若是二进制或不可解码内容则跳过。
     */
    private fun readTextLines(path: Path): List<String>? = try {
        Files.readAllLines(path)
    } catch (_: MalformedInputException) {
        null
    }

    /**
     * 输出一条命中及其上下文。
     */
    private fun appendMatch(
        output: StringBuilder,
        file: Path,
        lineNumber: Int,
        lines: List<String>,
        contextLines: Int,
    ) {
        output.append(file).append(':').append(lineNumber).append(':').append(lines[lineNumber - 1]).appendLine()
        if (contextLines == 0) {
            return
        }
        val start = (lineNumber - contextLines - 1).coerceAtLeast(0)
        val end = (lineNumber + contextLines - 1).coerceAtMost(lines.lastIndex)
        for (index in start..end) {
            if (index == lineNumber - 1) {
                continue
            }
            output
                .append("  ")
                .append(index + 1)
                .append(":")
                .append(lines[index])
                .appendLine()
        }
    }

    /**
     * 将 bare glob 转为递归匹配写法。
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
     * 构建与 glob 工具一致的匹配器集合。
     */
    private fun buildGlobMatchers(root: Path, pattern: String): List<java.nio.file.PathMatcher> {
        val normalized = normalizePattern(pattern)
        val candidates = linkedSetOf(normalized)
        if (normalized.startsWith("**/")) {
            candidates += normalized.removePrefix("**/")
        }
        return candidates.map { root.fileSystem.getPathMatcher("glob:$it") }
    }

    /**
     * 单行匹配器，隔离匹配实现细节。
     */
    private class LineMatcher(
        private val regex: Regex,
    ) {
        /**
         * 判断一行文本是否命中。
         */
        fun matches(line: String): Boolean = regex.containsMatchIn(line)
    }
}
