# Runtime Foundation And Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 定义 runtime 主轴的核心契约，让后续 provider、Koog agent、CLI 和 ACP 都能复用同一套调用流。

**Architecture:** 先在 `com.agent.runtime` 下定义 session、request context、capability request、event/result/error 的模型，再用最小 dispatcher 串起请求流。测试优先锁定契约，避免后续阶段反向改形状。

**Tech Stack:** Kotlin/JVM, JetBrains Koog, kotlin.test, JUnit 5

---

### Task 1: 定义 runtime 核心模型

**Files:**
- Create: `src/main/kotlin/com/agent/runtime/RuntimeContracts.kt`
- Test: `src/test/kotlin/com/agent/runtime/RuntimeContractsTest.kt`

- [ ] **Step 1: 写失败测试，锁定 runtime 契约字段**

在 `src/test/kotlin/com/agent/runtime/RuntimeContractsTest.kt` 定义测试，至少覆盖：

```kotlin
class RuntimeContractsTest {
    @Test
    fun `should expose session context request event result and failure models`() {
        // 验证 RuntimeSession、RuntimeRequestContext、CapabilityRequest、
        // RuntimeEvent、RuntimeResult、RuntimeFailure 可被稳定构造
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.RuntimeContractsTest
```

Expected: FAIL，提示 `RuntimeContracts.kt` 中的类型尚未定义。

- [ ] **Step 3: 写最小实现**

在 `src/main/kotlin/com/agent/runtime/RuntimeContracts.kt` 定义：

```kotlin
data class RuntimeSession(val id: String)
data class RuntimeRequestContext(val sessionId: String)
sealed interface CapabilityRequest
sealed interface RuntimeEvent
sealed interface RuntimeResult
sealed interface RuntimeFailure
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.RuntimeContractsTest
```

Expected: PASS。

### Task 2: 定义最小 dispatcher

**Files:**
- Create: `src/main/kotlin/com/agent/runtime/RuntimeRequestDispatcher.kt`
- Test: `src/test/kotlin/com/agent/runtime/RuntimeRequestDispatcherTest.kt`

- [ ] **Step 1: 写失败测试，锁定最小请求流**

在 `src/test/kotlin/com/agent/runtime/RuntimeRequestDispatcherTest.kt` 定义测试，至少覆盖：

```kotlin
class RuntimeRequestDispatcherTest {
    @Test
    fun `should dispatch request through unified runtime pipeline`() {
        // 验证 dispatcher 接收 context 和 request 后返回统一结果
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.RuntimeRequestDispatcherTest
```

Expected: FAIL，提示 dispatcher 未定义。

- [ ] **Step 3: 写最小实现**

在 `src/main/kotlin/com/agent/runtime/RuntimeRequestDispatcher.kt` 定义：

```kotlin
class RuntimeRequestDispatcher(
    private val capabilityRouter: RuntimeCapabilityRouter
) {
    fun dispatch(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: CapabilityRequest
    ): RuntimeResult = capabilityRouter.route(context, request)
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.RuntimeRequestDispatcherTest
```

Expected: PASS。

### Task 3: 引入 capability router 抽象

**Files:**
- Create: `src/main/kotlin/com/agent/runtime/RuntimeCapabilityRouter.kt`
- Modify: `src/main/kotlin/com/agent/runtime/RuntimeRequestDispatcher.kt`
- Test: `src/test/kotlin/com/agent/runtime/RuntimeCapabilityRouterTest.kt`

- [ ] **Step 1: 写失败测试，锁定 router 责任**

在 `src/test/kotlin/com/agent/runtime/RuntimeCapabilityRouterTest.kt` 定义测试，验证 runtime 不直接感知 tool、MCP 或 HTTP 的底层细节。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.RuntimeCapabilityRouterTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

在 `src/main/kotlin/com/agent/runtime/RuntimeCapabilityRouter.kt` 定义：

```kotlin
interface RuntimeCapabilityRouter {
    suspend fun route(
        context: RuntimeRequestContext,
        request: CapabilityRequest
    ): RuntimeResult
}
```

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.*
```

Expected: PASS。
