# 04 CLI Streaming And Output

## 目标

把 CLI 做成第一主入口，并让它稳定消费 runtime 的统一输出。

当前阶段的 CLI 不再指普通一次性命令行，而是以接近 Kilo 的交互式 CLI / TUI 为目标。

本阶段重点包括：

1. CLI 输入解析
2. runtime 调用发起
3. streaming 输出
4. structured output 呈现
5. 状态提示
6. 用户可理解的错误展示

## 范围

CLI 是第一主入口，但它只负责入口与呈现。

CLI 在工程结构上应作为独立模块存在。当前优先方案是：

1. `runtime` 模块继续使用 Kotlin/JVM
2. `cli` 模块使用 TypeScript + Bun + OpenTUI
3. `cli` 通过本地 `stdio` 协议消费 `runtime` 暴露的能力，而不是直接依赖 JVM 内部类

当前第一版最小闭环已经明确为：

1. 启动 `cli`
2. `cli` 启动本地 `runtime` 子进程
3. `cli` 通过 `stdin` 发送一条用户输入
4. `runtime` 通过 `stdout` 连续返回 `status`、`event`、`result` 或 `failure`
5. TUI 把这些消息稳定显示到会话区和状态区

CLI 不负责：

1. provider 探测逻辑
2. capability 实现细节
3. ACP 协议桥接

样式技术边界也需要明确：

1. 当前 `cli` 是终端 TUI，OpenTUI 渲染不使用 Tailwind CSS
2. 如果后续补充浏览器调试面板、文档站或 Web client surface，可默认采用 Tailwind CSS 4 最新稳定版
3. 浏览器前端样式方案不应成为当前 CLI/TUI 落地的前置条件

## 关键边界

CLI 只消费：

1. `runtime` 暴露的稳定协议
2. 事件流
3. 结构化结果
4. 错误与状态反馈

CLI 不应该知道底层到底走的是：

1. local/custom tool
2. MCP
3. direct HTTP internal API

## 输出模型

本阶段需要统一至少三类可见输出：

1. 流式事件
2. 结构化结果
3. 错误与状态提示

如果实现为 TUI，还应额外满足：

1. 会话区与输入区分离
2. 状态区可见当前模型、上下文和运行状态
3. streaming 输出在界面上连续追加，而不是每轮整屏重绘式刷日志

### Thinking / Reasoning 流式输出

对于支持 thinking / reasoning delta 的模型，runtime 必须把 thinking 文本作为一等流式输出传给 CLI，而不是只写入日志、只更新状态栏，或等最终结果出来后再一次性补齐。

协议层不把 thinking 绑定到某个 provider 的私有字段，而是统一映射为 runtime event：

1. 普通回答增量使用 `channel = "text"`
2. thinking / reasoning 增量使用 `channel = "thinking"`
3. tool 调用提示使用 `channel = "tool"`
4. 一般运行状态提示使用 `channel = "status"`
5. 最终结构化结果仍然通过 `result` 消息返回

TUI 必须把 `thinking` channel 的文本连续追加到会话区中。第一版呈现为一个可折叠的 `thinking` 区块，并且默认展开；后续用户折叠后，新到达的 thinking delta 仍然追加到同一块内容中，但不应强制重新展开用户已经折叠的区块。

当底层模型不提供 thinking / reasoning delta 时，runtime 不需要伪造 thinking 内容；此时只显示普通 `text` 流和最终结果。

## 非目标

本阶段明确不做以下事情：

1. 不在这一阶段引入 ACP
2. 不提前设计桌面端或 Web UI
3. 不在 CLI 内部重建第二套 runtime
4. 不把 CLI 代码直接合并进 `runtime` 模块
5. 不让 OpenTUI 前端直接依赖 Kotlin 内部实现

## 模块边界

本阶段默认采用以下工程边界：

1. `runtime` 模块继续承载 runtime、provider、capability、agent 和当前宿主集成
2. `cli` 模块承载命令解析、交互式 TUI、流式输出、structured output 呈现和用户可见错误
3. `cli` 采用 TypeScript + Bun + OpenTUI
4. `cli` 通过本地 `stdio` 协议与 `runtime` 进程通信
5. `cli` 不直接依赖 provider 底层实现或 capability adapter 细节，只依赖 runtime 暴露的协议与入口

## 协议边界

为了支撑交互式 TUI，本阶段应优先定义一套最小本地协议：

1. `cli -> runtime` 的用户输入请求
2. `runtime -> cli` 的流式事件
3. `runtime -> cli` 的最终结果
4. `runtime -> cli` 的结构化失败

第一版协议优先使用 `stdin/stdout` 文本流承载，并保持逐条事件可流式消费。

流式事件必须能区分普通回答文本、thinking/reasoning 文本、工具状态和一般状态提示，避免 CLI 只能看到不可分类的 `event.message`。推荐的最小事件载荷为：

```json
{
  "type": "event",
  "sessionId": "session-1",
  "requestId": "request-1",
  "event": {
    "message": "agent.reasoning.delta",
    "channel": "thinking",
    "delta": "I need to inspect the available context first."
  }
}
```

其中 `message` 保持机器可读事件名，`channel` 决定 TUI 展示区域，`delta` 承载可连续追加的文本片段，`payload` 继续用于非文本结构化载荷。

为了先打通链路，第一版允许在未提供 provider binding 时走 `demo` 模式：

1. `runtime` 返回可预测的 `status -> event -> result` 消息序列
2. 先验证协议、子进程桥和 TUI 展示链路
3. 后续再把真实 provider/agent streaming 映射接到同一协议上

## 验证标准

完成本阶段后，应满足：

1. CLI 可以作为第一主入口稳定触发完整调用流
2. streaming 与 structured output 有统一用户可见表现
3. 错误展示与状态提示不依赖底层 capability 类型
4. 在没有真实 provider 配置时，最小 demo 闭环也能独立运行
