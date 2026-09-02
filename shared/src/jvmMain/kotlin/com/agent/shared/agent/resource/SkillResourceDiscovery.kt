package com.agent.shared.agent.resource

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** 描述一个 Skill 根目录及其 Pi 兼容语义。 */
internal data class SkillSearchRoot(
    val path: Path,
    val origin: AgentResourceOrigin,
    val agentsCompatibilityRoot: Boolean = false,
)

/**
 * 按 Kilo 优先级发现 Skills。相同名称由后发现的项覆盖，并留下可见冲突诊断。
 */
internal fun discoverSkillResources(
    roots: List<SkillSearchRoot>,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): List<AgentSkillResource> {
    val skillsByName = linkedMapOf<String, AgentSkillResource>()
    roots.forEach { root ->
        if (!Files.isDirectory(root.path)) return@forEach
        discoverSkillFiles(root, diagnostics).forEach { file ->
            val skill = parseSkillResource(file, root, diagnostics) ?: return@forEach
            val existing = skillsByName.put(skill.name, skill)
            if (existing != null) {
                diagnostics += AgentResourceDiagnostic(
                    severity = AgentResourceDiagnosticSeverity.WARNING,
                    message = "Skill 名称 '${skill.name}' 与较低优先级项冲突，已使用当前项覆盖。",
                    path = file,
                )
            }
        }
    }
    return skillsByName.values.toList()
}

/** 使用文件树访问器支持递归、ignore 文件和 SKILL.md 目录边界。 */
private fun discoverSkillFiles(
    root: SkillSearchRoot,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): List<Path> {
    val files = mutableListOf<Path>()
    val ignoreRules = MutableIgnoreRules(root.path)
    Files.walkFileTree(root.path, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
            if (directory != root.path) {
                if (shouldSkipSkillDirectory(directory, root.path, ignoreRules)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                val skillFile = directory.resolve(SKILL_FILE_NAME)
                if (Files.isRegularFile(skillFile)) {
                    files.add(skillFile)
                    return FileVisitResult.SKIP_SUBTREE
                }
            }
            ignoreRules.loadRulesFrom(directory, diagnostics)
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
            if (ignoreRules.ignores(file)) return FileVisitResult.CONTINUE
            if (!file.fileName.toString().endsWith(".md", ignoreCase = true)) return FileVisitResult.CONTINUE
            if (
                root.agentsCompatibilityRoot &&
                file.parent == root.path &&
                !file.fileName.toString().equals(SKILL_FILE_NAME, ignoreCase = true)
            ) {
                // Pi 对 ~/.agents/skills 的兼容根不把根目录普通 markdown 当作 Skill。
                return FileVisitResult.CONTINUE
            }
            files.add(file)
            return FileVisitResult.CONTINUE
        }
    })
    return files.distinctBy(Path::normalizedIdentity)
}

/** 解析一个候选 Skill，采用宽容 frontmatter 校验但拒绝没有任何可识别 name 的文件。 */
private fun parseSkillResource(
    file: Path,
    root: SkillSearchRoot,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): AgentSkillResource? {
    val content = runCatching { Files.readString(file, StandardCharsets.UTF_8).removePrefix("\uFEFF") }
        .getOrElse { error ->
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "无法读取 Skill：${error.message ?: "未知错误"}",
                path = file,
            )
            return null
        }
    val frontmatter = parseMarkdownFrontmatter(content)
    if (!frontmatter.hasFrontmatter) {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "Skill 缺少 frontmatter，已跳过。",
            path = file,
        )
        return null
    }
    val fallbackName = file.parent
        ?.takeIf { it.fileName?.toString() != "skills" }
        ?.fileName
        ?.toString()
        ?: file.fileName.toString().substringBeforeLast('.')
    val name = frontmatter.value("name") ?: fallbackName.also {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "Skill 缺少 name，已使用目录或文件名 '$it'。",
            path = file,
        )
    }
    if (!SKILL_NAME_PATTERN.matches(name)) {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "Skill name '$name' 不符合命令命名规则，已跳过。",
            path = file,
        )
        return null
    }
    val fullDescription = frontmatter.value("description")
        ?: frontmatter.body.lineSequence().firstOrNull(String::isNotBlank)?.trim()
        ?: "未提供说明"
    if (frontmatter.value("description") == null) {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "Skill 缺少 description，已使用正文首行或默认说明。",
            path = file,
        )
    }
    return AgentSkillResource(
        name = name,
        description = fullDescription.replace(Regex("\\s+"), " ").take(SKILL_DESCRIPTION_MAX_LENGTH),
        fullDescription = fullDescription,
        location = file.normalizedExistingOrAbsolute(),
        content = content,
        disableModelInvocation = frontmatter.boolean("disable-model-invocation"),
        origin = root.origin,
    )
}

/** 隐藏目录、node_modules、.git 和 ignore 命中的目录都不参与 Skill 扫描。 */
private fun shouldSkipSkillDirectory(
    directory: Path,
    root: Path,
    ignoreRules: MutableIgnoreRules,
): Boolean {
    val name = directory.fileName?.toString().orEmpty()
    if (name == ".git" || name == "node_modules" || name.startsWith('.')) return true
    return directory != root && ignoreRules.ignores(directory)
}

/**
 * 每个目录的 `.gitignore`、`.ignore` 和 `.fdignore` 以本目录为基准累积，足以覆盖 Skill
 * 包常用的忽略规则。复杂否定规则不改变前序已忽略目录，保持可预测与安全。
 */
private class MutableIgnoreRules(
    private val root: Path,
) {
    private val rules = mutableListOf<IgnoreRule>()

    fun loadRulesFrom(
        directory: Path,
        diagnostics: MutableList<AgentResourceDiagnostic>,
    ) {
        IGNORE_FILE_NAMES.forEach { name ->
            val file = directory.resolve(name)
            if (!Files.isRegularFile(file)) return@forEach
            runCatching {
                Files.readAllLines(file, StandardCharsets.UTF_8)
                    .map(String::trim)
                    .filter { pattern -> pattern.isNotBlank() && !pattern.startsWith('#') && !pattern.startsWith('!') }
                    .forEach { pattern -> rules += IgnoreRule(directory, pattern) }
            }.onFailure { error ->
                diagnostics += AgentResourceDiagnostic(
                    severity = AgentResourceDiagnosticSeverity.INFO,
                    message = "无法读取 ignore 规则：${error.message ?: "未知错误"}",
                    path = file,
                )
            }
        }
    }

    fun ignores(path: Path): Boolean = rules.any { rule -> rule.matches(path) }
}

/** 单条以 ignore 文件所在目录为根的 glob 规则。 */
private data class IgnoreRule(
    val base: Path,
    val rawPattern: String,
) {
    fun matches(path: Path): Boolean {
        if (!path.startsWith(base)) return false
        val relative = base.relativize(path).toString().replace('\\', '/')
        val pattern = rawPattern.removePrefix("/").removeSuffix("/")
        if (pattern.isBlank()) return false
        if (rawPattern.endsWith('/') && (relative == pattern || relative.startsWith("$pattern/"))) return true
        val matcher = runCatching {
            base.fileSystem.getPathMatcher("glob:$pattern")
        }.getOrNull() ?: return false
        val relativePath = base.fileSystem.getPath(relative)
        return matcher.matches(relativePath) || matcher.matches(relativePath.fileName)
    }
}

private const val SKILL_FILE_NAME = "SKILL.md"
private const val SKILL_DESCRIPTION_MAX_LENGTH = 240
private val SKILL_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
private val IGNORE_FILE_NAMES = listOf(".gitignore", ".ignore", ".fdignore")
