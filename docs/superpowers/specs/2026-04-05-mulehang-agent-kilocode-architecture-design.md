# 参考 Kilocode 的 Mulehang Agent 架构设计

## 目标

为 `mulehang-agent` 设计一套真正可用、可扩展、可演进的 Kotlin Agent 架构。该架构参考 `kilocode` 的产品分层与运行时思路，但不照搬 TypeScript 实现，而是结合 Koog 的原生能力，在 Kotlin/JVM 上落地以下目标：

1. 完整 BYOK，支持自定义 `baseUrl + apiKey + model`。
2. 完整 ACP 支持。
3. MCP 接入与生命周期管理。
4. Agent Skill 系统。
5. Harness 风格的 subagent / task delegation / resume / fork。
6. CLI、ACP、HTTP API 共用同一运行时，而不是三套平行实现。
7. 保留足够清晰的边界，便于后续扩展到 IDE 宿主或前端。

## 本轮范围

本轮设计覆盖：

- 核心引擎
- CLI
- ACP Server
- HTTP API
- MCP
- Skill
- Harness / Subagent
- 持久化、权限、可观测性

本轮设计不覆盖：

- JetBrains 插件 UI
- Web 前端
- 多租户云端控制平面
- 商业化账户系统

## 设计结论

推荐采用 `Kilo-like Runtime over Koog` 的方案：

- `Koog` 负责执行内核和已经成熟的通用能力。
- `mulehang-agent` 自己实现产品级运行时语义。
- 所有入口都挂在统一 runtime 上。
- ACP、HTTP、CLI 只是适配层，不拥有自己的业务语义。

这是比“直接用 Koog 拼一个 Agent”更稳的方案。原因是：`kilocode` 真正可用的关键不在单个 Agent，而在 provider/session/tool/permission/protocol/harness 这几层分离明确、可独立演进。

---

## 一、Kilocode 架构拆解

### 1.1 它不是一个单体 Agent，而是一个产品运行时

从 `kilocode` 代码结构看，核心不是某个 prompt 或某个 tool，而是完整运行时：

- `provider`
  - provider catalog
  - auth
  - model capability
  - base URL / custom provider 适配
- `session`
  - 会话、消息、part、summary、retry、revert、todo
- `tool`
  - tool registry
  - built-in tools
  - plugin tools
  - task/subagent tool
- `permission`
  - allow / ask / deny
  - persisted approval
  - session-scoped permission
- `server`
  - HTTP API
  - SSE
  - OpenAPI
  - WebSocket
- `acp`
  - ACP session mapping
  - event conversion
  - permission bridge
- `skill`
  - skill discovery
  - project/global/remote skill loading
- `mcp`
  - MCP connection
  - OAuth lifecycle
  - routing into tool space

换句话说，`kilocode` 的产品核心是：

`统一状态 + 统一权限 + 统一工具语义 + 多入口协议适配`

### 1.2 Kilocode 最值得借鉴的实现原则

#### 原则 A：入口很多，但运行时只有一套

CLI、HTTP、ACP 都不直接操作模型，而是经过统一 session/runtime。

#### 原则 B：协议不是业务中心，session 才是业务中心

ACP 和 HTTP 都只是把外部请求映射到内部 session 生命周期。

#### 原则 C：Tool 不是 SDK 细节，而是产品能力目录

每个 tool 都有稳定 id、输入输出形态、权限语义、审计语义。

#### 原则 D：Subagent 不是简单递归调用

真正关键的是：

- 子任务拥有独立 session
- 可限制权限继承
- 可 resume
- 可 fork
- 可作为上层任务的受控执行单元

#### 原则 E：Provider 层必须产品化

真正支持 BYOK 时，不能把 `baseUrl` 和 `apiKey` 散落在调用点。必须有 provider registry、model catalog、capability catalog。

### 1.3 Kilocode 对 Mulehang 的直接启发

对于 `mulehang-agent`，最该学习的不是“它有哪些工具”，而是：

1. 为什么它能让 CLI / ACP / HTTP 共享一套语义。
2. 为什么 provider 可以抽象到统一层。
3. 为什么 permission 是独立引擎，不是某个协议回调。
4. 为什么 harness 可以在 session 层上安全地创建 subagent。

---

## 二、Koog 能提供什么，不能提供什么

## 2.1 已确认可复用的 Koog 能力

基于官方文档与公开发布资料，Koog 当前可提供：

