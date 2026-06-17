# 2026-06-16 Desktop Tooling Design

## 背景

当前桌面聊天已经具备：

1. 多会话 UI
2. 工作区分组
3. 流式正文、reasoning 和 tool 时间线展示
4. profile / model / permission preset 等基础控制

但底层执行链仍然只有“模型输出文本或 provider 原生 tool frame 映射”的能力，还没有真正把本地工具注册给 agent 执行。

这导致当前的 `ToolEventItem` 更多只是展示层投影，而不是“agent 真能调用本地工具”的产品能力。尤其是内置交互工具 `sayToUser` / `askUser` 的默认语义基于终端 `stdout/stdin`，不适合当前 Windows Desktop + Compose 聊天界面。

本轮设计的目标不是机械照搬 Koog 的终端内置工具，而是建立一套贴合当前桌面产品的工具执行体系：

1. 底层通过 Koog agent 真正执行本地工具
2. 只读工具默认免确认
3. 写入和命令执行走明确的桌面权限模型
4. `askUser` 通过聊天面板内嵌交互卡片完成，并在同一轮次中恢复执行

## 目标

本轮设计目标如下：

1. 将当前单纯的 prompt executor 执行链升级为支持本地工具的 agent 执行链。
2. 首批接入一组桌面可用的内置工具，而不是终端语义的 `stdin/stdout` 版本。
3. 保持工具调用过程可在当前聊天时间线中被理解、被确认、被恢复。
4. 明确只读、写入、执行三类工具的权限和路径边界。
5. 为后续继续扩展更多本地工具或 MCP 风格工具保留稳定架构。

## 非目标

本轮不做以下内容：

1. 不复刻 Koog 或 kilo 的全部工具协议字段。
2. 不在本轮引入 MCP bridge 或外部 server 工具。
3. 不实现跨应用或跨机器的远程执行。
4. 不做 shell 多平台兼容；命令执行只支持 PowerShell 7。
5. 不把所有潜在工具一次接齐，例如 git、browser、task delegation 等高阶能力。
6. 不在本轮实现完整的工具持久化审计页或工具设置页。

## 方案对比

本轮评估过三种实现路线：

### 方案 A：升级为真正的 Koog agent + ToolRegistry

把当前 `KoogAgentGateway` 从单纯的 `PromptExecutor` 流式转发升级为真正的 `AIAgent + ToolRegistry` 执行链。工具注册、权限校验、交互恢复都在同一条 agent run 内完成。

优点：

1. 与 Koog 的 tool execution 语义一致
2. `askUser` 这种“挂起并恢复”能力更自然
3. 后续扩展更多工具不需要重复发明协议

缺点：

1. 需要改造当前 gateway 和事件模型
2. 首轮实现复杂度高于只做展示增强

### 方案 B：保留现有 prompt executor，自行实现 tool loop

继续让模型输出 tool call，再由本地解析、执行、把 tool result 拼回下一次请求。

优点：

1. 表面改动较小

缺点：

1. 本质上会自建一套半 agent 协议
2. `askUser` 恢复、迭代上限、错误处理都需要自己维护
3. 长期维护成本高

### 方案 C：普通对话与工具对话双轨并存

无工具时走当前链路，调用工具时切到另一条 agent 链路。

优点：

1. 迁移温和

缺点：

1. 维护两套执行路径
2. 状态和测试成本显著增加

### 结论

本轮采用 **方案 A**。

原因是本轮核心诉求已经不是“展示 tool 事件”，而是“本地工具真实可执行，且 `askUser` 能在当前轮次内挂起并恢复”。这类能力放在真正的 agent/tool 执行链中最稳。

## 设计原则

### 1. 终端交互工具必须产品化重写

`sayToUser` / `askUser` 的默认 `stdout/stdin` 语义不适用于桌面聊天产品。本轮不直接复用它们的默认实现，而是做项目自己的桌面交互工具。

### 2. 工具能力分层而不是逐个散写规则

工具权限和边界不应按单个工具临时判断，而应先分为三类：

1. `Read`
2. `Write`
3. `Execute`

后续新增工具时，只需要先归类，再复用已有策略。

### 3. `askUser` 是挂起点，不是普通 assistant 文本

`askUser` 不应伪装成一条普通消息，而应成为显式的挂起请求，由 UI 渲染专门卡片，并在用户回复后恢复当前轮次。

### 4. 当前轮次恢复优先于“新开一轮消息”

用户在 `askUser` 卡片上的回答不视为新一轮 user prompt，而视为当前工具调用的返回值。回复后继续同一轮 agent run。

