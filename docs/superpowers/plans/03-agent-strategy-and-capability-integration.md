# Agent Strategy And Capability Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `ProviderBinding` 与 `CapabilitySet` 可以装配成真实 JetBrains Koog agent，并通过统一 runtime 完成 `agent.run(...)` 调用、结果翻译和错误分层。

**Architecture:** 先扩展 runtime 契约，显式表达 `agent.run` 请求以及 provider 解析失败、capability 桥接失败、agent 执行失败三类错误；再把 `ProviderBinding` 解析为 Koog `PromptExecutor` + `LLModel`，把 `CapabilitySet` 解析为 Koog 可消费的 tool/MCP/HTTP registry；最后由 `RuntimeAgentExecutor` 把装配结果接入 `RuntimeRequestDispatcher` 现有主轴。仓库侧稳定边界仍然是 `ProviderBinding` 与 `CapabilitySet`，Koog API 只允许出现在 resolver、assembler、assembly 和 executor 四个桥接点。

**Tech Stack:** Kotlin/JVM, JetBrains Koog 0.8.0 (`koog-agents`, `agents-mcp`), kotlin.test, JUnit 5, kotlinx.coroutines-test

---

## File Map

- `src/main/kotlin/com/agent/runtime/RuntimeContracts.kt`
  为 phase 03 增加 `RuntimeAgentRunRequest` 和三类结构化失败，保证 CLI/ACP 后续仍只消费 runtime 契约。
- `src/main/kotlin/com/agent/agent/KoogExecutorResolver.kt`
  新增 provider 到 Koog executor/model 的唯一解析入口，隔离 OpenAI-compatible、Anthropic-compatible、Gemini-compatible 差异。
- `src/main/kotlin/com/agent/agent/ProviderCompatiblePromptExecutorFactory.kt`
  仅负责 OpenAI-compatible `baseUrl` 自定义 client，避免把 endpoint 细节散落到 resolver。
- `src/main/kotlin/com/agent/capability/CapabilityContract.kt`
  扩展 capability 元数据，使 tool、MCP、HTTP 三类适配器可以被 Koog registry assembler 消费。
- `src/main/kotlin/com/agent/capability/CapabilitySet.kt`
  提供按 capability kind 分组和遍历的入口，保留 runtime 的统一执行边界。
- `src/main/kotlin/com/agent/capability/ToolCapabilityAdapter.kt`
  暴露 local/custom tool 所需的 Koog tool 元数据与调用桥。
- `src/main/kotlin/com/agent/capability/McpCapabilityAdapter.kt`
  暴露 MCP server/client 信息，让 Koog MCP registry provider 在 assembler 中统一创建。
- `src/main/kotlin/com/agent/capability/HttpCapabilityAdapter.kt`
  暴露 direct HTTP internal API 的描述、参数 schema 和调用桥，用于包装成 Koog tool。
- `src/main/kotlin/com/agent/agent/KoogToolRegistryAssembler.kt`
  负责把 capability set 组装成 Koog 可消费的 registry bundle，并集中处理 capability 桥接失败。
- `src/main/kotlin/com/agent/agent/AgentAssembly.kt`
  改为依赖 `KoogExecutorResolver` 和 `KoogToolRegistryAssembler`，装配真实 Koog `AIAgent`。
- `src/main/kotlin/com/agent/agent/AgentStrategyFactory.kt`
  保持 `singleRunStrategy()` 为 phase 03 默认 strategy，并让测试固定其类型。
- `src/main/kotlin/com/agent/agent/RuntimeAgentExecutor.kt`
  新增 runtime -> agent.run -> runtime result 的统一执行器。
- `src/main/kotlin/com/agent/runtime/AgentCapabilityRouter.kt`
  新增 `RuntimeCapabilityRouter` 实现，把 `RuntimeAgentRunRequest` 交给 `RuntimeAgentExecutor`。
- `src/test/kotlin/com/agent/runtime/RuntimeContractsTest.kt`
  固定 phase 03 的请求契约与错误分层。
