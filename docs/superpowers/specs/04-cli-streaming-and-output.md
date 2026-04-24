# 04 CLI Streaming And Output

## 目标

把 CLI 做成第一主入口，并让它稳定消费 runtime 的统一输出。

本阶段重点包括：

1. CLI 输入解析
2. runtime 调用发起
3. streaming 输出
4. structured output 呈现
5. 状态提示
6. 用户可理解的错误展示

## 范围

CLI 是第一主入口，但它只负责入口与呈现。

CLI 在工程结构上应作为独立 `cli` Gradle 模块存在，并通过模块依赖消费 `runtime` 暴露的契约与执行能力。

CLI 不负责：

1. provider 探测逻辑
2. capability 实现细节
3. ACP 协议桥接

## 关键边界

CLI 只消费：

1. `RuntimeEvent`
2. `RuntimeResult`
3. `RuntimeFailure`
4. `runtime` 模块对外暴露的稳定调用入口

CLI 不应该知道底层到底走的是：

1. local/custom tool
2. MCP
3. direct HTTP internal API

## 输出模型

本阶段需要统一至少三类可见输出：

1. 流式事件
2. 结构化结果
3. 错误与状态提示

## 非目标

本阶段明确不做以下事情：

1. 不在这一阶段引入 ACP
2. 不提前设计桌面端或 Web UI
3. 不在 CLI 内部重建第二套 runtime
4. 不把 CLI 代码直接合并进 `runtime` 模块

## 模块边界

本阶段默认采用以下工程边界：

1. `runtime` 模块继续承载 runtime、provider、capability、agent 和当前宿主集成
2. `cli` 模块承载命令解析、流式输出、structured output 呈现和用户可见错误
3. `cli` 通过 `implementation(project(":runtime"))` 依赖 `runtime`
4. `cli` 不直接依赖 provider 底层实现或 capability adapter 细节，只依赖 runtime 暴露的契约与入口

## 验证标准

完成本阶段后，应满足：

1. CLI 可以作为第一主入口稳定触发完整调用流
2. streaming 与 structured output 有统一用户可见表现
3. 错误展示与状态提示不依赖底层 capability 类型
