package com.agent.shared.agent.resource

import com.agent.shared.agent.api.AgentRuntimeResources
import com.agent.shared.agent.api.AgentRuntimeMcpServer
import com.agent.shared.agent.api.AgentRuntimeMcpTransport
import java.nio.file.Path

/** 资源诊断的严重等级，供桌面扩展中心按颜色和筛选展示。 */
enum class AgentResourceDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

/** 资源来自的确定性优先级层级。枚举声明顺序即同类资源的首项胜出顺序。 */
enum class AgentResourceOrigin {
    PROJECT_CONFIGURATION,
    PROJECT_AUTO_DISCOVERY,
    USER_CONFIGURATION,
    USER_AUTO_DISCOVERY,
    PACKAGE,
    BUILTIN,
}

/** 单条可定位的资源发现、解析或冲突诊断。 */
data class AgentResourceDiagnostic(
    val severity: AgentResourceDiagnosticSeverity,
    val message: String,
    val path: Path? = null,
)

/** 被注入运行时系统上下文的 AGENTS/CLAUDE 文档。 */
data class AgentContextDocument(
    val path: Path,
    val content: String,
    val origin: AgentResourceOrigin,
)

/** Pi 兼容 Skill 的解析结果。 */
data class AgentSkillResource(
    val name: String,
    val description: String,
    val fullDescription: String = description,
    val location: Path,
    val content: String,
    val disableModelInvocation: Boolean,
    val origin: AgentResourceOrigin,
)

/** `/` 浏览器可展示的命令类型。 */
enum class AgentPromptCommandKind {
    PROMPT,
    SKILL,
    BUILTIN,
}

/** Pi prompt 模板或内建命令的不可变描述。 */
data class AgentPromptCommand(
    val name: String,
    val description: String,
    val argumentHint: String? = null,
    val template: String? = null,
    val kind: AgentPromptCommandKind,
    val sourcePath: Path? = null,
    val skillName: String? = null,
    val origin: AgentResourceOrigin,
)

/** 可由受控扩展包声明的 MCP 传输类型。 */
enum class AgentMcpTransport {
    STDIO,
    SSE,
    STREAMABLE_HTTP,
}

/** 单个 MCP 服务的受控声明，真正连接只在用户启用包并开始后续运行时发生。 */
data class AgentMcpServerResource(
    val id: String,
    val transport: AgentMcpTransport,
    val command: List<String> = emptyList(),
    val url: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val packageId: String,
    val origin: AgentResourceOrigin = AgentResourceOrigin.PACKAGE,
)

/** 已发现的扩展包及其可见状态。 */
data class AgentExtensionPackageResource(
    val id: String,
    val displayName: String,
    val root: Path,
    val enabled: Boolean,
    val origin: AgentResourceOrigin,
)

/** GUI 或配置层已解析的包根目录。 */
data class InstalledAgentExtensionPackage(
    val id: String,
    val root: Path,
    val enabled: Boolean = true,
    val origin: AgentResourceOrigin = AgentResourceOrigin.PACKAGE,
)

/**
 * 资源加载输入。项目 Skills、prompts 与扩展包只在 [projectTrusted] 为 true 时参与发现；Pi 风格
 * AGENTS/CLAUDE 上下文文件始终按目录规则加载，用户级资源始终可用。
 */
data class AgentResourceLoadRequest(
    val userHome: Path,
    val workspacePath: Path? = null,
    val projectTrusted: Boolean = false,
    val userSkillDirectories: List<Path> = emptyList(),
    val projectSkillDirectories: List<Path> = emptyList(),
    val userPromptDirectories: List<Path> = emptyList(),
    val projectPromptDirectories: List<Path> = emptyList(),
    val packages: List<InstalledAgentExtensionPackage> = emptyList(),
)

/**
 * 一次运行固定使用的 Pi 风格资源快照。
 *
 * 所有集合在构造时由加载器以不可变副本提供；[version] 每次手动重载单调增加。
 */
data class AgentResourceSnapshot(
    val version: Long,
    val workspacePath: Path?,
    val contextDocuments: List<AgentContextDocument>,
    val skills: List<AgentSkillResource>,
    val commands: List<AgentPromptCommand>,
    val packages: List<AgentExtensionPackageResource>,
    val mcpServers: List<AgentMcpServerResource>,
    val diagnostics: List<AgentResourceDiagnostic>,
) {
    /** 将可注入内容折叠为跨平台 Agent 请求可携带的不可变系统提示词附录。 */
    fun toRuntimeResources(): AgentRuntimeResources = AgentRuntimeResources(
        version = version,
        systemPromptAppendix = buildString {
            if (contextDocuments.isNotEmpty()) {
                append("<project_instructions>\n")
                contextDocuments.forEach { document ->
                    append("<instruction path=\"")
                    append(document.path)
                    append("\">\n")
                    append(document.content)
                    append("\n</instruction>\n")
                }
                append("</project_instructions>")
            }
            val modelVisibleSkills = skills.filterNot(AgentSkillResource::disableModelInvocation)
            if (modelVisibleSkills.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("<available_skills>\n")
                modelVisibleSkills.forEach { skill ->
                    append("<skill><name>")
                    append(skill.name)
                    append("</name><description>")
                    append(skill.description)
                    append("</description><location>")
                    append(skill.location)
                    append("</location></skill>\n")
                }
                append("</available_skills>")
            }
        },
        mcpServers = mcpServers.map { server ->
            AgentRuntimeMcpServer(
                id = server.id,
                transport = server.transport.toRuntimeMcpTransport(),
                command = server.command,
                url = server.url,
                environment = server.environment,
                packageId = server.packageId,
            )
        },
    )

    companion object {
        /** 没有任何已加载资源时的稳定空快照。 */
        fun empty(workspacePath: Path? = null): AgentResourceSnapshot = AgentResourceSnapshot(
            version = 0L,
            workspacePath = workspacePath,
            contextDocuments = emptyList(),
            skills = emptyList(),
            commands = emptyList(),
            packages = emptyList(),
            mcpServers = emptyList(),
            diagnostics = emptyList(),
        )
    }
}

/** 保持 JVM 资源模型与跨平台请求模型的传输枚举一一对应。 */
private fun AgentMcpTransport.toRuntimeMcpTransport(): AgentRuntimeMcpTransport = when (this) {
    AgentMcpTransport.STDIO -> AgentRuntimeMcpTransport.STDIO
    AgentMcpTransport.SSE -> AgentRuntimeMcpTransport.SSE
    AgentMcpTransport.STREAMABLE_HTTP -> AgentRuntimeMcpTransport.STREAMABLE_HTTP
}
