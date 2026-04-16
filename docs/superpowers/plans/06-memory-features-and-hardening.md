# Memory Features And Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不破坏前面核心契约的前提下，引入 memory、snapshot/persistence、tracing/observability 和失败恢复。

**Architecture:** 所有增强都作为 feature/hardening 层叠加到 runtime 与 Koog agent 主线上，不回头改写 provider、CLI、ACP 或 capability integration 的核心模型。

**Tech Stack:** Kotlin/JVM, JetBrains Koog features, kotlin.test, JUnit 5

---

### Task 1: 接入 memory

**Files:**
- Create: `src/main/kotlin/com/agent/features/MemoryFeatureInstaller.kt`
- Test: `src/test/kotlin/com/agent/features/MemoryFeatureInstallerTest.kt`

- [ ] **Step 1: 写失败测试**

验证 memory 作为 feature 安装，不改变 runtime 契约。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.features.MemoryFeatureInstallerTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class MemoryFeatureInstaller
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.features.MemoryFeatureInstallerTest
```

Expected: PASS。

### Task 2: 接入 snapshot / persistence 与 observability

**Files:**
- Create: `src/main/kotlin/com/agent/features/SnapshotSupport.kt`
- Create: `src/main/kotlin/com/agent/features/ObservabilitySupport.kt`
- Test: `src/test/kotlin/com/agent/features/HardeningSupportTest.kt`

- [ ] **Step 1: 写失败测试**

验证 snapshot / persistence 与 observability 可以作为增强层接入。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.features.HardeningSupportTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class SnapshotSupport
class ObservabilitySupport
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.features.HardeningSupportTest
```

Expected: PASS。

### Task 3: 定义失败恢复并补集成验证

**Files:**
- Create: `src/main/kotlin/com/agent/features/RuntimeRecovery.kt`
- Create: `src/test/kotlin/com/agent/features/RuntimeRecoveryTest.kt`
- Create: `src/test/kotlin/com/agent/integration/RuntimeIntegrationTest.kt`

- [ ] **Step 1: 写失败测试**

验证失败恢复不会破坏已有 runtime、provider、agent、CLI、ACP 边界。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.features.RuntimeRecoveryTest --tests com.agent.integration.RuntimeIntegrationTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class RuntimeRecovery
```

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
.\gradlew.bat test --tests com.agent.features.* --tests com.agent.integration.RuntimeIntegrationTest
```

Expected: PASS。
