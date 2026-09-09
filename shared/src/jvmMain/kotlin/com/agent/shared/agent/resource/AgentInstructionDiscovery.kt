package com.agent.shared.agent.resource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Pi 兼容的 AGENTS/CLAUDE 候选文件顺序。每层只加载第一个匹配项。 */
internal val agentInstructionCandidateNames = listOf(
    "AGENTS.override.md",
    "AGENTS.md",
    "AGENTS.MD",
    "CLAUDE.md",
    "CLAUDE.MD",
)

/**
 * 发现全局与工作区层级的 Agent 指令。
 *
 * 全局 `~/.mulehang` 位于最外层，工作区则从 Git 根到当前目录按外层到内层注入。用户级指令始终
 * 可用；项目指令与项目扩展一样必须先获信任，避免打开陌生仓库便将其文本注入会话。linked worktree
 * 的 `.git` 文件被视为当前 worktree 的根边界，绝不跳到共享主工作区继续扫描。
 */
internal fun discoverAgentInstructionResources(
    request: AgentResourceLoadRequest,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): List<AgentContextDocument> {
    val documents = mutableListOf<AgentContextDocument>()
    val loadedPaths = mutableSetOf<String>()

    loadFirstInstructionCandidate(
        directory = request.userHome.resolve(".mulehang"),
        origin = AgentResourceOrigin.USER_AUTO_DISCOVERY,
        loadedPaths = loadedPaths,
        diagnostics = diagnostics,
    )?.let(documents::add)

    val workspace = request.workspacePath?.normalizedExistingOrAbsolute() ?: return documents.toList()
    if (!request.projectTrusted) {
        val candidate = agentInstructionDirectories(workspace)
            .asSequence()
            .flatMap { directory -> agentInstructionCandidateNames.asSequence().map(directory::resolve) }
            .firstOrNull(Files::isRegularFile)
        if (candidate != null) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.INFO,
                message = "项目尚未信任，跳过 Agent 指令。",
                path = candidate,
            )
        }
        return documents.toList()
    }
    val directories = agentInstructionDirectories(workspace)
    directories.forEach { directory ->
        loadFirstInstructionCandidate(
            directory = directory,
            origin = AgentResourceOrigin.PROJECT_AUTO_DISCOVERY,
            loadedPaths = loadedPaths,
            diagnostics = diagnostics,
        )?.let(documents::add)
    }
    return documents.toList()
}

/** 返回当前 workspace 从 Git 根到工作目录的唯一目录链。 */
internal fun agentInstructionDirectories(workspace: Path): List<Path> {
    val root = findWorkspaceScanRoot(workspace)
    val directories = mutableListOf<Path>()
    var current: Path? = workspace
    while (true) {
        val directory = current ?: break
        directories.add(directory)
        if (directory == root) break
        current = directory.parent
    }
    return directories.asReversed().distinctBy(Path::normalizedIdentity)
}

/**
 * 寻找扫描上界。普通仓库以 `.git` 文件夹为根，linked worktree 的 `.git` 文件同样终止扫描，
 * 因此共享主仓库的父路径不会错误地遮蔽本 worktree 的 AGENTS 文件。
 */
internal fun findWorkspaceScanRoot(workspace: Path): Path {
    var current: Path? = workspace
    while (current != null) {
        if (Files.exists(current.resolve(".git"))) return current
        current = current.parent
    }
    return workspace.root ?: workspace
}

/** 从候选顺序中读取首个存在文件，并处理 BOM、真实路径去重与读取失败诊断。 */
private fun loadFirstInstructionCandidate(
    directory: Path,
    origin: AgentResourceOrigin,
    loadedPaths: MutableSet<String>,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): AgentContextDocument? {
    val candidate = agentInstructionCandidateNames
        .asSequence()
        .map(directory::resolve)
        .firstOrNull(Files::isRegularFile)
        ?: return null
    val identity = candidate.normalizedIdentity()
    if (!loadedPaths.add(identity)) {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.INFO,
            message = "重复指令文件已跳过。",
            path = candidate,
        )
        return null
    }
    val content = runCatching {
        Files.readString(candidate, StandardCharsets.UTF_8).removePrefix("\uFEFF")
    }.getOrElse { error ->
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "无法读取 Agent 指令：${error.message ?: "未知错误"}",
            path = candidate,
        )
        return null
    }
    return AgentContextDocument(
        path = candidate.normalizedExistingOrAbsolute(),
        content = content,
        origin = origin,
    )
}

/** 以真实路径去重；不存在的文件回退到绝对规范化路径，方便测试与未来创建后重载。 */
internal fun Path.normalizedIdentity(): String = normalizedExistingOrAbsolute().toString()

/** 获取可能解析 symlink 的绝对路径，失败时保留原路径的稳定规范化形式。 */
internal fun Path.normalizedExistingOrAbsolute(): Path = runCatching {
    toRealPath()
}.getOrElse {
    toAbsolutePath().normalize()
}
