# 2026-06-16 Kilo Style Multi Turn Chat Design

## 背景

当前桌面聊天界面已经具备多会话 UI、流式消息展示、reasoning 展示和 tool 事件展示，但底层发送链路仍然是单轮模式。

现状的关键问题不是 UI 不能连续聊天，而是模型侧每次请求只发送当前 `prompt`，没有把当前会话里已经发生过的用户消息、助手输出、reasoning 和 tool 语义一起回灌给下一轮请求。

这导致当前产品虽然在展示层看起来像“对话”，在模型语义上实际上仍然是一次次彼此割裂的单轮请求。

本轮设计的目标不是做一个简化版“文本历史拼接”，而是明确按 kilo 的做法，引入一套独立于 UI 时间线的结构化会话历史模型，把真正给模型看的历史与当前页面展示状态分离维护。

## 目标

本轮设计的目标如下：

1. 将当前活动会话从单轮请求改造为真正的多轮对话。
2. 采用接近 kilo 的结构化历史语义，而不是从 UI 展示项反推聊天历史。
3. 把用户消息、助手正文、reasoning、tool 语义纳入会话历史。
4. 保持当前 `ChatWindowState` 和时间线 UI 的展示行为基本不回退。
5. 先打通当前实际使用的 `DeepSeek + openai-chat-completions` 链路。

## 非目标

本轮不做以下内容：

1. 不做会话持久化，关闭应用后不要求恢复多轮历史。
2. 不同时补齐 `openai-responses`、`anthropic`、`google` 全部 provider 分支。
3. 不把当前 UI 中的 `status` 事件纳入模型历史。
4. 不在这一轮补齐 kilo 全量协议细节，例如稳定的 `tool_call_id` 或 provider 级 hosted tool 特殊编码。
5. 不重做当前聊天界面的布局、样式或交互结构。

## 当前实现问题

当前实现中，`composeApp` 的 `ChatWindowState` 已经维护了多会话和完整时间线，但底层发送链路仍然只接受一个 prompt：

1. `SendMessageUseCase` 当前主要转发 `AgentRunRequest(prompt, profile, reasoningEffort)`。
2. `KoogAgentGateway` 当前会把请求构造成只包含一条 `user(prompt)` 的消息。
3. 当前时间线里的 `ChatMessageItem`、`ReasoningItem` 和 `ToolEventItem` 主要是 UI 展示模型，不是模型历史模型。

因此，当前问题的根源不在 UI，而在“给模型看的历史模型”缺位。

## 设计原则

### 1. 模型历史与 UI 时间线分离

本轮必须把“给模型看的历史”与“给用户看的时间线”分离维护。

UI 时间线负责：

1. 消息列表渲染
2. reasoning 展开/折叠状态
3. tool 事件的时间线展示
4. status 文案展示

结构化历史负责：

1. 定义真实的对话顺序
2. 表达 assistant 的多种 part 语义
3. 作为下一轮请求的唯一历史来源

后续发送请求时，不能再从 `ConversationItem` 反推历史。

### 2. 采用 kilo 风格的 assistant parts 语义

本轮不把 assistant 历史压扁成一段纯文本，而是允许 assistant 消息内部携带多个 part。至少需要支持：

1. text
2. reasoning
3. tool call
4. tool result

这不是为了过度设计，而是为了避免把 UI 展示块错误地当成模型语义块。

### 3. 当前 provider 先打通，架构为后续 provider 留口

当前项目实际使用的是 `DeepSeek + openai-chat-completions`。因此这一轮的实现目标应收敛为：

1. 设计上按可扩展 provider 适配写
2. 实现上先保证 DeepSeek 链路可用
3. 其它 provider 本轮只保留接口和边界，不强行补齐

### 4. status 不是模型历史

当前 `AgentStreamEvent.Status` 更像运行时状态或调试提示，而不是稳定的 assistant 语义。因此本轮明确规定：

1. `status` 继续保留在 UI 展示层
2. `status` 不进入结构化历史
3. 下一轮请求不回灌 `status`

## 数据模型与状态边界

### 结构化会话历史

需要在 `shared` 内新增一套结构化会话历史模型，用于表达当前会话已经累积的真实历史。

该模型的基本语义如下：

1. 一个会话包含一组有序历史消息
2. 每条历史消息有明确 role
3. `user` 消息至少承载文本输入
4. `assistant` 消息包含一组有序 parts
5. assistant parts 至少支持 `text`、`reasoning`、`tool call`、`tool result`

这套模型的职责不是 UI 展示，而是为下一轮请求生成 provider 历史。

### UI 时间线继续存在，但降级为投影层

当前 `ConversationItem`、`ChatMessageItem`、`ReasoningItem`、`ToolEventItem` 继续保留，但其职责调整为：

1. 只服务于当前页面渲染
2. 不再承担“请求历史来源”的职责
3. 由结构化历史和流式事件共同投影生成

### ChatWindowState 的双轨维护职责

`ChatWindowState` 在本轮之后需要同时维护两份状态：

1. 结构化会话历史
2. 现有 UI 时间线状态

其中：

1. 结构化历史负责下一轮请求的真实上下文
2. UI 时间线负责当前展示
3. 两者共享同一批流式事件输入，但不共享数据模型

## 发送链路设计

### AgentRunRequest 从单轮 prompt 升级为历史化请求

