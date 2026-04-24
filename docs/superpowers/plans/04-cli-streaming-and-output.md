# CLI Streaming And Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 CLI 做成第一主入口，并以 TypeScript/Bun/OpenTUI 的交互式 TUI 形式稳定消费 runtime 的统一输出；优先完成可独立运行的最小闭环。

**Architecture:** 先新增独立 `cli` 模块，使用 TypeScript + Bun + OpenTUI/React 承载 TUI；再在 `runtime` 与 `cli` 之间定义最小 `stdio` 协议，让 CLI 通过启动本地 runtime 进程并消费事件流完成交互。第一版允许在没有 provider 配置时走 `demo` 模式，先验证协议、子进程桥和展示链路。CLI 不直接依赖 Kotlin 内部类，只依赖本地协议。

**Tech Stack:** Kotlin/JVM runtime, TypeScript, Bun, OpenTUI, React bindings, Gradle, kotlin.test, JUnit 5. 如果后续新增浏览器前端，再默认采用 Tailwind CSS 4 最新稳定版；当前终端 TUI 不引入 Tailwind。

---

## Current First-Pass Update

当前第一批最小实现已经按以下边界落地：

1. `runtime` 新增了 `RuntimeCliProtocol`、`DefaultRuntimeCliService`、`RuntimeCliHost`
2. `runtime` 通过 `installCliHostDist` 生成本地 `stdio` 宿主启动脚本
3. `cli` 已初始化 Bun/OpenTUI 模块，并包含协议层、子进程桥和最小状态驱动 TUI
4. 在没有 provider 配置时，`cli -> runtime` 会走 `demo` 模式返回稳定的 `status/event/result` 序列

因此，后续任务应以“在现有最小闭环上继续扩展真实 streaming 与 richer TUI”为准，而不是回退到重新搭空骨架。

### Task 1: 建立独立的 CLI 模块与运行边界

**Files:**
- Modify: `settings.gradle.kts`
- Create: `cli/package.json`
- Create: `cli/tsconfig.json`
- Create: `cli/src/index.tsx`

- [ ] **Step 1: 保留 Gradle 只管理 Kotlin `runtime`**

确认 `settings.gradle.kts` 仍然只包含：

```kotlin
include(":runtime")
```

- [ ] **Step 2: 初始化独立 `cli` 前端模块**

创建最小 `package.json`，至少包含：

```json
{
  "name": "mulehang-agent-cli",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "bun run src/index.tsx"
  },
  "dependencies": {
    "@opentui/core": "latest",
    "@opentui/react": "latest",
    "react": "latest"
  }
}
```

- [ ] **Step 3: 添加最小 TypeScript 配置**

创建 `tsconfig.json`，至少包含：

```json
{
  "compilerOptions": {
    "lib": ["ESNext", "DOM"],
    "target": "ESNext",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "jsxImportSource": "@opentui/react",
    "strict": true,
    "skipLibCheck": true
  }
}
```

- [ ] **Step 4: 添加最小 OpenTUI 入口**

创建：

```ts
import { createCliRenderer } from "@opentui/core"
import { createRoot } from "@opentui/react"
import { jsx as _jsx } from "@opentui/react/jsx-runtime"

function App() {
  return _jsx("text", { children: "Hello, mulehang-agent CLI" })
}

const renderer = await createCliRenderer({ exitOnCtrlC: true })
createRoot(renderer).render(_jsx(App, {}))
```

- [ ] **Step 5: 运行前端依赖安装与最小启动验证**

Run:

```powershell
bun install
bun run src/index.tsx
```

Expected: 能看到最小 OpenTUI 输出，并可通过 `Ctrl+C` 退出。

### Task 2: 定义 `runtime <-> cli` 的最小 `stdio` 协议

**Files:**
- Create: `runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliProtocol.kt`
- Create: `runtime/src/test/kotlin/com/agent/runtime/cli/RuntimeCliProtocolTest.kt`
- Create: `docs/superpowers/specs/2026-04-24-cli-stdio-protocol-notes.md`

- [ ] **Step 1: 写失败测试**

验证 runtime 侧协议至少能表达：

1. 用户输入请求
2. 流式事件
3. 最终结果
4. 失败响应

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.cli.RuntimeCliProtocolTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

定义：

```kotlin
sealed interface RuntimeCliInboundMessage
sealed interface RuntimeCliOutboundMessage
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.agent.runtime.cli.RuntimeCliProtocolTest
```

Expected: PASS。

### Task 3: 让 CLI 能启动本地 runtime 进程并消费事件流

**Files:**
- Create: `cli/src/runtime-process.ts`
- Create: `cli/src/protocol.ts`
- Create: `cli/src/app.tsx`
- Create: `cli/src/__tests__/runtime-process.test.ts`

- [ ] **Step 1: 写失败测试**

验证 CLI 能：

1. 启动本地 runtime 子进程
2. 向 `stdin` 写入请求
3. 从 `stdout` 连续读取事件

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
bun test
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

实现：

```ts
export class RuntimeProcessClient {}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
bun test
```

Expected: PASS。

### Task 4: 用 OpenTUI/React 实现第一版交互式 TUI

**Files:**
- Modify: `cli/src/app.tsx`
- Create: `cli/src/components/ChatPane.tsx`
- Create: `cli/src/components/InputBar.tsx`
- Create: `cli/src/components/StatusPane.tsx`

- [ ] **Step 1: 写失败测试**

验证第一版界面至少包含：

1. 消息/事件区
2. 输入区
3. 状态区

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
bun test
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

把 `RuntimeEvent`、`RuntimeResult`、`RuntimeFailure` 统一渲染到 TUI。

- [ ] **Step 4: 运行阶段测试**

Run:

```powershell
bun test
.\gradlew.bat build
```

Expected: PASS，且 Kotlin `runtime` 构建不被 CLI 前端破坏。
