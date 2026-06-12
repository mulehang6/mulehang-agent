# 2026-06-12 Codex Like Multi Provider Chat Prototype Design

## 背景

当前桌面聊天页已经具备最小聊天闭环，但界面仍偏工程骨架，和用户期望的 `codex app` 式交互还有明显差距。目标不是简单复刻，而是在保留 codex 单焦点、低噪声布局的前提下，把本产品最核心的差异能力前置出来：

1. 多 provider 选择
2. 模型级差异化能力展示
3. thinking level 的条件式暴露
4. 权限档位的高频可见控制
5. 工作目录上下文感知的对话组织

本次产出只覆盖页面原型，不直接进入 Compose 代码实现。

## 目标

本轮原型设计的目标如下：

1. 建立一个整体气质接近 codex app 的桌面聊天界面原型。
2. 明确空态与对话态在视觉语义上的区别。
3. 设计适用于多 provider 产品的 composer 控制带。
4. 明确按 `pwd` 分组的侧栏组织方式。
5. 产出一份静态 HTML 原型，作为后续 Compose 实现对照。

## 非目标

本次不做以下内容：

1. 不实现真实 provider 列表拉取或模型发现。
2. 不实现真实附件上传、hover、tooltip 或下拉逻辑。
3. 不改动 `shared/` 中的配置、状态或 Koog 执行逻辑。
4. 不在当前轮次接入 Compose Desktop 正式页面。
5. 不定义设置页、模型管理页或 profile 编辑页的完整信息架构。

## 设计原则

### 1. 骨架接近 codex，差异集中在控制带

页面整体构图保持 codex 风格：

1. 左侧会话栏
2. 中央主聊天区
3. 底部大 composer

但产品差异不放在页面标题区或复杂工具栏里，而是集中放进 composer 控制带。这样用户第一眼会觉得熟悉，第二眼才发现该产品支持多 provider、多模型和权限档位。

### 2. 高风险控制必须常显

权限档位不是低频设置，而是每次发送前都可能影响行为边界，因此必须常显。provider 与 model 同理，也不能被埋到二级面板中。

### 3. 低频细节按需暴露

thinking level 不是所有模型都支持，也不是每次都必须调节，因此不进入主控制带，而是挂在模型下拉菜单内部，并且只在支持思考的模型项 hover 时出现。

### 4. 工作目录优先于时间线

桌面 agent 的上下文往往由仓库目录决定，因此侧栏组织优先按 `pwd` 分组，而不是只按时间顺序平铺全部会话。

## 整体结构

原型采用三段式结构：

```text
┌ Sidebar: 按 pwd 分组的会话列表
├ Main: 空态能力入口 / 对话态消息流
└ Composer: 附件 + provider + model + context + permission + send
```

### 左侧侧栏

侧栏负责工作目录感知的会话组织。

1. 顶部保留全局 `New conversation` 入口。
2. 主体按 `pwd` 分组显示会话。
3. 每个分组只显示路径末级目录名。
4. 完整路径只在 hover 或次级说明中出现。
5. 鼠标 hover 到分组行时，右侧出现新建对话图标按钮。

示例：

1. `E:\abc\def` 在主列表中显示为 `def`
2. hover `def` 时显示 `+` 图标，用于在该目录上下文下直接新建对话

这样可以减少长路径噪声，同时保留工作目录的明确归属。

## 主聊天区

### 空态

空态保留 codex 风格的大留白，但中央不只是标题和输入框，还应给出能力入口，让用户明确知道这个 agent 的典型起手动作。

能力入口建议为 4 个卡片：

1. 代码审查
2. 解释文件
3. 修改代码
4. 规划任务

空态的核心语义是“你可以在当前工作目录下做什么”。

### 对话态

发送第一条消息后，主区切换为消息流，不再显示能力卡片。对话态可包含：

1. 用户消息气泡
2. 助手正文块
3. thinking 块
4. tool / status 事件块

对话态的核心语义是“当前上下文正在推进什么”。

### 状态切换原则

空态与对话态的差别不只在消息列表是否为空，而在于中部语义切换；底部 composer 结构保持稳定，不要求用户在进入对话后重新学习控件位置。

## Composer 设计

### 总体结构

composer 分为两层：

1. 输入内容层
2. 底部控制带

当存在附件时，附件展示层浮在输入内容层上方。

### 附件

1. 附件入口按钮位于 composer 最左侧，使用 `+` 图标。
2. 上传完成后的附件不写进正文，不塞进底部控制带。
3. 附件以 tag / pill 形式显示在输入框上方。

这样可以保证正文输入区仍然可读，不会因为附件列表把消息内容挤乱。