`AgentRunRequest` 不应再只描述一次裸 prompt，而需要同时描述：

1. 当前轮发送前的结构化历史
2. 当前轮用户输入
3. 当前 profile
4. 当前 reasoning effort

当前用户输入仍然单独保留，不要求调用方先手动把当前输入塞进历史里再发请求。

### ChatWindowState 的发送流程

`ChatWindowState.sendDraft()` 在本轮之后的目标流程如下：

1. 读取当前活动会话的结构化历史
2. 以“已有历史 + 当前用户输入”构造 `AgentRunRequest`
3. 先把当前用户消息写入 UI 时间线
4. 同时把当前用户消息写入结构化历史
5. 启动流式请求
6. 在流式过程中持续补全当前轮 assistant message 的结构化 parts 与 UI 投影

### DeepSeek 请求生成方式

对本轮实际要打通的 `DeepSeek + openai-chat-completions` 分支，底层不再构造单条 `user(prompt)`，而是要按顺序构造完整历史消息列表。

其语义顺序应为：

1. 历史 user messages
2. 历史 assistant message parts 映射后的 assistant messages
3. 当前轮用户输入作为最后一条 user message

发送给 provider 的历史来源必须是结构化历史，而不是 UI item。

## 流式事件到结构化历史的映射

### TextDelta

`AgentStreamEvent.TextDelta` 进入当前轮 assistant message 的 text part。

其规则如下：

1. 连续 text delta 追加到同一个文本 part
2. UI 侧继续合并成当前助手正文块
3. 结构化历史中保留为 assistant text 语义

### ReasoningDelta 与 ReasoningCompleted

`AgentStreamEvent.ReasoningDelta` 和 `AgentStreamEvent.ReasoningCompleted` 进入当前轮 assistant message 的 reasoning part。

其规则如下：

1. reasoning 与正文分离存储
2. UI 侧继续展示为 `ReasoningItem`
3. reasoning 不与 assistant 最终正文混成一段纯文本

### ToolCallStarted

`AgentStreamEvent.ToolCallStarted` 进入当前轮 assistant message 的 tool-call part。

本轮记录的最小必要信息如下：

1. tool name
2. arguments preview
3. 调用状态

UI 侧继续落成 tool started 时间线项。

### ToolCallFinished

`AgentStreamEvent.ToolCallFinished` 进入当前轮 assistant message 的 tool-result part。

本轮记录的最小必要信息如下：

1. tool name
2. result preview
3. 完成状态

UI 侧继续落成 tool finished 时间线项。

### Status

`AgentStreamEvent.Status` 不进入结构化历史。

其规则如下：

1. 只保留在 UI 时间线或调试展示
2. 不作为 assistant history part
3. 不回灌到下一轮模型请求

## 与 kilo 的对齐边界

本轮的目标是按 kilo 的消息语义工作，而不是一次性复制 kilo 的全部协议细节。

本轮与 kilo 对齐的部分：

1. 模型历史与 UI 时间线分离
2. assistant 以多 part 形式表达历史
3. tool 与 reasoning 都进入 assistant 历史
4. 下一轮请求基于结构化历史生成

本轮暂不完全对齐 kilo 的部分：

1. 不要求完整的 provider 级 hosted tool 编码
2. 不要求稳定的 `tool_call_id`
3. 不要求一次打通所有 provider 协议分支

这意味着本轮是“按 kilo 的核心语义做”，而不是“逐字段复刻 kilo 内部实现”。

## 测试策略

### shared 层测试

`shared` 层测试应优先覆盖：

1. 结构化历史在多轮发送中持续累积
2. `SendMessageUseCase` 会转发“历史 + 当前输入”
3. `TextDelta`、`ReasoningDelta`、`ToolCallStarted`、`ToolCallFinished`、`Status` 的归类规则
4. 当前轮 assistant 流式事件能正确回填到结构化历史

### composeApp 层测试

`composeApp` 层测试应优先覆盖：

1. 同一会话连续两轮发送时，第二轮请求读取第一轮历史
2. 切换会话时，各会话结构化历史互不污染
3. 引入结构化历史后，现有 UI 时间线展示不回退

## 验收标准

本轮完成后，至少应满足以下标准：

1. 同一会话连续发送两轮时，第二轮请求不再是裸 `prompt`，而是包含第一轮结构化历史。
2. 第一轮中的用户消息、助手正文、reasoning 和 tool 语义会进入会话历史。
3. `status` 事件只用于展示，不进入模型历史。
4. 不同会话之间的结构化历史相互隔离。
5. 当前实际使用的 `DeepSeek + openai-chat-completions` 链路通过测试并可工作。
6. 当前 UI 时间线中的消息、reasoning 和 tool event 展示行为不退化。
7. 本轮不做持久化，重启后不要求恢复多轮历史。
8. 其它 provider 分支本轮不要求实现完成，但设计边界需要保留。

## 本轮结论

本轮不采用“从 UI 时间线反推聊天历史”的方案，而是明确引入 kilo 风格的结构化会话历史模型。

真正的多轮能力将建立在以下基础上：

1. 结构化历史是模型上下文的真相源
2. UI 时间线只是展示投影
3. assistant 历史保留 text、reasoning、tool call、tool result 等 part
4. 当前先打通 DeepSeek 链路，后续再扩展其它 provider
