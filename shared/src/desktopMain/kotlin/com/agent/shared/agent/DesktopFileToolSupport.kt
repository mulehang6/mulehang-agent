package com.agent.shared.agent

import java.nio.file.Path
import java.nio.file.Paths

/**
 * 处理桌面工具的路径归一化和写入边界判断。
 */
class DesktopFileToolSupport(
    workspacePath: String,
) {
    private val workspaceRoot: Path = Paths.get(workspacePath).toAbsolutePath().normalize()

    /**
     * 只读工具默认允许。
     */
    fun canRead(rawPath: String): Boolean = rawPath.isNotBlank()

    /**
     * 写入工具只允许修改当前工作区内路径。
     */
    fun canWrite(rawPath: String): Boolean {
        if (rawPath.isBlank()) {
            return false
        }
        val target = resolvePath(rawPath)
        return target.startsWith(workspaceRoot)
    }

    /**
     * 将相对路径解析到当前工作区，绝对路径则直接归一化。
     */
    fun resolvePath(rawPath: String): Path {
        val path = Paths.get(rawPath)
        return if (path.isAbsolute) {
            path.toAbsolutePath().normalize()
        } else {
            workspaceRoot.resolve(path).normalize()
        }
    }
}
