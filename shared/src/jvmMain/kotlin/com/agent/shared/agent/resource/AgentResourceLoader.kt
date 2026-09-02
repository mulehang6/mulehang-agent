package com.agent.shared.agent.resource

import java.nio.file.Files
import java.nio.file.Path

/**
 * Pi 语义资源加载器。
 *
 * 此类只发现和解析本地声明，不执行第三方扩展代码，也不在重载时自行拉取 Git。调用方将返回
 * 的 [AgentResourceSnapshot] 固定到一轮 Agent 请求，后续手动 reload 才会创建新版本。
 */
class AgentResourceLoader {
    /** 按给定版本加载一次不可变资源快照。 */
    fun load(
        request: AgentResourceLoadRequest,
        version: Long,
    ): AgentResourceSnapshot {
        val diagnostics = mutableListOf<AgentResourceDiagnostic>()
        val packageResources = discoverExtensionPackages(
            installedPackages = trustedPackages(request, diagnostics),
            diagnostics = diagnostics,
        )
        val skills = discoverSkillResources(
            roots = buildSkillRoots(request, packageResources, diagnostics),
            diagnostics = diagnostics,
        )
        val commands = discoverPromptCommands(
            roots = buildPromptRoots(request, packageResources, diagnostics),
            skills = skills,
            diagnostics = diagnostics,
        )
        val mcpServers = mergeMcpServers(packageResources.mcpServers, diagnostics)
        return AgentResourceSnapshot(
            version = version,
            workspacePath = request.workspacePath?.normalizedExistingOrAbsolute(),
            contextDocuments = discoverAgentInstructionResources(request, diagnostics),
            skills = skills,
            commands = commands,
            packages = packageResources.packages,
            mcpServers = mcpServers,
            diagnostics = diagnostics.toList(),
        )
    }

