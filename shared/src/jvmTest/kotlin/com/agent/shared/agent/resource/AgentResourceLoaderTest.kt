package com.agent.shared.agent.resource

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证 Pi 语义资源加载器的优先级、信任门控与快照生命周期。 */
class AgentResourceLoaderTest {
    /** 全局指令在最外层，项目根到当前目录按外层到内层，且每层只取候选顺序中的首项。 */
    @Test
    fun `should load agent instructions from global outer to inner using candidate precedence`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val workspaceRoot = Files.createTempDirectory("mulehang-resource-workspace")
        val nested = Files.createDirectories(workspaceRoot.resolve("nested"))
        Files.createDirectory(workspaceRoot.resolve(".git"))
        Files.createDirectories(home.resolve(".mulehang"))
        Files.writeString(home.resolve(".mulehang/CLAUDE.md"), "global")
        Files.writeString(workspaceRoot.resolve("AGENTS.md"), "outer agents")
        Files.writeString(workspaceRoot.resolve("CLAUDE.md"), "must not win")
        Files.writeString(nested.resolve("AGENTS.override.md"), "\uFEFFinner override")
        Files.writeString(nested.resolve("AGENTS.md"), "must not win")

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(userHome = home, workspacePath = nested, projectTrusted = true),
            version = 1,
        )

        assertEquals(
            listOf("global", "outer agents", "inner override"),
            snapshot.contextDocuments.map(AgentContextDocument::content),
        )
    }

    /** linked worktree 的 `.git` 文件必须遮蔽其外层主仓库的指令。 */
    @Test
    fun `should stop instruction scan at linked worktree git file`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val mainRepository = Files.createTempDirectory("mulehang-main-repository")
        val worktree = Files.createDirectories(mainRepository.resolve("linked-worktree"))
        val nested = Files.createDirectories(worktree.resolve("module"))
        Files.createDirectory(mainRepository.resolve(".git"))
        Files.writeString(mainRepository.resolve("AGENTS.md"), "main repository")
        Files.writeString(worktree.resolve(".git"), "gitdir: ../.git/worktrees/linked")
        Files.writeString(worktree.resolve("AGENTS.md"), "linked worktree")

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(userHome = home, workspacePath = nested, projectTrusted = true),
            version = 1,
        )

        assertEquals(listOf("linked worktree"), snapshot.contextDocuments.map(AgentContextDocument::content))
    }

    /** Pi 语义下 AGENTS/CLAUDE 是上下文而非可执行资源，未信任项目也必须加载。 */
    @Test
    fun `should load project instructions even before project trust`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val workspace = Files.createTempDirectory("mulehang-resource-workspace")
        Files.createDirectory(workspace.resolve(".git"))
        Files.writeString(workspace.resolve("AGENTS.md"), "always visible convention")

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(userHome = home, workspacePath = workspace, projectTrusted = false),
            version = 1,
        )

        assertEquals(listOf("always visible convention"), snapshot.contextDocuments.map(AgentContextDocument::content))
    }

    /** `~/.agents/skills` 是用户级默认根，不需要附加目录配置或项目资源信任。 */
    @Test
    fun `should automatically load every user agents skill without configured directories`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val agentsSkills = Files.createDirectories(home.resolve(".agents/skills"))
        writeSkill(agentsSkills.resolve("review/SKILL.md"), name = "review", body = "review code")
        writeSkill(agentsSkills.resolve("plan/SKILL.md"), name = "plan", body = "plan work")

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(userHome = home, projectTrusted = false),
            version = 1,
        )

        assertEquals(setOf("review", "plan"), snapshot.skills.map(AgentSkillResource::name).toSet())
        assertTrue(snapshot.commands.map(AgentPromptCommand::name).containsAll(listOf("skill:review", "skill:plan")))
    }

    /** 模型上下文保持紧凑，但设置页必须能取得未截断的 Skill 原始描述。 */
    @Test
    fun `should retain full skill description alongside model summary`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val fullDescription = "d".repeat(300)
        val skillFile = home.resolve(".agents/skills/long/SKILL.md")
        Files.createDirectories(requireNotNull(skillFile.parent))
        Files.writeString(
            skillFile,
            "---\nname: long\ndescription: $fullDescription\n---\nlong skill",
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(userHome = home),
            version = 1,
        )

        val skill = snapshot.skills.single()
        assertEquals(240, skill.description.length)
        assertEquals(fullDescription, skill.fullDescription)
    }

    /** Kilo 语义让后加载的项目 Skill 覆盖用户级同名项，同时保留冲突可见性。 */
    @Test
    fun `should let project skill override same named user skill using Kilo precedence`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val workspace = Files.createTempDirectory("mulehang-resource-workspace")
        Files.createDirectory(workspace.resolve(".git"))
        writeSkill(home.resolve(".agents/skills/same/SKILL.md"), name = "same", body = "user agents")
        writeSkill(home.resolve(".mulehang/skills/same/SKILL.md"), name = "same", body = "user mulehang")
        writeSkill(workspace.resolve(".agents/skills/same/SKILL.md"), name = "same", body = "project agents")
        writeSkill(workspace.resolve(".mulehang/skills/same/SKILL.md"), name = "same", body = "project mulehang")

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(userHome = home, workspacePath = workspace, projectTrusted = true),
            version = 1,
        )

        assertEquals(1, snapshot.skills.size)
        assertTrue(snapshot.skills.single().content.endsWith("project mulehang"))
        assertEquals(AgentResourceOrigin.PROJECT_AUTO_DISCOVERY, snapshot.skills.single().origin)
        assertTrue(snapshot.diagnostics.any { it.message.contains("已使用当前项覆盖") })
    }

    /** `.mulehang/skills` 的高优先级同名项应赢过 `.agents/skills`，ignore 文件与根目录规则也生效。 */
    @Test
    fun `should discover trusted project skills with precedence ignore rules and agents compatibility`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val workspace = Files.createTempDirectory("mulehang-resource-workspace")
        Files.createDirectory(workspace.resolve(".git"))
        val mulehangSkills = Files.createDirectories(workspace.resolve(".mulehang/skills"))
        Files.writeString(mulehangSkills.resolve(".ignore"), "ignored/")
        writeSkill(mulehangSkills.resolve("same/SKILL.md"), name = "same", body = "project preferred")
        writeSkill(mulehangSkills.resolve("ignored/SKILL.md"), name = "ignored", body = "must not load")
        val agentsSkills = Files.createDirectories(workspace.resolve(".agents/skills"))
        writeSkill(agentsSkills.resolve("same/SKILL.md"), name = "same", body = "agents fallback")
        writeSkill(agentsSkills.resolve("nested/SKILL.md"), name = "nested", body = "agents nested")
        Files.writeString(
            agentsSkills.resolve("README.md"),
            "---\nname: direct-readme\ndescription: ignored root markdown\n---\nignored",
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(userHome = home, workspacePath = workspace, projectTrusted = true),
            version = 1,
        )

        assertEquals(listOf("nested", "same"), snapshot.skills.map(AgentSkillResource::name))
        assertTrue(snapshot.skills.single { it.name == "same" }.content.endsWith("project preferred"))
        assertFalse(snapshot.skills.any { it.name == "ignored" || it.name == "direct-readme" })
        assertTrue(snapshot.diagnostics.any { it.message.contains("同名") || it.message.contains("冲突") })
    }

    /** 未信任项目的扩展包不能贡献 Skills、prompts 或 MCP 声明。 */
    @Test
    fun `should gate project extension packages behind trust`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val workspace = Files.createTempDirectory("mulehang-resource-workspace")
        val packageRoot = Files.createDirectories(workspace.resolve("extension"))
        Files.writeString(
            packageRoot.resolve("package.json"),
            """{"name":"project-extension","mulehang.mcp":{"demo":{"transport":"stdio","command":"node"}}}""",
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(
                userHome = home,
                workspacePath = workspace,
                projectTrusted = false,
                packages = listOf(
                    InstalledAgentExtensionPackage(
                        id = "project-extension",
                        root = packageRoot,
                        origin = AgentResourceOrigin.PROJECT_CONFIGURATION,
                    ),
                ),
            ),
            version = 1,
        )

        assertTrue(snapshot.packages.isEmpty())
        assertTrue(snapshot.mcpServers.isEmpty())
        assertTrue(snapshot.diagnostics.any { it.message.contains("项目尚未信任") })
    }

    /** 受控包的 Pi Skills/prompts 与 Mulehang MCP 声明应被发现，而 pi.extensions 仅留下诊断。 */
    @Test
    fun `should load package resources and diagnose unsupported executable pi extensions`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val packageRoot = Files.createTempDirectory("mulehang-extension")
        Files.writeString(
            packageRoot.resolve("package.json"),
            """
            {
              "name":"demo-extension",
              "pi":{"skills":"skills","prompts":["prompts"],"extensions":"index.ts"},
              "mulehang.mcp":{"demo":{"transport":"stdio","command":"node","args":["server.js"]}}
            }
            """.trimIndent(),
        )
        writeSkill(packageRoot.resolve("skills/demo/SKILL.md"), name = "demo", body = "package skill")
        Files.createDirectories(packageRoot.resolve("prompts"))
        Files.writeString(
            packageRoot.resolve("prompts/review.md"),
            "---\nname: review\ndescription: review package changes\n---\nReview $@",
        )
        Files.createDirectories(packageRoot.resolve("prompts/nested"))
        Files.writeString(
            packageRoot.resolve("prompts/nested/hidden.md"),
            "---\nname: hidden\ndescription: must stay undiscovered\n---\nHidden",
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(
                userHome = home,
                packages = listOf(InstalledAgentExtensionPackage(id = "demo", root = packageRoot)),
            ),
            version = 1,
        )

        assertEquals(listOf("demo-extension"), snapshot.packages.map(AgentExtensionPackageResource::id))
        assertEquals(listOf("demo"), snapshot.skills.map(AgentSkillResource::name))
        assertTrue(snapshot.commands.any { it.name == "review" })
        assertFalse(snapshot.commands.any { it.name == "hidden" })
        assertEquals("demo", snapshot.mcpServers.single().id)
        assertTrue(snapshot.diagnostics.any { it.message.contains("pi.extensions") })
    }

    /** 声明式 MCP 包可同时提供 stdio、SSE 与 Streamable HTTP，连接本身不会在发现阶段启动。 */
    @Test
    fun `should parse every supported mcp transport without connecting`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val packageRoot = Files.createTempDirectory("mulehang-extension")
        Files.writeString(
            packageRoot.resolve("package.json"),
            """
            {
              "name":"mcp-extension",
              "mulehang.mcp":{
                "stdio":{"transport":"stdio","command":"node","args":["server.js"]},
                "sse":{"transport":"sse","url":"https://example.test/sse"},
                "http":{"transport":"streamable-http","url":"https://example.test/mcp"}
              }
            }
            """.trimIndent(),
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(
                userHome = home,
                packages = listOf(InstalledAgentExtensionPackage(id = "mcp", root = packageRoot)),
            ),
            version = 1,
        )

        assertEquals(
            mapOf(
                "stdio" to AgentMcpTransport.STDIO,
                "sse" to AgentMcpTransport.SSE,
                "http" to AgentMcpTransport.STREAMABLE_HTTP,
            ),
            snapshot.mcpServers.associate { server -> server.id to server.transport },
        )
    }

    /** 同名 MCP 在保持传输类型时合并项目覆盖字段，并继承全局连接配置。 */
    @Test
    fun `should merge same transport mcp declarations from user to project`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val userPackage = Files.createTempDirectory("mulehang-user-extension")
        val projectPackage = Files.createTempDirectory("mulehang-project-extension")
        Files.writeString(
            userPackage.resolve("package.json"),
            """{"name":"same-extension","mulehang.mcp":{"demo":{"transport":"stdio","command":"node","args":["server.js"],"env":{"GLOBAL":"1","SHARED":"user"}}}}""",
        )
        Files.writeString(
            projectPackage.resolve("package.json"),
            """{"name":"same-extension","mulehang.mcp":{"demo":{"env":{"PROJECT":"2","SHARED":"project"}}}}""",
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(
                userHome = home,
                projectTrusted = true,
                packages = listOf(
                    InstalledAgentExtensionPackage(
                        id = "same-extension",
                        root = userPackage,
                        origin = AgentResourceOrigin.USER_CONFIGURATION,
                    ),
                    InstalledAgentExtensionPackage(
                        id = "same-extension",
                        root = projectPackage,
                        origin = AgentResourceOrigin.PROJECT_CONFIGURATION,
                    ),
                ),
            ),
            version = 1,
        )

        val server = snapshot.mcpServers.single()
        assertEquals(AgentMcpTransport.STDIO, server.transport)
        assertEquals(listOf("node", "server.js"), server.command)
        assertEquals(mapOf("GLOBAL" to "1", "SHARED" to "project", "PROJECT" to "2"), server.environment)
        assertEquals("same-extension", server.packageId)
        assertEquals(AgentResourceOrigin.PROJECT_CONFIGURATION, server.origin)
        assertTrue(snapshot.diagnostics.any { it.message.contains("按 Kilo 规则合并") })
    }

    /** 改变 MCP 传输类型时不能把旧协议的命令和环境变量带入新声明。 */
    @Test
    fun `should reset connection fields when mcp transport changes`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val userPackage = Files.createTempDirectory("mulehang-user-extension")
        val projectPackage = Files.createTempDirectory("mulehang-project-extension")
        Files.writeString(
            userPackage.resolve("package.json"),
            """{"name":"user-extension","mulehang.mcp":{"demo":{"transport":"stdio","command":"node","env":{"TOKEN":"global"}}}}""",
        )
        Files.writeString(
            projectPackage.resolve("package.json"),
            """{"name":"project-extension","mulehang.mcp":{"demo":{"transport":"sse","url":"https://example.test/sse"}}}""",
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(
                userHome = home,
                projectTrusted = true,
                packages = listOf(
                    InstalledAgentExtensionPackage(
                        id = "user-extension",
                        root = userPackage,
                        origin = AgentResourceOrigin.USER_CONFIGURATION,
                    ),
                    InstalledAgentExtensionPackage(
                        id = "project-extension",
                        root = projectPackage,
                        origin = AgentResourceOrigin.PROJECT_CONFIGURATION,
                    ),
                ),
            ),
            version = 1,
        )

        val server = snapshot.mcpServers.single()
        assertEquals(AgentMcpTransport.SSE, server.transport)
        assertEquals("https://example.test/sse", server.url)
        assertTrue(server.command.isEmpty())
        assertTrue(server.environment.isEmpty())
    }

    /** 禁用包仍应在扩展中心可见，但它不能注册任何 Skill、prompt 或 MCP 能力。 */
    @Test
    fun `should retain disabled package metadata without activating capabilities`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val packageRoot = Files.createTempDirectory("mulehang-extension")
        Files.writeString(
            packageRoot.resolve("package.json"),
            """{"name":"disabled-extension","mulehang.mcp":{"demo":{"transport":"stdio","command":"node"}}}""",
        )

        val snapshot = AgentResourceLoader().load(
            AgentResourceLoadRequest(
                userHome = home,
                packages = listOf(InstalledAgentExtensionPackage(id = "disabled", root = packageRoot, enabled = false)),
            ),
            version = 1,
        )

        assertEquals(listOf(false), snapshot.packages.map(AgentExtensionPackageResource::enabled))
        assertTrue(snapshot.skills.isEmpty())
        assertTrue(snapshot.commands.none { it.name == "skill:demo" })
        assertTrue(snapshot.mcpServers.isEmpty())
    }

    /** 写入一份能被宽容 frontmatter 解析器识别的标准 Skill。 */
    private fun writeSkill(
        path: Path,
        name: String,
        body: String,
    ) {
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, "---\nname: $name\ndescription: $name description\n---\n$body")
    }
}
