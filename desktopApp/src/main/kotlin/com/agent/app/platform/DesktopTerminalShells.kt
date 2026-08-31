package com.agent.app.platform

import com.agent.shared.session.DEFAULT_DESKTOP_TERMINAL_SHELL_ID
import com.agent.shared.session.DesktopTerminalPreferences
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

/**
 * Windows 桌面端支持的内嵌终端 Shell 类型。
 *
 * 每个类型使用稳定存储标识；实际可执行文件路径只在应用启动时检测，绝不写入用户设置。
 */
internal enum class DesktopTerminalShell(
    val storageId: String,
    val label: String,
    private val arguments: List<String>,
) {
    WINDOWS_POWERSHELL(
        storageId = DEFAULT_DESKTOP_TERMINAL_SHELL_ID,
        label = "Windows PowerShell",
        arguments = listOf("-NoLogo"),
    ),
    POWERSHELL_7(
        storageId = "powershell-7",
        label = "PowerShell 7",
        arguments = listOf("-NoLogo"),
    ),
    COMMAND_PROMPT(
        storageId = "command-prompt",
        label = "命令提示符",
        arguments = emptyList(),
    ),
    GIT_BASH(
        storageId = "git-bash",
        label = "Git Bash",
        arguments = listOf("--login", "-i"),
    ),
    MSYS2_BASH(
        storageId = "msys2-bash",
        label = "MSYS2 Bash",
        arguments = listOf("--login", "-i"),
    ),
    CYGWIN_BASH(
        storageId = "cygwin-bash",
        label = "Cygwin Bash",
        arguments = listOf("--login", "-i"),
    ),
    NUSHELL(
        storageId = "nushell",
        label = "Nushell",
        arguments = emptyList(),
    ),
    ;

    /** 根据此类型和临时检测到的可执行文件路径构造启动命令。 */
    fun launchCommand(executable: Path): List<String> = listOf(executable.toString()) + arguments

    companion object {
        /** 从持久化标识解析已知 Shell 类型；未知标识保持由调用方回退。 */
        fun fromStorageId(storageId: String): DesktopTerminalShell? =
            entries.firstOrNull { shell -> shell.storageId == storageId }
    }
}

/**
 * 一个已在本机检测到、可以用于创建新终端的 Shell 描述符。
 */
internal data class TerminalShellDescriptor(
    val shell: DesktopTerminalShell,
    val executable: Path,
) {
    /** 返回供 PTY4J 使用的完整启动命令。 */
    fun launchCommand(): List<String> = shell.launchCommand(executable)
}

/**
 * 新建终端时解析偏好的结果。
 */
internal data class ResolvedTerminalShell(
    val descriptor: TerminalShellDescriptor,
    val preferredShellAvailable: Boolean,
)

/**
 * 本次应用启动期间可用的 Shell 目录。
 */
internal data class TerminalShellCatalog(
    val availableShells: List<TerminalShellDescriptor>,
) {
    /** 判断指定的稳定 Shell 标识在当前系统是否可用。 */
    fun isAvailable(storageId: String): Boolean = availableShells.any { descriptor ->
        descriptor.shell.storageId == storageId
    }

    /** 返回用于展示已保存标识的可读名称。 */
    fun labelFor(storageId: String): String =
        DesktopTerminalShell.fromStorageId(storageId)?.label ?: storageId

    /**
     * 解析新终端应该使用的 Shell；不可用的偏好不会被改写，而是临时回退到旧版 Windows PowerShell。
     */
    fun resolve(preferences: DesktopTerminalPreferences): ResolvedTerminalShell {
        val normalizedPreferences = preferences.normalized()
        val preferred = availableShells.firstOrNull { descriptor ->
            descriptor.shell.storageId == normalizedPreferences.defaultShellId
        }
        return ResolvedTerminalShell(
            descriptor = preferred ?: legacyPowerShellFallbackDescriptor(),
            preferredShellAvailable = preferred != null,
        )
    }
}

/**
 * 在应用启动时扫描当前 Windows 系统中可用的原生 Shell。
 *
 * 此目录刻意不扫描 WSL，也不接受任意自定义可执行文件路径。
 */
internal fun loadDesktopTerminalShellCatalog(): TerminalShellCatalog = createTerminalShellCatalog(
    candidatesByShell = terminalShellCandidates(
        environment = System.getenv(),
        findOnPath = ::findTerminalExecutablesOnPath,
    ),
)

/**
 * 用已收集的候选路径建立按固定产品顺序排列、且按真实文件路径去重的 Shell 目录。
 */
internal fun createTerminalShellCatalog(
    candidatesByShell: Map<DesktopTerminalShell, List<Path>>,
    isUsableExecutable: (Path) -> Boolean = Files::isRegularFile,
): TerminalShellCatalog {
    val seenExecutablePaths = mutableSetOf<String>()
    val descriptors = buildList {
        DesktopTerminalShell.entries.forEach { shell ->
            val executable = candidatesByShell[shell]
                .orEmpty()
                .firstOrNull { candidate ->
                    isUsableExecutable(candidate) &&
                        normalizedTerminalExecutablePath(candidate) !in seenExecutablePaths
                }
                ?: return@forEach
            seenExecutablePaths += normalizedTerminalExecutablePath(executable)
            add(TerminalShellDescriptor(shell = shell, executable = executable))
        }
    }
    return TerminalShellCatalog(availableShells = descriptors)
}

