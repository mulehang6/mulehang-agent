# 05 ACP Protocol Bridge

## 目标

把 ACP 作为第二入口桥接到同一套 runtime 与 Koog agent 主线。

本阶段重点包括：

1. Koog `AcpAgent` feature
2. ACP session bridge
3. ACP 事件映射
4. runtime 生命周期到 ACP 生命周期的映射

## 范围

本阶段只解决协议桥接，不解决：

1. provider 模型
2. capability 实现细节
3. CLI 主入口的行为定义

## 关键边界

ACP 是第二入口，不是第二套 runtime。

它必须复用：

1. 已有 runtime 主轴
2. 已有 binding 与 capability set
3. 已有 Koog agent 装配

它只增加：

1. ACP session 模型
2. ACP 事件桥接
3. 协议级消息转换

## 数据流

最小调用流应当是：

1. ACP client 发起会话请求
2. ACP bridge 建立或关联 runtime session
3. Koog `AcpAgent` feature 转发 agent 事件
4. ACP client 消费统一事件流

## 非目标

本阶段明确不做以下事情：

1. 不把 ACP 提前提升为第一主入口
2. 不为了 ACP 改写 CLI 调用流
3. 不在 ACP 层新增独立状态模型

## 验证标准

完成本阶段后，应满足：

1. ACP 和 CLI 共用同一套 runtime 主轴
2. ACP 事件与 session 生命周期映射清楚
3. ACP 只作为协议桥接层存在
