# Koog Built-in File Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Koog 官方文件/目录 built-in tools 接入当前 runtime capability 主线，并为它们补上 `LOW` / `MID` 风险分层与工作区根目录约束。

**Architecture:** 先扩展 capability 元数据，让现有 descriptor 可以稳定表达风险等级；再新增一个只负责声明 Koog built-in 文件工具的 capability 模型，以及一个工作区受限的文件系统 provider 包装层；最后在 `KoogToolRegistryAssembler` 中把这些声明翻译成真实的 Koog built-in tools，并验证不会破坏现有 custom/MCP/HTTP registry 装配。仓库规则不允许未获授权擅自提交，因此本计划不包含 commit 步骤。

**Tech Stack:** Kotlin/JVM, JetBrains Koog 0.8.0 (`koog-agents`, `agents-ext`, `rag-base`), kotlin.test, JUnit 5, kotlinx.coroutines-test

---

## File Map

- `runtime/src/main/kotlin/com/agent/runtime/capability/CapabilityContract.kt`
  为统一 descriptor 增加 `ToolRiskLevel`，并让现有 capability 描述具备结构化风险字段。
- `runtime/src/main/kotlin/com/agent/runtime/capability/CapabilitySet.kt`
  扩展为同时持有可执行 adapter 与 Koog built-in 文件工具声明，并统一输出 descriptors。
- `runtime/src/main/kotlin/com/agent/runtime/capability/BuiltInFileToolCapability.kt`
  新增 Koog built-in 文件工具声明模型，固定 `__list_directory__`、`__read_file__`、`__write_file__`、`edit_file` 的 id、风险等级与工作区根目录。
- `runtime/src/main/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProvider.kt`
  新增围绕 `JVMFileSystemProvider` 的工作区受限包装层，拒绝访问仓库根目录外的路径。
- `runtime/src/main/kotlin/com/agent/runtime/agent/KoogToolRegistryAssembler.kt`
  把 built-in 文件工具声明翻译成真实 Koog tools，并并入现有 primary registry。
- `runtime/src/test/kotlin/com/agent/runtime/capability/CapabilityAdaptersTest.kt`
  固定 descriptor 风险字段与 built-in 文件工具声明的输出。
- `runtime/src/test/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProviderTest.kt`
  固定工作区根目录约束行为。
- `runtime/src/test/kotlin/com/agent/runtime/agent/KoogToolRegistryAssemblerTest.kt`
  固定 built-in 文件工具的 registry 装配结果，以及只读/读写子集装配行为。
- `runtime/src/test/kotlin/com/agent/runtime/agent/AgentAssemblyTest.kt`
  固定 assembled agent 在保留现有 tool/MCP registry 的同时，能把 built-in 文件工具并入最终 registry。

---

### Task 1: 扩展 capability 元数据并声明 built-in 文件工具

**Files:**
- Modify: `runtime/src/main/kotlin/com/agent/runtime/capability/CapabilityContract.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/capability/CapabilitySet.kt`
- Create: `runtime/src/main/kotlin/com/agent/runtime/capability/BuiltInFileToolCapability.kt`
- Modify: `runtime/src/test/kotlin/com/agent/runtime/capability/CapabilityAdaptersTest.kt`

- [ ] **Step 1: 先写失败测试，锁定风险字段和 built-in 文件工具声明**

```kotlin
@Test
fun `should expose risk metadata on existing runtime capability descriptors`() = runTest {
    val capabilitySet = CapabilitySet(
        adapters = listOf(
            ToolCapabilityAdapter.echo(id = "tool.echo"),
            McpCapabilityAdapter.streamableHttp(id = "mcp.list", url = "http://localhost:8931/mcp"),
            HttpCapabilityAdapter.internalGet(id = "http.internal", path = "/internal"),
        ),
    )

    assertEquals(
        listOf(
            CapabilityDescriptor(id = "tool.echo", kind = "tool", riskLevel = ToolRiskLevel.MID),
            CapabilityDescriptor(id = "mcp.list", kind = "mcp", riskLevel = ToolRiskLevel.HIGH),
            CapabilityDescriptor(id = "http.internal", kind = "http", riskLevel = ToolRiskLevel.HIGH),
        ),
        capabilitySet.descriptors(),
    )
}

@Test
fun `should expose built in file tool declarations with exact low and mid risks`() {
    val root = "D:\\JetBrains\\projects\\idea_projects\\mulehang-agent"
    val capabilitySet = CapabilitySet(
        adapters = emptyList(),
        builtInFileTools = listOf(
            BuiltInFileToolCapability.listDirectory(root),
            BuiltInFileToolCapability.readFile(root),
            BuiltInFileToolCapability.writeFile(root),
            BuiltInFileToolCapability.editFile(root),
        ),
    )

    assertEquals(
        listOf(
            CapabilityDescriptor(id = "__list_directory__", kind = "filesystem", riskLevel = ToolRiskLevel.LOW),
            CapabilityDescriptor(id = "__read_file__", kind = "filesystem", riskLevel = ToolRiskLevel.LOW),
            CapabilityDescriptor(id = "__write_file__", kind = "filesystem", riskLevel = ToolRiskLevel.MID),
            CapabilityDescriptor(id = "edit_file", kind = "filesystem", riskLevel = ToolRiskLevel.MID),
        ),
        capabilitySet.descriptors(),
    )
}
```

