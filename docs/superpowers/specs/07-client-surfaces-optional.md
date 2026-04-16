# 07 Client Surfaces Optional

## 目标

为未来的 KMP desktop 和 Web UI 预留 client surface，但不把它们纳入当前核心主线。

## 范围

本阶段只定义扩展边界：

1. 新 client surface 怎样接入 runtime
2. 怎样复用已有协议和输出模型
3. 怎样避免重写 agent 核心

## 关键边界

未来 client surface 必须复用：

1. runtime 主轴
2. provider / binding 体系
3. Koog agent 主线
4. 已有协议或输出契约

未来 client surface 不应重写：

1. provider 模型
2. capability integration
3. ACP 或 CLI 的核心内部逻辑

## 候选表层

当前明确的候选方向：

1. KMP desktop
2. Web UI

## 非目标

本阶段明确不做以下事情：

1. 不把桌面端或 Web UI 提前纳入第一轮核心交付
2. 不为了未来表层需求破坏当前 runtime 设计

## 验证标准

完成本阶段后，应满足：

1. client surface 的接入边界已经清楚
2. 未来实现桌面端或 Web UI 时不需要重写核心架构
