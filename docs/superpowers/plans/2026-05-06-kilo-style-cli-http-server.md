# Kilo-Style CLI Shared HTTP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 CLI 从本地 `stdio` 子进程桥接硬切换为 Kilo 风格的共享本地 runtime HTTP server，并统一 CLI 与直接 HTTP 调用入口。

**Architecture:** `runtime` 以 `RuntimeHttpServer` 为唯一真实入口，新增本地 server 元信息、token 认证、健康检查和 SSE 流。`cli` 新增 `ServerManager + RuntimeHttpClient + SseEventClient`，不再消费 `RuntimeCliHost` 的 `stdin/stdout` 协议。最后删除 `runtime/cli` 主链和 `cli` 中的 `runtime-process.ts`/`protocol.ts` 依赖，并同步 `AGENTS.md` 与本地 docs。

**Tech Stack:** Kotlin/JVM, Ktor CIO, kotlinx.serialization, Kotlin test/JUnit 5, TypeScript, Bun, OpenTUI/React, Fetch API, Server-Sent Events.

---

### Task 1: 把 runtime HTTP server 升级为共享本地 server 宿主

**Files:**
- Create: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeServerMetadata.kt`
- Create: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeServerAuth.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpContract.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpServer.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpModule.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/RuntimeHttpModuleTest.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/DefaultRuntimeHttpServiceTest.kt`

- [ ] **Step 1: 写 `/meta` 与 token 认证的失败测试**

在 [RuntimeHttpModuleTest.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/test/kotlin/com/agent/runtime/server/RuntimeHttpModuleTest.kt) 先补两类失败测试：

```kotlin
@Test
fun `should expose runtime metadata for shared local server`() = testApplication {
    application {
        runtimeHttpModule(
            service = FakeRuntimeHttpService.success(),
            metadata = RuntimeServerMetadata(
                service = "mulehang-agent",
                protocolVersion = "2026-05-06",
                serverVersion = "test",
                authMode = "token",
            ),
            auth = RuntimeServerAuth.disabledForTests(),
        )
    }

    val response = client.get("/meta")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("\"protocolVersion\":\"2026-05-06\""))
}

@Test
fun `should reject unauthenticated runtime run request when token auth enabled`() = testApplication {
    application {
        runtimeHttpModule(
            service = FakeRuntimeHttpService.success(),
            metadata = RuntimeServerMetadata(
                service = "mulehang-agent",
                protocolVersion = "2026-05-06",
                serverVersion = "test",
                authMode = "token",
            ),
            auth = RuntimeServerAuth.required("secret-token"),
        )
    }

    val response = client.post("/runtime/run") {
        contentType(ContentType.Application.Json)
        setBody(
            """
            {
              "sessionId": "session-1",
              "prompt": "hello",
              "provider": {
                "providerId": "demo",
                "providerType": "OPENAI",
                "baseUrl": "http://localhost",
                "apiKey": "x",
                "modelId": "demo-model"
              }
            }
            """.trimIndent(),
        )
    }

    assertEquals(HttpStatusCode.Unauthorized, response.status)
}
```

- [ ] **Step 2: 运行 server 模块测试，确认新增用例先失败**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.RuntimeHttpModuleTest
```

Expected: FAIL，提示 `/meta` 未实现或 token 校验行为不匹配。

- [ ] **Step 3: 增加本地 server 元信息与认证实现**

先补生产代码骨架，新增 [RuntimeServerMetadata.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeServerMetadata.kt) 和 [RuntimeServerAuth.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeServerAuth.kt)：

```kotlin
@Serializable
data class RuntimeServerMetadata(
    val service: String,
    val protocolVersion: String,
    val serverVersion: String,
    val authMode: String,
)

