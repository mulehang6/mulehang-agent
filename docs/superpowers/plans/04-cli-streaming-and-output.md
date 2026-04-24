# CLI Streaming And Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 CLI 做成第一主入口，并让它以独立模块的形式稳定消费 runtime 的统一输出。

**Architecture:** 先新增独立 `cli` Gradle 模块，并让它通过 `project(":runtime")` 依赖 `runtime`；再定义 CLI 入口与 renderer，补齐 streaming printer 和 structured output 呈现。CLI 只依赖 runtime 的 event/result/failure 契约与稳定入口，不依赖底层 capability 类型。

**Tech Stack:** Kotlin/JVM, Gradle multi-module, kotlin.test, JUnit 5

---

### Task 1: 建立独立的 CLI 模块边界

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `cli/build.gradle.kts`

- [ ] **Step 1: 在 settings 中声明 `:cli` 子模块**

把：

```kotlin
include(":runtime")
```

改成：

```kotlin
include(":runtime")
include(":cli")
```

- [ ] **Step 2: 创建 `cli/build.gradle.kts`**

```kotlin
plugins {
    application
    kotlin("jvm")
}

group = "com.agent"
version = "0.0.1"

dependencies {
    implementation(project(":runtime"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: 运行模块测试命令，确认 `:cli` 已被 Gradle 识别**

Run:

```powershell
.\gradlew.bat :cli:test
```

Expected: PASS 或 NO-SOURCE，且不会报 `Project ':cli' not found`。

### Task 2: 定义 CLI 入口

**Files:**
- Create: `cli/src/main/kotlin/com/agent/cli/CliEntry.kt`
- Create: `cli/src/main/kotlin/com/agent/cli/CliCommandParser.kt`
- Test: `cli/src/test/kotlin/com/agent/cli/CliEntryTest.kt`

- [ ] **Step 1: 写失败测试**

验证 CLI 能接收输入并把请求交给 runtime。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :cli:test --tests com.agent.cli.CliEntryTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class CliCommandParser
class CliEntry(
    private val dispatcher: RuntimeRequestDispatcher
)
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat :cli:test --tests com.agent.cli.CliEntryTest
```

Expected: PASS。

### Task 3: 支持 streaming 输出

**Files:**
- Create: `cli/src/main/kotlin/com/agent/cli/CliStreamingPrinter.kt`
- Create: `cli/src/main/kotlin/com/agent/cli/CliRenderer.kt`
- Test: `cli/src/test/kotlin/com/agent/cli/CliStreamingPrinterTest.kt`

- [ ] **Step 1: 写失败测试**

验证 streaming 事件能被稳定消费并输出。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :cli:test --tests com.agent.cli.CliStreamingPrinterTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

实现：

```kotlin
class CliStreamingPrinter
class CliRenderer
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat :cli:test --tests com.agent.cli.CliStreamingPrinterTest
```

Expected: PASS。

### Task 4: 支持 structured output 和用户可见错误

**Files:**
- Modify: `cli/src/main/kotlin/com/agent/cli/CliRenderer.kt`
- Create: `cli/src/test/kotlin/com/agent/cli/CliRendererTest.kt`

- [ ] **Step 1: 写失败测试**

验证 structured output 和 failure 都通过统一 renderer 展示。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :cli:test --tests com.agent.cli.CliRendererTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

把 `RuntimeEvent`、`RuntimeResult`、`RuntimeFailure` 统一渲染到 CLI。

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
.\gradlew.bat :cli:test --tests com.agent.cli.*
```

Expected: PASS。
