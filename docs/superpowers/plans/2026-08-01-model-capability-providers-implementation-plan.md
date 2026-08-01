# 模型能力提供者 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将模型能力规则拆分为可扩展的 provider，并允许用户在 `settings.json` 为自定义模型声明 reasoning 能力。

**Architecture:** `ModelCapabilitiesResolver` 只按优先级调用能力 provider。DeepSeek 与官方 OpenAI 的已知能力由各自 provider 维护；`ConfiguredModelCapabilityProvider` 读取由 `SettingsMerger` 解析并校验后的用户声明，以最高优先级覆盖代码内置规则。

**Tech Stack:** Kotlin Multiplatform、kotlinx.serialization、kotlin.test、JUnit 5。

## Global Constraints

- 仅修改 `shared/` 的领域模型、配置合并和能力解析；desktop UI 只通过既有 `ModelCapabilitiesResolver` 消费结果。
- 公共生产类型、函数和数据类都要写简短 KDoc。
- `reasoningEfforts` 使用 JSON 小写 wire 值：`low`、`medium`、`high`、`max`。
- JSON 中未声明 `reasoningEfforts` 时，不为自定义 OpenAI-compatible 模型推测能力；显式空数组表示不支持 reasoning。
- 不新增 reasoning 相关环境变量。
- 示例配置只使用占位 API key，且不得提交真实用户配置或密钥。
- 不创建 Git 提交，除非用户另行授权。

---

### Task 1: 扩展模型配置并在合并阶段校验 reasoning 声明

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/settings/model/ModelProfile.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/settings/model/ConfigProfile.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/settings/resolver/SettingsMerger.kt`
- Modify: `shared/src/commonTest/kotlin/com/agent/shared/settings/resolver/SettingsMergerTest.kt`

**Interfaces:**
- Consumes: JSON `ModelProfile` 中的原始 `reasoningEfforts: List<String>?` 和 `defaultReasoningEffort: String?`。
- Produces: `ConfigProfile.reasoningEfforts: List<ReasoningEffort>?` 与 `ConfigProfile.defaultReasoningEffort: ReasoningEffort?`；`null` 保留“未声明”的语义，空列表保留“显式不支持”的语义。

- [ ] **Step 1: 写失败的设置合并测试**

在 `SettingsMergerTest` 中新增以下三组测试，使用 `ProviderProfile` 和 `ModelProfile` 构造输入，调用 `SettingsMerger.merge(user = null, project = settings, environment = emptyMap())`：

```kotlin
@Test
fun `should merge configured reasoning efforts into runtime profile`() {
    val merged = SettingsMerger.merge(
        user = null,
        project = customModelSettings(
            reasoningEfforts = listOf("low", "medium", "high"),
            defaultReasoningEffort = "medium",
        ),
        environment = emptyMap(),
    )

    assertEquals(
        listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
        merged.single().reasoningEfforts,
    )
    assertEquals(ReasoningEffort.MEDIUM, merged.single().defaultReasoningEffort)
}

@Test
fun `should preserve an explicitly empty reasoning effort list`() {
    val merged = SettingsMerger.merge(
        user = null,
        project = customModelSettings(reasoningEfforts = emptyList()),
        environment = emptyMap(),
    )

    assertEquals(emptyList(), merged.single().reasoningEfforts)
    assertEquals(null, merged.single().defaultReasoningEffort)
}

@Test
fun `should reject invalid configured reasoning effort`() {
    val exception = assertFailsWith<IllegalConfigExceptions> {
        SettingsMerger.merge(
            user = null,
            project = customModelSettings(reasoningEfforts = listOf("deep")),
            environment = emptyMap(),
        )
    }

    assertContains(exception.message.orEmpty(), "custom:custom-reasoning-model")
    assertContains(exception.message.orEmpty(), "deep")
}
```

再新增“默认档位未包含在列表中”测试，断言抛出 `IllegalConfigExceptions` 且消息包含 `custom:custom-reasoning-model` 和 `defaultReasoningEffort`。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.settings.resolver.SettingsMergerTest"
```

Expected: 编译失败，因为 `ModelProfile` 与 `ConfigProfile` 尚无 reasoning 字段。

- [ ] **Step 3: 实现配置字段、转换和校验**

1. 在 `ModelProfile` 添加序列化字段：

```kotlin
val reasoningEfforts: List<String>? = null,
val defaultReasoningEffort: String? = null,
```

2. 在 `ConfigProfile` 添加类型安全的运行时字段：