class RuntimeServerAuth private constructor(
    private val requiredToken: String?,
) {
    fun isAuthorized(token: String?): Boolean = requiredToken == null || token == requiredToken

    companion object {
        fun required(token: String): RuntimeServerAuth = RuntimeServerAuth(token)
        fun disabledForTests(): RuntimeServerAuth = RuntimeServerAuth(null)
    }
}
```

再把 [RuntimeHttpContract.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpContract.kt) 里的健康返回扩成稳定元信息载荷：

```kotlin
@Serializable
data class HealthPayload(
    val healthy: Boolean,
    val service: String,
    val protocolVersion: String,
)
```

- [ ] **Step 4: 把 `/health`、`/meta` 和 token 校验接到模块入口**

修改 [RuntimeHttpModule.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpModule.kt) 和 [RuntimeHttpServer.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpServer.kt)，让模块支持 metadata + auth：

```kotlin
fun Application.runtimeHttpModule(
    service: RuntimeHttpService = LoggingRuntimeHttpService(DefaultRuntimeHttpService()),
    metadata: RuntimeServerMetadata = RuntimeServerMetadata(
        service = "mulehang-agent",
        protocolVersion = "2026-05-06",
        serverVersion = "dev",
        authMode = "disabled",
    ),
    auth: RuntimeServerAuth = RuntimeServerAuth.disabledForTests(),
) {
    routing {
        get("/health") {
            call.respond(Result.success(HealthPayload(true, metadata.service, metadata.protocolVersion)))
        }

        get("/meta") {
            call.respond(Result.success(metadata))
        }

        post("/runtime/run") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
            if (!auth.isAuthorized(token)) {
                call.respond(HttpStatusCode.Unauthorized, Result.fail("Unauthorized.", RuntimeRunPayload(requestId = "unauthorized")))
                return@post
            }
            // existing run flow
        }
    }
}
```

- [ ] **Step 5: 运行定向测试，确认 metadata/auth 通过**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.RuntimeHttpModuleTest --tests com.agent.runtime.server.DefaultRuntimeHttpServiceTest
```

Expected: PASS。

- [ ] **Step 6: 如已获用户明确授权才提交**

本仓库规则要求未获用户明确授权不得提交。本任务默认不执行 `git commit`。

### Task 2: 为 runtime HTTP server 增加 SSE 事件流

**Files:**
- Create: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeSseEvent.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpService.kt`
- Modify: `runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpModule.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/DefaultRuntimeHttpServiceTest.kt`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/RuntimeHttpModuleTest.kt`

- [ ] **Step 1: 写 SSE 事件流的失败测试**

在 [DefaultRuntimeHttpServiceTest.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/test/kotlin/com/agent/runtime/server/DefaultRuntimeHttpServiceTest.kt) 先增加执行链到事件流的测试：

```kotlin
@Test
fun `should expose status event and final result as sse frames`() = runTest {
    val service = DefaultRuntimeHttpService(
        runtimeAgentExecutor = FakeRuntimeAgentExecutor.success(
            events = listOf(
                RuntimeEvent(message = "agent.reasoning.delta", payload = JsonPrimitive("thinking")),
                RuntimeEvent(message = "agent.output.delta", payload = JsonPrimitive("hello")),
            ),
            output = JsonPrimitive("hello"),
        ),
    )

    val stream = service.stream(
        RuntimeRunHttpRequest(
            sessionId = "session-1",
            prompt = "hello",
            provider = demoProviderRequest(),
        ),
    ).toList()

    assertEquals("status", stream.first().event)
    assertEquals("run.completed", stream.last().event)
}
```

在 [RuntimeHttpModuleTest.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/test/kotlin/com/agent/runtime/server/RuntimeHttpModuleTest.kt) 增加路由级别测试：

```kotlin
@Test
fun `should stream runtime events through sse route`() = testApplication {
    application {
        runtimeHttpModule(
            service = FakeRuntimeHttpService.streaming(),
            metadata = testMetadata(),
            auth = RuntimeServerAuth.disabledForTests(),
        )
    }

    val response = client.post("/runtime/run/stream") {
        contentType(ContentType.Application.Json)
        setBody(validRunRequestJson())
    }

    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals("text/event-stream", response.headers[HttpHeaders.ContentType]?.substringBefore(";"))
}
```

