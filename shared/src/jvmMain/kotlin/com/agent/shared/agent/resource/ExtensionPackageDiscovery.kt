package com.agent.shared.agent.resource

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** 一个包扫描的聚合结果，供资源加载器与 MCP 桥分别消费。 */
internal data class DiscoveredExtensionPackageResources(
    val packages: List<AgentExtensionPackageResource>,
    val skillRoots: List<SkillSearchRoot>,
    val promptRoots: List<PromptSearchRoot>,
    val mcpServers: List<DeclaredMcpServer>,
)

/** MCP 合并前保留字段是否声明，供后续按 Kilo 规则处理分层覆盖。 */
internal data class DeclaredMcpServer(
    val id: String,
    val transport: AgentMcpTransport?,
    val command: List<String>?,
    val url: String?,
    val environment: Map<String, String>?,
    val packageId: String,
    val origin: AgentResourceOrigin,
    val source: Path,
)

/**
 * 读取受控扩展包的 Pi 兼容目录声明，以及 Mulehang 的 declarative MCP 声明。
 *
 * `pi.extensions` 明确只生成诊断，不执行 npm/TypeScript 代码。`pi.skills`、`pi.prompts`
 * 可以是字符串或字符串数组；缺失时使用包根下的 `skills/`、`prompts/` 常规目录。
 */
internal fun discoverExtensionPackages(
    installedPackages: List<InstalledAgentExtensionPackage>,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): DiscoveredExtensionPackageResources {
    val packages = mutableListOf<AgentExtensionPackageResource>()
    val skillRoots = mutableListOf<SkillSearchRoot>()
    val promptRoots = mutableListOf<PromptSearchRoot>()
    val mcpServers = mutableListOf<DeclaredMcpServer>()
    val seenPackageIds = mutableSetOf<String>()
    val seenPackageRoots = mutableSetOf<String>()

    installedPackages.forEach { installed ->
        val root = installed.root.normalizedExistingOrAbsolute()
        if (!Files.isDirectory(root)) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "扩展包目录不存在，已跳过。",
                path = installed.root,
            )
            return@forEach
        }
        if (!seenPackageRoots.add(root.normalizedIdentity())) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "扩展包目录重复，后续声明已跳过。",
                path = root,
            )
            return@forEach
        }
        val manifest = readPackageManifest(root, diagnostics)
        val id = manifest?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: installed.id
        if (seenPackageIds.add(id)) {
            val displayName = manifest?.get("displayName")?.jsonPrimitive?.contentOrNull
                ?: manifest?.get("name")?.jsonPrimitive?.contentOrNull
                ?: id
            packages += AgentExtensionPackageResource(
                id = id,
                displayName = displayName,
                root = root,
                enabled = installed.enabled,
                origin = installed.origin,
            )
        } else {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.INFO,
                message = "扩展包 id '$id' 重复；其资源将按来源优先级合并。",
                path = root,
            )
        }
        if (!installed.enabled) return@forEach

        val pi = manifest?.get("pi") as? JsonObject
        if (pi?.get("extensions").hasDeclaration()) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "扩展包声明了 pi.extensions；Mulehang 不执行第三方 TypeScript 扩展。",
                path = root.resolve(PACKAGE_JSON_FILE),
            )
        }
        resolveDeclaredDirectories(
            root = root,
            declared = pi?.get("skills").stringValues().ifEmpty { listOf("skills") },
            resourceName = "Skill",
            diagnostics = diagnostics,
        ).forEach { directory ->
            skillRoots += SkillSearchRoot(directory, installed.origin)
        }
        resolveDeclaredDirectories(
            root = root,
            declared = pi?.get("prompts").stringValues().ifEmpty { listOf("prompts") },
            resourceName = "prompt",
            diagnostics = diagnostics,
        ).forEach { directory ->
            promptRoots += PromptSearchRoot(directory, installed.origin)
        }
        mcpServers += parseMcpDeclarations(
            packageId = id,
            origin = installed.origin,
            root = root,
            manifest = manifest,
            diagnostics = diagnostics,
        )
    }
    return DiscoveredExtensionPackageResources(
        packages = packages.toList(),
        skillRoots = skillRoots.toList(),
        promptRoots = promptRoots.toList(),
        mcpServers = mcpServers.toList(),
    )
}

/** 读取可选 package.json；无文件仍按常规目录支持本地包。 */
private fun readPackageManifest(
    root: Path,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): JsonObject? {
    val manifestPath = root.resolve(PACKAGE_JSON_FILE)
    if (!Files.isRegularFile(manifestPath)) return null
    return runCatching {
        packageJson.parseToJsonElement(Files.readString(manifestPath, StandardCharsets.UTF_8)).jsonObject
    }.getOrElse { error ->
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "package.json 无法解析：${error.message ?: "未知错误"}",
            path = manifestPath,
        )
        null
    }
}

