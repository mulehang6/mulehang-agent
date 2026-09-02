package com.agent.shared.agent.resource

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 验证 Pi prompts 参数替换、Skill 命令与显式资源重载语义。 */
class PromptCommandTemplateTest {
    /** 带引号参数、默认值与切片替换必须按同一解析顺序展开。 */
    @Test
    fun `should expand quoted prompt arguments defaults and slices`() {
        val template = "first=${'$'}1 all=${'$'}@ named=${'$'}ARGUMENTS second=${'$'}{2:-fallback} slice=${'$'}{@:2:2}"

        val expanded = expandPromptTemplate(template, "one \"two words\" three four")

        assertEquals(
            "first=one all=one two words three four named=one two words three four second=two words slice=two words three",
            expanded,
        )
        assertEquals(listOf("one", "two words", "three"), parsePromptCommandArguments("one 'two words' three"))
        assertEquals("fallback", expandPromptTemplate("${'$'}{2:-fallback}", "one"))
    }

    /** `/skill:name` 插入完整 Skill 内容和用户参数，而 `/reload` 保持控制动作。 */
    @Test
    fun `should expand skill command and preserve reload as control action`() {
        val skill = AgentSkillResource(
            name = "review",
            description = "review changes",
            location = Path.of("review/SKILL.md"),
            content = "---\nname: review\n---\nReview carefully\n",
            disableModelInvocation = false,
            origin = AgentResourceOrigin.USER_AUTO_DISCOVERY,
        )
        val skillCommand = AgentPromptCommand(
            name = "skill:review",
            description = skill.description,
            kind = AgentPromptCommandKind.SKILL,
            skillName = skill.name,
            origin = skill.origin,
        )
        val reloadCommand = AgentPromptCommand(
            name = "reload",
            description = "reload",
            kind = AgentPromptCommandKind.BUILTIN,
            origin = AgentResourceOrigin.BUILTIN,
        )
        val snapshot = AgentResourceSnapshot.empty().copy(
            skills = listOf(skill),
            commands = listOf(skillCommand, reloadCommand),
        )

        val expansion = snapshot.expandSlashCommand("/skill:review \"inspect tests\"")

        assertIs<AgentCommandExpansion.InsertText>(expansion)
        assertTrue(expansion.text.contains("Review carefully"))
        assertTrue(expansion.text.endsWith("User: \"inspect tests\""))
        assertIs<AgentCommandExpansion.ReloadResources>(snapshot.expandSlashCommand("/reload"))
    }

    /** 同一工作区只读已发布快照；文件变更直到用户显式 reload 才会影响下一轮。 */
    @Test
    fun `should keep snapshot fixed until explicit reload`() {
        val home = Files.createTempDirectory("mulehang-resource-home")
        val workspace = Files.createTempDirectory("mulehang-resource-workspace")
        Files.createDirectory(workspace.resolve(".git"))
        Files.createDirectories(home.resolve(".mulehang"))
        Files.writeString(home.resolve(".mulehang/AGENTS.md"), "version one")
        val request = AgentResourceLoadRequest(userHome = home, workspacePath = workspace, projectTrusted = true)
        val runtime = AgentResourceRuntime()

        val initial = runtime.snapshotFor(request)
        Files.writeString(home.resolve(".mulehang/AGENTS.md"), "version two")
        val unchanged = runtime.snapshotFor(request)
        val reloaded = runtime.reload(request)

        assertEquals(initial.version, unchanged.version)
        assertEquals("version one", unchanged.contextDocuments.single().content)
        assertTrue(reloaded.version > initial.version)
        assertEquals("version two", reloaded.contextDocuments.single().content)
    }
}