- Agent execution
- strategy graph / planner 能力
- tool calling
- ACP 集成
- MCP 集成
- history compression
- persistence
- streaming 与 tool event
- tracing
- agent-as-tool

参考：

- `https://docs.koog.ai/agent-client-protocol/`
- `https://docs.koog.ai/model-context-protocol/`
- `https://docs.koog.ai/history-compression/`
- `https://docs.koog.ai/tracing/`
- `https://docs.koog.ai/tools-overview/`

## 2.2 不能直接交给 Koog 的产品能力

Koog 是框架，不是完整产品 runtime。下面这些能力仍应由 `mulehang-agent` 自己实现：

- 完整 BYOK 配置系统
- provider/model registry
- 会话存储与审计模型
- 权限审批引擎
- HTTP API 与 SDK
- skill discovery / 安装 / 作用域
- harness / subagent orchestration
- 成本与运行时指标归档

## 2.3 Koog 版本现实约束

当前项目依赖：

- `ai.koog:koog-agents:0.7.3`

已确认公开仓库与文档在 2026 年 3 月仍持续快速迭代。在线文档并非严格版本冻结，因此总计划必须包含一个 `Koog 0.7.3 兼容性探针` 阶段，用真实编译与最小运行样例确认以下 API 在项目中可用：

- ACP feature
- MCP integration
- persistence
- createAgentTool / subtask / planner
- streaming tool events
- provider executor 自定义能力

这个阶段不是额外负担，而是避免后续方案按文档设计、实现时撞 API 变动。

---

## 三、Mulehang Agent 总体架构

## 3.1 推荐总体结构

建议逐步演进为多模块结构：

- `mulehang-agent-app`
  - 启动装配
  - CLI / ACP / HTTP 入口
- `mulehang-agent-config`
  - 全局配置
  - 项目配置
  - provider/mcp/skill/agent profile 配置
- `mulehang-agent-provider`
  - BYOK
  - provider registry
  - model registry
  - capability catalog
  - executor factory
- `mulehang-agent-runtime`
  - session
  - message
  - turn
  - event bus
  - persistence
  - compression
- `mulehang-agent-tooling`
  - local tools
  - MCP tools
  - Koog tool adapters
  - tool execution policy
- `mulehang-agent-permission`
  - allow / ask / deny
  - persisted approvals
  - session overrides
- `mulehang-agent-skill`
  - skill discovery
  - skill manifest
  - prompt/resource injection
- `mulehang-agent-acp`
  - ACP adapter
  - session bridge
  - event bridge
- `mulehang-agent-http`
  - HTTP API
  - SSE
  - OpenAPI
  - SDK generation input
- `mulehang-agent-harness`
  - agent profile
  - subagent session
  - task delegation
  - resume/fork lineage
- `mulehang-agent-observability`
  - tracing
  - token/cost
  - audit log
  - run timeline

如果短期内不拆 Gradle 多模块，至少也要在 `src/main/kotlin` 先按相同包边界拆开，避免继续把所有逻辑堆在 `agent/` 下。

## 3.2 核心依赖方向

依赖应该单向流动：

- `app -> http/acp/cli -> runtime`
- `runtime -> provider/tooling/permission/skill/observability`
- `tooling -> provider/permission`
- `harness -> runtime/tooling/permission`
- `http/acp` 不直接依赖底层 Koog API 细节，只依赖 runtime service

禁止反向依赖：

- protocol 层直接操作数据库
- protocol 层直接构造 Koog agent
- tool 直接操作配置存储
- skill 直接绕过 runtime 修改 session

---

## 四、执行模型

## 4.1 四层执行链路

推荐执行链路：

`入口 -> 编排 -> 执行 -> 回写`

### 入口层

负责接受外部输入：

- CLI command
- ACP prompt
- HTTP request

入口层只负责：

- 参数解析
- session 标识
- 调用 runtime service
- 把 runtime event 映射回协议输出

### 编排层

核心服务为 `SessionService` 或 `RunService`。负责：

- 打开 turn
- 解析 agent profile
- 合并 skill 上下文
- 装配可用 tools
- 应用 permission policy
- 创建 Koog execution context

### 执行层

由 Koog 承担：

- prompt execution
- strategy graph
- tool calling
- planner
- history compression
- tracing

### 回写层

负责：

- 持久化 message / tool call / tool result / reasoning
- 推送事件总线
- 累积 token / cost
- 写审计日志
- 结束 turn

