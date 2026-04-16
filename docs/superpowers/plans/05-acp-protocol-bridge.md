# ACP Protocol Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Koog `AcpAgent` feature 把同一套 runtime 主轴桥接给 ACP 客户端。

**Architecture:** 先定义 ACP session bridge，再补 `AcpAgent` feature 安装和事件映射。ACP 只复用现有 runtime 和 agent 装配，不新增第二套核心状态模型。

**Tech Stack:** Kotlin/JVM, JetBrains Koog ACP feature, kotlin.test, JUnit 5

---

### Task 1: 定义 ACP session bridge

**Files:**
- Create: `src/main/kotlin/com/agent/acp/AcpSessionBridge.kt`
- Create: `src/main/kotlin/com/agent/acp/AcpSessionContext.kt`
- Test: `src/test/kotlin/com/agent/acp/AcpSessionBridgeTest.kt`

- [ ] **Step 1: 写失败测试**

验证 ACP session 可以关联到既有 runtime session。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.acp.AcpSessionBridgeTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class AcpSessionBridge
data class AcpSessionContext(val acpSessionId: String, val runtimeSessionId: String)
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.acp.AcpSessionBridgeTest
```

Expected: PASS。

### Task 2: 安装 Koog `AcpAgent` feature

**Files:**
- Create: `src/main/kotlin/com/agent/acp/AcpAgentFactory.kt`
- Test: `src/test/kotlin/com/agent/acp/AcpAgentFactoryTest.kt`

- [ ] **Step 1: 写失败测试**

验证 ACP agent 装配复用现有 runtime 和 Koog agent 组装逻辑。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.acp.AcpAgentFactoryTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class AcpAgentFactory
```

并在内部安装 Koog `AcpAgent` feature。

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.acp.AcpAgentFactoryTest
```

Expected: PASS。

### Task 3: 映射 ACP 事件并验证与 CLI 共用 runtime

**Files:**
- Create: `src/main/kotlin/com/agent/acp/AcpEventMapper.kt`
- Test: `src/test/kotlin/com/agent/acp/AcpEventMapperTest.kt`

- [ ] **Step 1: 写失败测试**

验证 ACP 事件映射使用同一套 runtime event/result/failure 源。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.acp.AcpEventMapperTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
class AcpEventMapper
```

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
.\gradlew.bat test --tests com.agent.acp.*
```

Expected: PASS，且确认 ACP 与 CLI 共用 runtime 主轴。