```kotlin
val reasoningEfforts: List<ReasoningEffort>? = null,
val defaultReasoningEffort: ReasoningEffort? = null,
```

3. 在 `SettingsMerger` 中，在构造每个 JSON 模型对应的 `ConfigProfile` 前调用私有转换函数。该函数以 `ReasoningEffort.entries.firstOrNull { it.wireValue == raw.trim().lowercase() }` 解析每一个值，保留列表顺序；未知值抛出 `IllegalConfigExceptions`，消息包含运行时 profile id 和原始值。
4. `reasoningEfforts == null` 时返回 `null` 且不解析默认值。非空列表时，若 `defaultReasoningEffort` 存在则解析并验证其属于已解析列表；不属于时抛出 `IllegalConfigExceptions`，消息包含运行时 profile id 与 `defaultReasoningEffort`。
5. 环境变量仅覆盖模型名时继续构造没有 reasoning 字段的 `ModelProfile`，确保它不会继承不匹配的 JSON 模型能力。

- [ ] **Step 4: 运行设置合并测试确认通过**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.settings.resolver.SettingsMergerTest"
```

Expected: `SettingsMergerTest` 全部通过。

### Task 2: 用 provider 替换中央模型规则

**Files:**
- Create: `shared/src/commonMain/kotlin/com/agent/shared/settings/resolver/ModelCapabilities.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/settings/resolver/ModelCapabilityProvider.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/settings/resolver/ConfiguredModelCapabilityProvider.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/settings/resolver/DeepSeekModelCapabilityProvider.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/settings/resolver/OpenAIModelCapabilityProvider.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/settings/resolver/ModelCapabilitiesResolver.kt`
- Modify: `shared/src/commonTest/kotlin/com/agent/shared/settings/resolver/ModelCapabilitiesResolverTest.kt`

**Interfaces:**
- Consumes: `ConfigProfile` 的 provider type、base URL、模型名、限制和 Task 1 产生的 reasoning 字段。
- Produces: `ModelCapabilityProvider.resolve(profile: ConfigProfile): ModelCapabilities?`；`null` 表示 provider 不匹配，非空的空能力表示显式禁止 reasoning。

- [ ] **Step 1: 写 provider 行为的失败测试**

将现有能力测试扩展为以下场景：

```kotlin
@Test
fun `should let configured capabilities override deepseek defaults`() {
    val capabilities = ModelCapabilitiesResolver.resolve(
        profile(
            model = "deepseek-v4-flash",
            baseUrl = "https://api.deepseek.com/v1",
            providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
            reasoningEfforts = listOf(ReasoningEffort.LOW),
            defaultReasoningEffort = ReasoningEffort.LOW,
        ),
    )

    assertEquals(listOf(ReasoningEffort.LOW), capabilities.reasoningEfforts)
    assertEquals(ReasoningEffort.LOW, capabilities.defaultReasoningEffort)
}

@Test
fun `should expose no reasoning for explicitly empty configured efforts`() {
    val capabilities = ModelCapabilitiesResolver.resolve(
        profile(
            model = "custom-model",
            baseUrl = "https://gateway.example/v1",
            providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
            reasoningEfforts = emptyList(),
        ),
    )

    assertEquals(emptyList(), capabilities.reasoningEfforts)
    assertEquals(null, capabilities.defaultReasoningEffort)
}

@Test
fun `should expose reasoning only for official OpenAI responses models`() {
    val official = ModelCapabilitiesResolver.resolve(
        profile(
            model = "gpt-5-codex",
            baseUrl = "https://api.openai.com/v1",
            providerType = ProviderType.OPENAI_RESPONSES,
        ),
    )
    val customEndpoint = ModelCapabilitiesResolver.resolve(
        profile(
            model = "gpt-5-codex",
            baseUrl = "https://gateway.example/v1",
            providerType = ProviderType.OPENAI_RESPONSES,
        ),
    )

    assertEquals(
        listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
        official.reasoningEfforts,
    )
    assertEquals(emptyList(), customEndpoint.reasoningEfforts)
}
```

保留并调整既有 DeepSeek 测试：它必须仍断言 `high/max`、默认 `high` 和默认上下文限制。新增一个测试验证非 DeepSeek 的 OpenAI chat-completions profile 不会因模型名包含 `gpt-5` 而推断能力。

- [ ] **Step 2: 运行能力测试确认失败**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.settings.resolver.ModelCapabilitiesResolverTest"
```

