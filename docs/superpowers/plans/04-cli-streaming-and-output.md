# CLI Streaming And Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 CLI 做成第一主入口，并让它稳定消费 runtime 的统一输出。

**Architecture:** 先定义 CLI 入口与 renderer，再补 streaming printer 和 structured output 呈现。CLI 只依赖 runtime 的 event/result/failure 契约，不依赖底层 capability 类型。

**Tech Stack:** Kotlin/JVM, JetBrains Koog, kotlin.test, JUnit 5

---

### Task 1: 定义 CLI 入口

**Files:**
- Create: `src/main/kotlin/com/agent/cli/CliEntry.kt`
- Create: `src/main/kotlin/com/agent/cli/CliCommandParser.kt`
- Test: `src/test/kotlin/com/agent/cli/CliEntryTest.kt`

- [ ] **Step 1: 写失败测试**

验证 CLI 能接收输入并把请求交给 runtime。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.cli.CliEntryTest
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
.\gradlew.bat test --tests com.agent.cli.CliEntryTest
```

Expected: PASS。

### Task 2: 支持 streaming 输出

**Files:**
- Create: `src/main/kotlin/com/agent/cli/CliStreamingPrinter.kt`
- Create: `src/main/kotlin/com/agent/cli/CliRenderer.kt`
- Test: `src/test/kotlin/com/agent/cli/CliStreamingPrinterTest.kt`

- [ ] **Step 1: 写失败测试**

验证 streaming 事件能被稳定消费并输出。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.cli.CliStreamingPrinterTest
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
.\gradlew.bat test --tests com.agent.cli.CliStreamingPrinterTest
```

Expected: PASS。

### Task 3: 支持 structured output 和用户可见错误

**Files:**
- Modify: `src/main/kotlin/com/agent/cli/CliRenderer.kt`
- Create: `src/test/kotlin/com/agent/cli/CliRendererTest.kt`

- [ ] **Step 1: 写失败测试**

验证 structured output 和 failure 都通过统一 renderer 展示。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.cli.CliRendererTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

把 `RuntimeEvent`、`RuntimeResult`、`RuntimeFailure` 统一渲染到 CLI。

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
.\gradlew.bat test --tests com.agent.cli.*
```

Expected: PASS。