- `src/test/kotlin/com/agent/runtime/RuntimeRequestDispatcherTest.kt`
  验证 dispatcher 仍沿现有 runtime 主轴路由 `agent.run` 请求。
- `src/test/kotlin/com/agent/agent/KoogExecutorResolverTest.kt`
  固定三类 provider 的解析路径和受限能力行为。
- `src/test/kotlin/com/agent/capability/CapabilityAdaptersTest.kt`
  固定 capability adapter 新增的 Koog 桥接元数据。
- `src/test/kotlin/com/agent/agent/KoogToolRegistryAssemblerTest.kt`
  固定 tool、MCP、HTTP 三类 capability 都能被组装进 Koog 视图。
- `src/test/kotlin/com/agent/agent/AgentAssemblyTest.kt`
  固定真实 `AIAgent` 装配和 `singleRunStrategy`。
- `src/test/kotlin/com/agent/agent/RuntimeAgentExecutorTest.kt`
  固定成功路径和三类错误翻译。
- `src/test/kotlin/com/agent/agent/AgentRuntimeIntegrationTest.kt`
  固定 `ProviderBinding -> CapabilitySet -> AgentAssembly -> RuntimeAgentExecutor -> agent.run(...) -> RuntimeResult` 这条 phase 03 核心链路。

---

### Task 1: 扩展 runtime 契约，承接 agent.run 与错误分层

**Files:**
- Modify: `src/main/kotlin/com/agent/runtime/RuntimeContracts.kt`
- Modify: `src/test/kotlin/com/agent/runtime/RuntimeContractsTest.kt`
- Modify: `src/test/kotlin/com/agent/runtime/RuntimeRequestDispatcherTest.kt`

- [ ] **Step 1: 写失败测试，锁定 phase 03 的请求和失败模型**

```kotlin
class RuntimeContractsTest {
    @Test
    fun `should represent agent run request through runtime contract`() {
        val request = RuntimeAgentRunRequest(prompt = "summarize this")

        assertEquals("agent.run", request.capabilityId)
        assertEquals("summarize this", request.prompt)
    }

    @Test
    fun `should keep provider capability and agent failures distinct`() {
        assertIs<RuntimeProviderResolutionFailure>(
            RuntimeProviderResolutionFailure(message = "provider failed")
        )
        assertIs<RuntimeCapabilityBridgeFailure>(
            RuntimeCapabilityBridgeFailure(message = "capability failed")
        )
        assertIs<RuntimeAgentExecutionFailure>(
            RuntimeAgentExecutionFailure(message = "agent failed")
        )
    }
}
```

- [ ] **Step 2: 运行 runtime 测试，确认新契约尚未实现**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.RuntimeContractsTest --tests com.agent.runtime.RuntimeRequestDispatcherTest
```

Expected: FAIL，报错包含 `RuntimeAgentRunRequest`、`RuntimeProviderResolutionFailure`、`RuntimeCapabilityBridgeFailure` 或 `RuntimeAgentExecutionFailure` 未定义。

- [ ] **Step 3: 写最小实现，把 agent.run 纳入 runtime 主轴**

```kotlin
data class RuntimeAgentRunRequest(
    val prompt: String,
    override val capabilityId: String = "agent.run",
    override val payload: JsonElement? = null,
) : CapabilityRequest