- [ ] **Step 2: 运行 SSE 定向测试，确认先失败**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.DefaultRuntimeHttpServiceTest --tests com.agent.runtime.server.RuntimeHttpModuleTest
```

Expected: FAIL，提示 `stream()` 未定义或 `/runtime/run/stream` 路由不存在。

- [ ] **Step 3: 定义稳定的 SSE 事件模型**

新增 [RuntimeSseEvent.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeSseEvent.kt)：

```kotlin
@Serializable
data class RuntimeSseEvent(
    val event: String,
    val sessionId: String,
    val requestId: String,
    val channel: String? = null,
    val message: String? = null,
    val delta: String? = null,
    val output: JsonElement? = null,
    val failureKind: String? = null,
)
```

并扩展 [RuntimeHttpService.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpService.kt)：

```kotlin
interface RuntimeHttpService {
    suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload>
    fun stream(request: RuntimeRunHttpRequest): Flow<RuntimeSseEvent>
}
```

- [ ] **Step 4: 在 HTTP module 中输出 `text/event-stream`**

修改 [RuntimeHttpModule.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpModule.kt)，新增 SSE 路由：

```kotlin
post("/runtime/run/stream") {
    val request = call.receive<RuntimeRunHttpRequest>()
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondTextWriter(ContentType.Text.EventStream) {
        service.stream(request).collect { event ->
            write("event: ${event.event}\n")
            write("data: ${runtimeHttpModuleJson.encodeToString(RuntimeSseEvent.serializer(), event)}\n\n")
            flush()
        }
    }
}
```

并在 `DefaultRuntimeHttpService.stream()` 中先映射已有 `RuntimeSuccess` / `RuntimeFailed`：

```kotlin
emit(RuntimeSseEvent(event = "status", sessionId = sessionId, requestId = requestId, message = "run.started"))
result.events.forEach { event ->
    emit(
        RuntimeSseEvent(
            event = if (event.message.contains("reasoning")) "thinking.delta" else "text.delta",
            sessionId = sessionId,
            requestId = requestId,
            channel = if (event.message.contains("reasoning")) "thinking" else "text",
            delta = event.payload?.jsonPrimitive?.contentOrNull,
            message = event.message,
        ),
    )
}
emit(RuntimeSseEvent(event = "run.completed", sessionId = sessionId, requestId = requestId, output = result.output))
```

- [ ] **Step 5: 运行 SSE 测试与全量 runtime 测试**

Run:

```powershell
.\gradlew.bat :runtime:test --tests com.agent.runtime.server.DefaultRuntimeHttpServiceTest --tests com.agent.runtime.server.RuntimeHttpModuleTest
.\gradlew.bat :runtime:test
```

Expected: PASS。

- [ ] **Step 6: 如已获用户明确授权才提交**

本任务默认不执行 `git commit`。

### Task 3: 在 CLI 中引入 ServerManager 和 HTTP/SSE client

**Files:**
- Create: `cli/src/runtime-server-manager.ts`
- Create: `cli/src/runtime-http-client.ts`
- Create: `cli/src/runtime-events.ts`
- Create: `cli/src/__tests__/runtime-server-manager.test.ts`
- Create: `cli/src/__tests__/runtime-http-client.test.ts`
- Modify: `cli/src/runtime-request.ts`

- [ ] **Step 1: 写共享 server 发现与启动的失败测试**

新增 [runtime-server-manager.test.ts](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/__tests__/runtime-server-manager.test.ts)：

```ts
test("reuses healthy shared server from state file", async () => {
  const calls: string[] = [];
  const manager = new RuntimeServerManager({
    readState: async () => ({
      port: 8091,
      pid: 123,
      token: "secret",
      protocolVersion: "2026-05-06",
      serverVersion: "test",
      authMode: "token",
    }),
    fetchMeta: async (baseUrl) => {
      calls.push(baseUrl);
      return {
        service: "mulehang-agent",
        protocolVersion: "2026-05-06",
        serverVersion: "test",
        authMode: "token",
      };
    },
    spawnServer: async () => {
      throw new Error("should not spawn");
    },
  });

  const server = await manager.ensureServer();
  expect(server.baseUrl).toBe("http://127.0.0.1:8091");
  expect(calls).toEqual(["http://127.0.0.1:8091"]);
});
```

- [ ] **Step 2: 运行 CLI 定向测试，确认先失败**

Run:

```powershell
Push-Location .\cli
bun test src\__tests__\runtime-server-manager.test.ts
Pop-Location
```

Expected: FAIL，提示 `RuntimeServerManager` 不存在。

- [ ] **Step 3: 实现共享 server 管理与运行请求客户端**

新增 [runtime-server-manager.ts](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/runtime-server-manager.ts)：

```ts
export interface RuntimeServerState {
  pid: number;
  port: number;
  token?: string;
  protocolVersion: string;
  serverVersion: string;
  authMode: "token" | "disabled";
}