    /** 项目包与项目 Skills 一样必须先获用户信任；未信任时保留可见诊断但绝不读取其声明。 */
    private fun trustedPackages(
        request: AgentResourceLoadRequest,
        diagnostics: MutableList<AgentResourceDiagnostic>,
    ): List<InstalledAgentExtensionPackage> = request.packages.filter { extensionPackage ->
        val projectPackage = extensionPackage.origin == AgentResourceOrigin.PROJECT_CONFIGURATION
        val allowed = !projectPackage || request.projectTrusted
        if (!allowed) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.INFO,
                message = "项目尚未信任，跳过扩展包。",
                path = extensionPackage.root,
            )
        }
        allowed
    }

    /** 以 Kilo 的低优先级先加载、后续来源覆盖同名 Skill 的顺序组合根目录。 */
    private fun buildSkillRoots(
        request: AgentResourceLoadRequest,
        packages: DiscoveredExtensionPackageResources,
        diagnostics: MutableList<AgentResourceDiagnostic>,
    ): List<SkillSearchRoot> = buildList {
        val workspace = request.workspacePath?.normalizedExistingOrAbsolute()
        // Kilo 先装载扩展包与用户级目录，再由项目目录覆盖同名 Skill。
        addAll(packages.skillRoots)
        add(
            SkillSearchRoot(
                path = request.userHome.resolve(".agents/skills"),
                origin = AgentResourceOrigin.USER_AUTO_DISCOVERY,
                agentsCompatibilityRoot = true,
            ),
        )
        add(SkillSearchRoot(request.userHome.resolve(".mulehang/skills"), AgentResourceOrigin.USER_AUTO_DISCOVERY))
        request.userSkillDirectories.asReversed().forEach { directory ->
            add(SkillSearchRoot(directory, AgentResourceOrigin.USER_CONFIGURATION))
        }
        if (request.projectTrusted && workspace != null) {
            // 从 Git 根向工作目录扫描，让最近目录成为最后一个覆盖项。
            agentInstructionDirectories(workspace).forEach { directory ->
                add(
                    SkillSearchRoot(
                        path = directory.resolve(".agents/skills"),
                        origin = AgentResourceOrigin.PROJECT_AUTO_DISCOVERY,
                        agentsCompatibilityRoot = true,
                    ),
                )
            }
            add(SkillSearchRoot(workspace.resolve(".mulehang/skills"), AgentResourceOrigin.PROJECT_AUTO_DISCOVERY))
            request.projectSkillDirectories.asReversed().forEach { directory ->
                add(SkillSearchRoot(directory, AgentResourceOrigin.PROJECT_CONFIGURATION))
            }
        } else if (workspace != null) {
            reportUntrustedProjectRoots(
                paths = listOf(workspace.resolve(".mulehang/skills")) +
                        agentInstructionDirectories(workspace).map { it.resolve(".agents/skills") },
                resourceLabel = "Skill",
                diagnostics = diagnostics,
            )
        }
    }.distinctBy { root -> "${root.origin}:${root.path.normalizedIdentity()}" }

    /** 项目与用户 `.mulehang/prompts` 采用与 Skill 一致的优先级，.agents 不额外定义 prompts。 */
    private fun buildPromptRoots(
        request: AgentResourceLoadRequest,
        packages: DiscoveredExtensionPackageResources,
        diagnostics: MutableList<AgentResourceDiagnostic>,
    ): List<PromptSearchRoot> = buildList {
        val workspace = request.workspacePath?.normalizedExistingOrAbsolute()
        if (request.projectTrusted && workspace != null) {
            request.projectPromptDirectories.forEach { directory ->
                add(PromptSearchRoot(directory, AgentResourceOrigin.PROJECT_CONFIGURATION))
            }
            add(PromptSearchRoot(workspace.resolve(".mulehang/prompts"), AgentResourceOrigin.PROJECT_AUTO_DISCOVERY))
        } else if (workspace != null) {
            reportUntrustedProjectRoots(
                paths = listOf(workspace.resolve(".mulehang/prompts")),
                resourceLabel = "prompt",
                diagnostics = diagnostics,
            )
        }
        request.userPromptDirectories.forEach { directory ->
            add(PromptSearchRoot(directory, AgentResourceOrigin.USER_CONFIGURATION))
        }
        add(PromptSearchRoot(request.userHome.resolve(".mulehang/prompts"), AgentResourceOrigin.USER_AUTO_DISCOVERY))
        addAll(packages.promptRoots)
    }.distinctBy { root -> "${root.origin}:${root.path.normalizedIdentity()}" }

    /** 对存在但未信任的项目根给出一次可见诊断，避免用户误认为资源没有被发现。 */
    private fun reportUntrustedProjectRoots(
        paths: List<Path>,
        resourceLabel: String,
        diagnostics: MutableList<AgentResourceDiagnostic>,
    ) {
        paths.filter(Files::isDirectory).distinctBy(Path::normalizedIdentity).forEach { path ->
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.INFO,
                message = "项目尚未信任，跳过 $resourceLabel 目录。",
                path = path,
            )
        }
    }

    /** 按 Kilo 规则合并同名 MCP 声明，并在合并完成后验证可连接性。 */
    private fun mergeMcpServers(
        servers: List<DeclaredMcpServer>,
        diagnostics: MutableList<AgentResourceDiagnostic>,
    ): List<AgentMcpServerResource> {
        val result = linkedMapOf<String, DeclaredMcpServer>()
        servers.forEach { server ->
            val existing = result[server.id]
            if (existing != null) {
                diagnostics += AgentResourceDiagnostic(
                    severity = AgentResourceDiagnosticSeverity.INFO,
                    message = "MCP 服务 '${server.id}' 与包 '${existing.packageId}' 同名，已按 Kilo 规则合并。",
                    path = server.source,
                )
            }
            result[server.id] = if (existing == null) server else mergeMcpDeclaration(existing, server)
        }
        return result.values.mapNotNull { declaration -> declaration.toMcpServerResource(diagnostics) }
    }

    /** 同传输保留未声明字段；传输变化时丢弃旧连接字段，避免跨协议继承无效配置。 */
    private fun mergeMcpDeclaration(
        current: DeclaredMcpServer,
        override: DeclaredMcpServer,
    ): DeclaredMcpServer {
        val transportChanged = current.transport != null &&
                override.transport != null &&
                current.transport != override.transport
        if (transportChanged) return override
        return current.copy(
            transport = override.transport ?: current.transport,
            command = override.command ?: current.command,
            url = override.url ?: current.url,
            environment = when (val overrideEnvironment = override.environment) {
                null -> current.environment
                else -> current.environment.orEmpty() + overrideEnvironment
            },
            packageId = override.packageId,
            origin = override.origin,
            source = override.source,
        )
    }

    /** 将合并后的部分声明校验为运行时可以连接的 MCP 服务。 */
    private fun DeclaredMcpServer.toMcpServerResource(
        diagnostics: MutableList<AgentResourceDiagnostic>,
    ): AgentMcpServerResource? {
        val resolvedTransport = transport
        if (resolvedTransport == null) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "MCP '$id' 缺少 transport，已跳过。",
                path = source,
            )
            return null
        }
        if (resolvedTransport == AgentMcpTransport.STDIO && command.isNullOrEmpty()) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "stdio MCP '$id' 缺少 command，已跳过。",
                path = source,
            )
            return null
        }
        if (resolvedTransport != AgentMcpTransport.STDIO && url == null) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "HTTP MCP '$id' 缺少 url，已跳过。",
                path = source,
            )
            return null
        }
        return AgentMcpServerResource(
            id = id,
            transport = resolvedTransport,
            command = command.orEmpty(),
            url = url,
            environment = environment.orEmpty(),
            packageId = packageId,
            origin = origin,
        )
    }
}
