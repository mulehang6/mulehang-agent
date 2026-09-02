package com.agent.shared.agent.resource

/** `/` 命令展开后的输入或运行时控制动作。 */
sealed interface AgentCommandExpansion {
    /** 将模板或 Skill 内容插入到 composer 的文本。 */
    data class InsertText(
        val text: String,
        val command: AgentPromptCommand,
    ) : AgentCommandExpansion

    /** `/reload` 由桌面状态层处理，不把控制语句发送给模型。 */
    data object ReloadResources : AgentCommandExpansion
}

/**
 * 解析 Pi prompt 语义的带引号参数。
 *
 * 引号只用于分组，不保留在结果中；反斜杠仅转义紧随其后的引号或反斜杠，未闭合引号将剩余
 * 字符视为同一个参数，保证输入尚未完成时 UI 也能稳定预览。
 */
internal fun parsePromptCommandArguments(raw: String): List<String> {
    val arguments = mutableListOf<String>()
    val buffer = StringBuilder()
    var quote: Char? = null
    var escaping = false
    var started = false

    fun flush() {
        if (started) {
            arguments += buffer.toString()
            buffer.clear()
            started = false
        }
    }

    raw.forEach { character ->
        if (escaping) {
            buffer.append(character)
            started = true
            escaping = false
            return@forEach
        }
        when {
            character == '\\' -> {
                escaping = true
                started = true
            }

            quote != null && character == quote -> quote = null
            quote == null && (character == '\'' || character == '"') -> {
                quote = character
                started = true
            }

            quote == null && character.isWhitespace() -> flush()
            else -> {
                buffer.append(character)
                started = true
            }
        }
    }
    if (escaping) buffer.append('\\')
    flush()
    return arguments
}

/** 按 Pi prompt 模板变量语义展开用户提供的参数。 */
internal fun expandPromptTemplate(template: String, rawArguments: String): String {
    val arguments = parsePromptCommandArguments(rawArguments)
    fun allArguments(): String = arguments.joinToString(" ")
    fun slice(startRaw: String, lengthRaw: String?): String {
        val start = startRaw.toIntOrNull()?.coerceAtLeast(1)?.minus(1) ?: return ""
        val selected = arguments.drop(start)
        val limited = lengthRaw?.toIntOrNull()?.coerceAtLeast(0)?.let(selected::take) ?: selected
        return limited.joinToString(" ")
    }

    var expanded = template
    expanded = ARGUMENTS_DEFAULT_PATTERN.replace(expanded) { match ->
        allArguments().ifBlank { match.groupValues[1] }
    }
    expanded = ALL_ARGUMENTS_DEFAULT_PATTERN.replace(expanded) { match ->
        allArguments().ifBlank { match.groupValues[1] }
    }
    expanded = INDEXED_DEFAULT_PATTERN.replace(expanded) { match ->
        arguments.getOrNull(match.groupValues[1].toIntOrNull()?.minus(1) ?: -1).orEmpty()
            .ifBlank { match.groupValues[2] }
    }
    expanded = ARGUMENT_SLICE_PATTERN.replace(expanded) { match ->
        slice(match.groupValues[1], match.groupValues.getOrNull(2)?.takeIf(String::isNotEmpty))
    }
    expanded = ALL_ARGUMENTS_PATTERN.replace(expanded) { allArguments() }
    expanded = ARGUMENTS_PATTERN.replace(expanded) { allArguments() }
    return INDEXED_ARGUMENT_PATTERN.replace(expanded) { match ->
        arguments.getOrNull(match.groupValues[1].toIntOrNull()?.minus(1) ?: -1).orEmpty()
    }
}

/**
 * 识别完整 `/command arguments` 输入并返回插入内容或控制命令；部分输入不应抢占普通文本。
 */
fun AgentResourceSnapshot.expandSlashCommand(input: String): AgentCommandExpansion? {
    val match = SLASH_COMMAND_PATTERN.matchEntire(input.trim()) ?: return null
    val commandName = match.groupValues[1]
    val rawArguments = match.groupValues.getOrElse(2) { "" }.trimStart()
    val command = commands.firstOrNull { candidate -> candidate.name.equals(commandName, ignoreCase = true) }
        ?: return null
    return when (command.kind) {
        AgentPromptCommandKind.PROMPT -> AgentCommandExpansion.InsertText(
            text = expandPromptTemplate(requireNotNull(command.template), rawArguments),
            command = command,
        )

        AgentPromptCommandKind.SKILL -> {
            val skill = skills.firstOrNull { it.name == command.skillName } ?: return null
            val userSuffix = rawArguments.takeIf(String::isNotBlank)?.let { "\n\nUser: $it" }.orEmpty()
            AgentCommandExpansion.InsertText(
                text = skill.content.trimEnd() + userSuffix,
                command = command,
            )
        }

        AgentPromptCommandKind.BUILTIN -> AgentCommandExpansion.ReloadResources
    }
}

private val SLASH_COMMAND_PATTERN = Regex("^/([^\\s]+)(?:\\s+(.*))?$")
private val ARGUMENTS_DEFAULT_PATTERN = Regex("\\$\\{ARGUMENTS:-([^}]*)}")
private val ALL_ARGUMENTS_DEFAULT_PATTERN = Regex("\\$\\{@:-([^}]*)}")
private val INDEXED_DEFAULT_PATTERN = Regex("\\$\\{(\\d+):-([^}]*)}")
private val ARGUMENT_SLICE_PATTERN = Regex("\\$\\{@:(\\d+)(?::(\\d+))?}")
private val ALL_ARGUMENTS_PATTERN = Regex("\\$@")
private val ARGUMENTS_PATTERN = Regex("\\$" + "ARGUMENTS")
private val INDEXED_ARGUMENT_PATTERN = Regex("\\$(\\d+)")