export class RuntimeServerManager {
  constructor(private readonly ops: RuntimeServerManagerOps = defaultOps) {}

  async ensureServer(): Promise<{ baseUrl: string; token?: string }> {
    const state = await this.ops.readState();
    if (state) {
      const baseUrl = `http://127.0.0.1:${state.port}`;
      await this.ops.fetchMeta(baseUrl, state.token);
      return { baseUrl, token: state.token };
    }
    return this.ops.spawnServer();
  }
}
```

新增 [runtime-http-client.ts](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/runtime-http-client.ts)：

```ts
export class RuntimeHttpClient {
  constructor(
    private readonly baseUrl: string,
    private readonly token?: string,
    private readonly impl: typeof fetch = fetch,
  ) {}

  async run(request: RuntimeHttpRunRequest): Promise<Response> {
    return this.impl(`${this.baseUrl}/runtime/run`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
      },
      body: JSON.stringify(request),
    });
  }
}
```

- [ ] **Step 4: 新增 SSE 事件消费测试与实现**

补 [runtime-http-client.test.ts](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/__tests__/runtime-http-client.test.ts)：

```ts
test("parses sse run stream into runtime events", async () => {
  const text =
    "event: status\n" +
    "data: {\"event\":\"status\",\"sessionId\":\"session-1\",\"requestId\":\"request-1\",\"message\":\"run.started\"}\n\n" +
    "event: run.completed\n" +
    "data: {\"event\":\"run.completed\",\"sessionId\":\"session-1\",\"requestId\":\"request-1\",\"output\":\"hello\"}\n\n";

  const events = await collectRuntimeSse(new Response(text, {
    headers: { "Content-Type": "text/event-stream" },
  }));

  expect(events.map((item) => item.event)).toEqual(["status", "run.completed"]);
});
```

新增 [runtime-events.ts](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/runtime-events.ts)：

```ts
export async function collectRuntimeSse(response: Response): Promise<RuntimeSseEvent[]> {
  const text = await response.text();
  return text
    .split("\n\n")
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk) => {
      const data = chunk.split("\n").find((line) => line.startsWith("data: "));
      if (!data) throw new Error(`Missing sse data frame: ${chunk}`);
      return JSON.parse(data.slice("data: ".length)) as RuntimeSseEvent;
    });
}
```

- [ ] **Step 5: 运行 CLI 定向测试与 typecheck**

Run:

```powershell
Push-Location .\cli
bun test src\__tests__\runtime-server-manager.test.ts src\__tests__\runtime-http-client.test.ts
bun run typecheck
Pop-Location
```

Expected: PASS。

- [ ] **Step 6: 如已获用户明确授权才提交**

本任务默认不执行 `git commit`。

### Task 4: 用 HTTP/SSE client 接管 OpenTUI 会话流

**Files:**
- Modify: `cli/src/app.tsx`
- Modify: `cli/src/app-state.ts`
- Modify: `cli/src/runtime-request.ts`
- Delete: `cli/src/runtime-process.ts`
- Delete: `cli/src/protocol.ts`
- Test: `cli/src/__tests__/app-state.test.ts`
- Test: `cli/src/__tests__/app.test.ts`
- Test: `cli/src/__tests__/runtime-request.test.ts`

- [ ] **Step 1: 写 UI 状态映射的失败测试**

在 [app-state.test.ts](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/__tests__/app-state.test.ts) 先加 SSE 事件映射测试：

```ts
test("maps sse thinking and completion events into transcript", () => {
  const started = applyRuntimeSseEvent(createInitialAppState(), {
    event: "status",
    sessionId: "session-1",
    requestId: "request-1",
    message: "run.started",
  });

  const thinking = applyRuntimeSseEvent(started, {
    event: "thinking.delta",
    sessionId: "session-1",
    requestId: "request-1",
    channel: "thinking",
    delta: "Inspecting provider",
  });

  const completed = applyRuntimeSseEvent(thinking, {
    event: "run.completed",
    sessionId: "session-1",
    requestId: "request-1",
    output: "done",
  });

  expect(completed.transcript.some((entry) => entry.kind === "thinking")).toBe(true);
  expect(completed.runtime.phase).toBe("completed");
});
```

- [ ] **Step 2: 运行定向测试，确认先失败**

Run:

```powershell
Push-Location .\cli
bun test src\__tests__\app-state.test.ts src\__tests__\app.test.ts
Pop-Location
```

Expected: FAIL，提示 `applyRuntimeSseEvent` 不存在或 `app.tsx` 仍依赖 `RuntimeProcessClient`。

- [ ] **Step 3: 在状态层引入 SSE 事件映射**

修改 [app-state.ts](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/app-state.ts)，新增稳定入口：

```ts
export function applyRuntimeSseEvent(
  state: AppState,
  event: RuntimeSseEvent,
): AppState {
  switch (event.event) {
    case "status":
      return {
        ...state,
        runtime: {
          ...state.runtime,
          phase: event.message === "run.started" ? "running" : state.runtime.phase,
          sessionId: event.sessionId,
          requestId: event.requestId,
          detail: event.message,
        },
      };
    case "thinking.delta":
      return applyRuntimeEventToTranscript(state, {
        channel: "thinking",
        delta: event.delta,
      });
    case "text.delta":
      return applyRuntimeEventToTranscript(state, {
        channel: "text",
        delta: event.delta,
      });
    case "run.completed":
      return {
        ...state,
        runtime: {
          phase: "completed",
          mode: state.runtime.mode,
          sessionId: event.sessionId,
          requestId: event.requestId,
          detail: "completed",
        },
      };
    default:
      return state;
  }
}
```

- [ ] **Step 4: 在 `app.tsx` 用 ServerManager + HTTP client 替换 stdio 子进程**

修改 [app.tsx](D:/JetBrains/projects/idea_projects/mulehang-agent/cli/src/app.tsx)，替换启动链：

```ts
const serverRef = useRef<{ baseUrl: string; token?: string } | null>(null);

