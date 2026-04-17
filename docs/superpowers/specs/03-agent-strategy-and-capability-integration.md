# 03 Agent Strategy And Capability Integration

## 目标

本阶段的目标不再是“最小 `AIAgent` 装配”，而是把真实 Koog 执行链接入当前 runtime 主轴。

完成本阶段后，系统应当能够：

1. 基于 `ProviderBinding` 组装真实 Koog agent
2. 让 agent 消费统一的 capability set
3. 通过至少一条 `runtime -> agent.run(...) -> runtime result` 链路完成执行

## 范围

本阶段只解决 Koog agent 语义与能力桥接，不解决：

1. CLI 输出体验
2. ACP 协议桥接
3. memory / snapshot / tracing
4. 复杂多轮 workflow 设计

## 关键边界

Koog 负责：

1. `AIAgent`
2. `singleRunStrategy`
3. tool calling 语义
4. MCP tool registry 集成

仓库层负责：

1. `ProviderBinding` 到 Koog executor / model 的解析
2. `CapabilitySet` 到 Koog registry 的桥接
3. runtime request 到 `agent.run(...)` 的调用与结果翻译

CLI、ACP 和未来 client surface 仍然只消费 runtime，不直接拼装 Koog。

## 阶段完成标准

phase 03 完成时，必须满足：

1. `OPENAI_COMPATIBLE`
2. `ANTHROPIC_COMPATIBLE`
3. `GEMINI_COMPATIBLE`

这三种 provider type 都能被解析为 Koog 可执行的 executor 与 model 组合。

同时必须满足：

1. local/custom tool
2. MCP-backed capability
3. direct HTTP internal API capability

这三类能力都能通过统一 contract 挂到 Koog agent 的真实执行链上。

## 组件拆分

### 1. KoogExecutorResolver

职责：

1. 把 `ProviderBinding` 解析为真实 Koog `PromptExecutor`
2. 把 `modelId` 解析为当前 provider 下可执行的 Koog model 表示

输入：

1. `ProviderBinding`

输出：

1. `ResolvedKoogModelBinding`

它是 provider 到 Koog 的唯一入口；外层不允许自己猜 executor 或 model。

### 2. KoogToolRegistryAssembler

职责：

1. 把仓库侧 `CapabilitySet` 组装为 Koog 可消费的 registry / provider
2. 把三类 capability 桥接到 Koog 的 tool calling 体系

输入：

1. `CapabilitySet`

输出：

1. Koog `ToolRegistry`
2. 通过 `McpToolRegistryProvider` 创建的 MCP `ToolRegistry`
3. 必要的 HTTP tool wrapper

它只负责桥接，不负责执行。

### 3. AgentAssembly

职责：

1. 组合 `ResolvedKoogModelBinding`
2. 注入 strategy
3. 注入 tool registry / MCP registry
4. 创建真实 Koog `AIAgent`

输入：

1. `ResolvedKoogModelBinding`
2. 已装配的 capability runtime pieces

输出：

1. 真实 Koog agent

### 4. RuntimeAgentExecutor

职责：

1. 把 runtime request 映射为 `agent.run(...)`
2. 把 Koog 返回值翻译为统一 `RuntimeResult`
3. 把 Koog 失败翻译为 runtime 错误分层

输入：

1. `RuntimeSession`
2. `RuntimeRequestContext`
3. 用户请求文本
4. 真实 Koog agent

输出：

1. `RuntimeSuccess`
2. `RuntimeFailed`

### 5. CapabilityAdapter 家族

仓库内仍然保留统一 `CapabilityAdapter` contract。

原因：

1. runtime 需要稳定边界
2. phase 04 CLI 不应该知道 Koog tool API
3. 后续如果 Koog API 变化，调整点应集中在桥接层

因此本阶段允许存在两套视图：

1. runtime 视图：`CapabilitySet`
2. Koog 视图：`ToolRegistry` / MCP registry / HTTP tool wrapper

## 数据流

本阶段固定最小调用流为：