## 4.2 为什么要这样分层

这样做之后：

- CLI、ACP、HTTP 不会出现三套会话语义。
- 你可以替换执行引擎而不改 session 语义。
- 你可以替换协议入口而不改 tool/permission 语义。
- subagent 可以复用同一套 session 生命周期。

---

## 五、Provider 与 BYOK 设计

## 5.1 目标

实现真正的 BYOK，而不是只支持某一个固定 provider。

## 5.2 数据模型

建议最少定义以下对象：

### ProviderSpec

- `id`
- `displayName`
- `kind`
- `defaultBaseUrl`
- `authModes`
- `capabilities`
- `modelSource`

### ProviderAuth

- `providerId`
- `type`
- `apiKey`
- `baseUrl`
- `headers`
- `extra`

### ModelSpec

- `providerId`
- `modelId`
- `displayName`
- `contextWindow`
- `supportsTools`
- `supportsStreaming`
- `supportsReasoning`
- `supportsVision`

### ProviderBinding

运行时将 `ProviderAuth + ProviderSpec + ModelSpec` 绑定成真正可执行的 Koog executor/client。

## 5.3 实现策略

- provider registry 持有静态 provider 目录
- config/auth store 持有用户实际密钥与 baseUrl
- executor factory 根据 provider + model 构造 Koog executor
- runtime 只依赖 `ProviderGateway`，不依赖具体 provider SDK

## 5.4 与 Kilocode 的映射

对应 `kilocode` 中的：

- provider registry
- auth 存储
- model capability
- baseURL 处理

## 5.5 成功标准

- 新增 provider 不需要改 session/runtime 逻辑。
- `baseUrl`、`apiKey`、`model` 可以项目级覆盖。
- ACP / HTTP 可以复用同一份 provider 目录。

---

## 六、Session Runtime 设计

## 6.1 Session 不是 Koog history 的别名

Koog 的消息历史可作为执行输入，但 `mulehang-agent` 必须有自己的产品级 session 模型。

## 6.2 最少需要的持久化实体

### Session

- `id`
- `title`
- `mode`
- `agentProfile`
- `parentId`
- `createdAt`
- `updatedAt`
- `status`

### Message

- `id`
- `sessionId`
- `role`
- `model`
- `provider`
- `createdAt`

### Part

- `id`
- `messageId`
- `type`
- `status`
- `payload`
- `startedAt`
- `endedAt`

### Turn

- `sessionId`
- `reason`
- `openedAt`
- `closedAt`

### SessionLineage

- `parentSessionId`
- `childSessionId`
- `relation`

## 6.3 Event Bus

建议统一事件：

- `session.created`
- `session.updated`
- `turn.opened`
- `turn.closed`
- `message.created`
- `part.updated`
- `permission.asked`
- `permission.replied`
- `tool.started`
- `tool.finished`
- `run.failed`

协议适配层直接订阅这些事件。

## 6.4 History Compression

长会话压缩建议交给 Koog 的历史压缩机制，但压缩点和压缩结果的写入仍由 runtime 记录。这样可保留：

- 为什么压缩
- 何时压缩
- 压缩前后 message 范围

## 6.5 成功标准

- session 可以被 CLI/ACP/HTTP 同时读取。
- session 可以取消、恢复、fork。
- tool call 生命周期可审计。

---

## 七、Tooling 与 Permission 设计

## 7.1 Tool 不应直接裸露为 Koog API

需要两层表示：

### ToolSpec

产品层定义：

- `id`
- `name`
- `description`
- `inputSchema`
- `outputSchema`
- `permissionKey`
- `kind`
- `executor`

### ToolAdapter

把 `ToolSpec` 适配成 Koog tool。

## 7.2 工具来源

建议统一纳入同一 registry：

- built-in local tools
- external command tools
- MCP tools
- agent-as-tool

## 7.3 Permission Engine

权限必须独立于协议层存在。

### 规则模型

- `permission`
- `pattern`
- `action: allow | ask | deny`
- `scope: global | workspace | project | session`

### 能力

- 静态规则匹配
- 当前 session override
- `always allow` 持久化
- 审批事件广播

## 7.4 与 ACP / HTTP 的关系

- ACP 负责向客户端展示审批请求
- HTTP 负责暴露审批 API
- 真正决定 allow/ask/deny 的是 permission engine

## 7.5 成功标准

