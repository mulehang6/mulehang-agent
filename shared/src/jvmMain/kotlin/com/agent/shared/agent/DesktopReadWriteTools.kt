package com.agent.shared.agent

import java.nio.file.Files

/**
 * 封装桌面文件读写工具的基础实现。
 */
class DesktopReadWriteTools(
    private val fileSupport: DesktopFileToolSupport,
) {
    /**
     * 读取文本文件内容。
     */
    fun readFile(path: String): String {
        require(fileSupport.canRead(path)) { "目标路径不能为空" }
        return Files.readString(fileSupport.resolvePath(path))
    }

    /**
     * 列出目录中的一级条目，并按名称排序。
     */
    fun listDir(path: String): String {
        require(fileSupport.canRead(path)) { "目标路径不能为空" }
        Files.list(fileSupport.resolvePath(path)).use { stream ->
            return stream
                .map { it.fileName.toString() }
                .sorted()
                .toList()
                .joinToString(separator = System.lineSeparator())
        }
    }

    /**
     * 将完整内容写入工作区内文件。
     */
    fun writeFile(path: String, content: String): String {
        check(fileSupport.canWrite(path)) { "只允许修改当前工作区内文件" }
        val target = fileSupport.resolvePath(path)
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, content)
        return "ok"
    }

    /**
     * 在工作区内执行一次定点文本替换。
     */
    fun editFile(path: String, oldText: String, newText: String): String {
        check(fileSupport.canWrite(path)) { "只允许修改当前工作区内文件" }
        val target = fileSupport.resolvePath(path)
        val current = Files.readString(target)
        check(current.contains(oldText)) { "目标文本不存在，无法执行定点替换" }
        Files.writeString(target, current.replaceFirst(oldText, newText))
        return "ok"
    }
}
