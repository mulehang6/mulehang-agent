# 04 CLI Streaming And Output

## 目标

把 CLI 稳定确立为第一主入口，并让它通过 shared local runtime HTTP server 消费统一的流式输出。

当前阶段的 CLI 不是一次性命令包装，而是接近 Kilo 风格的交互式 CLI / TUI：

1. 输入 prompt 或本地 `/` 命令
2. 连接或拉起共享本地 `RuntimeHttpServer`
3. 通过 HTTP + SSE 发起请求并消费事件流
4. 把 thinking、text、result、failure 映射成用户可理解的终端界面

## 范围

本阶段聚焦以下能力：

1. CLI 输入解析与提交
2. shared local server 发现、复用与必要时拉起
3. HTTP 请求与 SSE 流式事件消费
4. structured output 呈现
5. 状态提示与错误展示
6. 会话级 transcript 维护

CLI 依然只是入口与呈现层，不负责 provider 探测、capability 实现和 ACP 桥接。

## 工程形态

当前主链采用以下边界：

1. `runtime` 模块继续使用 Kotlin/JVM，正式入口是本地 `RuntimeHttpServer`
2. `cli` 模块使用 TypeScript + Bun + OpenTUI/React 承载交互式 TUI
3. `cli` 不直接依赖 JVM 内部类，只依赖 shared local server 暴露的 HTTP/SSE 契约
4. `cli` 通过状态文件、`/meta` 探针和本地 token 认证发现或复用共享 server
5. 必要时 `cli` 自动触发 `:runtime:installDist`，确保本地 runtime 分发脚本已生成

这里的关键变化是：`stdio` 子进程桥接不再是 CLI 正式主链，只保留为历史设计背景，不再写入当前阶段架构要求。

## 关键边界

CLI 只消费这些稳定边界：

1. `POST /runtime/run/stream`
2. `POST /runtime/run`
3. `GET /meta`
4. 统一的状态、事件、结果、失败语义

CLI 不应该知道底层到底走的是：

1. local/custom tool
2. MCP
3. direct HTTP internal API

这些差异都留在 runtime、capability 和 agent 装配层内部。

## 流式输出模型

本阶段至少要稳定处理以下事件：

1. `status`
2. `thinking.delta`
3. `text.delta`
4. `run.completed`
5. `run.failed`

推荐语义如下：

1. `status` 用于会话开始、状态切换和可见提示
2. `thinking.delta` 连续追加到可折叠的 thinking 区块
3. `text.delta` 连续追加到 assistant 回复区块
4. `run.completed` 输出最终结构化结果，并把状态切到完成
5. `run.failed` 输出一行失败摘要，并把状态切到失败

thinking 区块默认展开；如果用户已经折叠，后续 delta 仍然追加到同一块内容，但不强制重新展开。

## Shared Local Server 要求

shared local runtime server 必须满足：

1. 不与单个 CLI 窗口绑定生命周期
2. 可通过状态文件复用已有实例
3. 可通过 `/meta` 快速健康检查
4. 默认启用本地 token 认证
5. 对 CLI 暴露稳定的协议版本

CLI 负责发现和复用；runtime 负责真正执行请求。

## 输出与界面要求

如果实现为 TUI，本阶段至少满足：

1. 会话区与输入区分离
2. 状态区可见当前模式、会话状态和基础元信息
3. streaming 输出连续追加，而不是把日志整屏刷出
4. 失败信息以用户可读摘要展示，而不是直接暴露堆栈
5. `/` 命令与普通 prompt 共用同一套输入体验

## 非目标

本阶段明确不做以下事情：

1. 不在这一阶段引入 ACP
2. 不回到 `stdio` 单行 JSON 主链
3. 不让 CLI 直接依赖 Kotlin runtime 内部类
4. 不在 CLI 内部重建第二套 provider / capability / agent 逻辑
5. 不把浏览器 UI 设计约束带进终端 TUI

## 前端技术边界

1. 当前第一主入口是终端 TUI，渲染层依赖 OpenTUI，不采用浏览器 CSS 方案
2. 如果后续新增 Web UI、浏览器 companion、官网或其他浏览器前端，默认采用 Tailwind CSS 4 最新稳定版
3. Tailwind CSS 的引入不应反向影响当前 `cli` 与 `runtime` 的 local HTTP/SSE 边界

## 验证标准

完成本阶段后，应满足：

1. CLI 可以稳定发现或启动 shared local runtime server
2. CLI 可以通过 HTTP + SSE 触发完整调用流
3. thinking、text、result、failure 都有统一且可见的终端呈现
4. runtime 与 CLI 的边界以 shared local server 契约为准，而不是 `stdio` 文本流
5. `bun test`、`bun run typecheck` 与 `:runtime:test` 能作为该阶段主验证命令
