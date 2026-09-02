package com.agent.shared.agent.resource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/** 一个 Pi prompts 语义目录及其优先级来源。 */
internal data class PromptSearchRoot(
    val path: Path,
    val origin: AgentResourceOrigin,
)

/**
 * 从 `prompts/` 目录与 Skill 列表构造 `/` 命令。内建 `/reload` 先注册，之后按根目录优先级
 * 与稳定字典序添加 prompt，最后添加 `/skill:<name>`。
 */
internal fun discoverPromptCommands(
    roots: List<PromptSearchRoot>,
    skills: List<AgentSkillResource>,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): List<AgentPromptCommand> {
    val commands = linkedMapOf<String, AgentPromptCommand>()
    commands[RELOAD_COMMAND_NAME] = AgentPromptCommand(
        name = RELOAD_COMMAND_NAME,
        description = "重新加载项目资源、Skills 与扩展声明",
        kind = AgentPromptCommandKind.BUILTIN,
        origin = AgentResourceOrigin.BUILTIN,
    )
    roots.forEach { root ->
        discoverPromptFiles(root).forEach { file ->
            val command = parsePromptCommand(file, root, diagnostics) ?: return@forEach
            val existing = commands.putIfAbsent(command.name, command)
            if (existing != null) {
                diagnostics += AgentResourceDiagnostic(
                    severity = AgentResourceDiagnosticSeverity.WARNING,
                    message = "命令 '/${command.name}' 与更高优先级项冲突，已跳过。",
                    path = file,
                )
            }
        }
    }
    skills.forEach { skill ->
        val command = AgentPromptCommand(
            name = "skill:${skill.name}",
            description = skill.description,
            argumentHint = "可选任务说明",
            kind = AgentPromptCommandKind.SKILL,
            sourcePath = skill.location,
            skillName = skill.name,
            origin = skill.origin,
        )
        val existing = commands.putIfAbsent(command.name, command)
        if (existing != null) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "Skill 命令 '/${command.name}' 与已有命令冲突，已跳过。",
                path = skill.location,
            )
        }
    }
    return commands.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { command -> command.name })
}

/** Pi prompts 目录只读取直接子级 Markdown；子目录需作为单独配置目录或包声明显式加入。 */
private fun discoverPromptFiles(root: PromptSearchRoot): List<Path> {
    if (!Files.isDirectory(root.path)) return emptyList()
    return Files.list(root.path).use { paths ->
        paths
            .filter(Files::isRegularFile)
            .filter { file -> file.fileName.toString().endsWith(".md", ignoreCase = true) }
            .sorted()
            .toList()
    }
}

/** 解析单个 prompt 文件；frontmatter 可选，但 name 和 description 会给出稳定回退。 */
private fun parsePromptCommand(
    file: Path,
    root: PromptSearchRoot,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): AgentPromptCommand? {
    val content = runCatching { Files.readString(file, StandardCharsets.UTF_8).removePrefix("\uFEFF") }
        .getOrElse { error ->
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "无法读取 prompt：${error.message ?: "未知错误"}",
                path = file,
            )
            return null
        }
    val frontmatter = parseMarkdownFrontmatter(content)
    val relativeName = root.path.relativize(file).toString()
        .substringBeforeLast('.')
        .replace('\\', ':')
        .replace('/', ':')
    val name = frontmatter.value("name") ?: relativeName
    if (!PROMPT_NAME_PATTERN.matches(name)) {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "prompt 命令名 '$name' 不符合规则，已跳过。",
            path = file,
        )
        return null
    }
    val template = frontmatter.body.trim()
    if (template.isBlank()) {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "prompt 模板为空，已跳过。",
            path = file,
        )
        return null
    }
    val description = frontmatter.value("description")
        ?: template.lineSequence().firstOrNull(String::isNotBlank)?.trim().orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(PROMPT_DESCRIPTION_MAX_LENGTH)
            .ifBlank { "执行 $name 模板" }
    return AgentPromptCommand(
        name = name,
        description = description,
        argumentHint = frontmatter.value("argument-hint"),
        template = template,
        kind = AgentPromptCommandKind.PROMPT,
        sourcePath = file.normalizedExistingOrAbsolute(),
        origin = root.origin,
    )
}

private const val RELOAD_COMMAND_NAME = "reload"
private const val PROMPT_DESCRIPTION_MAX_LENGTH = 160
private val PROMPT_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")
