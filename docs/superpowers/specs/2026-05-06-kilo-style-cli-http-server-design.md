# Kilo-Style CLI Shared HTTP Server Design

## 目标

把当前仓库的 CLI 主入口从“前端直接拉起 `RuntimeCliHost` 并通过本地 `stdio` 协议通信”硬切换为“前端连接共享本地 runtime server，并通过 HTTP + SSE 通信”的架构。

本次设计的目标不是抽象讨论“是否支持另一种入口”，而是明确把：

1. `RuntimeHttpServer` 升级为唯一真实运行入口
2. `cli` 从 `stdio` 子进程桥接改为 Kilo 风格的 shared local server client
3. session、memory、provider、错误、日志、流式事件统一收敛到同一条 HTTP server 主链

这次设计同时要求同步更新本地文档，包括：

1. `AGENTS.md`
2. `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
3. `docs/superpowers/specs/04-cli-streaming-and-output.md`
4. `docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md`
5. `docs/superpowers/plans/04-cli-streaming-and-output.md`

## 背景

当前仓库已经形成两套 runtime 入口：

1. `cli` 通过 `RuntimeProcessClient` 启动本地 `runtime-cli-host.bat`，再通过 `stdin/stdout` 交换协议消息
2. `runtime` 同时还暴露了一个独立的 `RuntimeHttpServer`

这导致当前架构存在三个问题：

1. CLI 与直接 HTTP 调试并不共享同一条真实入口链
2. session、日志、鉴权、memory 和调试体验都被迫分叉
3. 总体设计文档和 `04-cli-streaming-and-output` 阶段文档仍然把 `stdio` 当成 CLI 主线，这已经不符合“完全按 Kilo 设计”的新目标

对照 `vendor/kilocode` 可以看到，Kilo 的关键不是“任何场景都用 WebSocket”，而是：

1. client 连接共享本地 server
2. server 作为 runtime、session 与 HTTP API 的唯一宿主
3. client 通过 HTTP + SSE 消费事件流
4. WebSocket 只在 PTY 或特定实时场景中局部使用

因此，这次设计的目标是对齐 Kilo 的 **shared local server + HTTP + SSE** 主体架构，而不是继续扩展 `stdio` 协议。

## 设计原则

### 1. Runtime Server Becomes The Only Real Entry

`RuntimeHttpServer` 必须成为唯一真实运行入口。

这意味着：

1. CLI 不再直接调用 `RuntimeCliHost`
2. 外部手工 HTTP 调试和 CLI 前端必须走同一套 runtime 路由
3. provider 选择、memory、agent 组装、错误边界都只能在 HTTP server 主链上定义一次

### 2. Shared Local Server Over Per-CLI Child Process

CLI 不再拥有一次性私有 runtime 子进程，而是连接一个共享本地 server。

这意味着：

1. 多个 CLI 会话可以复用同一个 server
2. server 生命周期与单个 CLI 窗口解耦
3. “一个 CLI 实例多个 session”“未来 Web UI”“外部本地 HTTP 调试”都可以建立在同一 server 上

### 3. HTTP For Management, SSE For Streaming

这次设计不采用 WebSocket-first。

原因不是 WebSocket 不能做，而是当前目标是“完全按 Kilo 的设计来”，而 Kilo 的主通信模型是：

1. HTTP 请求
2. SSE 事件流
3. 局部 WebSocket 用于 PTY 或特殊实时链路

因此，本仓库本轮对齐的主协议是：

1. HTTP：管理、健康检查、元信息、一次性命令
2. SSE：全局事件流与运行中事件消费

### 4. CLI Remains A Replaceable Client Surface

CLI 仍然只是 client surface，不拥有 runtime 内部语义。

CLI 负责：

1. 发现或启动 shared local server
2. 发起 HTTP 请求
3. 订阅 SSE 事件流
4. 把事件渲染到 OpenTUI 界面

CLI 不负责：

1. provider 探测
2. memory 存储策略
3. tool / MCP / direct HTTP capability 实现
4. session 的核心业务语义

## 总体架构

### 新主链

硬切之后，唯一主链变为：

1. `CLI Frontend`
2. `Local ServerManager`
3. `Shared Local Runtime HTTP Server`
4. `Runtime Core / Koog Agent`

其含义是：

1. `runtime` 的核心执行、session 和 memory 生命周期只存在一份
2. `cli` 不再与 `runtime` 通过本地 `stdio` 文本流通信
3. `RuntimeHttpServer` 不再是附加调试入口，而是正式主入口

### 旧主链移除

以下对象在迁移完成后应删除或废弃：

1. `RuntimeCliHost`
2. `RuntimeCliProtocol`
3. `DefaultRuntimeCliService`
4. `runtime-cli-host.bat` 及其分发安装链
5. `cli` 中所有以 `stdin/stdout` 为中心的协议桥接实现
6. 所有把 `stdio` 视为 CLI 正式主链的文档表述

## Server 生命周期

### ServerManager 职责

CLI 侧新增 `ServerManager`，负责管理共享本地 server。

其职责包括：

1. 读取本地状态文件
2. 执行健康检查
3. 在 server 缺失或失效时启动新实例
4. 避免并发 CLI 重复拉起多个 server
5. 把 `baseUrl`、`token` 和兼容性信息交给 CLI HTTP client

### 启动规则

CLI 启动时遵循以下顺序：

1. 读取状态文件
2. 如果状态文件存在，取出 `port`、`pid`、`token`
3. 对该 server 执行 `/health` 与 `/meta` 检查
4. 健康检查成功则直接复用
5. 状态文件不存在、内容损坏、健康检查失败或版本不兼容时，启动新的 runtime server
6. 新 server 启动成功后，写入新的状态文件

### 退出规则

CLI 退出时：

1. 不主动关闭 shared local server
2. server 作为共享常驻实例继续存活
3. 下一个 CLI 会话优先复用现有实例

这个行为与当前 `stdio host` 有本质差别：server 生命周期不再绑定单个 CLI 窗口。

## 状态文件与发现机制

### 状态文件内容

状态文件至少保存：

1. `pid`
2. `port`
3. `token`
4. `startedAt`
5. `protocolVersion`
6. `serverVersion`
7. `authMode`

这些字段用于支持：

1. server 发现
2. 兼容性判断
3. 认证信息下发
4. 失效重启

### 状态文件位置

状态文件必须存放在用户本地可写目录，而不是仓库源码目录中。

原因：

1. shared local server 不应绑定某个 git worktree
2. CLI 与未来 Web UI 可能来自不同工作目录
3. 这类状态本质上属于本地运行时状态，而不是项目文件

### 并发与原子性

多个 CLI 同时启动时，必须避免重复拉起多个 server。

因此需要：

1. 锁文件或等效原子机制
2. 状态文件写入必须保证完整性
3. 读到半写状态文件时，CLI 应视为失效并尝试恢复，而不是信任脏数据

## 认证模型

### 默认认证

shared local server 默认启用本地 token 认证。

行为如下：

1. server 启动时生成随机 token
2. token 写入状态文件
3. CLI 所有 HTTP / SSE 请求自动带 token

这样做的原因不是把本地调试复杂化，而是共享常驻 server 一旦存在，就需要基本的本地边界。

### 调试覆盖

为了兼顾手工 HTTP 调试，本地调试允许显式关闭 token 校验。

因此，认证策略必须支持两种模式：

1. 默认模式：启用 token
2. 调试模式：显式关闭 token

`authMode` 应进入状态文件与 `/meta` 响应，便于 client 明确当前 server 的边界。

## HTTP 与 SSE 协议

### 管理接口

shared local server 至少暴露以下管理接口：

1. `GET /health`
2. `GET /meta`

`/health` 用于判断 server 存活。  
`/meta` 用于返回：

1. `protocolVersion`
2. `serverVersion`
3. `authMode`

### 执行接口

执行入口统一收敛为 HTTP server 上的 `run` 类接口。

其入参继续承载：

1. `sessionId`
2. `prompt`
3. `provider`

CLI 与外部直接调用必须共用这一入口，而不是一边走 CLI 私有协议、一边走 HTTP。

### 事件流

Kilo 风格对齐下，流式事件使用 SSE。

本仓库需要至少支持以下事件类型：

1. `status`
2. `text.delta`
3. `thinking.delta`
4. `tool.event`
5. `run.completed`
6. `run.failed`

这些事件不要求在 transport 层复刻当前 `RuntimeCliOutboundMessage` 的所有细节，但必须保留现有 CLI 已经依赖的核心展示语义：

1. 文本增量
2. thinking 区块
3. tool 状态
4. 最终结果
5. 结构化失败

### 多 session

Kilo 的多 session 并行不是靠“一个 session 一个 websocket”，而是靠：

1. 共享 server
2. 全局或可订阅事件流
3. 事件负载里的 `sessionId`
4. client 自己按 `sessionId` 分发、筛选和路由

因此，本仓库如果要支持“一个 CLI 实例多个 session”，也应采用同一路径：

1. SSE 事件中带 `sessionId`
2. CLI 状态层按 `sessionId` 建立路由与聚合
3. 不因为未来多 session 需求而提前把主协议改成 WebSocket-first

## CLI 改造范围

### 保留的部分

以下部分可以大体保留：

1. OpenTUI 组件结构
2. transcript / thinking / status 的展示语义
3. 现有 `AppState` 和 session 可视化思路

### 重写的部分

CLI runtime 接入层需要重写为：

1. `ServerManager`
2. `RuntimeHttpClient`
3. `SseEventClient`

当前基于 `RuntimeProcessClient` 的 `stdio` 桥接逻辑不再保留为主链。

### 提交行为

`app.tsx` 层的行为调整为：

1. 启动时先拿到 shared local server 连接信息
2. 启动 SSE 订阅
3. 提交 prompt 时通过 HTTP 发起请求
4. 流式事件从 SSE 回流，再映射为当前 UI 状态

## Runtime 改造范围

### RuntimeHttpServer 升级

`RuntimeHttpServer` 要从“附加宿主”升级为正式主入口。

这意味着需要补齐：

1. shared local server 启动参数
2. token 校验
3. `/health`
4. `/meta`
5. SSE 事件流
6. 统一 run 入口

### Session 与 Memory

当前 memory 已经建立在 `sessionId` 上，本次改造不应重新设计 memory 语义。

本轮要求是：

1. CLI 继续传稳定 `sessionId`
2. HTTP 直调继续传稳定 `sessionId`
3. 二者命中同一条 runtime session / memory 主链

也就是说，这次改造是 **入口统一**，不是 **memory 重做**。

## 错误处理

错误必须分成三层：

### 1. ServerManager 层

处理：

1. 状态文件不存在
2. 状态文件损坏
3. token 缺失
4. 健康检查失败
5. 启动超时
6. 版本不兼容

这些错误在 CLI 中应表现为“连接/启动失败”，不能伪装成 agent 执行失败。

### 2. HTTP API 层

处理：

1. 认证失败
2. 请求格式错误
3. provider 配置错误
4. session 不存在

错误体必须稳定，CLI 与外部调用可共享解析逻辑。

### 3. Runtime 执行层

处理：

1. provider 调用失败
2. tool / MCP / capability 失败
3. agent 执行失败
4. memory / session 恢复失败

这些错误继续进入统一结果与事件语义，而不是在 client 侧被特殊分流。

## 日志与调试

硬切之后，日志策略也需要统一。

### Server 日志

server 负责：

1. runtime 执行日志
2. HTTP 入口日志
3. 启动与健康状态日志

### CLI 日志

CLI 只负责：

1. server 启动/复用日志
2. 连接与重试日志
3. SSE 连接诊断

CLI 不应把内部运行日志直接塞进 transcript。

### 与当前 stdio 特殊逻辑的关系

当前为 `stdio host` 引入的“stdout 不能混日志、stderr 只作诊断”的特殊约束，在 shared local HTTP server 架构下不再是主问题。

新的主边界是：

1. server 作为普通本地服务输出日志
2. CLI 通过 HTTP / SSE 与之通信
3. UI 消息和控制台日志严格分层

## 测试策略

### Runtime

runtime 需要覆盖：

1. `GET /health`
2. `GET /meta`
3. token 开关
4. `run` 接口
5. SSE 事件输出
6. session / memory 在 HTTP 主链下仍然有效

### CLI

CLI 需要覆盖：

1. 状态文件发现
2. server 启动与复用
3. 状态文件损坏后的恢复
4. token 注入
5. SSE 事件消费
6. 多 session 路由
7. 连接/启动错误的可见反馈

### 文档

文档需要覆盖：

1. 总架构文档不再把 `stdio` 视为 CLI 主线
2. `04-cli-streaming-and-output` 阶段文档改为 shared local server + HTTP + SSE
3. `AGENTS.md` 的仓库说明和验证命令同步更新
4. 相关实施 plan 与新架构一致

## 文档同步要求

### 总架构文档

以下表述需要改写：

1. “CLI 与 runtime 通过本地 `stdio` 协议通信”
2. “CLI 第一版最小闭环是启动本地 runtime 子进程并通过 stdin/stdout 往返消息”

应改为：

1. CLI 连接 shared local runtime server
2. runtime server 作为唯一真实入口
3. client 通过 HTTP + SSE 消费统一事件流

### 阶段文档

`04-cli-streaming-and-output.md` 需要同步改写：

1. 移除 `stdio` 主链假设
2. 补充 shared local server、状态文件、token、SSE
3. 明确 CLI 连接 runtime 的方式对齐 Kilo

### `AGENTS.md`

`AGENTS.md` 至少需要同步以下内容：

1. CLI 不再通过 `:runtime:installCliHostDist` 作为主链验证命令
2. 文档入口说明要反映新的 CLI/runtime 架构
3. 运行与测试命令要改为围绕 HTTP server 与 CLI 新链路组织

## 非目标

本次硬切设计明确不做以下事情：

1. 不在本轮引入 WebSocket-first 主协议
2. 不为浏览器 Web UI 单独再设计第二套 runtime
3. 不保留 `stdio` 作为长期兼容主链
4. 不在本设计中细化所有 Kotlin/TypeScript 实现细节
5. 不把 ACP 提前改成共享 server 的第二主线

## 风险与控制

### 风险 1：硬切导致 CLI 短期不可用

控制方式：

1. 先补 ServerManager、`/health`、`/meta`
2. 再补 HTTP run 与 SSE
3. 最后删除 `stdio` 链路

### 风险 2：多 CLI 并发启动打出多个 server

控制方式：

1. 引入锁文件或等效原子机制
2. 对状态文件读写做完整性校验
3. 健康检查失败时才允许重启

### 风险 3：memory 在 CLI 与 HTTP 下表现不一致

控制方式：

1. 明确 `RuntimeHttpServer` 为唯一正式执行入口
2. 所有 session 请求都经过同一条 server 主链
3. 用同一组 session 集成测试覆盖 CLI 与 HTTP

### 风险 4：文档和实现再次漂移

控制方式：

1. 本次先写独立设计 spec
2. 后续实施前先重写 `04` 阶段 spec / plan 与总架构文档
3. `AGENTS.md` 同步更新，避免未来继续按 `stdio` 误导开发

## 成功标准

完成迁移后，应满足：

1. CLI 启动后不再依赖 `RuntimeCliHost`
2. CLI 通过 shared local HTTP server 工作
3. 多个 CLI 会话可复用同一个 server
4. 外部手工 HTTP 调试和 CLI 走同一套 runtime 入口
5. memory / session 语义在 CLI 与 HTTP 下保持一致
6. `stdio` 主链和相关分发脚本、协议、测试被移除
7. 本地 docs 与 `AGENTS.md` 已同步到新架构