### 5. 只读工具默认允许，但仍需预算和失败分类

只读工具不需要权限确认，也允许读取工作区外路径；但实现上仍需控制扫描预算、区分失败原因，防止无界搜索和误判。

## 执行架构

### DesktopAgentGateway

新增桌面化 agent 执行入口，替代当前仅负责流式转发的 `KoogAgentGateway`。

其职责如下：

1. 基于当前 profile 创建 Koog agent
2. 注入当前会话可用的 `ToolRegistry`
3. 把 agent 运行过程转换为当前应用可消费的 `AgentStreamEvent`
4. 协调工具挂起、审批、恢复与失败处理

### MulehangToolRegistryFactory

新增工具注册表工厂，根据当前会话上下文构建本轮可用工具集合。

上下文至少包含：

1. 当前工作区路径
2. 当前 `PermissionPreset`
3. 文件系统与搜索实现
4. UI 交互桥

### DesktopToolInteractionBridge

新增桌面交互桥，用于让工具层向 UI 发起挂起请求，而不直接依赖 Compose 组件。

其职责包括：

1. 请求用户回答问题
2. 请求用户确认写入
3. 请求用户确认命令执行
4. 在用户提交或拒绝后恢复对应挂起操作

### 单轮执行语义

一次 agent run 可以经历以下阶段：

1. 模型输出文本
2. 模型调用只读工具
3. 模型请求 `askUser`
4. UI 展示问题卡片并等待用户输入
5. 用户回复
6. 当前轮次继续执行
7. 最终完成或失败

也就是说，本轮之后“一次发送”不再等于“一次无中断直达结束的请求”，而是允许中途出现用户参与的挂起点。

## 工具清单

### 首批只读工具

首批只读工具如下：

1. `read_file`
2. `list_dir`
3. `glob_files`
4. `grep_code`
5. `ask_user`
6. `say_to_user`
7. `exit`

其中：

1. `ask_user` / `say_to_user` / `exit` 在产品语义上属于交互或控制工具，但默认不需要权限确认
2. 文件与搜索工具属于真正的 `Read`

### 首批写入工具

首批写入工具如下：

1. `write_file`
2. `edit_file`

### 首批执行工具

首批执行工具如下：

1. `run_powershell`

## `askUser` 与 `sayToUser` 的产品化语义

### `say_to_user`

不再向终端打印，而是作为一种显式时间线事件或 assistant 说明片段进入当前聊天流。

它的职责是：

1. 让 agent 明确向用户展示一段说明
2. 不要求用户立即输入
3. 不挂起当前执行

### `ask_user`

`ask_user` 是本轮最关键的桌面交互工具。其产品语义如下：

1. agent 可以给出一个问题
2. 可以附带最多 3 个候选项
3. 始终预留一个自由输入框
4. 用户点选候选项或提交输入后，立刻作为工具返回值
5. 返回后继续当前轮次执行

它不应退化为“请用户下一轮自己重新发消息”。

### 参数约束

`ask_user` 的请求参数建议固定为：

1. `question`
2. `options`
3. `allowFreeText`

其中：

1. `options` 最多 3 个
2. 空字符串候选项应被过滤
3. 重复候选项应被去重
4. `allowFreeText` 第一版恒定视为 `true`

### 单次提交即封口

默认情况下，用户不应同时“先输入再选项”或“先选项再输入”，因为第一次有效提交后就会立刻恢复当前轮次。

因此：

1. 点击候选项后立即提交并关闭交互态
2. 手动输入后点击提交也立即关闭交互态
3. 卡片在提交后进入只读完成态

## `askUser` 的状态模型

### 独立问题请求事件

参考 kilo 的做法，本轮不把 `ask_user` 混进普通文本或普通 `ToolEventItem`。它需要独立的问题请求语义。

建议新增一组独立事件：

1. `QuestionRequested`
2. `QuestionAnswered`
3. `QuestionRejected`

### PendingQuestionUiState

每个对话线程在同一时刻最多存在一个待回答问题：

1. `requestId`
2. `conversationId`
3. `toolCallId`
4. `question`
5. `options`
6. `allowFreeText`
7. `status`

### UI 呈现

问题请求应渲染为聊天时间线中的内嵌卡片，而不是弹窗。

卡片包含：

1. 问题标题
2. 最多 3 个候选按钮
3. 一个自由输入框
4. 提交按钮

用户提交后：

1. 卡片显示已回答状态
2. 当前会话执行状态从等待中恢复为运行中
3. 当前轮次继续推进

