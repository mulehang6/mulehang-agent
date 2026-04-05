# 使用单一路径 OpenRouter Agent 与始终启用的 Tools Schema 的设计

## 目标

将当前 CLI 从“普通聊天路径”和“工具路径”两套执行链路，收敛为一条统一的 OpenRouter 流式主路径。

在新设计下：

1. 所有用户输入都进入同一个 streaming agent 主循环。
2. 每次请求都向 OpenRouter 发送 `messages + tools schema + reasoning`。
3. 模型自行决定是直接回答，还是返回 `tool_calls`。
4. 一旦出现 `tool_calls`，本地通过 Koog 工具系统执行，再把工具结果追加回消息继续下一轮。
5. 不再依赖关键词判断“这是不是工具问题”。

这个设计的核心是：让“是否需要工具”从本地路由规则转移到模型决策。

## 当前问题

- 目前存在普通聊天路径和工具路径两套链路，行为不一致。
- 工具模式依赖关键词路由，像“你当前在哪个目录”这种问题会被误判成工具请求。
- 当前自定义 OpenRouter 请求虽然支持回灌 `tool_call` 和 `tool_result` 消息，但首轮请求没有发送 `tools` 字段，模型并不知道自己有哪些工具可用。
- system prompt 当前只描述角色，不包含当前工作目录这类稳定运行时上下文。

## 根因判断

这次“模型不知道自己有工具”不是单一模型能力问题，而是请求协议不完整。

当前代码只做到了工具循环的后半段：

- 已能解析和执行 `tool_calls`
- 已能把 `tool_result` 回灌到后续消息

但缺少最关键的前提：

- 首轮请求没有把 `tools schema` 发给模型

没有 `tools schema`，模型只能把问题当普通文本问答来回答，即使本地已经准备好了工具执行器，也不会主动使用。

## 推荐方案

使用一个统一的 OpenRouter 流式 agent 路径，始终携带 tools schema，让模型自行决定是否调用工具。

### 保留的能力边界

- Koog 继续负责：
  - 工具定义与注册
  - 工具执行
  - 聊天历史存储
- 自定义 OpenRouter 路径继续负责：
  - 流式请求与 SSE 解析
  - reasoning 提取与 `<thinking>...</thinking>` 归一化
  - tool loop 控制
  - 工具消息与工具结果的 OpenRouter 序列化

### 去掉的设计

- 去掉 `shouldUseToolAgent(...)`
- 去掉基于关键词的“切换工具模式”
- 去掉“普通聊天 runner / 工具 runner”双入口

## 目标架构

### 1. 单一主执行器

CLI 每轮输入都调用一个统一入口，例如：

- `runStreamingAgent(...)`

这个入口负责：

1. 加载当前 session 历史
2. 构造请求体
3. 发送 OpenRouter 流式请求
4. 处理 reasoning / content / tool calls
5. 如有工具调用，则执行工具并继续下一轮
6. 最终持久化 assistant 回复

### 2. 请求体始终携带 Tools Schema

每次发往 OpenRouter 的请求都应包含：

- `model`
- `temperature`
- `stream`
- `reasoning`
- `messages`
- `tools`

这里的 `tools` 来自当前 Koog `ToolRegistry` 的工具定义转换，而不是手写一份独立 schema。

### 3. 模型自己决定是否调用工具

对于任意输入：

- 如果模型直接返回正文，没有 `tool_calls`，则这一轮直接结束。
- 如果模型返回 `tool_calls`，则执行相应工具，并将工具结果作为 `role=tool` 消息追加回 transcript。
- 之后继续发起下一轮 OpenRouter 请求，直到模型停止请求工具或触发循环上限。

### 4. CWD 通过 System Prompt 注入

当前工作目录不需要设计成单独工具。

它属于稳定的运行时上下文，应直接写入 system prompt，例如：

- 你的当前工作目录是 `D:/JetBrains/projects/idea_projects/mulehang-agent`

这样模型在回答“你当前在哪个目录”时不需要先决定是否调用工具。

## 数据流

### 普通回答

1. 读取 session 历史
2. 组装带 `tools` 的请求
3. 流式输出 reasoning 与正文
4. 没有 `tool_calls`
5. 持久化最终 assistant 回复

### 带工具调用的回答

1. 读取 session 历史
2. 组装带 `tools` 的请求
3. 流式输出 reasoning 与正文
4. 收集 `tool_calls`
5. 执行 Koog 工具
6. 把工具结果作为 `Message.Tool.Result` 追加到 transcript
7. 再发起下一轮带 `tools` 的 OpenRouter 请求
8. 直到不再有 `tool_calls`
9. 持久化最终 assistant 回复

## Prompt 策略

system prompt 只承担两类职责：

1. 角色约束
2. 稳定环境上下文

当前阶段只额外加入：

- 当前工作目录

本设计明确不做的事：

- 不在 system prompt 里重复罗列工具 schema
- 不用自然语言手工描述每个工具的参数格式

工具能力的正式定义只通过 `tools schema` 传给模型。

## 错误处理

### Provider 错误

- OpenRouter 返回非 200 时，直接报错。
- 只有明确命中现有 400 回退策略时，才回退到非流式聊天请求。
- 不再有“工具模式回退到另一条工具智能体链路”的概念。

### 工具执行错误

- 工具执行失败仍应转换成 `tool result` 消息返回给模型。
- 这样模型可以在后续回复中解释失败原因或尝试调整策略。

### 循环保护

- 保留最大工具调用轮数限制，防止无限循环。

## 测试策略

### 单元测试

- 请求体包含 `tools` 字段
- `Message.Tool.Call` 和 `Message.Tool.Result` 能正确序列化为 OpenRouter 格式
- 统一主循环在无工具调用时直接结束
- 统一主循环在有工具调用时能正确执行工具并继续下一轮
- 去掉关键词路由后，所有请求都走统一主路径
- “你当前在哪个目录”这类问题不会再因为关键词命中而切入特殊工具模式

### 集成验证

- 问“你当前在哪个目录”，模型可以直接回答当前工作目录
- 问“读取 build.gradle.kts 并总结 plugins”，模型能发起工具调用
- 工具调用前后的文本保持流式
- 工具结果保持块级输出

## 范围边界

本轮设计不包含：

- 重新引入第二条 Koog agent 执行链
- 在 system prompt 中手写工具说明文档
- 长期记忆或用户画像系统
- 多 provider 通用抽象

## 成功标准

- 项目中不再存在基于关键词切换“工具模式”的路由逻辑
- 所有 OpenRouter 请求都携带 `tools schema`
- 模型能够在统一主路径中自主触发工具调用
- “当前工作目录”问题可以直接回答，不需要额外工具
- 现有 `<thinking>` 流式归一化逻辑继续保持稳定