/** 只允许包根内的相对资源路径，禁止包声明借机读取工作区外任意位置。 */
private fun resolveDeclaredDirectories(
    root: Path,
    declared: List<String>,
    resourceName: String,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): List<Path> = declared.mapNotNull { raw ->
    val resolved = root.resolve(raw).normalize()
    if (!resolved.startsWith(root)) {
        diagnostics += AgentResourceDiagnostic(
            severity = AgentResourceDiagnosticSeverity.WARNING,
            message = "$resourceName 目录越出扩展包根目录，已拒绝。",
            path = root,
        )
        null
    } else {
        resolved
    }
}.distinctBy(Path::normalizedIdentity)

/** 从 `mulehang.mcp` 或 `mulehang.mcp.json` 读取可由高优先级来源补全的 MCP 声明。 */
private fun parseMcpDeclarations(
    packageId: String,
    origin: AgentResourceOrigin,
    root: Path,
    manifest: JsonObject?,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): List<DeclaredMcpServer> {
    val declaration = manifest?.get("mulehang.mcp")
        ?: (manifest?.get("mulehang") as? JsonObject)?.get("mcp")
        ?: readExternalMcpDeclaration(root, diagnostics)
        ?: return emptyList()
    val entries = declaration.toMcpEntries()
    return entries.mapNotNull { (fallbackId, config) ->
        val id = config["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: fallbackId
        val transportName = config["transport"]?.jsonPrimitive?.contentOrNull
            ?: config["type"]?.jsonPrimitive?.contentOrNull
        val transport = transportName?.toAgentMcpTransport()
        if (transportName != null && transport == null) {
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "MCP '$id' 使用了不支持的 transport。",
                path = root,
            )
            return@mapNotNull null
        }
        val command = if (config.containsKey("command") || config.containsKey("args")) {
            buildList<String> {
                config["command"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let(::add)
                config["args"].stringValues().forEach(::add)
            }
        } else {
            null
        }
        val url = config["url"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        DeclaredMcpServer(
            id = id,
            transport = transport,
            command = command,
            url = url,
            environment = config["env"]?.stringMap(),
            packageId = packageId,
            origin = origin,
            source = root,
        )
    }
}

/** 可选外置声明文件，便于本地包把 package metadata 与连接配置分开维护。 */
private fun readExternalMcpDeclaration(
    root: Path,
    diagnostics: MutableList<AgentResourceDiagnostic>,
): JsonElement? {
    val file = root.resolve(MCP_DECLARATION_FILE)
    if (!Files.isRegularFile(file)) return null
    return runCatching { packageJson.parseToJsonElement(Files.readString(file, StandardCharsets.UTF_8)) }
        .getOrElse { error ->
            diagnostics += AgentResourceDiagnostic(
                severity = AgentResourceDiagnosticSeverity.WARNING,
                message = "mulehang.mcp.json 无法解析：${error.message ?: "未知错误"}",
                path = file,
            )
            null
        }
}

/** 兼容 `{servers:{...}}`、对象映射和数组三种易编辑的 MCP 声明形态。 */
private fun JsonElement.toMcpEntries(): List<Pair<String, JsonObject>> = when (this) {
    is JsonArray -> mapIndexedNotNull { index, element ->
        (element as? JsonObject)?.let { "server-$index" to it }
    }

    is JsonObject -> {
        val servers = this["servers"]
        if (servers != null) return servers.toMcpEntries()
        entries.mapNotNull { (id, element) -> (element as? JsonObject)?.let { id to it } }
    }

    else -> emptyList()
}

/** 读取字符串或字符串数组字段。 */
private fun JsonElement?.stringValues(): List<String> = when (this) {
    is JsonPrimitive -> contentOrNull?.let(::listOf).orEmpty()
    is JsonArray -> mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}

/** 读取 env 对象，非字符串值与 null 被安全忽略。 */
private fun JsonElement?.stringMap(): Map<String, String> = (this as? JsonObject)
    ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }
    ?.toMap()
    .orEmpty()

/** 将受控声明字符串映射为当前首期支持的三种 MCP 传输。 */
private fun String.toAgentMcpTransport(): AgentMcpTransport? = when (lowercase().replace('_', '-')) {
    "stdio" -> AgentMcpTransport.STDIO
    "sse" -> AgentMcpTransport.SSE
    "streamable-http", "streamablehttp", "http" -> AgentMcpTransport.STREAMABLE_HTTP
    else -> null
}

private fun JsonElement?.hasDeclaration(): Boolean = when (this) {
    null -> false
    is JsonArray -> isNotEmpty()
    is JsonObject -> isNotEmpty()
    is JsonPrimitive -> contentOrNull?.isNotBlank() == true
}

private const val PACKAGE_JSON_FILE = "package.json"
private const val MCP_DECLARATION_FILE = "mulehang.mcp.json"
private val packageJson = Json { ignoreUnknownKeys = true }
