# 02 Provider BYOK And Model Discovery

## 目标

把 BYOK 做成第一批正式能力，而不是普通配置读取。

系统必须支持：

1. 创建 `custom provider`
2. 为 `custom provider` 配置 `baseUrl`
3. 为 `custom provider` 配置 `apiKey`
4. 为 `custom provider` 选择提供商类型
5. 保存后自动执行连接探测
6. 探测成功后自动获取可用模型列表
7. 生成可供 runtime 消费的最终 binding

## 支持的提供商类型

1. `OpenAI-compatible`
2. `Anthropic-compatible`
3. `Gemini-compatible`

默认值为 `OpenAI-compatible`，但创建后允许修改。

## 范围

本阶段只解决 provider profile、探测、模型发现和 binding。

本阶段不解决：

1. Koog agent 装配
2. CLI 交互展示
3. ACP 协议桥接
4. memory / observability

## 关键边界

必须区分四类信息：

1. 用户输入的 `custom provider` 配置
2. 用户选择的提供商类型
3. 远端探测返回的事实
4. 运行时消费的最终 binding

Koog 官方 provider 与仓库内的 `custom provider` 也必须分开：

1. Koog 官方 provider 保持原义
2. `custom provider` 是仓库自己的配置模型
3. 解析 binding 时再把 `custom provider` 转成 Koog 可消费配置

## 适配点

本阶段至少要定义以下适配点：

1. `ConnectionProbeAdapter`
2. `ModelDiscoveryAdapter`
3. `BindingResolver`

这三个适配点都要按提供商类型分离实现。

## 数据流

最小调用流应当是：

1. 用户保存 `custom provider`
2. 系统按提供商类型执行连接探测
3. 探测成功后执行模型发现
4. 产出模型目录与默认选择
5. 解析最终 binding 并交回 runtime

## 非目标

本阶段明确不做以下事情：

1. 不直接修改 Koog 官方 provider
2. 不在 provider 层实现 CLI 反馈
3. 不在 provider 层实现 agent strategy
4. 不在 provider 层承担工具调用逻辑

## 验证标准

完成本阶段后，应满足：

1. 三种提供商类型都存在稳定的探测与模型发现路径
2. 提供商类型变更后会重新探测并刷新模型目录和 binding
3. runtime 上层不需要自己决定走哪套 probe / discovery 逻辑
