# Koog Agent Architecture Design

## 目标

把当前仓库重组为一条真正可执行的 superpowers 开发主线，用 JetBrains Koog 作为 agent 框架核心，逐阶段逼近 `kilocode` 当前体现出来的能力形状，但不直接照抄其实现。

这条主线的第一原则不是“先把某个入口跑起来”，而是先建立一条稳定的 runtime 主轴，让后续的 BYOK、模型发现、Koog agent 组装、工具接入、直接 HTTP 调用、CLI、ACP 和 memory feature 都能挂到同一条内部契约上。

## 背景

当前仓库的 `docs/superpowers/specs` 和 `docs/superpowers/plans` 仍然保留了旧的“手搓学习阶段说明”语义：

1. 文档主要解释“这一阶段练什么”，而不是“系统要怎么设计和落地”。
2. 多份 plan 不具备 superpowers workflow 所要求的可执行粒度。
3. 旧文档没有把 JetBrains Koog 的官方抽象放到架构中心。
4. 旧文档没有把完整 BYOK、自动模型发现、direct HTTP internal API、CLI 优先、ACP 次级入口这些新要求纳入主线。

仓库本身也已经切换方向：

1. 不再继续“纯手搓重建”主线。
2. 当前 git 历史已经被重置。
3. `vendor/kilocode` 被重新作为 submodule 引入，用于长期参考。

因此，文档层需要一起重建为面向 superpowers 的设计文档与实施计划体系。

## 设计原则

### 1. Runtime First

系统先定义运行时主轴，再挂能力。

这里的“运行时主轴”至少包括：

1. `session`
2. `request context`
3. `capability request`
4. `event`
5. `result`
6. `error boundary`

所有入口和能力都必须经过这条主轴流动，而不是各自维护独立的控制流。

### 2. BYOK Is First-Class

BYOK 不是普通配置读取，而是第一批核心能力。

系统必须支持：

1. 用户新建 `custom provider`
2. 用户为 `custom provider` 自定义 `baseUrl`
3. 用户为 `custom provider` 输入 `apiKey`
4. 用户为 `custom provider` 选择提供商类型
5. 保存后自动验证连接
6. 连接成功后自动获取可用模型列表
7. 生成可供运行时和 Koog agent 消费的最终 binding

这里的 `custom provider` 是仓库自有概念，不是对 Koog 官方 provider 的覆写。

`提供商类型` 至少支持：

1. `OpenAI-compatible`
2. `Anthropic-compatible`
3. `Gemini-compatible`

其中默认值为 `OpenAI-compatible`，但创建后允许修改。提供商类型变更后，系统必须重新执行连接探测、模型发现与 binding 刷新。

### 3. Koog Owns Agent Semantics

JetBrains Koog 负责 agent 本体的语义，包括：

1. `AIAgent`
2. strategy / workflow
3. tool registry
4. streaming
5. structured output
6. feature 安装点，例如 ACP、memory、snapshot

仓库自己的 runtime 负责组织请求、配置、能力接入和协议边界，不重新发明一套平行 agent 框架。

### 4. Capability Integrations Stay Decoupled

系统必须同时容纳三类能力接入：

1. tool
2. MCP
3. direct HTTP internal API

它们都属于 capability integration，但不能直接互相混写，也不能把调用细节扩散到 CLI 或协议层。

### 5. CLI Before ACP

第一批主入口是 CLI。

ACP 是第二批协议入口，用于把同一套 agent 运行时桥接给 ACP 客户端。它不应该反向决定核心 runtime 的形状。

### 6. Client Surfaces Stay Replaceable

未来如果增加 KMP 桌面端或 Web 端，它们应该只是新的 client surface，而不是新的 runtime 或新的 agent 栈。

## 官方抽象对齐

这套设计显式对齐 Koog 官方文档里的一等概念：

1. provider / prompt executor / model
2. `AIAgent`
3. strategy / graph workflow
4. tool registry 与 MCP 集成
5. streaming 与 structured output
6. features，例如 ACP、memory、snapshot

这里不把 `runtime` 理解为对 Koog 的替代，而是把它定义为仓库自己的系统主轴，用来承接 Koog 与外围系统之间的边界。

## 总体架构

### Runtime 主轴

内部调用流统一收敛为：

1. client surface 发起请求
2. runtime 建立 session 和 request context
3. runtime 解析当前可用 binding 与 capability set
4. runtime 把请求交给 Koog agent 或相关 capability adapter
5. runtime 汇总事件、结果和错误
6. client surface 消费统一输出

这条主轴必须对不同入口保持一致：

1. CLI
2. ACP
3. 未来的 KMP / Web client

### Provider 与 Binding

provider 层负责把“用户配置的连接”转化为“系统可用的模型 binding”。

它至少包含四类对象：

1. custom provider profile
2. 提供商类型
3. connection probe result
4. model discovery result
5. executor / model binding