/**
 * 生成各 Shell 的候选路径；检测顺序优先系统固定位置，再使用 PATH 和常见安装目录。
 */
internal fun terminalShellCandidates(
    environment: Map<String, String>,
    findOnPath: (String) -> List<Path>,
): Map<DesktopTerminalShell, List<Path>> {
    val systemRoot = environmentPath(environment, "SystemRoot")
    val programFiles = environmentPath(environment, "ProgramFiles")
    val programFilesX86 = environmentPath(environment, "ProgramFiles(x86)")
    val userProfile = environmentPath(environment, "USERPROFILE")
    val gitPaths = findOnPath("git.exe")
    val bashPaths = findOnPath("bash.exe")
    val gitBashCandidates = buildList {
        gitPaths.forEach { gitExecutable ->
            val directory = gitExecutable.parent ?: return@forEach
            add(directory.resolve("bash.exe"))
            directory.parent?.let { gitRoot -> add(gitRoot.resolve("bin").resolve("bash.exe")) }
        }
        programFiles?.let { add(it.resolve("Git").resolve("bin").resolve("bash.exe")) }
        programFilesX86?.let { add(it.resolve("Git").resolve("bin").resolve("bash.exe")) }
        addAll(bashPaths.filter { path -> pathContainsShellMarker(path, "git") })
    }
    val msys2Root = environmentPath(environment, "MSYS2_ROOT")
    val cygwinRoot = environmentPath(environment, "CYGWIN_ROOT")

    return linkedMapOf(
        DesktopTerminalShell.WINDOWS_POWERSHELL to buildList {
            systemRoot?.let { add(it.resolve("System32").resolve("WindowsPowerShell").resolve("v1.0").resolve("powershell.exe")) }
            addAll(findOnPath("powershell.exe"))
        },
        DesktopTerminalShell.POWERSHELL_7 to buildList {
            programFiles?.let { add(it.resolve("PowerShell").resolve("7").resolve("pwsh.exe")) }
            addAll(findOnPath("pwsh.exe"))
        },
        DesktopTerminalShell.COMMAND_PROMPT to buildList {
            environmentPath(environment, "ComSpec")?.let(::add)
            systemRoot?.let { add(it.resolve("System32").resolve("cmd.exe")) }
            addAll(findOnPath("cmd.exe"))
        },
        DesktopTerminalShell.GIT_BASH to gitBashCandidates,
        DesktopTerminalShell.MSYS2_BASH to buildList {
            msys2Root?.let { add(it.resolve("usr").resolve("bin").resolve("bash.exe")) }
            add(Paths.get("C:\\msys64\\usr\\bin\\bash.exe"))
            addAll(bashPaths.filter { path -> pathContainsShellMarker(path, "msys") })
        },
        DesktopTerminalShell.CYGWIN_BASH to buildList {
            cygwinRoot?.let { add(it.resolve("bin").resolve("bash.exe")) }
            add(Paths.get("C:\\cygwin64\\bin\\bash.exe"))
            add(Paths.get("C:\\cygwin\\bin\\bash.exe"))
            addAll(bashPaths.filter { path -> pathContainsShellMarker(path, "cygwin") })
        },
        DesktopTerminalShell.NUSHELL to buildList {
            userProfile?.let { add(it.resolve(".cargo").resolve("bin").resolve("nu.exe")) }
            addAll(findOnPath("nu.exe"))
        },
    )
}

/** 解析环境变量中的路径，并兼容 Windows 不区分大小写的变量名。 */
private fun environmentPath(environment: Map<String, String>, name: String): Path? =
    environment.entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { rawPath -> runCatching { Paths.get(rawPath) }.getOrNull() }

/** 通过 Windows 的 where.exe 查询 PATH 中实际可执行的文件。 */
private fun findTerminalExecutablesOnPath(fileName: String): List<Path> = runCatching {
    val process = ProcessBuilder("where.exe", fileName)
        .redirectErrorStream(true)
        .start()
    val lines = process.inputStream.bufferedReader().use { reader -> reader.readLines() }
    if (process.waitFor() != 0) {
        emptyList()
    } else {
        lines.mapNotNull { line ->
            line.trim().takeIf(String::isNotEmpty)?.let { rawPath ->
                runCatching { Paths.get(rawPath) }.getOrNull()
            }
        }
    }
}.getOrDefault(emptyList())

/** 为路径去重构造忽略大小写、规范化后的 Windows 文件系统键。 */
private fun normalizedTerminalExecutablePath(path: Path): String =
    path.toAbsolutePath().normalize().toString().lowercase(Locale.ROOT)

/** 判断 PATH 中的 Bash 是否属于一个受支持的已知 Shell 发行版。 */
private fun pathContainsShellMarker(path: Path, marker: String): Boolean =
    path.toString().contains(marker, ignoreCase = true)

/** 返回不可用偏好时使用的旧版 Windows PowerShell 临时描述符。 */
private fun legacyPowerShellFallbackDescriptor(): TerminalShellDescriptor = TerminalShellDescriptor(
    shell = DesktopTerminalShell.WINDOWS_POWERSHELL,
    executable = Paths.get("powershell.exe"),
)