- 任意 tool 都有稳定 permission key。
- 同一 tool 在 CLI 和 ACP 中审批语义一致。
- MCP tool 不绕过 permission engine。

---

## 八、Skill 设计

## 8.1 Skill 不只是 prompt 片段

建议 skill 定义为一个资源包：

- `manifest`
- `SKILL.md`
- `resources/`
- `templates/`
- `scripts/`

## 8.2 作用域

- built-in
- user-global
- project-local
- config 指定路径
- remote installed

## 8.3 加载顺序

推荐：

1. built-in
2. global
3. project
4. configured paths
5. remote cache

后加载覆盖前加载。

## 8.4 运行时注入策略

通过 `SkillResolver` 将 skill 解析为：

- prompt additions
- required resources
- suggested tools
- process constraints

skill 不应直接操作 session 数据库，而是通过 runtime service 生效。

## 8.5 成功标准

- 同名 skill 覆盖规则可预测。
- skill 资源路径解析稳定。
- skill 可被 CLI、HTTP、ACP 统一发现。

---

## 九、MCP 设计

## 9.1 Koog 负责协议，Mulehang 负责产品生命周期

MCP 集成不应停留在“能调用工具”层面。还需要：

- server config 持久化
- connect/disconnect/status
- auth / OAuth
- env / secret 注入
- tool registry 挂载

## 9.2 运行时组件

- `McpServerConfigStore`
- `McpConnectionManager`
- `McpAuthService`
- `McpToolImporter`

## 9.3 关键边界

- MCP server 生命周期由 `McpConnectionManager` 管理
- imported tool 进入统一 `ToolSpecRegistry`
- MCP prompt/resource 若后续支持，也要以统一资源抽象暴露

## 9.4 成功标准

- MCP 连接状态可查询。
- MCP tools 与本地 tools 在 session 中表现一致。
- auth 与 reconnect 具备可恢复能力。

---

## 十、Harness 与 Subagent 设计

## 10.1 目标

实现接近 `kilocode task tool` 的受控子任务模型，而不是简单递归调用。

## 10.2 核心概念

### AgentProfile

- `primary`
- `subagent`
- `all`

### TaskInvocation

- `description`
- `prompt`
- `targetAgent`
- `taskId`
- `resume`
- `fork`
- `commandSource`

### SubagentSession

- 独立 session
- 可继承 parent context
- 可裁剪 permissions
- 可控制是否允许继续发起 task

## 10.3 Harness 的职责

- 创建/加载子 session
- 继承或截断权限
- 把子 agent 结果折叠回父任务
- 管理 task lineage
- 控制 planner / explore / specialist agent profile

## 10.4 Koog 的使用方式

- `createAgentTool()` 可作为 agent-as-tool 的实现方式
- planner / strategy graph 可用来做 task orchestration
- 但产品语义仍由 Harness 控制

## 10.5 成功标准

- 每个 subagent invocation 都有独立 session id
- 可 resume 原子任务
- parent/child lineage 可查询
- task approval 可被 permission engine 接管

---

## 十一、ACP 与 HTTP API 设计

## 11.1 统一原则

ACP 与 HTTP 都是 runtime 适配层。

它们不拥有自己的 session 语义。

## 11.2 ACP 设计

参考 Koog ACP 文档，建议：

- 使用 `channelFlow` 输出事件
- 使用 `Mutex` 串行化同一 session 执行
- `AgentSession.prompt()` 内不直接操作底层 session 存储，而是委托给 `SessionService.prompt()`
- runtime event 转换为 ACP event

ACP 需要暴露：

- session lifecycle
- prompt lifecycle
- tool status
- permission ask/reply
- usage updates

## 11.3 HTTP API 设计

建议按资源建模：

- `providers`
- `sessions`
- `messages`
- `permissions`
- `mcp`
- `skills`
- `agents`
- `tasks`
- `telemetry`

建议协议形式：

- 查询：JSON
- 长执行：SSE
- 强双向场景：后续再考虑 WebSocket

## 11.4 为什么要同时保留 ACP 和 HTTP

- ACP 用于标准 Agent Client 集成
- HTTP 用于自家宿主、自动化与 SDK
- 两者共用 runtime 才能避免语义分裂

---

## 十二、可观测性设计

## 12.1 最低要求

- llm call trace
- tool call trace
- permission audit
- token / cost
- session lineage
- run failure classification

## 12.2 实现方式

建议链路：