它与 CLI、ACP、tool 层都隔离，避免任何入口自己拼装 provider 细节。

这里要额外区分两类 provider 概念：

1. Koog 官方 provider
2. 仓库内的 `custom provider`

Koog 官方 provider 维持自身原义；仓库内的 `custom provider` 负责承载用户输入的兼容接口配置与提供商类型，并在运行时被解析成可供 Koog 消费的实际 binding。

### Agent 与 Capability Integration

agent 层基于 Koog 实现，能力接入层为其提供外部能力。

能力接入层至少区分：

1. local/custom tools
2. MCP-backed tools
3. direct HTTP adapters

agent 消费的是统一的 capability 接口和上下文，而不是直接感知不同接入类型的底层实现。

### Client Surfaces

对外入口按优先级拆分：

1. CLI
2. ACP
3. optional client surfaces，例如 KMP desktop / Web

它们共享同一套 runtime 契约和 agent 主线，只是在协议和展示层不同。

在工程结构上，client surface 默认应采用独立模块承载，而不是直接混入 `runtime` 模块。

当前阶段的首个明确落地方向是：

1. `runtime` 模块承载 runtime、provider、capability、agent 与宿主集成核心
2. `cli` 模块作为第一主入口，可以使用独立技术栈承载 TUI
3. 后续 ACP 或其他 client surface 再按相同原则决定是否独立成模块

对当前仓库而言，CLI 的优先实现方向是：

1. Kotlin/JVM 的 `runtime` 模块继续承载核心执行链
2. 独立 `cli` 模块采用 TypeScript + Bun + OpenTUI
3. `cli` 通过 shared local runtime HTTP server 与 `runtime` 通信，而不是直接共享 JVM 代码

这里补充一个前端技术边界：

1. 当前第一主入口是终端 TUI，渲染层依赖 OpenTUI，不采用浏览器 CSS 方案
2. 如果后续新增 Web UI、浏览器 companion、官网或其他浏览器前端，默认采用 Tailwind CSS 4 最新稳定版
3. Tailwind CSS 的引入不应反向影响当前 `cli` 与 `runtime` 的 local HTTP/SSE 边界

## 阶段地图

### 01 Runtime Foundation And Contracts

这一阶段先定义系统主轴，而不是先补功能。

职责：

1. 定义 session 边界
2. 定义 request context
3. 定义 capability request / response 契约
4. 定义 event / result / error 模型
5. 定义 runtime 与入口层的接口

完成后，系统应当已经知道“一次请求怎样进入、怎样流动、怎样结束”，但不要求能力已经完整。

### 02 Provider BYOK And Model Discovery

这一阶段实现 BYOK 主线。

职责：

1. 支持创建 `custom provider`
2. 支持为 `custom provider` 设置 `baseUrl`
3. 支持为 `custom provider` 设置 `apiKey`
4. 支持为 `custom provider` 选择提供商类型
5. 提供商类型默认值为 `OpenAI-compatible`
6. 允许在创建后修改提供商类型
7. 在保存后自动执行连接探测
8. 在探测成功后自动发现模型列表
9. 生成最终 binding 并回接 runtime

这一阶段必须清晰区分：

1. 用户输入的 `custom provider` 配置
2. 用户选择的提供商类型
3. 探测得到的远端事实
4. 运行时消费的最终 binding

这一阶段还必须定义按提供商类型分离的适配点：

1. connection probe adapter
2. model discovery adapter
3. binding resolver

### 03 Agent Strategy And Capability Integration

这一阶段把 Koog 真正接入系统。

职责：

1. 组装最小 `AIAgent`
2. 用 `singleRunStrategy` 打通第一条链路
3. 为后续 workflow / graph strategy 留出演进空间
4. 接入 local tools
5. 接入 MCP
6. 接入 direct HTTP internal API

这里的重点不是 capability 数量，而是能力接入边界必须统一。

### 04 CLI Streaming And Output

这一阶段把 CLI 做成第一主入口。

职责：

1. 解析 CLI 输入
2. 连接 runtime
3. 消费并输出事件流
4. 支持 streaming
5. 支持 structured output 的可见呈现
6. 呈现用户可理解的错误和状态反馈

CLI 只消费统一输出契约，不关心底层能力是 tool、MCP 还是 direct HTTP。

在实现形态上，CLI 应作为独立模块落地。当前优先方向不是在 `runtime` 内直接拼终端界面，也不是让 CLI 直接依赖 runtime 内部类，而是采用：

1. `runtime` 作为 Kotlin/JVM 核心进程
2. `cli` 作为 TypeScript/Bun/OpenTUI TUI 前端
3. 两者通过 shared local runtime HTTP server + SSE 流式桥接

### 05 ACP Protocol Bridge

这一阶段再引入 ACP。

职责：