- [ ] **Step 2: 运行 capability 测试，确认新模型尚未实现**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.capability.CapabilityAdaptersTest
```

Expected: FAIL，报错包含 `ToolRiskLevel`、`riskLevel`、`BuiltInFileToolCapability` 或 `builtInFileTools` 未定义。

- [ ] **Step 3: 写最小实现，补齐风险枚举、descriptor 扩展和 built-in 文件工具声明**

```kotlin
enum class ToolRiskLevel {
    LOW,
    MID,
    HIGH,
}

data class CapabilityDescriptor(
    val id: String,
    val kind: String,
    val riskLevel: ToolRiskLevel,
)

data class BuiltInFileToolCapability(
    val descriptor: CapabilityDescriptor,
    val workspaceRoot: String,
) {
    companion object {
        fun listDirectory(root: String) = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor("__list_directory__", "filesystem", ToolRiskLevel.LOW),
            workspaceRoot = root,
        )

        fun readFile(root: String) = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor("__read_file__", "filesystem", ToolRiskLevel.LOW),
            workspaceRoot = root,
        )

        fun writeFile(root: String) = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor("__write_file__", "filesystem", ToolRiskLevel.MID),
            workspaceRoot = root,
        )

        fun editFile(root: String) = BuiltInFileToolCapability(
            descriptor = CapabilityDescriptor("edit_file", "filesystem", ToolRiskLevel.MID),
            workspaceRoot = root,
        )
    }
}

class CapabilitySet(
    adapters: List<CapabilityAdapter>,
    builtInFileTools: List<BuiltInFileToolCapability> = emptyList(),
) {
    private val adaptersById = adapters.associateBy { it.descriptor.id }
    private val builtInFileToolsById = builtInFileTools.associateBy { it.descriptor.id }

    fun descriptors(): List<CapabilityDescriptor> =
        adaptersById.values.map { it.descriptor } + builtInFileToolsById.values.map { it.descriptor }

    fun builtInFileTools(): List<BuiltInFileToolCapability> = builtInFileToolsById.values.toList()
}
```

并把现有 adapter descriptor 调整为保守默认值：

```kotlin
override val descriptor = CapabilityDescriptor(id = id, kind = "tool", riskLevel = ToolRiskLevel.MID)
override val descriptor = CapabilityDescriptor(id = id, kind = "mcp", riskLevel = ToolRiskLevel.HIGH)
override val descriptor = CapabilityDescriptor(id = id, kind = "http", riskLevel = ToolRiskLevel.HIGH)
```

- [ ] **Step 4: 重新运行 capability 测试，确认 descriptor 与 built-in 文件工具声明稳定**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.capability.CapabilityAdaptersTest
```

Expected: PASS，并覆盖现有 custom/MCP/HTTP descriptor 与新 built-in 文件工具 descriptor 的 `riskLevel`。

### Task 2: 增加工作区受限的文件系统 provider 包装层

**Files:**
- Create: `runtime/src/main/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProvider.kt`
- Create: `runtime/src/test/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProviderTest.kt`

- [ ] **Step 1: 先写失败测试，锁定工作区内允许、工作区外拒绝**

