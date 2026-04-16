# Client Surfaces Optional Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为未来的 KMP desktop 与 Web UI 预留 client surface 接入边界，而不重写当前核心主线。

**Architecture:** 先固化 client surface contract，再分别写 KMP desktop 和 Web UI 的接入假设文档。当前阶段不要求实现完整桌面端或 Web 前端。

**Tech Stack:** Markdown, Kotlin/JVM, optional KMP/Web planning

---

### Task 1: 定义可复用的 client surface contract

**Files:**
- Create: `src/main/kotlin/com/agent/client/ClientSurfaceContract.kt`
- Test: `src/test/kotlin/com/agent/client/ClientSurfaceContractTest.kt`

- [ ] **Step 1: 写失败测试**

验证未来 client surface 只能消费 runtime、protocol 和输出契约，而不能绕开核心主线。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.client.ClientSurfaceContractTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
interface ClientSurfaceContract
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.client.ClientSurfaceContractTest
```

Expected: PASS。

### Task 2: 写 KMP desktop 接入假设

**Files:**
- Create: `docs/client-surfaces/kmp-desktop.md`

- [ ] **Step 1: 写文档**

文档必须明确：

```md
1. KMP desktop 复用 runtime 主轴
2. KMP desktop 复用 provider / binding 模型
3. KMP desktop 不重写 agent 与 capability integration
```

- [ ] **Step 2: 运行文件检查**

对 `docs/client-surfaces/kmp-desktop.md` 运行 JetBrains file inspection。

Expected: no errors, no warnings.

### Task 3: 写 Web UI 接入假设

**Files:**
- Create: `docs/client-surfaces/web-ui.md`

- [ ] **Step 1: 写文档**

文档必须明确：

```md
1. Web UI 复用 runtime 主轴
2. Web UI 通过现有协议或输出契约接入
3. Web UI 不重写 provider、agent、ACP 的核心逻辑
```

- [ ] **Step 2: 运行文件检查**

对 `docs/client-surfaces/web-ui.md` 运行 JetBrains file inspection。

Expected: no errors, no warnings.
