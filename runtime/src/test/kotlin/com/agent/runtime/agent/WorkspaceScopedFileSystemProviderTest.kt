package com.agent.runtime.agent

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证工作区受限文件系统 provider 的路径边界。
 */
class WorkspaceScopedFileSystemProviderTest {

    @Test
    fun `should accept absolute paths under workspace root for read only and read write providers`() = runTest {
        val root = Files.createTempDirectory("workspace-root").toAbsolutePath().normalize()
        val nestedFile = root.resolve("docs").resolve("note.txt")

        val readOnly = WorkspaceScopedFileSystemProvider.readOnly(root)
        val readWrite = WorkspaceScopedFileSystemProvider.readWrite(root)

        assertEquals(nestedFile.normalize(), readOnly.fromAbsolutePathString(nestedFile.toString()))
        assertEquals(nestedFile.normalize(), readWrite.fromAbsolutePathString(nestedFile.toString()))
    }

    @Test
    fun `should reject paths outside workspace root including parent traversal`() = runTest {
        val root = Files.createTempDirectory("workspace-root").toAbsolutePath().normalize()
        val outside = Files.createTempFile("outside-file", ".txt").toAbsolutePath().normalize()
        val readOnly = WorkspaceScopedFileSystemProvider.readOnly(root)

        assertFailsWith<IllegalArgumentException> {
            readOnly.fromAbsolutePathString(outside.toString())
        }
        assertFailsWith<IllegalArgumentException> {
            readOnly.fromAbsolutePathString(root.resolve("..").resolve(outside.fileName).toString())
        }
    }
}
