package com.agent.app.chat.state

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证 `@` 工作区文件浏览器的边界与忽略目录规则。 */
class WorkspaceFileReferenceDiscoveryTest {
    /** 只返回工作区内普通源码，并忽略 Git、依赖和构建目录。 */
    @Test
    fun `should list matching workspace files while excluding generated directories`() {
        val workspace = Files.createTempDirectory("mulehang-file-reference")
        Files.writeString(workspace.resolve("App.kt"), "fun main() = Unit")
        Files.createDirectories(workspace.resolve("src"))
        Files.writeString(workspace.resolve("src/Feature.kt"), "class Feature")
        Files.createDirectories(workspace.resolve(".git"))
        Files.writeString(workspace.resolve(".git/config"), "ignored")
        Files.createDirectories(workspace.resolve("node_modules/pkg"))
        Files.writeString(workspace.resolve("node_modules/pkg/index.js"), "ignored")
        Files.createDirectories(workspace.resolve("build"))
        Files.writeString(workspace.resolve("build/output.kt"), "ignored")

        val references = discoverWorkspaceFileReferences(workspace.toString(), "kt")

        assertEquals(listOf("App.kt", "src/Feature.kt"), references.map(WorkspaceFileReference::relativePath))
        val realWorkspace = workspace.toRealPath()
        assertTrue(references.all { reference -> Path.of(reference.absolutePath).startsWith(realWorkspace) })
    }
}