### 底部控制带顺序

最终确定的顺序如下：

```text
+(附件按钮) | provider | model | context ring | permission | send(图标按钮)
```

注意：

1. `provider`、`model`、`permission` 都是常显状态项。
2. `send` 使用图标按钮，而不是文字 `Send` 按钮。
3. 分隔使用轻量 `|`，保持接近参考图的阅读节奏。

### Provider

provider 作为第一层选择项，常显在控制带中。它用于表达当前会话请求将发往哪个提供商协议实现或兼容服务。

### Model

model 为下拉菜单。菜单项需要支持两类状态：

1. 不支持 thinking level 的模型：只显示模型名，不显示 thinking 子控件
2. 支持 thinking level 的模型：鼠标 hover 到该模型项时，显示 thinking level 选择

thinking level 不进入常显控制带。

### Thinking Level

thinking level 仅作为模型项的附属交互暴露。规则如下：

1. 只在支持思考的模型项上出现
2. 只在 hover 该模型项时出现
3. 不支持的模型不显示该区域

该策略用于避免把所有模型都强制套进同一套复杂控制逻辑。

### Context Remaining

上下文剩余使用一个小圆环表示。

规则如下：

1. 默认只显示小圆环
2. 不常显数字百分比
3. hover 小圆环时，以 tooltip 显示具体剩余数值

这样既能维持界面克制感，又不会让用户失去精确信息。

### Permission

权限档位为常显控制项，位于控制带靠后位置。当前固定为五档：

1. `auto`：由 AI 决定是否请求权限
2. `default`：默认只读，修改前请求权限
3. `edit allow`：允许编辑
4. `plan`：只读且完全不允许修改
5. `brave`：全部允许

权限控件与 provider、model 同级展示，避免被收进更多设置。

## 视觉方向

本次视觉方向已经确认：

1. 整体像 codex，但不是像素级复刻
2. 配色采用当前原型中的暖色、低对比、纸张感方案
3. 页面重点不是炫技，而是把信息密度压在恰当位置

视觉重点如下：

1. 留白充分
2. 中央内容单焦点
3. 控制带低噪声但高信息密度
4. hover 才出现的辅助信息不干扰常态阅读

## HTML 原型交付

本次同步提供一份静态 HTML 原型，用于后续 Compose 实现对照。

建议路径：

1. `docs/superpowers/spec-html/2026-06-12-codex-like-multi-provider-chat-prototype.html`

该 HTML 不承载真实业务逻辑，只负责固定以下设计结论：

1. 按 `pwd` 分组的侧栏结构
2. 空态与对话态的版式差异
3. 附件浮在输入框上方的展示方式
4. 底部控制带的顺序与信息层级
5. `context ring` 的 hover 数值表达
6. `send` 图标按钮风格

## 后续实现建议

正式进入 Compose 实现时，建议拆成以下几个 UI 单元：

1. `WorkspaceConversationSidebar`
2. `WorkspaceConversationGroup`
3. `ChatLandingState`
4. `ChatTimelineState`
5. `ChatComposer`
6. `ChatComposerAttachments`
7. `ChatComposerControlBar`
8. `ModelSelectorMenu`
9. `ContextUsageIndicator`
10. `PermissionSelector`

这样可以保证侧栏分组、控制带和状态切换逻辑保持清晰边界。

## 验收标准

当后续进入正式实现时，界面至少应满足以下标准：

1. 侧栏默认按 `pwd` 分组显示，并且主标题只显示末级目录名。
2. hover 分组行时能露出新建对话图标。
3. 空态与对话态的主区语义明确不同。
4. composer 保持固定结构，不因状态切换改变控件位置。
5. 附件显示在输入框上方，而不是正文中或底部控制带中。
6. model 菜单只在支持 thinking 的模型项 hover 时显示 thinking level。
7. context remaining 默认只显示小圆环，hover 才显示具体数值。
8. permission 五档可见且可选。
9. 发送按钮使用图标形态。

## 本轮结论

本轮已经确定的关键结论如下：

1. 采用 codex 风格三段式布局。
2. 侧栏按 `pwd` 分组，不按纯时间平铺。
3. 分组主标题只显示末级目录名。
4. hover 分组时显示新建对话图标。
5. composer 最左为附件按钮。
6. 附件上传后显示在输入框上方。
7. 底部控制带顺序固定为 `+ | provider | model | context ring | permission | send icon`。
8. thinking level 仅在支持的模型项 hover 时显示。
9. context remaining 只显示小圆环，hover 才显示具体数值。
10. send 使用图标按钮。