```kotlin
class WorkspaceScopedFileSystemProviderTest {
    @Test
    fun `should accept absolute paths under workspace root for read only and read write providers`() = runTest {
        val root = createTempDirectory("workspace-root").toAbsolutePath().normalize()
        val nestedFile = root.resolve("docs").resolve("note.txt")

        val readOnly = WorkspaceScopedFileSystemProvider.readOnly(root)
        val readWrite = WorkspaceScopedFileSystemProvider.readWrite(root)

        assertEquals(nestedFile.normalize(), readOnly.fromAbsolutePathString(nestedFile.toString()))
        assertEquals(nestedFile.normalize(), readWrite.fromAbsolutePathString(nestedFile.toString()))
    }

    @Test
    fun `should reject paths outside workspace root including parent traversal`() = runTest {
        val root = createTempDirectory("workspace-root").toAbsolutePath().normalize()
        val outside = createTempFile("outside-file", ".txt").toAbsolutePath().normalize()
        val readOnly = WorkspaceScopedFileSystemProvider.readOnly(root)

        assertFailsWith<IllegalArgumentException> {
            readOnly.fromAbsolutePathString(outside.toString())
        }
        assertFailsWith<IllegalArgumentException> {
            readOnly.fromAbsolutePathString(root.resolve("..").resolve(outside.fileName).toString())
        }
    }
}
```

- [ ] **Step 2: 运行 provider 包装层测试，确认受限文件系统尚未实现**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.agent.WorkspaceScopedFileSystemProviderTest
```

Expected: FAIL，报错包含 `WorkspaceScopedFileSystemProvider` 未定义。

- [ ] **Step 3: 写最小实现，围绕 `JVMFileSystemProvider` 包一层工作区校验**

```kotlin
object WorkspaceScopedFileSystemProvider {
    fun readOnly(root: Path): FileSystemProvider.ReadOnly<Path> =
        ScopedReadOnly(root = root.toAbsolutePath().normalize(), delegate = JVMFileSystemProvider.ReadOnly)

    fun readWrite(root: Path): FileSystemProvider.ReadWrite<Path> =
        ScopedReadWrite(root = root.toAbsolutePath().normalize(), delegate = JVMFileSystemProvider.ReadWrite)
}

private class ScopedReadOnly(
    private val root: Path,
    private val delegate: FileSystemProvider.ReadOnly<Path>,
) : FileSystemProvider.ReadOnly<Path> by delegate {
    override fun fromAbsolutePathString(path: String): Path = delegate.fromAbsolutePathString(path).also(::requireWithinRoot)
    override fun joinPath(base: Path, vararg parts: String): Path = delegate.joinPath(base.also(::requireWithinRoot), *parts).also(::requireWithinRoot)
    override suspend fun metadata(path: Path): FileMetadata? = delegate.metadata(path.also(::requireWithinRoot))
    override suspend fun getFileContentType(path: Path): FileMetadata.FileContentType =
        delegate.getFileContentType(path.also(::requireWithinRoot))
    override suspend fun list(directory: Path): List<Path> = delegate.list(directory.also(::requireWithinRoot))
    override suspend fun exists(path: Path): Boolean = delegate.exists(path.also(::requireWithinRoot))
    override suspend fun readBytes(path: Path): ByteArray = delegate.readBytes(path.also(::requireWithinRoot))
    override suspend fun inputStream(path: Path): Source = delegate.inputStream(path.also(::requireWithinRoot))
    override suspend fun size(path: Path): Long = delegate.size(path.also(::requireWithinRoot))

    private fun requireWithinRoot(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.startsWith(root)) {
            "Path '$normalized' is outside workspace root '$root'."
        }
    }
}

private class ScopedReadWrite(
    private val root: Path,
    private val delegate: FileSystemProvider.ReadWrite<Path>,
) : FileSystemProvider.ReadWrite<Path>, FileSystemProvider.ReadOnly<Path> by ScopedReadOnly(root, delegate) {
    override suspend fun create(path: Path, type: FileMetadata.FileType) =
        delegate.create(path.also(::requireWithinRoot), type)

    override suspend fun move(source: Path, target: Path) =
        delegate.move(source.also(::requireWithinRoot), target.also(::requireWithinRoot))

    override suspend fun copy(source: Path, target: Path) =
        delegate.copy(source.also(::requireWithinRoot), target.also(::requireWithinRoot))

    override suspend fun writeBytes(path: Path, data: ByteArray) =
        delegate.writeBytes(path.also(::requireWithinRoot), data)

    override suspend fun outputStream(path: Path, append: Boolean): Sink =
        delegate.outputStream(path.also(::requireWithinRoot), append)

    override suspend fun delete(path: Path) =
        delegate.delete(path.also(::requireWithinRoot))

    private fun requireWithinRoot(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.startsWith(root)) {
            "Path '$normalized' is outside workspace root '$root'."
        }
    }
}
```

- [ ] **Step 4: 重新运行 provider 包装层测试，确认工作区边界稳定**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.agent.WorkspaceScopedFileSystemProviderTest
```

Expected: PASS，并明确拒绝工作区外绝对路径和 `..` 穿越路径。

