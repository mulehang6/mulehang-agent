# Mulehang Agent Phase 0-1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 验证 Koog 0.7.3 的关键 API 在当前工程中可用，并建立 `config/provider/BYOK` 产品底座，让现有 CLI 不再依赖硬编码的 OpenRouter 配置。

**Architecture:** 先通过最小兼容性测试锁定 `ACP + MCP + streaming` 所需 Koog surface，再引入 `mulehang.config` 和 `mulehang.provider` 两个新包，统一处理配置文件、环境变量、provider 目录、model 目录、`baseUrl + apiKey + model` 解析，并让现有 `agent` 原型从新底座读取默认绑定。

**Tech Stack:** Kotlin/JVM 21, Koog 0.7.3, kotlinx.serialization, kotlin.test/JUnit, PowerShell, Gradle

---

## 文件结构

- Modify: `build.gradle.kts`
- Create: `src/test/kotlin/mulehang/probe/KoogCompatibilityTest.kt`
- Create: `src/main/kotlin/mulehang/config/AppConfig.kt`
- Create: `src/main/kotlin/mulehang/config/ConfigLoader.kt`
- Create: `src/test/kotlin/mulehang/config/ConfigLoaderTest.kt`
- Create: `src/main/kotlin/mulehang/provider/ProviderSpec.kt`
- Create: `src/main/kotlin/mulehang/provider/ProviderRegistry.kt`
- Create: `src/main/kotlin/mulehang/provider/ProviderGateway.kt`
- Create: `src/main/kotlin/mulehang/provider/ExecutorBinding.kt`
- Create: `src/main/kotlin/mulehang/provider/ExecutorFactory.kt`
- Create: `src/test/kotlin/mulehang/provider/ProviderGatewayTest.kt`
- Modify: `src/main/kotlin/agent/AgentConfig.kt`
- Modify: `src/main/kotlin/agent/AgentApp.kt`
- Modify: `src/test/kotlin/agent/MySimpleAgentTest.kt`

### Task 1: 建立 Koog 兼容性探针

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/test/kotlin/mulehang/probe/KoogCompatibilityTest.kt`

- [ ] **Step 1: 先写一个会失败的 Koog 兼容性测试**

```kotlin
package mulehang.probe

import ai.koog.agents.features.acp.AcpAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoogCompatibilityTest {
    @Test
    fun `should expose Koog feature classes required by the roadmap`() {
        assertNotNull(AcpAgent)
        assertNotNull(McpToolRegistryProvider::class)
    }
}
```

- [ ] **Step 2: 运行测试并确认当前工程缺少依赖**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.probe.KoogCompatibilityTest"
```

Expected: FAIL，出现 `unresolved reference` 或测试类无法编译，因为工程当前只有 `koog-agents` 主依赖。

- [ ] **Step 3: 在构建脚本中补齐本阶段需要的依赖与序列化插件**

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
}

