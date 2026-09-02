package com.agent.shared.agent.resource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** 用户从扩展中心主动安装或更新 Git 包后返回的受控安装位置。 */
data class AgentExtensionInstallResult(
    val installedPath: Path,
    val message: String,
)

/**
 * 受控扩展包的 Git 安装器。它只由 GUI 明确操作触发，资源重载绝不会调用此类去拉取网络内容。
 */
class DesktopExtensionPackageInstaller {
    /** 将 Git 源克隆到指定用户或项目范围的 `.mulehang/extensions/<id>` 目录；已存在仓库则更新。 */
    suspend fun installGit(
        source: String,
        packageId: String,
        managedBase: Path,
    ): AgentExtensionInstallResult = withContext(Dispatchers.IO) {
        val normalizedSource = source.trim().also { require(it.isNotBlank()) { "Git 地址不能为空。" } }
        val normalizedId = packageId.normalizedExtensionPackageId()
        val extensionsRoot = managedBase.toAbsolutePath().normalize().resolve(".mulehang/extensions")
        val target = extensionsRoot.resolve(normalizedId).normalize()
        check(target.startsWith(extensionsRoot)) { "扩展包安装路径越出受控目录。" }
        Files.createDirectories(extensionsRoot)
        val message = if (Files.exists(target)) {
            check(Files.isDirectory(target.resolve(".git"))) { "安装目录已存在且不是 Git 仓库。" }
            runGit(target, "fetch", "--all", "--prune")
            runGit(target, "pull", "--ff-only")
            "已更新 Git 扩展包。"
        } else {
            runGit(extensionsRoot, "clone", normalizedSource, target.toString())
            "已安装 Git 扩展包。"
        }
        AgentExtensionInstallResult(installedPath = target, message = message)
    }

    /** 更新一个已经由扩展中心安装的 Git 包；本地目录包不会隐式执行 Git。 */
    suspend fun updateGit(installedPath: Path): AgentExtensionInstallResult = withContext(Dispatchers.IO) {
        val target = installedPath.toRealPath()
        check(Files.isDirectory(target.resolve(".git"))) { "该扩展目录不是 Git 仓库。" }
        runGit(target, "fetch", "--all", "--prune")
        runGit(target, "pull", "--ff-only")
        AgentExtensionInstallResult(installedPath = target, message = "已更新 Git 扩展包。")
    }
}

/** 在已验证的工作目录中执行一条固定的 Git 子命令，并将失败输出保留给 GUI 反馈。 */
private fun runGit(
    workingDirectory: Path,
    vararg arguments: String,
) {
    val process = ProcessBuilder(listOf("git", *arguments))
        .directory(workingDirectory.toFile())
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { reader -> reader.readText().take(MAX_GIT_OUTPUT_LENGTH) }
    check(process.waitFor() == 0) { output.ifBlank { "Git 命令执行失败。" } }
}

/** 将 GUI 输入约束为可作为受控目录名的稳定包 id。 */
private fun String.normalizedExtensionPackageId(): String = trim().also { id ->
    require(EXTENSION_PACKAGE_ID_PATTERN.matches(id)) { "扩展包 id 只能包含字母、数字、点、下划线和连字符。" }
}

private val EXTENSION_PACKAGE_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
private const val MAX_GIT_OUTPUT_LENGTH = 4_000
