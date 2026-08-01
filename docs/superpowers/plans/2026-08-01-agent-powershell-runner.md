# Agent PowerShell 执行器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Agent 的短生命周期 PowerShell 工具补齐可靠进程执行边界。

**Architecture:** `DesktopProcessRunner` 负责子进程生命周期和受限输出采集；`DesktopPowerShellTool` 缓存可用性并委派执行；`DesktopToolSet` 固定传入工作区。UI PTY 不参与 Agent 工具执行。

**Tech Stack:** Kotlin/JVM、Java `ProcessBuilder`、kotlin.test、JUnit 5。

## Global Constraints

- 维持一次调用一个 `pwsh` 子进程，不实现持久 shell。
- 默认超时 120 秒，最大超时 600 秒。
- stdout、stderr 各限制为 1 MiB，分别标记截断。
- 工作目录默认为并限定在已选工作区。

---

### Task 1: 建立受控进程运行器

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/agent/shared/tool/runtime/DesktopProcessRunner.kt`
- Test: `shared/src/jvmTest/kotlin/com/agent/shared/tool/runtime/DesktopProcessRunnerTest.kt`

- [x] **Step 1: 写失败测试**

覆盖并行排空 stdout/stderr、输出截断、超时及取消时子进程被终止。

- [x] **Step 2: 运行失败测试**

运行：`.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.tool.runtime.DesktopProcessRunnerTest"`

- [x] **Step 3: 实现最小运行器**

使用 `ProcessBuilder` 启动进程，两个后台读取任务并发消费输出流，`waitFor(timeout)` 控制期限，并执行 `destroy` 后的 `destroyForcibly` 兜底。

- [x] **Step 4: 验证测试通过**

运行同一 Gradle 测试任务。

### Task 2: 接入 PowerShell 工具与工作目录

**Files:**
- Modify: `shared/src/jvmMain/kotlin/com/agent/shared/tool/runtime/DesktopPowerShellTool.kt`
- Modify: `shared/src/jvmMain/kotlin/com/agent/shared/tool/runtime/DesktopToolSet.kt`
- Test: `shared/src/jvmTest/kotlin/com/agent/shared/tool/runtime/DesktopPowerShellToolTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/agent/shared/tool/runtime/DesktopToolRegistryFactoryTest.kt`

- [x] **Step 1: 写失败测试**

覆盖版本探测仅发生一次、默认工作目录为工作区、结果包含超时/取消/截断状态。

- [x] **Step 2: 运行失败测试**

运行：`.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.tool.runtime.DesktopPowerShellToolTest" --tests "com.agent.shared.tool.runtime.DesktopToolRegistryFactoryTest"`

- [x] **Step 3: 实现最小接入**

缓存 PowerShell 7 探测结果，使用运行器执行脚本，并从 `DesktopToolSet` 传递 `workspacePath`。

- [x] **Step 4: 验证测试通过**

运行同一 Gradle 测试任务。

### Task 3: 模块级验证

**Files:**
- Modify: `docs/superpowers/specs/2026-08-01-agent-powershell-runner-design.md`
- Modify: `docs/superpowers/plans/2026-08-01-agent-powershell-runner.md`

- [x] **Step 1: 检查所有需求均有测试覆盖**

核对并发排空、超时、取消、截断、工作目录和缓存均由自动化测试保护。

- [x] **Step 2: 执行模块验证**

运行：`.\gradlew.bat :shared:jvmTest :shared:compileKotlinJvm`