### 恢复语义

用户在卡片上的回答不进入“新一轮 user draft -> sendDraft”链路，而直接作为 `ask_user` 工具调用的结果返回给 agent。

因此：

1. 不新增一轮 user prompt
2. 不清空或重建当前对话线程
3. 不结束当前 run

## 参考 kilo 的对齐边界

本轮参考 kilo 的部分如下：

1. 问题请求采用独立事件流，而不是混入普通消息
2. 问题 UI 在 transcript 内嵌渲染
3. 回复后返回 busy/running 状态，继续当前轮次
4. 支持 pending question 恢复

本轮不完全照搬 kilo 的部分如下：

1. 不做多题分页
2. 不做多选题
3. 不引入完整 RPC 或 SSE 协议层
4. 不复制它的全部 DTO 字段

第一版只保留“单题、最多 3 个候选项、支持自定义输入”的最小产品化能力。

## 只读工具边界

### 读取范围

只读工具允许读取当前工作区之外的路径，不请求权限确认。

原因如下：

1. 只读操作本身无副作用
2. agent 有合理场景需要读取工作区外的配置、用户目录或其他项目
3. 用户已经明确接受读取类工具默认允许

### 失败分类

只读工具需要尽量返回可区分的失败结果，例如：

1. 路径不存在
2. 无读取权限
3. 编码不支持
4. 命中数量超过预算
5. 搜索被截断

这样 agent 才能据此继续缩小范围，而不是把失败都当成同一种“读不到”。

### 搜索预算

参考 `paicli` 的 `glob_files -> grep_code -> read_file` 搜索链，第一版采用类似预算策略。

`glob_files`：

1. `pattern`
2. `path`
3. `max_results`

默认：

1. `max_results = 50`
2. 上限 `200`

`grep_code`：

1. `pattern`
2. `path`
3. `glob`
4. `regex`
5. `case_sensitive`
6. `context_lines`
7. `max_results`
8. `head_limit`
9. `max_chars`

默认：

1. `context_lines = 0`
2. `max_results = 50`
3. `head_limit = 20`
4. `max_chars = 24000`

搜索实现策略：

1. 优先使用本机 `rg`
2. 不可用时回退 JVM 搜索
3. 跳过二进制文件和超大文件
4. 允许返回 `partial` 状态，提示后续缩小范围

## 写入工具边界

### 路径范围

`write_file` 和 `edit_file` 只允许修改当前会话工作区内路径。

工作区外写入一律拒绝。

原因如下：

1. 读取和写入的风险等级不同
2. 当前桌面产品首先要保证“对当前项目的可控修改”
3. 工作区外写入会显著增加误操作面

### 工具语义

`write_file`：

1. 用于新建文件或整体覆盖文件

`edit_file`：

1. 用于一次定点替换
2. 第一版只支持单次 targeted replacement

## PowerShell 工具设计

### 工具名

命令执行工具采用：

1. `run_powershell`

### 平台边界

只支持 PowerShell 7。

如果当前环境不是 PowerShell 7，则直接返回明确错误：

1. 当前工具仅支持 PowerShell 7
2. 请用户自行升级后再使用

本轮不兼容：

1. Windows PowerShell 5.x
2. `cmd`
3. `bash`
4. 其他 shell

### 返回结构

命令执行结果至少包含：

1. `exitCode`
2. `stdout`
3. `stderr`
4. `timedOut`

### 输出预算

命令输出必须做字符预算限制，防止单次结果把上下文淹没。

## 权限模型

### 工具分类

本轮固定三类：

1. `Read`
2. `Write`
3. `Execute`

### Read

默认允许，不请求权限确认。

包括：

1. `read_file`
2. `list_dir`
3. `glob_files`
4. `grep_code`
5. `ask_user`
6. `say_to_user`
7. `exit`

### Write

只允许工作区内写入，权限规则按 `PermissionPreset` 决策。

包括：

1. `write_file`
2. `edit_file`

### Execute

命令执行独立于普通写入处理，风险更高。

包括：

1. `run_powershell`

### PermissionPreset 矩阵

#### `DEFAULT`

1. `Read`：允许
2. `Write`：逐次确认
3. `Execute`：逐次确认

#### `EDIT_ALLOW`

1. `Read`：允许
2. `Write`：工作区内自动放行
3. `Execute`：逐次确认

#### `BRAVE`

1. `Read`：允许
2. `Write`：工作区内自动放行
3. `Execute`：自动放行

#### `PLAN`