### Task 3: 在 KoogToolRegistryAssembler 中注册 built-in 文件工具

**Files:**
- Modify: `runtime/src/main/kotlin/com/agent/runtime/agent/KoogToolRegistryAssembler.kt`
- Modify: `runtime/src/test/kotlin/com/agent/runtime/agent/KoogToolRegistryAssemblerTest.kt`

- [ ] **Step 1: 先写失败测试，锁定 4 个 built-in 文件工具与只读子集的注册结果**

```kotlin
@Test
fun `should register all built in file tools into primary koog registry with workspace root restriction`() = runTest {
    val root = "D:\\JetBrains\\projects\\idea_projects\\mulehang-agent"
    val capabilitySet = CapabilitySet(
        adapters = emptyList(),
        builtInFileTools = listOf(
            BuiltInFileToolCapability.listDirectory(root),
            BuiltInFileToolCapability.readFile(root),
            BuiltInFileToolCapability.writeFile(root),
            BuiltInFileToolCapability.editFile(root),
        ),
    )

    val assembled = KoogToolRegistryAssembler().assemble(capabilitySet)

    assertEquals(
        listOf("__list_directory__", "__read_file__", "__write_file__", "edit_file"),
        assembled.primaryRegistry.tools.map { it.descriptor.name },
    )
    assertEquals(
        listOf("__list_directory__", "__read_file__", "__write_file__", "edit_file"),
        assembled.primaryCapabilityIds,
    )
}

@Test
fun `should register only low risk built in file tools when write tools are absent`() = runTest {
    val root = "D:\\JetBrains\\projects\\idea_projects\\mulehang-agent"
    val capabilitySet = CapabilitySet(
        adapters = emptyList(),
        builtInFileTools = listOf(
            BuiltInFileToolCapability.listDirectory(root),
            BuiltInFileToolCapability.readFile(root),
        ),
    )

    val assembled = KoogToolRegistryAssembler().assemble(capabilitySet)

    assertEquals(
        listOf("__list_directory__", "__read_file__"),
        assembled.primaryRegistry.tools.map { it.descriptor.name },
    )
}
```

- [ ] **Step 2: 运行 assembler 测试，确认 built-in 文件工具尚未接入**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.agent.KoogToolRegistryAssemblerTest
```

Expected: FAIL，报错包含 `builtInFileTools` 为空、工具名缺失，或 primary registry 未包含 `__list_directory__` / `__read_file__` / `__write_file__` / `edit_file`。

- [ ] **Step 3: 写最小实现，把 built-in 文件工具声明翻译成真实 Koog tools**

```kotlin
val primaryIds = buildList {
    addAll(capabilitySet.toolAdapters().map { it.descriptor.id })
    addAll(capabilitySet.httpAdapters().map { it.descriptor.id })
    addAll(capabilitySet.builtInFileTools().map { it.descriptor.id })
}

val primaryRegistry = ToolRegistry {
    capabilitySet.toolAdapters().forEach { tool(LocalToolBridge(it)) }
    capabilitySet.httpAdapters().forEach { tool(HttpToolBridge(it)) }
    capabilitySet.builtInFileTools().forEach { builtIn ->
        val root = Path.of(builtIn.workspaceRoot)
        when (builtIn.descriptor.id) {
            "__list_directory__" -> tool(ListDirectoryTool(WorkspaceScopedFileSystemProvider.readOnly(root)))
            "__read_file__" -> tool(ReadFileTool(WorkspaceScopedFileSystemProvider.readOnly(root)))
            "__write_file__" -> tool(WriteFileTool(WorkspaceScopedFileSystemProvider.readWrite(root)))
            "edit_file" -> tool(EditFileTool(WorkspaceScopedFileSystemProvider.readWrite(root)))
        }
    }
}
```

并补一条注释，明确 `edit_file` 是当前 Koog `0.8.0` 源码中的真实 descriptor name。

- [ ] **Step 4: 重新运行 assembler 测试，确认 built-in 文件工具被正确装配**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.agent.KoogToolRegistryAssemblerTest
```

Expected: PASS，并覆盖：

```text
__list_directory__
__read_file__
__write_file__
edit_file
只读子集装配
现有 MCP 桥接测试不回归
```

### Task 4: 验证 AgentAssembly 汇总结果与回归面

**Files:**
- Modify: `runtime/src/test/kotlin/com/agent/runtime/agent/AgentAssemblyTest.kt`

- [ ] **Step 1: 先写失败测试，锁定 assembled agent 同时包含现有 tool/MCP 与 built-in 文件工具**

