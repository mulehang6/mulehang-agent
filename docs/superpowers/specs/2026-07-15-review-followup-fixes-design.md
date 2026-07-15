# 代码审查后续修复设计

## 背景

项目结构重构合并后，代码审查指出三个既有边界问题：空白 DeepSeek system 消息、标题栏长文本布局，以及旧 Koog `StreamFrame` 兼容映射把工具调用参数误当成执行结果。

本次在 `main` 上做小范围缺陷修复，不继续调整包结构，不修改配置、依赖、原型或 vendor 子模块。

## 目标

1. DeepSeek 请求中不出现缺少有效 `content` 的 system 消息。
2. 标题栏的 breadcrumb 与会话标题保持 12dp 间距，标题过长时单行省略。
3. 旧 `streamRunner` 兼容路径不再把 `ToolCallComplete.content` 伪装为工具执行结果。

## 设计

### DeepSeek 空白 system 消息

`toDeepSeekPromptMessages` 对 `Message.System` 的文本先做非空白判断。有效文本继续映射为一条 `DeepSeekChatMessage`；空白文本映射为空列表，整条 system 消息不进入请求。

不修改 `DeepSeekChatMessage.content` 的可空性，因为 assistant 工具调用消息仍需要允许无正文。

### Desktop 标题栏

保持当前 Header 布局层级和左右操作区不变，仅为会话标题增加：

- `Modifier.padding(start = 12.dp)`；
- `maxLines = 1`；
- `overflow = TextOverflow.Ellipsis`。

这与现有 Ring UI 原型的 12px gap 和标题省略行为一致。

### Koog 旧流兼容映射

`StreamFrame.ToolCallComplete` 表示模型已生成完整工具调用参数，不表示工具已经执行。因此兼容映射只在尚未公告时发出 `AgentStreamEvent.ToolCallStarted`，不再发出 `ToolCallFinished`。

真实生产路径的工具完成事件仍由 Koog `onToolCallCompleted` 产生，并继续携带真实 `toolResult`。

不采用 `resultPreview = null` 的建议，因为它仍会让 Desktop reducer 标记 Finished 并写入一条虚假的 ToolResult 历史。

## 测试与验证

- DeepSeek：新增空白 system 消息被省略的回归测试，先确认旧实现失败，再实现修复。
- Koog：调整旧流事件映射测试，断言只产生 ToolCallStarted，不产生 ToolCallFinished，先确认旧实现失败。
- Header：项目当前没有 Compose UI 测试基础设施；不为三行样式引入新依赖，通过 IDEA problems 和 `:desktopApp:compileKotlin` 验证。
- 回归范围：运行 `:shared:jvmTest :desktopApp:test`、`:desktopApp:compileKotlin`，并检查 `git diff --check`。

## 非目标

- 不删除 `streamRunner` 测试接缝。
- 不改变正常 Koog 工具执行生命周期。
- 不重新设计 Header 的宽度分配策略。
- 不处理与本次三个问题无关的既有 warning 或代码风格问题。