1. 用 Koog 的 `AcpAgent` feature 桥接 agent 事件
2. 把 runtime 和 session 生命周期映射到 ACP
3. 确保 ACP 作为第二入口不分叉核心 runtime

CLI 和 ACP 的关系应该是：

1. 它们都共享同一套 runtime 主轴
2. 它们只是不同的 client surface

### 06 Memory Features And Hardening

这一阶段把 feature 和系统稳健性纳入主线。

职责：

1. 引入 memory
2. 引入 snapshot / persistence
3. 引入 tracing / observability
4. 补充失败恢复与错误分层
5. 补充集成级验证

这一阶段是增强，不应反向污染前面的核心边界。

### 07 Client Surfaces Optional

这一阶段不是第一轮核心交付，而是扩展预留。

职责：

1. 为 KMP desktop 预留入口契约
2. 为 Web UI 预留入口契约
3. 保证它们复用 runtime、agent 和协议层，而不是重写核心

如果进入浏览器前端阶段，应额外遵守：

1. Web UI 继续作为可替换的 client surface，而不是新的 runtime
2. 浏览器前端默认采用 Tailwind CSS 4 最新稳定版作为样式基线
3. 浏览器前端的样式体系不应泄漏到终端 TUI 的实现约束中

## 关键边界

### Runtime 和 Agent 的边界

runtime 负责：

1. session 生命周期
2. request context
3. binding 选择
4. capability 装配
5. result / error 汇总

Koog agent 负责：

1. prompt 执行
2. strategy / workflow
3. tool calling 语义
4. feature 生命周期

### Provider 和 Binding 的边界

provider 层负责：

1. `custom provider` 配置
2. 提供商类型选择与变更
3. 连接探测
4. 模型发现
5. binding 解析

上层不负责：

1. 拼装 API key
2. 选择 baseUrl 默认值
3. 自己维护模型目录缓存
4. 自己决定不同提供商类型走哪套探测逻辑

### Capability Integration 的边界

capability integration 层负责：

1. 把不同能力源统一成稳定接口
2. 隔离 tool、MCP、HTTP 的底层差异
3. 提供运行时可执行的 capability set

CLI、ACP、未来 UI 不直接碰具体能力实现。

### Client Protocol 的边界

CLI 和 ACP 都属于 client surface。

ACP 不是工具协议，也不是 direct HTTP capability。它是 agent 对外暴露能力时的客户端协议桥。

## 非目标

本轮文档重建明确不做以下事情：

1. 不直接抄 `kilocode` 的代码结构
2. 不一次性设计完整桌面端或 Web 端实现
3. 不在总 spec 里写具体 Kotlin 实现细节
4. 不把所有 provider 生态一次性设计到位
5. 不把 ACP 提前提升为第一主入口
6. 不把 memory / snapshot 提前塞进 runtime 基础阶段

## 风险与控制

### 风险 1：BYOK 逻辑反向吞掉 runtime

控制方式：

1. 先定义 runtime 契约
2. 再把 BYOK 接到 runtime
3. 禁止 CLI 或 provider 层绕过 runtime 自己维护调用流

### 风险 2：`custom provider` 和 Koog 官方 provider 语义冲突

控制方式：

1. 在文档和模型层明确区分两类 provider
2. 用户只编辑仓库自己的 `custom provider`
3. binding 阶段再把 `custom provider` 解析为 Koog 可消费的实际配置

### 风险 3：Koog 与仓库自定义层职责重叠

控制方式：

1. Koog 负责 agent 语义
2. 仓库层负责配置、运行时主轴、能力接入和协议桥接
3. 避免重复封装一套平行 workflow 框架

### 风险 4：提供商类型可变导致探测结果和模型目录脏化

控制方式：

1. 提供商类型变更后强制重新探测
2. 丢弃旧的模型发现结果与 binding 缓存
3. 对 probe、discovery、binding 使用同一提供商类型快照

### 风险 5：direct HTTP、tool、MCP 混成一个大杂烩

控制方式：

1. 明确 capability integration 层
2. 对三类接入保持统一接口与独立 adapter
3. 上层只消费 capability contract

### 风险 6：ACP 入口导致双 runtime

控制方式：

1. 先完成 CLI 主线
2. ACP 只作为第二入口桥接已有 runtime
3. 禁止 ACP 自己定义独立的核心状态模型

## 成功标准

文档重建完成后，应满足以下标准：

1. `docs/superpowers/specs` 和 `docs/superpowers/plans` 转为真正的 superpowers workflow 文档。
2. 总 spec 能解释为什么系统采用 `runtime-first`。
3. 阶段划分能容纳完整 BYOK、Koog agent、tools、MCP、direct HTTP、CLI、ACP、memory。
4. CLI 被明确为第一主入口，ACP 被明确为第二入口。
5. 未来 KMP / Web client 只作为 optional surface，不污染核心阶段。
6. 后续 implementation plan 可以直接基于这份设计拆成可执行任务。