Expected: 至少“配置覆盖 DeepSeek”与“官方 OpenAI Responses”断言失败，因为当前中央 resolver 没有配置优先级且未按官方 OpenAI endpoint/Responses 限定。

- [ ] **Step 3: 实现 provider 边界与注册顺序**

1. 将 `ModelCapabilities`、`ModelVariant` 和构造辅助移至 `ModelCapabilities.kt`；保留当前 `defaultReasoningEffort` 规则，并增加对 `ConfigProfile.defaultReasoningEffort` 的可选覆盖入口。
2. 新建接口：

```kotlin
interface ModelCapabilityProvider {
    fun resolve(profile: ConfigProfile): ModelCapabilities?
}
```

3. `ConfiguredModelCapabilityProvider`：仅当 `profile.reasoningEfforts != null` 时返回能力。将每个 `ReasoningEffort` 转为 `ModelVariant`；将 `profile.defaultReasoningEffort` 用作默认值；同时传递 `profile.limit`。空列表也必须返回非空的 `ModelCapabilities`，阻断后续内置 provider。
4. `DeepSeekModelCapabilityProvider`：仅匹配 `ProviderType.OPENAI_CHAT_COMPLETIONS`，且模型名以 `deepseek` 开头或 base URL 包含 `deepseek`。返回 `high/max`，并在 `profile.limit == null` 时提供 `ModelLimit(context = 1_000_000, output = 384_000)`。
5. `OpenAIModelCapabilityProvider`：仅匹配 `ProviderType.OPENAI_RESPONSES`、`https://api.openai.com` 或 `https://api.openai.com/v1`（忽略末尾 `/`）以及模型名包含 `gpt-5` 或 `codex`。返回 `low/medium/high` 和 `profile.limit`；不得匹配任何自定义 endpoint。
6. 将 `ModelCapabilitiesResolver` 缩减为按 `ConfiguredModelCapabilityProvider`、`DeepSeekModelCapabilityProvider`、`OpenAIModelCapabilityProvider` 的固定顺序调用；全部返回 `null` 时返回共享空能力。该文件不得保留厂商名、endpoint 或模型名匹配规则。

- [ ] **Step 4: 运行能力测试确认通过**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.settings.resolver.ModelCapabilitiesResolverTest"
```

Expected: `ModelCapabilitiesResolverTest` 全部通过，且新测试验证配置优先、官方 OpenAI 限定和自定义 endpoint 无推测能力。

### Task 3: 更新示例配置并完成模块验证

**Files:**
- Modify: `.mulehang/settings.json.example`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1 的 JSON 字段和 Task 2 的 `ModelCapabilitiesResolver.resolve(profile)` 行为。
- Produces: 自定义模型能力的可发现配置示例与完整模块验证记录。

- [ ] **Step 1: 更新示例和说明**

1. 在 `.mulehang/settings.json.example` 增加一个 `enabled: false` 的自定义 OpenAI chat-completions provider，使用 `https://gateway.example/v1`、`your-api-key` 和模型 `custom-reasoning-model`；模型中添加：

```json
"reasoningEfforts": ["low", "medium", "high"],
"defaultReasoningEffort": "medium"
```

2. 在 `README.md` 的配置章节补充同样的字段说明：字段缺失不猜测能力、空数组关闭 reasoning、默认值必须来自列表、仅允许四个小写值。

- [ ] **Step 2: 运行回归与完整验证**

Run:

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test :desktopApp:compileKotlin
git diff --check
```

Expected: Gradle 命令退出码为 0；`git diff --check` 无输出。确认没有真实 settings 文件或密钥进入 diff。

## 覆盖审查

- 官方 DeepSeek、官方 OpenAI 与自定义模型分别由 Task 2 覆盖。
- 用户配置的解析、空列表语义和非法配置由 Task 1 覆盖。
- 既有 Compose 会话默认值与模型切换回归由完整的 `:desktopApp:test` 覆盖。
- `.mulehang/settings.json.example` 与 README 的配置发现性由 Task 3 覆盖。

## 实施前检查

- [ ] 开始实现前读取 `shared/AGENTS.md` 与 `desktopApp/AGENTS.md`。
- [ ] 每次源码 patch 后使用可用的 IDEA 问题检查；若 IDEA MCP 不可用，运行对应的最小 Gradle 测试或编译任务。
- [ ] 不启动 Desktop、Vite 或其他长期运行服务。
- [ ] 未获用户明确授权前不执行 Git 提交。
