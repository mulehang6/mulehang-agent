# 01 Runtime Foundation And Contracts

## 目标

先定义系统的 runtime 主轴，让后续所有能力都通过统一契约流动。

本阶段需要明确：

1. `session`
2. `request context`
3. `capability request`
4. `event`
5. `result`
6. `error boundary`

## 范围

本阶段只解决运行时骨架和接口问题：

1. 一次请求怎样进入系统
2. runtime 怎样组织上下文
3. capability 调用怎样被抽象
4. 结果和错误怎样被统一返回

## 关键边界

runtime 负责：

1. session 生命周期
2. request context
3. capability 装配
4. event / result / error 汇总

runtime 不负责：

1. provider 连接探测
2. Koog agent 具体装配
3. CLI 或 ACP 展示逻辑
4. memory 与持久化增强

## 核心对象

建议本阶段至少定义以下概念：

1. `RuntimeSession`
2. `RuntimeRequestContext`
3. `CapabilityRequest`
4. `RuntimeEvent`
5. `RuntimeResult`
6. `RuntimeFailure`

## 数据流

最小调用流应当是：

1. client surface 发起请求
2. runtime 创建 session
3. runtime 建立 request context
4. runtime 发起 capability request
5. runtime 汇总 event / result / error
6. 上层入口消费统一输出

## 非目标

本阶段明确不做以下事情：

1. 不提前实现 BYOK
2. 不提前实现 tool、MCP 或 direct HTTP
3. 不提前引入 CLI 输出细节
4. 不提前引入 ACP
5. 不提前引入 memory 或 snapshot

## 验证标准

完成本阶段后，应满足：

1. 后续每个阶段的新能力都能挂到这套 runtime 契约上
2. 不同入口不需要自己定义一套独立调用流
3. runtime 与 Koog agent、provider、client surface 的边界清楚