`Koog tracing -> Mulehang EventBus -> storage/log exporter`

Koog 负责捕获原始执行事件，Mulehang 负责产品化归档。

## 12.3 成功标准

- 任意失败都能定位到 provider、tool、permission、session 四个维度之一
- token/cost 能按 session 聚合
- subagent lineage 能被观察

---

## 十三、推荐目录演进

## 13.1 近期目录建议

在单模块阶段，建议先按包拆开：

- `config`
- `provider`
- `runtime`
- `tooling`
- `permission`
- `skill`
- `mcp`
- `acp`
- `http`
- `harness`
- `obs`
- `cli`

逐步把当前 `agent/` 下的原型逻辑迁出。

## 13.2 中期建议

当 Phase 2 或 Phase 3 完成后，再升级为 Gradle 多模块。

这样可以避免过早拆模块造成阻力，同时保留未来边界清晰的演进路径。

---

## 十四、阶段路线图

## Phase 0：Koog 0.7.3 兼容性探针

目标：验证设计依赖的 Koog API 在当前版本真实可用。

输出：

- 最小 ACP demo
- 最小 MCP demo
- 最小 persistence demo
- 最小 agent-as-tool/subtask demo
- 最小 streaming/tool event demo

## Phase 1：Config + Provider + BYOK

目标：建立 provider registry、auth store、model capability。

输出：

- config model
- provider gateway
- executor factory
- baseUrl/apiKey/model 解析

## Phase 2：Session Runtime + Persistence + Event Bus

目标：建立统一会话模型。

输出：

- session/message/part storage
- event bus
- turn lifecycle
- cancel/resume/fork 基础设施

## Phase 3：Tool Registry + Permission Engine

目标：把本地工具、MCP 工具、agent-as-tool 纳入统一能力目录。

输出：

- ToolSpec registry
- permission engine
- approval persistence
- session-scoped overrides

## Phase 4：CLI Productization

目标：让当前 CLI 原型升级为真正可恢复、可审计、可切模型的产品入口。

输出：

- `session resume`
- provider switching
- permission prompts
- structured run output

## Phase 5：ACP Adapter

目标：提供标准 ACP server。

输出：

- ACP session bridge
- runtime event -> ACP event
- permission bridge
- usage update

## Phase 6：HTTP API + SDK

目标：提供正式接口层。

输出：

- REST + SSE
- OpenAPI
- Kotlin/TS SDK 输入

## Phase 7：Skill System

目标：完成发现、加载、作用域和资源解析。

输出：

- skill manifest
- resolver
- scope & precedence
- remote install 基础能力

## Phase 8：Harness + Subagent

目标：完成 task delegation / resume / fork / lineage。

输出：

- TaskInvocation
- AgentProfile
- subagent sessions
- parent-child lineage

## Phase 9：Hardening

目标：把系统从“能跑”推进到“可维护、可定位、可迭代”。

输出：

- tracing
- cost accounting
- failure taxonomy
- compatibility matrix
- regression suite

---

## 十五、架构决策摘要

### 决策 1

采用 `Kilo-like Runtime over Koog`，而不是 Koog-native 直接拼产品。

### 决策 2

CLI、ACP、HTTP 共用统一 runtime。

### 决策 3

provider、session、tool、permission、skill、harness 必须作为独立产品层存在。

### 决策 4

MCP、ACP、agent-as-tool 等能力优先复用 Koog，但由 Mulehang 负责产品化生命周期。

### 决策 5

先按包分层，再在中期演进到 Gradle 多模块。

---

## 十六、成功标准

当本设计落地后，`mulehang-agent` 应具备以下判断标准：

1. CLI、ACP、HTTP 对同一 session 的语义一致。
2. Provider 可通过配置切换，不改业务代码。
3. MCP tool 与本地 tool 具有统一权限语义。
4. Skill 可被多入口统一发现与注入。
5. Subagent 拥有独立 session、可 resume、可 fork。
6. 执行链路可观察、可审计、可定位失败。

---

## 十七、下一步

本设计确认后，进入分阶段 implementation plans 编写。推荐输出多份计划文档，而不是一份总计划：

1. `Phase 0-1`：Koog compatibility + config/provider/BYOK
2. `Phase 2-4`：session/tool/permission/CLI
3. `Phase 5-6`：ACP + HTTP API
4. `Phase 7-9`：skill + harness + hardening

这样每份计划都能保持可执行、可验证、可迭代。