useEffect(() => {
  const manager = new RuntimeServerManager();
  void manager.ensureServer()
    .then((server) => {
      serverRef.current = server;
    })
    .catch((error) => {
      setState((previous) =>
        appendSystemMessage(previous, error instanceof Error ? error.message : String(error)),
      );
    });
}, []);

const submitPrompt = (rawValue?: string) => {
  const prompt = resolveSubmittedPrompt(rawValue, draft);
  if (!prompt || serverRef.current == null) return;

  const client = new RuntimeHttpClient(serverRef.current.baseUrl, serverRef.current.token);
  void client.run(createRuntimeRunRequest(prompt, state.runtime.sessionId))
    .then(collectRuntimeSse)
    .then((events) => {
      setState((previous) => events.reduce(applyRuntimeSseEvent, previous));
    });
};
```

- [ ] **Step 5: 删除旧 stdio 入口并跑全量 CLI 验证**

删除：

```text
cli/src/runtime-process.ts
cli/src/protocol.ts
```

Run:

```powershell
Push-Location .\cli
bun test
bun run typecheck
Pop-Location
```

Expected: PASS。

- [ ] **Step 6: 如已获用户明确授权才提交**

本任务默认不执行 `git commit`。

### Task 5: 删除 runtime `stdio` 主链并同步本地文档

**Files:**
- Delete: `runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliHost.kt`
- Delete: `runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliProtocol.kt`
- Delete: `runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliService.kt`
- Modify: `runtime/build.gradle.kts`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
- Modify: `docs/superpowers/specs/04-cli-streaming-and-output.md`
- Modify: `docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md`
- Modify: `docs/superpowers/plans/04-cli-streaming-and-output.md`
- Test: `runtime/src/test/kotlin/com/agent/runtime/server/LoggingBackendConfigurationTest.kt`

- [ ] **Step 1: 写删除 `stdio` 主链后的失败测试或检查**

先在 [LoggingBackendConfigurationTest.kt](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/src/test/kotlin/com/agent/runtime/server/LoggingBackendConfigurationTest.kt) 收紧断言，移除 CLI host 专用 logback 的长期依赖：

```kotlin
@Test
fun `should keep default console logging on standard output`() {
    val logbackXml = readResource("logback.xml")
    assertFalse("<target>System.err</target>" in logbackXml)
}
```

并在文档更新前用搜索确认现存 `stdio` 文案：

```powershell
Get-ChildItem docs\superpowers\specs,docs\superpowers\plans -Recurse -Filter *.md |
  Select-String -Pattern "stdio"
