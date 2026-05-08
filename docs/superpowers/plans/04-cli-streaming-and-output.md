# CLI Streaming And Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 CLI 做成第一主入口，并以 TypeScript/Bun/OpenTUI 的交互式 TUI 形式通过 shared local runtime HTTP server 稳定消费 runtime 的统一输出。

**Architecture:** `runtime` 以 `RuntimeHttpServer` 作为正式入口，负责 `/meta`、`/runtime/run` 和 `/runtime/run/stream`。`cli` 通过 `RuntimeServerManager` 发现或拉起 shared local server，再用 `RuntimeHttpClient` 通过 HTTP + SSE 发起请求、消费事件，并更新 TUI 状态。`stdio` 子进程桥接不再是当前主线。

**Tech Stack:** Kotlin/JVM runtime, Ktor server, TypeScript, Bun, OpenTUI, React bindings, Gradle, kotlin.test, JUnit 5。

---

## Current Baseline

当前阶段应以 shared local server 主链为准：

1. `runtime` 对外暴露本地 HTTP/SSE 协议
2. `cli` 通过状态文件 + `/meta` 探针复用共享 server
3. `cli` 通过 HTTP POST 和 SSE 消费事件流
4. `runtime-process.ts`、`protocol.ts`、`RuntimeCliHost` 仅属于已删除的历史方案

后续工作不应再扩展 `stdio` 主链，而应继续收敛 shared local server、事件映射和文档一致性。

## Task 1: 升级 shared local runtime server 宿主

**Files:**
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpServer.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpModule.kt`
- Create: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpServerConfiguration.kt`
- Create: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeServerMetadata.kt`
- Create: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeServerAuth.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/RuntimeHttpServerConfigurationTest.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/RuntimeHttpModuleTest.kt`

- [ ] **Step 1: 先写配置与 `/meta` 探针测试**

覆盖：

1. host / port / token 读取
2. metadata 返回 `service`、`protocolVersion`、`serverVersion`、`authMode`
3. token 开启时的认证行为

- [ ] **Step 2: 运行定向测试确认先失败**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.RuntimeHttpServerConfigurationTest --tests com.agent.runtime.server.RuntimeHttpModuleTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

实现：

1. `RuntimeHttpServerConfiguration`
2. `RuntimeServerMetadata`
3. `RuntimeServerAuth`
4. `/meta` 路由

- [ ] **Step 4: 重新运行定向测试**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.RuntimeHttpServerConfigurationTest --tests com.agent.runtime.server.RuntimeHttpModuleTest
```

Expected: PASS。

## Task 2: 为 runtime HTTP server 增加 SSE 事件流

**Files:**
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpContract.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpService.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/LoggingRuntimeHttpService.kt`
- Create: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeSseEvent.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/DefaultRuntimeHttpServiceTest.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/LoggingRuntimeHttpServiceTest.kt`

- [ ] **Step 1: 先写 SSE 事件测试**

至少覆盖：

1. `status`
2. `thinking.delta`
3. `text.delta`
4. `run.completed`
5. `run.failed`

- [ ] **Step 2: 运行定向测试确认先失败**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.DefaultRuntimeHttpServiceTest --tests com.agent.runtime.server.LoggingRuntimeHttpServiceTest
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

要求：

1. `/runtime/run/stream` 返回 `text/event-stream`
2. 事件名和 JSON 数据都可被 CLI 增量消费
3. 失败路径也返回结构化事件

- [ ] **Step 4: 重新运行定向测试**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.DefaultRuntimeHttpServiceTest --tests com.agent.runtime.server.LoggingRuntimeHttpServiceTest
```

Expected: PASS。

## Task 3: 为 CLI 接入 shared local server manager 与 HTTP client

**Files:**
- Create: `cli/src/runtime-server-manager.ts`
- Create: `cli/src/runtime-http-client.ts`
- Create: `cli/src/runtime-events.ts`
- Modify: `cli/src/runtime-request.ts`
- Test: `cli/src/__tests__/runtime-server-manager.test.ts`
- Test: `cli/src/__tests__/runtime-http-client.test.ts`
- Test: `cli/src/__tests__/runtime-request.test.ts`

- [ ] **Step 1: 先写 CLI 侧失败测试**

覆盖：

1. 复用已有健康 server
2. 无健康 server 时拉起新实例并写状态文件
3. HTTP 请求头携带本地 token
4. SSE 帧被增量映射为 CLI 可消费消息

- [ ] **Step 2: 运行定向测试确认先失败**

Run:

