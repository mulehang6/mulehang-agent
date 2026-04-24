# Provider BYOK And Model Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持 `custom provider`、提供商类型、保存后自动探测连接和自动发现模型列表，并把结果解析为 runtime binding。

**Architecture:** 先定义 `custom provider` 与提供商类型模型，再按提供商类型拆分 probe adapter、model discovery adapter 和 binding resolver。协议切换后强制重新探测，避免模型目录和 binding 脏化。

**Tech Stack:** Kotlin/JVM, kotlin.test, JUnit 5, kotlinx.serialization

---

### Task 1: 定义 custom provider 与提供商类型模型

**Files:**
- Create: `src/main/kotlin/com/agent/provider/ProviderType.kt`
- Create: `src/main/kotlin/com/agent/provider/CustomProviderProfile.kt`
- Test: `src/test/kotlin/com/agent/provider/CustomProviderProfileTest.kt`

- [ ] **Step 1: 写失败测试，锁定默认提供商类型与可变性**

测试至少覆盖：

```kotlin
class CustomProviderProfileTest {
    @Test
    fun `should default provider type to openai compatible`() { }

    @Test
    fun `should allow changing provider type after creation`() { }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.provider.CustomProviderProfileTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
enum class ProviderType { OPENAI_RESPONSES, OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }
data class CustomProviderProfile(
    val id: String,
    val baseUrl: String,
    val apiKey: String,
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE
)
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.provider.CustomProviderProfileTest
```

Expected: PASS。

### Task 2: 定义 probe 与 discovery 契约

**Files:**
- Create: `src/main/kotlin/com/agent/provider/ConnectionProbe.kt`
- Create: `src/main/kotlin/com/agent/provider/ModelDiscovery.kt`
- Test: `src/test/kotlin/com/agent/provider/ProviderProbeContractsTest.kt`

- [ ] **Step 1: 写失败测试，锁定三种提供商类型都走适配器**

测试至少覆盖：

```kotlin
class ProviderProbeContractsTest {
    @Test
    fun `should resolve probe adapter by provider type`() { }

    @Test
    fun `should resolve discovery adapter by provider type`() { }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.provider.ProviderProbeContractsTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
interface ConnectionProbeAdapter { suspend fun probe(profile: CustomProviderProfile): ConnectionProbeResult }
interface ModelDiscoveryAdapter { suspend fun discover(profile: CustomProviderProfile): ModelDiscoveryResult }
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.provider.ProviderProbeContractsTest
```

Expected: PASS。

### Task 3: 定义 binding resolver 与提供商类型变更刷新规则

**Files:**
- Create: `src/main/kotlin/com/agent/provider/ProviderBindingResolver.kt`
- Create: `src/main/kotlin/com/agent/provider/ProviderBinding.kt`
- Test: `src/test/kotlin/com/agent/provider/ProviderBindingResolverTest.kt`

- [ ] **Step 1: 写失败测试，锁定 binding 解析与重新探测规则**

测试至少覆盖：

```kotlin
class ProviderBindingResolverTest {
    @Test
    fun `should resolve runtime binding from profile probe and discovery`() { }

    @Test
    fun `should invalidate previous discovery when provider type changes`() { }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.provider.ProviderBindingResolverTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
data class ProviderBinding(
    val providerId: String,
    val providerType: ProviderType,
    val modelId: String
)

class ProviderBindingResolver
```

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
.\gradlew.bat test --tests com.agent.provider.*
```

Expected: PASS，且覆盖：

```text
OpenAI-compatible
Anthropic-compatible
Gemini-compatible
提供商类型变更后的重新探测
```
