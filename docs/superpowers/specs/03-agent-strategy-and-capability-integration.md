# 03 Agent Strategy And Capability Integration

## 目标

把 JetBrains Koog 真正接入系统，并建立统一的 capability integration 边界。

本阶段至少要完成：

1. 最小 `AIAgent` 装配
2. `singleRunStrategy`
3. 为后续 workflow / graph strategy 留出演进空间
4. local/custom tool 接入
5. MCP 接入
6. direct HTTP internal API 接入

## 范围

本阶段只解决 agent 语义和能力接入，不解决：

1. CLI 输出体验
2. ACP 协议桥接
3. memory / snapshot / tracing

## 关键边界

Koog 负责：

1. `AIAgent`
2. strategy / workflow
3. tool 调用语义
4. feature 安装点

仓库层负责：

1. capability contract
2. capability adapter
3. binding 和 runtime 装配

agent 必须消费统一 capability contract，而不是直接依赖接入细节。

## Capability Integration

本阶段至少区分三类接入：

1. local/custom tools
2. MCP-backed tools
3. direct HTTP internal API adapters

这三类接入的底层实现可以不同，但都要通过统一接口交给 runtime 和 agent。

## 数据流

最小调用流应当是：

1. runtime 准备 binding 与 capability set
2. runtime 组装 Koog `AIAgent`
3. strategy 接收请求
4. strategy 按需要触发 capability
5. capability adapter 返回结果
6. runtime 汇总结果与错误

## 非目标

本阶段明确不做以下事情：

1. 不提前做 CLI 交互美化
2. 不把 ACP 当成 capability 接入
3. 不在本阶段引入 memory feature

## 验证标准

完成本阶段后，应满足：

1. `AIAgent` 与 `singleRunStrategy` 已经接入 runtime
2. tool、MCP、direct HTTP 三类能力有统一 contract
3. 新能力接入时不需要改 CLI 或 client surface