```powershell
Push-Location .\cli
bun test src\__tests__\runtime-server-manager.test.ts src\__tests__\runtime-http-client.test.ts src\__tests__\runtime-request.test.ts
Pop-Location
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

实现：

1. `RuntimeServerManager`
2. `RuntimeHttpClient`
3. `RuntimeMessage` / value formatting
4. 最小 `RuntimeRunRequest`

- [ ] **Step 4: 重新运行定向测试与 typecheck**

Run:

```powershell
Push-Location .\cli
bun test src\__tests__\runtime-server-manager.test.ts src\__tests__\runtime-http-client.test.ts src\__tests__\runtime-request.test.ts
bun run typecheck
Pop-Location
```

Expected: PASS。

## Task 4: 用 shared local server client 接管 OpenTUI 会话流

**Files:**
- Modify: `cli/src/app.tsx`
- Modify: `cli/src/app-state.ts`
- Test: `cli/src/__tests__/app-state.test.ts`
- Test: `cli/src/__tests__/app.test.ts`

- [ ] **Step 1: 先写状态映射与提交链路测试**

至少覆盖：

1. `run.started` 更新状态
2. thinking delta 归并到可折叠 transcript block
3. text delta 归并到 assistant transcript
4. duplicate final output 不重复追加
5. submit 时走 `RuntimeHttpClient.send()`

- [ ] **Step 2: 运行定向测试确认先失败**

Run:

```powershell
Push-Location .\cli
bun test src\__tests__\app-state.test.ts src\__tests__\app.test.ts
Pop-Location
```

Expected: FAIL。

- [ ] **Step 3: 写最小实现**

要求：

1. 启动时创建 `RuntimeServerManager` + `RuntimeHttpClient`
2. 请求通过 `createRuntimeRunRequest()` 构造
3. SSE 映射后的消息进入 `app-state`
4. 用户可见错误通过 transcript/system message 暴露

- [ ] **Step 4: 重新运行 CLI 全量验证**

Run:

```powershell
Push-Location .\cli
bun test
bun run typecheck
Pop-Location
```

Expected: PASS。

## Task 5: 删除旧 stdio 主链并同步文档

**Files:**
- Delete: `runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliHost.kt`
- Delete: `runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliProtocol.kt`
- Delete: `runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliService.kt`
- Delete: `cli/src/runtime-process.ts`
- Delete: `cli/src/protocol.ts`
- Modify: `runtime/build.gradle.kts`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
- Modify: `docs/superpowers/specs/04-cli-streaming-and-output.md`
- Modify: `docs/superpowers/plans/04-cli-streaming-and-output.md`
- Modify: `docs/CLI_LOGIC_RELATIONSHIPS.md`
- Modify: `docs/MAIN_LOGIC_RELATIONSHIPS.md`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/LoggingBackendConfigurationTest.kt`

- [ ] **Step 1: 先写删除主链后的回归检查**

至少确认：

1. `logback-cli-host.xml` 不再存在
2. 文档主叙事不再把 `stdio` 作为 CLI 正式主链
3. `runtime/build.gradle.kts` 只保留 runtime HTTP server 分发入口

- [ ] **Step 2: 删除旧入口并同步文档**

同步后的文档必须明确：

1. CLI 连接 shared local runtime HTTP server
2. 正式协议是 HTTP + SSE
3. `:runtime:installDist` 生成的是 runtime server 分发，而不是 `cli host`
4. `stdio` 只保留为历史背景，不再作为当前架构说明

- [ ] **Step 3: 运行全量验证**

Run:

```powershell
.\gradlew.bat :runtime:test
Push-Location .\cli
bun test
bun run typecheck
Pop-Location
```

Expected: PASS。

- [ ] **Step 4: 对修改文件运行问题检查**

至少检查：

```text
runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpServer.kt
runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpModule.kt
cli/src/app.tsx
docs/superpowers/specs/04-cli-streaming-and-output.md
docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md
docs/superpowers/plans/04-cli-streaming-and-output.md
docs/CLI_LOGIC_RELATIONSHIPS.md
docs/MAIN_LOGIC_RELATIONSHIPS.md
AGENTS.md
```

Expected: no errors；warning 需要人工判断是否可接受。

## Self-Review

- Spec coverage:
  - shared local server + metadata + auth：Task 1
  - HTTP + SSE：Task 2
  - CLI server manager + client：Task 3
  - OpenTUI 状态映射：Task 4
  - 删除旧 `stdio` 主链与文档同步：Task 5
- Placeholder scan:
  - 没有 `TODO`、`TBD` 或“后续补充”占位语
  - 所有任务都给出实际文件路径与验证命令
- Type consistency:
  - shared local server
  - HTTP + SSE
  - `RuntimeServerManager`
  - `RuntimeHttpClient`
  - `RuntimeMessage`