```

Expected: 仍能搜到 `stdio` 相关旧文案。

- [ ] **Step 2: 删除 runtime `stdio` 入口与构建分发**

修改 [runtime/build.gradle.kts](D:/JetBrains/projects/idea_projects/mulehang-agent/runtime/build.gradle.kts)，删除：

```kotlin
val cliHostMainClass = "com.agent.runtime.cli.RuntimeCliHostKt"
tasks.register<Sync>("installCliHostDist") { ... }
tasks.register<JavaExec>("runCliHost") { ... }
```

同时删除：

```text
runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliHost.kt
runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliProtocol.kt
runtime/src/main/kotlin/com/agent/runtime/cli/RuntimeCliService.kt
```

- [ ] **Step 3: 同步 `AGENTS.md` 与本地 docs**

把以下表述全部改写为 Kilo 风格 shared local server：

```md
- CLI 主链不再通过 `:runtime:installCliHostDist`
- `cli` 连接 shared local runtime HTTP server
- `runtime` 的正式主入口是 HTTP + SSE
- `stdio` 只作为历史实现，不再写入当前架构说明
```

至少要修改：

```text
AGENTS.md
docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md
docs/superpowers/specs/04-cli-streaming-and-output.md
docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md
docs/superpowers/plans/04-cli-streaming-and-output.md
```

- [ ] **Step 4: 运行全量验证与问题检查**

Run:

```powershell
.\gradlew.bat :runtime:test
Push-Location .\cli
bun test
bun run typecheck
Pop-Location
```

然后对以下文件跑 IDEA inspection：

```text
runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpServer.kt
runtime/src/main/kotlin/com/agent/runtime/server/RuntimeHttpModule.kt
cli/src/app.tsx
AGENTS.md
docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md
docs/superpowers/specs/04-cli-streaming-and-output.md
docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md
docs/superpowers/plans/04-cli-streaming-and-output.md
```

Expected: no errors, no warnings；weak warning 可忽略。

- [ ] **Step 5: 如已获用户明确授权才提交**

本任务默认不执行 `git commit`。

## Self-Review

- Spec coverage:
  - shared local server + status file + token：Task 1, Task 3
  - HTTP + SSE 主协议：Task 2, Task 4
  - CLI 不再依赖 `RuntimeCliHost`：Task 4, Task 5
  - 删除 `stdio` 主链：Task 5
  - 同步 `AGENTS.md` 与本地 docs：Task 5
- Placeholder scan:
  - 没有 `TODO`、`TBD` 或“后续补充”占位语
  - 每个任务都给出实际文件路径、测试片段和命令
- Type consistency:
  - 协议版本统一使用 `2026-05-06`
  - `RuntimeServerMetadata` / `RuntimeServerAuth` / `RuntimeSseEvent` / `RuntimeServerManager` / `RuntimeHttpClient` 命名在任务间保持一致

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-06-kilo-style-cli-http-server.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - 我按 task 分派独立 worker，逐任务 review 后再合并。

**2. Inline Execution** - 我在当前会话里直接按这个 plan 执行，边做边验证。

选一个。默认推荐 `1`。
