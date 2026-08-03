# Anthropic Compatible Models Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Anthropic 兼容端点能将配置中的任意模型 ID 原样传给 Koog Anthropic 客户端。

**Architecture:** 在 Koog 配置装配层增加一个内部的 Anthropic settings 构造函数。该函数使用当前 profile 构造 `LLModel`，并将该模型映射回 `ConfigProfile.model`；现有客户端和协议路径保持不变。

**Tech Stack:** Kotlin Multiplatform、Koog 1.1.1、kotlin.test、JUnit 5。

## Global Constraints

- 仅修改 `shared` JVM 的 Koog 装配代码与其 JVM 测试。
- Anthropic 请求协议、认证头、路径和推理字段不得改变。
- 不修改用户现有的 `.mulehang/settings.json`。
- 使用 `functions.apply_patch` 编辑文件，并在每次源文件修改后读取 IDEA 文件问题。
- 未获得明确授权不得创建 Git 提交。

---

### Task 1: 配置 Anthropic 兼容模型映射

**Files:**
- Modify: `shared/src/jvmMain/kotlin/com/agent/shared/agent/koog/DesktopPromptExecutorFactory.kt:179-202`
- Test: `shared/src/jvmTest/kotlin/com/agent/shared/agent/koog/KoogPromptTest.kt`

**Interfaces:**
- Consumes: `ConfigProfile`、`buildLlmModel(config): LLModel`、Koog `AnthropicClientSettings`。
- Produces: `internal fun buildAnthropicClientSettings(config: ConfigProfile): AnthropicClientSettings`。

- [ ] **Step 1: 写入失败测试**

在 `KoogPromptTest.kt` 添加一个使用 `ProviderType.ANTHROPIC`、`model = "deepseek-v4-flash"`、`baseUrl = "https://api.deepseek.com/anthropic"` 的 profile，并断言：

```kotlin
val profile = anthropicCompatibleProfile()
val settings = buildAnthropicClientSettings(profile)

assertEquals(profile.baseUrl, settings.baseUrl)
assertEquals(profile.model, settings.modelVersionsMap[buildLlmModel(profile)])
```

- [ ] **Step 2: 运行测试确认失败**

先读取 IDEA run configuration；若无对应配置，运行：

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.koog.KoogPromptTest"
```

预期：编译失败，因为 `buildAnthropicClientSettings` 尚不存在。

- [ ] **Step 3: 实现最小配置构造函数**

在 `DesktopPromptExecutorFactory.kt` 添加：

```kotlin
internal fun buildAnthropicClientSettings(config: ConfigProfile): AnthropicClientSettings =
    AnthropicClientSettings(
        modelVersionsMap = mapOf(buildLlmModel(config) to config.model),
        baseUrl = config.baseUrl,
    )
```

并将 `buildPromptExecutor` 的 Anthropic 分支改为调用此函数：

```kotlin
settings = buildAnthropicClientSettings(config),
```

- [ ] **Step 4: 用 IDEA 检查源文件问题**

调用 `get_file_problems` 检查 `DesktopPromptExecutorFactory.kt`；预期没有 Kotlin 错误。

- [ ] **Step 5: 运行定向测试确认通过**

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.koog.KoogPromptTest"
```

预期：目标测试和既有 `KoogPromptTest` 全部通过。

- [ ] **Step 6: 扩大验证范围**

```powershell
.\gradlew.bat :shared:jvmTest
.\gradlew.bat :shared:compileKotlinJvm
```

预期：两个任务均以退出码 0 完成。

- [ ] **Step 7: 检查最终改动**

使用 IDEA `git_status` 与 `get_file_problems` 检查只包含预期文件，且无新增错误；不提交，除非用户另行授权。