1. `Read`：允许
2. `Write`：禁止
3. `Execute`：禁止

#### `AUTO`

本轮不引入复杂“模型自己判断是否需要审批”的新策略。

第一版建议将其运行时语义落到与 `DEFAULT` 一致，避免在工具系统刚接入时出现额外不透明逻辑。

## 审批交互

### ToolApprovalRequest

危险操作审批不应塞进普通错误文本，应有独立请求模型。

建议新增：

1. `ToolApprovalRequested`
2. `ToolApprovalResolved`
3. `ToolApprovalRejected`

### 写入审批

写入工具审批卡片至少展示：

1. 工具名
2. 目标路径
3. 操作摘要
4. 对于 `write_file` 的内容预览或 diff 摘要
5. 对于 `edit_file` 的替换摘要

### PowerShell 审批

命令执行审批卡片至少展示：

1. 工具名
2. 完整脚本
3. 运行目录
4. PowerShell 版本要求说明

## 事件模型调整

当前 `AgentStreamEvent` 只覆盖：

1. 文本
2. reasoning
3. tool call started / finished
4. status
5. completed / failed

本轮之后需要补充两类能力：

1. 问题请求与回答
2. 危险操作审批请求与结果

建议新增事件族：

1. `QuestionRequested`
2. `QuestionAnswered`
3. `QuestionRejected`
4. `ApprovalRequested`
5. `ApprovalResolved`
6. `ApprovalRejected`

## 状态恢复

### Pending question 恢复

参考 kilo 的做法，挂起问题不能只依赖一次性事件。

因此：

1. 当前对话的 pending question 需要进入本地状态
2. 如果出现流中断、UI 重组或状态刷新，仍应能恢复卡片显示
3. 工具完成但缺少显式 answered 事件时，也要能根据工具完成态清理挂起问题

### Pending approval 恢复

危险操作审批也应采用同类恢复策略：

1. 审批未完成前会话保持等待态
2. UI 刷新后仍能看到待审批卡片
3. 审批完成或拒绝后再清理挂起状态

## 测试策略

### shared 层

优先覆盖：

1. `ToolRegistry` 注册结果正确
2. 只读工具对工作区外路径可读
3. 写入工具拒绝工作区外修改
4. `run_powershell` 在非 PowerShell 7 环境下返回不支持错误
5. `grep_code` / `glob_files` 预算与截断语义
6. `ask_user` 参数校验与恢复语义
7. `PermissionPreset` 到工具策略的映射

### composeApp 层

优先覆盖：

1. `ask_user` 卡片在时间线内展示
2. 点击候选项后立即恢复当前轮次
3. 提交自由输入后立即恢复当前轮次
4. 写入审批卡片在 `DEFAULT` 下出现
5. `EDIT_ALLOW` 下工作区内写入自动放行
6. `PLAN` 下写入和执行直接拒绝
7. `BRAVE` 下 PowerShell 自动放行

### 集成级行为

至少覆盖以下完整链路：

1. 模型调用 `glob_files` -> `grep_code` -> `read_file`
2. 模型调用 `ask_user`，用户点击候选项，当前轮次继续并完成
3. 模型调用 `write_file`，用户确认后继续当前轮次
4. 模型调用 `run_powershell`，在 PowerShell 7 下审批后执行并返回结果

## 验收标准

完成本轮后，至少应满足以下标准：

1. 当前项目具备真正可执行的本地工具链，而不是只展示 provider tool frame。
2. `ask_user` 不再依赖终端输入，而是通过聊天时间线内嵌卡片完成交互。
3. 用户回答 `ask_user` 后，当前轮次会继续执行，而不是开启新一轮 prompt。
4. 只读工具默认允许，且允许读取工作区外路径。
5. 写入工具只允许修改当前工作区内路径。
6. `run_powershell` 仅支持 PowerShell 7；老版本环境会直接给出明确错误。
7. `DEFAULT` / `EDIT_ALLOW` / `BRAVE` / `PLAN` 的工具权限行为清晰、可测试、可复现。

## 实施顺序建议

建议按以下顺序实施：

1. 引入 agent 执行链与 `ToolRegistry`
2. 建立工具分类与权限判定层
3. 接入 `read_file` / `list_dir` / `glob_files` / `grep_code`
4. 接入 `write_file` / `edit_file`
5. 接入 `run_powershell`
6. 实现 `ask_user` / `say_to_user` / `exit`
7. 补齐 Compose 侧问题卡片与审批卡片
8. 完成恢复逻辑和测试
