package com.agent.shared.tool.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 处理桌面工具的路径归一化、真实路径解析与工作区边界判断。
 *
 * 相对路径总是从工作区出发；符号链接会被解析为真实路径，不能伪装成工作区内文件。
 */
class DesktopFileToolSupport(workspacePath: String) {
    private val workspaceRoot = Paths.get(workspacePath).toAbsolutePath().normalize()
    private val workspaceRealRoot = workspaceRoot.takeIf(Files::exists)?.toRealPath() ?: workspaceRoot

    /** 非空路径可用于读取；调用者仍须根据 [resolveForRead] 验证其存在性。 */
    fun canRead(rawPath: String): Boolean = rawPath.isNotBlank()

    /** 将相对路径解析到工作区，绝对路径则直接归一化。 */
    fun resolvePath(rawPath: String): Path {
        require(rawPath.isNotBlank()) { "目标路径不能为空" }
        val path = Paths.get(rawPath)
        return if (path.isAbsolute) path.toAbsolutePath().normalize() else workspaceRoot.resolve(path).normalize()
    }

    /** 解析存在的读取目标的真实路径。 */
    fun resolveForRead(rawPath: String): ResolvedToolPath {
        val target = resolvePath(rawPath)
        require(Files.exists(target)) { "目标文件不存在: $target" }
        return ResolvedToolPath(target.toRealPath(), target.toRealPath().startsWith(workspaceRealRoot))
    }

    /**
     * 解析新建文件可能不存在的父路径，并将符号链接后的真实目标用于审批判断。
     */
    fun resolveForWrite(rawPath: String): ResolvedToolPath {
        val lexical = resolvePath(rawPath)
        val existing = generateSequence(lexical) { it.parent }.firstOrNull(Files::exists)
            ?: throw IllegalArgumentException("目标路径没有可访问的父目录: $lexical")
        val relative = existing.relativize(lexical)
        val realTarget = existing.toRealPath().resolve(relative).normalize()
        return ResolvedToolPath(realTarget, realTarget.startsWith(workspaceRealRoot))
    }

    /** 已归一化的真实路径及其工作区归属。 */
    data class ResolvedToolPath(
        val path: Path,
        val isInsideWorkspace: Boolean,
    )
}