dependencies {
    implementation("ai.koog:koog-agents:0.7.3")
    implementation("ai.koog:agents-features-acp:0.7.3")
    implementation("ai.koog:agents-mcp:0.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

- [ ] **Step 4: 重新运行兼容性测试，确认 API 已进入编译面**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.probe.KoogCompatibilityTest"
```

Expected: PASS

- [ ] **Step 5: 提交这个依赖与探针检查点**

```powershell
git add build.gradle.kts src/test/kotlin/mulehang/probe/KoogCompatibilityTest.kt
git commit -m "test(probe): add Koog compatibility coverage"
```

### Task 2: 建立统一配置模型与加载器

**Files:**
- Create: `src/main/kotlin/mulehang/config/AppConfig.kt`
- Create: `src/main/kotlin/mulehang/config/ConfigLoader.kt`
- Create: `src/test/kotlin/mulehang/config/ConfigLoaderTest.kt`

- [ ] **Step 1: 先写两个配置加载测试，锁定文件值与环境变量覆盖规则**

```kotlin
package mulehang.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigLoaderTest {
    @Test
    fun `should read provider config from mulehang-agent json`() {
        val dir = Files.createTempDirectory("mulehang-config").toFile()
        dir.resolve("mulehang-agent.json").writeText(
            """
            {
              "defaultProvider": "openrouter",
              "defaultModel": "openrouter.gpt4o",
              "providers": {
                "openrouter": {
                  "apiKey": "file-key",
                  "baseUrl": "https://openrouter.ai/api/v1"
                }
              }
            }
            """.trimIndent()
        )

        val cfg = ConfigLoader(root = dir.toPath(), env = emptyMap()).load()

        assertEquals("openrouter", cfg.defaultProvider)
        assertEquals("openrouter.gpt4o", cfg.defaultModel)
        assertEquals("file-key", cfg.providers.getValue("openrouter").apiKey)
    }

    @Test
    fun `should resolve api key from env when apiKeyEnv is provided`() {
        val dir = Files.createTempDirectory("mulehang-config-env").toFile()
        dir.resolve("mulehang-agent.json").writeText(
            """
            {
              "providers": {
                "openrouter": {
                  "apiKeyEnv": "OPENROUTER_API_KEY",
                  "baseUrl": "https://openrouter.ai/api/v1"
                }
              }
            }
            """.trimIndent()
        )

        val cfg = ConfigLoader(
            root = dir.toPath(),
            env = mapOf("OPENROUTER_API_KEY" to "env-key")
        ).load()

        assertEquals("env-key", cfg.providers.getValue("openrouter").apiKey)
    }
}
```

- [ ] **Step 2: 运行测试，确认当前还没有配置层**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.config.ConfigLoaderTest"
```

Expected: FAIL，`ConfigLoader` 和 `AppConfig` 尚未存在。

- [ ] **Step 3: 实现配置模型和加载器**

`AppConfig.kt`:

```kotlin
package mulehang.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val defaultProvider: String = "openrouter",
    val defaultModel: String = "openrouter.gpt4o",
    val providers: Map<String, ProviderConfig> = emptyMap(),
    val mcp: Map<String, McpServerConfig> = emptyMap(),
    val skills: SkillConfig = SkillConfig()
)

@Serializable
data class ProviderConfig(
    val enabled: Boolean = true,
    val apiKey: String? = null,
    val apiKeyEnv: String? = null,
    val baseUrl: String? = null,
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class McpServerConfig(
    val command: List<String> = emptyList(),
    val url: String? = null,
    val env: Map<String, String> = emptyMap()
)

@Serializable
data class SkillConfig(
    val paths: List<String> = emptyList(),
    val remote: List<String> = emptyList()
)
```

`ConfigLoader.kt`:

```kotlin
package mulehang.config

import java.nio.file.Path
import kotlinx.serialization.json.Json

class ConfigLoader(
    private val root: Path = Path.of("").toAbsolutePath().normalize(),
    private val env: Map<String, String> = System.getenv()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): AppConfig {
        val file = root.resolve("mulehang-agent.json").toFile()
        if (!file.exists()) return AppConfig()

        val cfg = json.decodeFromString<AppConfig>(file.readText())
        return cfg.copy(
            providers = cfg.providers.mapValues { (_, item) ->
                val apiKey = item.apiKey ?: item.apiKeyEnv?.let(env::get)
                item.copy(apiKey = apiKey)
            }
        )
    }
}
```

- [ ] **Step 4: 重新运行配置测试**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.config.ConfigLoaderTest"
```

Expected: PASS

- [ ] **Step 5: 提交配置层检查点**

```powershell
git add src/main/kotlin/mulehang/config src/test/kotlin/mulehang/config
git commit -m "feat(config): add application config loader"
```

### Task 3: 建立 provider 与 model 目录

**Files:**
- Create: `src/main/kotlin/mulehang/provider/ProviderSpec.kt`
- Create: `src/main/kotlin/mulehang/provider/ProviderRegistry.kt`
- Create: `src/main/kotlin/mulehang/provider/ProviderGateway.kt`
- Create: `src/test/kotlin/mulehang/provider/ProviderGatewayTest.kt`

- [ ] **Step 1: 先写 provider 解析测试，锁定默认 baseUrl 与配置覆盖规则**

```kotlin
package mulehang.provider

import mulehang.config.AppConfig
import mulehang.config.ProviderConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderGatewayTest {
    @Test
    fun `should merge registry defaults with config overrides`() {
        val cfg = AppConfig(
            providers = mapOf(
                "openrouter" to ProviderConfig(
                    apiKey = "key-1",
                    baseUrl = "https://openrouter.example.com/v1"
                )
            )
        )

        val binding = ProviderGateway(cfg).resolve("openrouter", "openrouter.gpt4o")

        assertEquals("openrouter", binding.providerId)
        assertEquals("openrouter.gpt4o", binding.modelId)
        assertEquals("key-1", binding.apiKey)
        assertEquals("https://openrouter.example.com/v1", binding.baseUrl)
    }
}
```

- [ ] **Step 2: 运行测试并确认 provider 层尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.provider.ProviderGatewayTest"
```

Expected: FAIL

- [ ] **Step 3: 定义 provider 与 model 规格对象**

`ProviderSpec.kt`:

```kotlin
package mulehang.provider

data class ProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val models: Map<String, ModelSpec>,
    val supportsCustomBaseUrl: Boolean = true
)

data class ModelSpec(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
    val supportsReasoning: Boolean
)
```

`ProviderRegistry.kt`:

```kotlin
package mulehang.provider

object ProviderRegistry {
    val providers = listOf(
        ProviderSpec(
            id = "openrouter",
            displayName = "OpenRouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            models = mapOf(
                "openrouter.gpt4o" to ModelSpec("openrouter", "openrouter.gpt4o", "GPT-4o via OpenRouter", true, true, true),
                "openrouter.claude-sonnet-4-5" to ModelSpec("openrouter", "openrouter.claude-sonnet-4-5", "Claude Sonnet 4.5 via OpenRouter", true, true, true)
            )
        ),
        ProviderSpec(
            id = "openai",
            displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            models = mapOf(
                "openai.chat.gpt4_1" to ModelSpec("openai", "openai.chat.gpt4_1", "GPT-4.1", true, true, true)
            )
        ),
        ProviderSpec(
            id = "ollama",
            displayName = "Ollama",
            defaultBaseUrl = "http://localhost:11434",
            models = mapOf(
                "ollama.meta.llama3.2" to ModelSpec("ollama", "ollama.meta.llama3.2", "Llama 3.2", true, true, false)
            )
        )
    ).associateBy { it.id }
}
```

- [ ] **Step 4: 实现 `ProviderGateway.resolve(...)`**

```kotlin
package mulehang.provider

import mulehang.config.AppConfig

class ProviderGateway(private val cfg: AppConfig) {
    fun resolve(providerId: String, modelId: String): ExecutorBinding {
        val spec = ProviderRegistry.providers.getValue(providerId)
        val model = spec.models.getValue(modelId)
        val item = cfg.providers[providerId]
        val apiKey = item?.apiKey.orEmpty()
        val baseUrl = item?.baseUrl ?: spec.defaultBaseUrl

        require(apiKey.isNotBlank() || providerId == "ollama") {
            "Missing apiKey for provider $providerId"
        }

        return ExecutorBinding(
            providerId = providerId,
            modelId = model.modelId,
            apiKey = apiKey,
            baseUrl = baseUrl,
            headers = item?.headers ?: emptyMap(),
            supportsTools = model.supportsTools,
            supportsStreaming = model.supportsStreaming,
            supportsReasoning = model.supportsReasoning
        )
    }
}
```

- [ ] **Step 5: 重新运行 provider 测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.provider.ProviderGatewayTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/provider src/test/kotlin/mulehang/provider
git commit -m "feat(provider): add registry and gateway"
```

### Task 4: 建立绑定对象与执行工厂

**Files:**
- Create: `src/main/kotlin/mulehang/provider/ExecutorBinding.kt`
- Create: `src/main/kotlin/mulehang/provider/ExecutorFactory.kt`
- Modify: `src/test/kotlin/mulehang/provider/ProviderGatewayTest.kt`

- [ ] **Step 1: 先写一个缺失密钥的失败测试**

```kotlin
@Test
fun `should reject provider bindings without api key`() {
    val cfg = mulehang.config.AppConfig()

    val err = runCatching {
        ProviderGateway(cfg).resolve("openrouter", "openrouter.gpt4o")
    }.exceptionOrNull()

    requireNotNull(err)
    assertEquals("Missing apiKey for provider openrouter", err.message)
}
```

- [ ] **Step 2: 创建统一绑定对象**

```kotlin
package mulehang.provider

data class ExecutorBinding(
    val providerId: String,
    val modelId: String,
    val apiKey: String,
    val baseUrl: String,
    val headers: Map<String, String>,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
    val supportsReasoning: Boolean
)
```

- [ ] **Step 3: 实现 `ExecutorFactory`，让旧原型只依赖 binding，不再依赖硬编码常量**

```kotlin
package mulehang.provider

import mulehang.config.AppConfig

class ExecutorFactory(private val cfg: AppConfig) {
    private val gateway = ProviderGateway(cfg)

    fun defaultBinding(): ExecutorBinding {
        return gateway.resolve(cfg.defaultProvider, cfg.defaultModel)
    }

    fun binding(providerId: String, modelId: String): ExecutorBinding {
        return gateway.resolve(providerId, modelId)
    }
}
```

- [ ] **Step 4: 重新运行 provider 测试**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.provider.ProviderGatewayTest"
```

Expected: PASS

- [ ] **Step 5: 提交绑定工厂检查点**

```powershell
git add src/main/kotlin/mulehang/provider/ExecutorBinding.kt src/main/kotlin/mulehang/provider/ExecutorFactory.kt src/test/kotlin/mulehang/provider/ProviderGatewayTest.kt
git commit -m "feat(provider): add executor binding factory"
```

### Task 5: 让现有 CLI 原型读取统一 provider binding

**Files:**
- Modify: `src/main/kotlin/agent/AgentConfig.kt`
- Modify: `src/main/kotlin/agent/AgentApp.kt`
- Modify: `src/test/kotlin/agent/MySimpleAgentTest.kt`

- [ ] **Step 1: 先写一个当前默认绑定测试**

```kotlin
package agent

import mulehang.config.AppConfig
import mulehang.config.ProviderConfig
import mulehang.provider.ExecutorFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class MySimpleAgentTest {
    @Test
    fun `should build default provider binding from config`() {
        val cfg = AppConfig(
            defaultProvider = "openrouter",
            defaultModel = "openrouter.gpt4o",
            providers = mapOf(
                "openrouter" to ProviderConfig(
                    apiKey = "abc",
                    baseUrl = "https://openrouter.ai/api/v1"
                )
            )
        )

        val binding = ExecutorFactory(cfg).defaultBinding()

        assertEquals("openrouter", binding.providerId)
        assertEquals("openrouter.gpt4o", binding.modelId)
        assertEquals("abc", binding.apiKey)
    }
}
```

- [ ] **Step 2: 修改 `AgentConfig.kt`，把默认配置收敛到 `ConfigLoader + ExecutorFactory`**

```kotlin
package agent

import java.net.URI
import mulehang.config.ConfigLoader
import mulehang.provider.ExecutorFactory

internal val APP_CONFIG = ConfigLoader().load()
internal val DEFAULT_BINDING = ExecutorFactory(APP_CONFIG).defaultBinding()
internal val CHAT_MODEL_ID = DEFAULT_BINDING.modelId
internal val CHAT_BASE_URL = DEFAULT_BINDING.baseUrl
internal val OPENROUTER_CHAT_COMPLETIONS_URI: URI =
    URI("${CHAT_BASE_URL.trimEnd('/')}/chat/completions")
```

- [ ] **Step 3: 修改 `AgentApp.kt`，把 API key 的读取改为来自 `DEFAULT_BINDING`**

```kotlin
val apiKey = DEFAULT_BINDING.apiKey.ifBlank {
    error("当前默认 provider 未配置 apiKey: ${DEFAULT_BINDING.providerId}")
}
```

- [ ] **Step 4: 运行现有 agent 测试与构建**

Run:

```powershell
.\gradlew.bat test --tests "agent.MySimpleAgentTest"
.\gradlew.bat build
```

Expected: PASS，并且 `AgentConfig.kt` 不再只认识固定常量 `OPENROUTER_API_KEY`。

- [ ] **Step 5: 提交 Phase 0-1 收口检查点**

```powershell
git add src/main/kotlin/agent/AgentConfig.kt src/main/kotlin/agent/AgentApp.kt src/test/kotlin/agent/MySimpleAgentTest.kt
git commit -m "feat(agent): load default binding from config"
```

## 自检

- 规格覆盖：
  - Koog 兼容性探针：Task 1
  - 配置模型：Task 2
  - provider/model 目录：Task 3
  - BYOK 绑定工厂：Task 4
  - 现有 CLI 接入：Task 5
- 占位符检查：无 `TBD`、`TODO`、`similar to`。
- 类型一致性：统一使用 `AppConfig`、`ProviderConfig`、`ProviderGateway`、`ExecutorBinding`、`ExecutorFactory`。
