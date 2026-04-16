# Agent Strategy And Capability Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 JetBrains Koog `AIAgent` 与 `singleRunStrategy` 接入 runtime，并建立 tool、MCP、direct HTTP internal API 的统一 capability integration。

**Architecture:** 先定义 capability contract，再实现 Koog agent 装配，最后把三类能力以独立 adapter 接入。runtime 只依赖统一 router，不依赖具体接入类型。

**Tech Stack:** Kotlin/JVM, JetBrains Koog, kotlin.test, JUnit 5

---

### Task 1: 定义 capability contract

**Files:**
- Create: `src/main/kotlin/com/agent/capability/CapabilityContract.kt`
- Create: `src/main/kotlin/com/agent/capability/CapabilitySet.kt`
- Test: `src/test/kotlin/com/agent/capability/CapabilityContractTest.kt`

- [ ] **Step 1: 写失败测试**

验证 capability contract 能统一表达 tool、MCP 和 HTTP。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.capability.CapabilityContractTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
interface CapabilityAdapter
data class CapabilityDescriptor(val id: String, val kind: String)
class CapabilitySet(val adapters: List<CapabilityAdapter>)
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.capability.CapabilityContractTest
```

Expected: PASS。

### Task 2: 接入 Koog `AIAgent` 与 `singleRunStrategy`

**Files:**
- Create: `src/main/kotlin/com/agent/agent/AgentAssembly.kt`
- Create: `src/main/kotlin/com/agent/agent/AgentStrategyFactory.kt`
- Test: `src/test/kotlin/com/agent/agent/AgentAssemblyTest.kt`

- [ ] **Step 1: 写失败测试**

验证 runtime 可以基于 binding 与 capability set 组装最小 Koog agent。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.agent.AgentAssemblyTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class AgentAssembly
object AgentStrategyFactory {
    fun singleRun(): String = "single-run"
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.agent.AgentAssemblyTest
```

Expected: PASS。

### Task 3: 接入 tool、MCP 与 direct HTTP adapters

**Files:**
- Create: `src/main/kotlin/com/agent/capability/ToolCapabilityAdapter.kt`
- Create: `src/main/kotlin/com/agent/capability/McpCapabilityAdapter.kt`
- Create: `src/main/kotlin/com/agent/capability/HttpCapabilityAdapter.kt`
- Test: `src/test/kotlin/com/agent/capability/CapabilityAdaptersTest.kt`

- [ ] **Step 1: 写失败测试**

验证三类 adapter 都能被 capability set 注册并通过统一接口调用。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.capability.CapabilityAdaptersTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

分别实现：

```kotlin
class ToolCapabilityAdapter : CapabilityAdapter
class McpCapabilityAdapter : CapabilityAdapter
class HttpCapabilityAdapter : CapabilityAdapter
```

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
.\gradlew.bat test --tests com.agent.capability.* --tests com.agent.agent.*
```

Expected: PASS。