data class RuntimeProviderResolutionFailure(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeFailure

data class RuntimeCapabilityBridgeFailure(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeFailure

data class RuntimeAgentExecutionFailure(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeFailure
```

- [ ] **Step 4: 运行 runtime 测试，确认契约稳定**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.RuntimeContractsTest --tests com.agent.runtime.RuntimeRequestDispatcherTest
```

Expected: PASS。

### Task 2: 实现 ProviderBinding 到 Koog executor/model 的唯一解析入口

**Files:**
- Create: `src/main/kotlin/com/agent/agent/KoogExecutorResolver.kt`
- Create: `src/main/kotlin/com/agent/agent/ProviderCompatiblePromptExecutorFactory.kt`
- Test: `src/test/kotlin/com/agent/agent/KoogExecutorResolverTest.kt`

- [ ] **Step 1: 写失败测试，锁定三类 provider 的解析路径和限制**

```kotlin
class KoogExecutorResolverTest {
    @Test
    fun `should resolve openai compatible binding with custom base url and arbitrary model id`() {
        val binding = ProviderBinding(
            providerId = "provider-openai",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-4.1-mini",
        )

        val resolved = KoogExecutorResolver().resolve(binding)

        assertEquals(binding, resolved.binding)
        assertEquals("openai/gpt-4.1-mini", resolved.llmModel.id)
    }

    @Test
    fun `should resolve anthropic and gemini bindings through Koog executors`() {
        val resolver = KoogExecutorResolver()

        val anthropic = resolver.resolve(
            ProviderBinding("provider-anthropic", ProviderType.ANTHROPIC_COMPATIBLE, "https://api.anthropic.com", "k", "claude-3-7-sonnet-latest")
        )
        val gemini = resolver.resolve(
            ProviderBinding("provider-gemini", ProviderType.GEMINI_COMPATIBLE, "https://generativelanguage.googleapis.com", "k", "gemini-2.5-flash")
        )

        assertEquals(ProviderType.ANTHROPIC_COMPATIBLE, anthropic.binding.providerType)
        assertEquals(ProviderType.GEMINI_COMPATIBLE, gemini.binding.providerType)
    }

    @Test
    fun `should fail with provider resolution failure when Koog path is unsupported`() {
        val binding = ProviderBinding(
            providerId = "provider-gemini",
            providerType = ProviderType.GEMINI_COMPATIBLE,
            baseUrl = "https://custom-gemini-endpoint.example.com",
            apiKey = "test-key",
            modelId = "gemini-2.5-flash",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            KoogExecutorResolver().resolve(binding)
        }

        assertContains(error.message.orEmpty(), "Koog 0.8.0")
    }
}
```

- [ ] **Step 2: 运行解析测试，确认 resolver 尚未实现**

Run:

```powershell
.\gradlew.bat test --tests com.agent.agent.KoogExecutorResolverTest
```

Expected: FAIL，报错包含 `KoogExecutorResolver` 或 `OpenAiCompatibleExecutorFactory` 未定义。

- [ ] **Step 3: 写最小实现，固定 provider -> Koog 映射**

```kotlin
data class ResolvedKoogModelBinding(
    val binding: ProviderBinding,
    val promptExecutor: PromptExecutor,
    val llmModel: LLModel,
)

class KoogExecutorResolver {
    fun resolve(binding: ProviderBinding): ResolvedKoogModelBinding = when (binding.providerType) {
        ProviderType.OPENAI_COMPATIBLE -> ResolvedKoogModelBinding(
            binding = binding,
            promptExecutor = OpenAiCompatibleExecutorFactory.create(binding),
            llmModel = LLModel(provider = LLMProvider.OpenAI, id = binding.modelId, capabilities = DEFAULT_TOOL_CAPABILITIES),
        )
        ProviderType.ANTHROPIC_COMPATIBLE -> ResolvedKoogModelBinding(
            binding = binding,
            promptExecutor = simpleAnthropicExecutor(binding.apiKey),
            llmModel = LLModel(provider = LLMProvider.Anthropic, id = binding.modelId, capabilities = DEFAULT_TOOL_CAPABILITIES),
        )
        ProviderType.GEMINI_COMPATIBLE -> ResolvedKoogModelBinding(
            binding = binding,
            promptExecutor = simpleGeminiExecutor(binding.apiKey),
            llmModel = LLModel(provider = LLMProvider.Google, id = binding.modelId, capabilities = DEFAULT_TOOL_CAPABILITIES),
        )
    }
}
```

`OpenAiCompatibleExecutorFactory` 只负责 OpenAI-compatible `baseUrl` 定制；Anthropic/Gemini 如遇 Koog 0.8.0 不支持的 endpoint override，直接抛出带 `Koog 0.8.0` 字样的受限能力错误，不允许静默忽略。

- [ ] **Step 4: 运行 provider 解析测试，确认三类 provider 都有真实 Koog 路径**

Run:

```powershell
.\gradlew.bat test --tests com.agent.agent.KoogExecutorResolverTest
```

Expected: PASS，并覆盖：

```text
OPENAI_COMPATIBLE
ANTHROPIC_COMPATIBLE
GEMINI_COMPATIBLE
自定义 baseUrl 的 OpenAI-compatible 路径
受限 provider endpoint 的结构化失败
```

### Task 3: 扩展 capability contract，并把三类能力桥接到 Koog registry

**Files:**
- Modify: `src/main/kotlin/com/agent/capability/CapabilityContract.kt`
- Modify: `src/main/kotlin/com/agent/capability/CapabilitySet.kt`
- Modify: `src/main/kotlin/com/agent/capability/ToolCapabilityAdapter.kt`
- Modify: `src/main/kotlin/com/agent/capability/McpCapabilityAdapter.kt`
- Modify: `src/main/kotlin/com/agent/capability/HttpCapabilityAdapter.kt`
- Modify: `src/test/kotlin/com/agent/capability/CapabilityAdaptersTest.kt`
- Create: `src/main/kotlin/com/agent/agent/KoogToolRegistryAssembler.kt`
- Test: `src/test/kotlin/com/agent/agent/KoogToolRegistryAssemblerTest.kt`

- [ ] **Step 1: 写失败测试，锁定 tool、MCP、HTTP 三类 capability 的 Koog 视图**

```kotlin
class KoogToolRegistryAssemblerTest {
    @Test
    fun `should bridge local tools and http capabilities into Koog tool registry`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                ToolCapabilityAdapter.echo(id = "tool.echo"),
                HttpCapabilityAdapter.internalGet(id = "http.health", path = "/health"),
            ),
        )

        val assembled = KoogToolRegistryAssembler().assemble(capabilitySet)

        assertEquals(listOf("tool.echo", "http.health"), assembled.descriptors.map { it.id })
        assertTrue(assembled.primaryRegistry.tools.isNotEmpty())
    }

    @Test
    fun `should keep mcp capabilities as dedicated Koog registry providers`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                McpCapabilityAdapter.sse(id = "mcp.playwright", url = "http://localhost:8931/sse"),
            ),
        )

        val assembled = KoogToolRegistryAssembler().assemble(capabilitySet)

        assertEquals(1, assembled.mcpRegistries.size)
        assertEquals("mcp.playwright", assembled.descriptors.single().id)
    }
}
```

- [ ] **Step 2: 运行 capability 测试，确认 Koog 桥接入口缺失**

Run:

```powershell
.\gradlew.bat test --tests com.agent.capability.CapabilityAdaptersTest --tests com.agent.agent.KoogToolRegistryAssemblerTest
```

Expected: FAIL，报错包含 `KoogToolRegistryAssembler`、adapter metadata 或 `CapabilitySet` 分组方法未定义。

- [ ] **Step 3: 写最小实现，集中吸收 capability 差异**

```kotlin
enum class CapabilityKind { TOOL, MCP, HTTP }

data class KoogToolingBundle(
    val primaryRegistry: ToolRegistry,
    val mcpRegistries: List<ToolRegistry>,
    val descriptors: List<CapabilityDescriptor>,
)

class CapabilitySet(adapters: List<CapabilityAdapter>) {
    fun toolAdapters(): List<ToolCapabilityAdapter> = adaptersById.values.filterIsInstance<ToolCapabilityAdapter>()
    fun mcpAdapters(): List<McpCapabilityAdapter> = adaptersById.values.filterIsInstance<McpCapabilityAdapter>()
    fun httpAdapters(): List<HttpCapabilityAdapter> = adaptersById.values.filterIsInstance<HttpCapabilityAdapter>()
}

class KoogToolRegistryAssembler {
    suspend fun assemble(capabilitySet: CapabilitySet): KoogToolingBundle {
        val primaryRegistry = ToolRegistry {
            capabilitySet.toolAdapters().forEach { tool(it.asKoogTool()) }
            capabilitySet.httpAdapters().forEach { tool(it.asKoogTool()) }
        }

        val mcpRegistries = capabilitySet.mcpAdapters().map { adapter ->
            createMcpRegistry(adapter.transport)
        }

        return KoogToolingBundle(
            primaryRegistry = primaryRegistry,
            mcpRegistries = mcpRegistries,
            descriptors = capabilitySet.descriptors(),
        )
    }
}
```

`McpCapabilityAdapter` 只负责描述 server/client 连接所需元数据；真正的 `McpToolRegistryProvider.fromSseUrl(...)` 或 `fromTransport(...)` 调用只能出现在 `KoogToolRegistryAssembler`，并且其返回值是可直接合并到 agent 的 `ToolRegistry`。

- [ ] **Step 4: 运行 capability 桥接测试，确认三类能力都进入 Koog 视图**

Run:

```powershell
.\gradlew.bat test --tests com.agent.capability.CapabilityAdaptersTest --tests com.agent.agent.KoogToolRegistryAssemblerTest
```

Expected: PASS，并覆盖：

```text
local/custom tool -> Koog ToolRegistry
direct HTTP internal API -> Koog tool wrapper
MCP-backed capability -> McpToolRegistryProvider 创建的 Koog ToolRegistry
capability 元数据缺失时的桥接失败
```

### Task 4: 重写 AgentAssembly，装配真实 Koog agent 与 singleRunStrategy

**Files:**
- Modify: `src/main/kotlin/com/agent/agent/AgentAssembly.kt`
- Modify: `src/main/kotlin/com/agent/agent/AgentStrategyFactory.kt`
- Modify: `src/test/kotlin/com/agent/agent/AgentAssemblyTest.kt`

- [ ] **Step 1: 写失败测试，锁定真实 Koog agent 装配行为**

```kotlin
class AgentAssemblyTest {
    @Test
    fun `should assemble real koog agent from resolved binding and capability registries`() = runTest {
        val binding = ProviderBinding(
            providerId = "provider-openai",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-4.1-mini",
        )
        val capabilitySet = CapabilitySet(
            adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo")),
        )
        val assembly = AgentAssembly()

        val assembled = assembly.assemble(binding = binding, capabilitySet = capabilitySet)

        assertEquals(binding, assembled.binding)
        assertEquals("ai.koog.agents.core.agent.GraphAIAgent", assembled.agent::class.qualifiedName)
        assertEquals("ai.koog.agents.core.agent.entity.AIAgentGraphStrategy", assembled.strategy::class.qualifiedName)
        assertEquals(listOf("tool.echo"), assembled.capabilities.map { it.id })
    }

    @Test
    fun `should keep single run as the default phase 03 strategy`() {
        val strategy = AgentStrategyFactory.singleRun()

        assertEquals(
            "ai.koog.agents.core.agent.entity.AIAgentGraphStrategy",
            strategy::class.qualifiedName,
        )
    }
}
```

- [ ] **Step 2: 运行 agent 装配测试，确认旧装配器已经不满足 spec**

Run:

```powershell
.\gradlew.bat test --tests com.agent.agent.AgentAssemblyTest
```

Expected: FAIL，报错包含旧的 `OPENAI_COMPATIBLE` 限制、缺少 resolver/assembler 依赖，或者 capability 数量断言失败。

- [ ] **Step 3: 写最小实现，让 AgentAssembly 只负责装配**

```kotlin
class AgentAssembly(
    private val executorResolver: KoogExecutorResolver = KoogExecutorResolver(),
    private val toolRegistryAssembler: KoogToolRegistryAssembler = KoogToolRegistryAssembler(),
) {
    suspend fun assemble(
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): AssembledAgent {
        val resolvedBinding = executorResolver.resolve(binding)
        val tooling = toolRegistryAssembler.assemble(capabilitySet)
        val strategy = AgentStrategyFactory.singleRun()

        val agent = AIAgent(
            promptExecutor = resolvedBinding.promptExecutor,
            strategy = strategy,
            llmModel = resolvedBinding.llmModel,
            systemPrompt = "You are a helpful assistant.",
            toolRegistry = tooling.primaryRegistry,
        )

        return AssembledAgent(
            binding = binding,
            strategy = strategy,
            agent = agent,
            capabilities = tooling.descriptors,
        )
    }
}
```

`tooling.mcpRegistries` 必须在 `KoogToolRegistryAssembler` 内合并成 Koog 可消费的单一 registry；`AgentAssembly` 不允许感知具体 capability 来源。

- [ ] **Step 4: 运行 agent 装配测试，确认真实 AIAgent 装配稳定**

Run:

```powershell
.\gradlew.bat test --tests com.agent.agent.AgentAssemblyTest
```

Expected: PASS。

### Task 5: 新增 RuntimeAgentExecutor，把 runtime 请求落到 agent.run(...)

**Files:**
- Create: `src/main/kotlin/com/agent/agent/RuntimeAgentExecutor.kt`
- Create: `src/main/kotlin/com/agent/runtime/AgentCapabilityRouter.kt`
- Test: `src/test/kotlin/com/agent/agent/RuntimeAgentExecutorTest.kt`
- Test: `src/test/kotlin/com/agent/agent/AgentRuntimeIntegrationTest.kt`
- Modify: `src/test/kotlin/com/agent/runtime/RuntimeRequestDispatcherTest.kt`

- [ ] **Step 1: 写失败测试，锁定 runtime 成功路径和三类错误翻译**

```kotlin
class RuntimeAgentExecutorTest {
    @Test
    fun `should translate successful agent run into runtime success`() = runTest {
        val result = runtimeAgentExecutor.execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
    }

    @Test
    fun `should translate provider capability and agent failures separately`() = runTest {
        assertIs<RuntimeFailed>(providerFailureResult)
        assertIs<RuntimeProviderResolutionFailure>((providerFailureResult as RuntimeFailed).failure)
        assertIs<RuntimeCapabilityBridgeFailure>((capabilityFailureResult as RuntimeFailed).failure)
        assertIs<RuntimeAgentExecutionFailure>((agentFailureResult as RuntimeFailed).failure)
    }
}

class AgentRuntimeIntegrationTest {
    @Test
    fun `should execute dispatcher router assembly and agent run through one runtime pipeline`() = runTest {
        val dispatcher = RuntimeRequestDispatcher(
            capabilityRouter = AgentCapabilityRouter(
                runtimeAgentExecutor = RuntimeAgentExecutor(),
                binding = openAiBinding(),
                capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
            ),
        )

        val result = dispatcher.dispatch(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "say hello"),
        )

        assertIs<RuntimeResult>(result)
    }
}
```

- [ ] **Step 2: 运行执行链测试，确认 runtime 到 agent.run 的桥还没打通**

Run:

```powershell
.\gradlew.bat test --tests com.agent.agent.RuntimeAgentExecutorTest --tests com.agent.agent.AgentRuntimeIntegrationTest --tests com.agent.runtime.RuntimeRequestDispatcherTest
```

Expected: FAIL，报错包含 `RuntimeAgentExecutor` 或 `AgentCapabilityRouter` 未定义。

- [ ] **Step 3: 写最小实现，集中处理结果翻译和错误边界**

```kotlin
class RuntimeAgentExecutor(
    private val agentAssembly: AgentAssembly = AgentAssembly(),
) {
    suspend fun execute(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: RuntimeAgentRunRequest,
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): RuntimeResult = try {
        val assembled = agentAssembly.assemble(binding = binding, capabilitySet = capabilitySet)
        val output = assembled.agent.run(request.prompt)

        RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "agent.run.completed")))
    } catch (error: ProviderResolutionException) {
        RuntimeFailed(RuntimeProviderResolutionFailure(message = error.message ?: "provider resolution failed", cause = error))
    } catch (error: CapabilityBridgeException) {
        RuntimeFailed(RuntimeCapabilityBridgeFailure(message = error.message ?: "capability bridge failed", cause = error))
    } catch (error: Throwable) {
        RuntimeFailed(RuntimeAgentExecutionFailure(message = error.message ?: "agent execution failed", cause = error))
    }
}

class AgentCapabilityRouter(
    private val runtimeAgentExecutor: RuntimeAgentExecutor,
    private val binding: ProviderBinding,
    private val capabilitySet: CapabilitySet,
) : RuntimeCapabilityRouter {
    override suspend fun route(
        context: RuntimeRequestContext,
        request: CapabilityRequest,
    ): RuntimeResult {
        require(request is RuntimeAgentRunRequest) { "AgentCapabilityRouter requires RuntimeAgentRunRequest." }
        return runtimeAgentExecutor.execute(
            session = RuntimeSession(id = context.sessionId),
            context = context,
            request = request,
            binding = binding,
            capabilitySet = capabilitySet,
        )
    }
}
```

- [ ] **Step 4: 运行 phase 03 测试，确认完整执行链成立**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.* --tests com.agent.capability.* --tests com.agent.agent.*
```

Expected: PASS，并至少覆盖：

```text
ProviderBinding -> KoogExecutorResolver
CapabilitySet -> KoogToolRegistryAssembler
AgentAssembly -> AIAgent
RuntimeAgentExecutor -> agent.run(...)
RuntimeSuccess / RuntimeFailed 翻译
provider 解析失败
capability 桥接失败
agent 执行失败
```

### Task 6: 做阶段级构建验证，确保 phase 03 交付可继续支撑 CLI 阶段

**Files:**
- Check: `src/main/kotlin/com/agent/agent/*.kt`
- Check: `src/main/kotlin/com/agent/capability/*.kt`
- Check: `src/main/kotlin/com/agent/runtime/*.kt`
- Check: `src/test/kotlin/com/agent/agent/*.kt`
- Check: `src/test/kotlin/com/agent/capability/*.kt`
- Check: `src/test/kotlin/com/agent/runtime/*.kt`

- [ ] **Step 1: 运行全量构建，确认 phase 03 不破坏前两阶段**

Run:

```powershell
.\gradlew.bat build
```

Expected: PASS。

- [ ] **Step 2: 手工通读受限能力说明**

确认以下限制已经写进代码注释、错误信息或测试断言，而不是留给实现者脑补：

```text
Koog 0.8.0 对 Anthropic/Gemini 自定义 baseUrl 的支持情况
arbitrary discovered model id 优先，白名单回退只在官方 API 没有通用构造路径时启用
MCP registry 的创建只允许出现在 KoogToolRegistryAssembler
```

- [ ] **Step 3: 记录 phase 03 交付结果**

在最终汇报中明确列出：

```text
1. 新增了哪些 Koog bridge 文件
2. 修改了哪些 runtime / capability 文件
3. 哪些测试证明三类 provider 已可解析
4. 哪些测试证明 tool、MCP、HTTP 都进入了统一执行链
5. `.\gradlew.bat build` 是否通过
```

## Self-Review

- Spec coverage:
  - 最小 `AIAgent` 与 `singleRunStrategy`：Task 4
  - local tools / MCP / direct HTTP internal API：Task 3
  - `runtime -> agent.run(...) -> runtime result`：Task 5
  - provider 三类型与 arbitrary model id / baseUrl 限制：Task 2
  - provider/capability/agent 三类错误边界：Task 1 + Task 5
- Placeholder scan:
  - 没有 `TODO`、`TBD`、`similar to above`、`适当处理错误` 这类占位描述
  - 每个任务都给出精确文件路径、测试入口、PowerShell 命令和最小代码形状
- Type consistency:
  - 统一使用 `ProviderBinding`、`CapabilitySet`、`RuntimeAgentRunRequest`、`KoogExecutorResolver`、`KoogToolRegistryAssembler`、`RuntimeAgentExecutor`
  - `singleRunStrategy`、`AIAgent`、`ToolRegistry`、`McpToolRegistryProvider` 与 phase 03 spec 的 Koog 术语保持一致