```kotlin
@Test
fun `should assemble built in file tools alongside existing runtime capabilities`() = runTest {
    val root = "D:\\JetBrains\\projects\\idea_projects\\mulehang-agent"
    val binding = ProviderBinding(
        providerId = "provider-1",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "test-key",
        modelId = "openai/gpt-oss-120b:free",
    )

    val assembledAgent = AgentAssembly().assemble(
        binding = binding,
        capabilitySet = CapabilitySet(
            adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo")),
            builtInFileTools = listOf(BuiltInFileToolCapability.readFile(root)),
        ),
    )

    assertEquals(
        listOf("tool.echo", "__read_file__"),
        assembledAgent.toolRegistry.tools.map { it.descriptor.name },
    )
    assertEquals(
        listOf(
            CapabilityDescriptor(id = "tool.echo", kind = "tool", riskLevel = ToolRiskLevel.MID),
            CapabilityDescriptor(id = "__read_file__", kind = "filesystem", riskLevel = ToolRiskLevel.LOW),
        ),
        assembledAgent.capabilities,
    )
}
```

- [ ] **Step 2: 运行 assembly 测试，确认旧断言尚未覆盖 built-in 文件工具**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.agent.AgentAssemblyTest
```

Expected: FAIL，报错包含 tool registry 或 capability descriptor 列表缺少 `__read_file__`。

- [ ] **Step 3: 只做测试对齐，不回头改 agent 主轴**

```kotlin
assertEquals(
    listOf("tool.echo", "__read_file__"),
    assembledAgent.toolRegistry.tools.map { it.descriptor.name },
)
```

如果现有测试构造器需要注入 `KoogToolRegistryAssembler`，沿用现有注入方式，不新增 runtime/HTTP/CLI 层改动。

- [ ] **Step 4: 运行完整目标测试集，确认 built-in 文件工具接入不破坏现有 capability 装配**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.capability.CapabilityAdaptersTest --tests com.agent.runtime.agent.WorkspaceScopedFileSystemProviderTest --tests com.agent.runtime.agent.KoogToolRegistryAssemblerTest --tests com.agent.runtime.agent.AgentAssemblyTest
```

Expected: PASS。

### Task 5: 做文件检查并收尾

**Files:**
- Check: `runtime/src/main/kotlin/com/agent/runtime/capability/CapabilityContract.kt`
- Check: `runtime/src/main/kotlin/com/agent/runtime/capability/CapabilitySet.kt`
- Check: `runtime/src/main/kotlin/com/agent/runtime/capability/BuiltInFileToolCapability.kt`
- Check: `runtime/src/main/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProvider.kt`
- Check: `runtime/src/main/kotlin/com/agent/runtime/agent/KoogToolRegistryAssembler.kt`
- Check: `runtime/src/test/kotlin/com/agent/runtime/capability/CapabilityAdaptersTest.kt`
- Check: `runtime/src/test/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProviderTest.kt`
- Check: `runtime/src/test/kotlin/com/agent/runtime/agent/KoogToolRegistryAssemblerTest.kt`
- Check: `runtime/src/test/kotlin/com/agent/runtime/agent/AgentAssemblyTest.kt`

- [ ] **Step 1: 对所有改动文件运行 IDEA 文件检查**

检查这些文件，要求 error 和 warning 为零：

```text
runtime/src/main/kotlin/com/agent/runtime/capability/CapabilityContract.kt
runtime/src/main/kotlin/com/agent/runtime/capability/CapabilitySet.kt
runtime/src/main/kotlin/com/agent/runtime/capability/BuiltInFileToolCapability.kt
runtime/src/main/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProvider.kt
runtime/src/main/kotlin/com/agent/runtime/agent/KoogToolRegistryAssembler.kt
runtime/src/test/kotlin/com/agent/runtime/capability/CapabilityAdaptersTest.kt
runtime/src/test/kotlin/com/agent/runtime/agent/WorkspaceScopedFileSystemProviderTest.kt
runtime/src/test/kotlin/com/agent/runtime/agent/KoogToolRegistryAssemblerTest.kt
runtime/src/test/kotlin/com/agent/runtime/agent/AgentAssemblyTest.kt
```

- [ ] **Step 2: 若有 warning，先修 warning 再重跑目标测试集**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.capability.CapabilityAdaptersTest --tests com.agent.runtime.agent.WorkspaceScopedFileSystemProviderTest --tests com.agent.runtime.agent.KoogToolRegistryAssemblerTest --tests com.agent.runtime.agent.AgentAssemblyTest
```

Expected: PASS，且没有新 warning。