1. runtime 获取 `ProviderBinding`
2. `KoogExecutorResolver` 解析 executor 与 model
3. runtime 获取 `CapabilitySet`
4. `KoogToolRegistryAssembler` 组装 Koog registry
5. `AgentAssembly` 创建真实 Koog `AIAgent`
6. `RuntimeAgentExecutor` 调用 `agent.run(...)`
7. Koog 返回文本或能力调用结果
8. runtime 统一翻译为 `RuntimeResult`

这条调用流是 phase 03 的核心交付。

## Provider 映射策略

### 映射原则

`ProviderBinding` 仍然是仓库自己的稳定边界，至少保留：

1. `providerType`
2. `baseUrl`
3. `apiKey`
4. `modelId`

`KoogExecutorResolver` 必须按 `providerType` 分三条路径解析：

1. `OPENAI_COMPATIBLE`
2. `ANTHROPIC_COMPATIBLE`
3. `GEMINI_COMPATIBLE`

### modelId 解析原则

失败条件不是“`modelId` 不在 Koog 文档里列出的内置模型常量中”。

失败条件是：

1. 当前 provider 类型下
2. 当前 Koog 0.8.0 API 下
3. 仓库无法把该 `modelId` 解析或构造成 Koog 可执行模型对象

因此解析策略必须分两级：

1. 优先支持 arbitrary `modelId`
   - 只要 Koog 0.8.0 为当前 provider 提供可用的通用/custom model 构造路径，就必须优先支持 discovered model id
2. provider-specific fallback
   - 如果 Koog 0.8.0 对某 provider 没有暴露可用的通用构造入口，才允许退回到“已验证模型白名单”

如果走到了 fallback，文档和错误信息都必须明确：

1. 这是 Koog 0.8.0 的接口限制
2. 不是仓库自己的业务限制

### baseUrl 处理原则

`baseUrl` 只在对应 provider 的 Koog 0.8.0 client 支持自定义 endpoint 时生效。

如果某 provider 在 0.8.0 下不支持可配置 `baseUrl`，阶段文档必须把它写成受限能力，而不是留给实现阶段自行判定。

## Capability 桥接策略

本阶段必须桥接三类能力：

1. local/custom tool
2. MCP-backed capability
3. direct HTTP internal API capability

桥接方式固定为：

1. local/custom tool -> Koog `ToolRegistry`
2. MCP-backed capability -> `McpToolRegistryProvider` 创建出的 Koog `ToolRegistry`
3. direct HTTP internal API capability -> Koog tool wrapper

仓库层不允许让 agent 直接理解 `CapabilityAdapter`；所有差异必须被桥接层吸收。

## 错误分层

本阶段至少区分三类错误：

1. provider 解析错误
2. capability 桥接错误
3. agent 执行错误

runtime 不允许把它们全部折叠成单一通用异常。

## 非目标

本阶段明确不做：

1. CLI 输出格式和交互优化
2. ACP 事件桥与 session 桥
3. memory、snapshot、tracing
4. 大规模 workflow / graph orchestration 设计

## 验证标准

### Provider 解析验证

必须验证：

1. `OPENAI_COMPATIBLE`
2. `ANTHROPIC_COMPATIBLE`
3. `GEMINI_COMPATIBLE`

都存在真实 Koog 解析路径。

每种 provider 至少覆盖两类情况：

1. `modelId` 可被解析为 Koog 可执行模型对象
2. 当前 provider 下无法构造时返回结构化失败

### Capability 桥接验证

必须验证：

1. local/custom tool 已进入 Koog `ToolRegistry`
2. MCP capability 已通过 `McpToolRegistryProvider` 进入 Koog `ToolRegistry`
3. HTTP capability 已被包装成 Koog 可调用 tool

### Agent 装配验证

必须验证：

1. `AgentAssembly` 产出真实 Koog agent
2. `AgentStrategyFactory` 返回真实 Koog `singleRunStrategy`

### 运行链验证

至少存在一条集成验证，覆盖：

1. `ProviderBinding`
2. `CapabilitySet`
3. `AgentAssembly`
4. `RuntimeAgentExecutor`
5. `agent.run(...)`
6. `RuntimeResult`

### 错误验证

至少验证：

1. provider 解析失败
2. capability 桥接失败
3. agent 执行失败

并确保 runtime 可以区分这三类错误。
