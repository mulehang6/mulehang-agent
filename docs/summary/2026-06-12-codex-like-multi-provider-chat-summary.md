# 2026-06-12 Codex Like Multi Provider Chat Summary

## 背景

本轮工作围绕桌面聊天界面重构展开，目标是把当前最小聊天骨架推进为更接近 `codex app` 使用方式的多 provider 聊天界面，并把已经确认的页面原型落到实际 Compose Desktop 代码中。

## 已完成

### 1. 原型与设计文档

已完成原型讨论并固化为以下文档：

1. `docs/superpowers/specs/2026-06-12-codex-like-multi-provider-chat-prototype-design.md`
2. `docs/superpowers/spec-html/2026-06-12-codex-like-multi-provider-chat-prototype.html`
3. `docs/superpowers/plans/2026-06-12-codex-like-multi-provider-chat-implementation-plan.md`

同时修正了一个目录约定：

1. `markdown spec` 放在 `docs/superpowers/specs/`
2. `html 原型` 放在 `docs/superpowers/spec-html/`

后续不要再把 html 原型放到 `specs` 同级错误位置。

### 2. Compose Desktop 界面实现

已将桌面聊天页从“单会话最小输入框”推进为 codex-like 三段式布局：

1. 左侧：按 `pwd` 分组的会话侧栏
2. 中间：空态能力入口 / 对话态消息流
3. 底部：大 composer + 控制带

当前已落地的核心交互：

1. 侧栏按工作目录分组显示对话
2. 分组标题只显示路径末级目录名，例如 `E:\abc\def` 只显示 `def`
3. hover 分组时显示新建对话按钮
4. 空态显示能力入口卡片
5. 对话态显示用户消息、助手消息、thinking 块、tool 事件
6. composer 最左是附件按钮
7. 附件上传后显示在输入框上方，而不是正文里
8. provider / model / context ring 位于左侧控制组
9. permission 位于右侧，靠近发送按钮
10. 发送按钮已经改为图标按钮

### 3. Context Ring 交互修正

关于 `context remaining`，这一轮已经从草稿状态修到当前规则：

1. 默认只显示小圆环
2. hover 时显示具体百分比
3. 具体百分比现在通过独立 `Popup` 浮层显示
4. 不再通过改变原组件尺寸或内容来展示 hover 数值

这项修改是为了修复之前“浮层像被截断”的问题。

### 4. 状态层重构

`ChatWindowState` 已经从单一 `ConversationState` 驱动器扩展为窗口级 UI 状态中心，当前负责：

1. 多会话管理
2. 当前活动会话切换
3. 按工作目录分组
4. draft 管理
5. 附件列表管理
6. permission 档位管理
7. 当前 profile 选择
8. 流式消息、reasoning、tool event 归并

## 本轮涉及的主要文件

### 核心实现

1. `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
2. `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
3. `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt`

### 测试

1. `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`
2. `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatScreenPresentationTest.kt`

### 文档

1. `docs/superpowers/specs/2026-06-12-codex-like-multi-provider-chat-prototype-design.md`
2. `docs/superpowers/spec-html/2026-06-12-codex-like-multi-provider-chat-prototype.html`
3. `docs/superpowers/plans/2026-06-12-codex-like-multi-provider-chat-implementation-plan.md`
4. `docs/summary/2026-06-12-codex-like-multi-provider-chat-summary.md`

## 已验证

本轮已经跑过并通过的验证包括：

1. `.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest" --tests "com.agent.app.ui.ChatScreenPresentationTest"`
2. `.\gradlew.bat build`
3. IDEA 文件检查：`ChatScreen.kt`、`ChatWindowState.kt`、`MulehangDesktopApp.kt` 无问题

## 当前仍未完成的点

虽然 UI 和状态骨架已经成型，但还有一些功能仍是“展示层已到位，真实业务接线未完成”：

### 1. thinking level 还未真正接线

现在模型下拉中，支持 reasoning 的模型 hover 时会出现 `low / medium / high` UI，但这仍然只是展示层骨架，尚未把选中的 thinking level 落到真正的会话配置或发送参数里。

### 2. permission 还未真正参与执行策略

当前 `permission` 已经有完整的 UI 状态和五档展示：

1. `auto`
2. `default`
3. `edit allow`
4. `plan`
5. `brave`

但它还没有真正接入到底层工具调用或执行约束逻辑中，目前主要是 UI 状态。

### 3. provider / model 选择仍是 UI 级切换

当前 provider 和 model 切换已经影响 `activeProfile` 选择，但还没有对更多运行时参数、上下文策略、模型能力矩阵做更深映射。

### 4. 附件仍是本地展示态

附件现在能够选择并显示在 composer 上方，但还没有真正参与发送消息的负载拼装或底层 agent 请求。

### 5. 侧栏分组仍是内存态

当前按 `pwd` 分组和多会话列表主要存在 `ChatWindowState` 内存中，没有持久化，也还没有和真实历史会话系统打通。

## 下个对话建议优先级

如果下一轮继续实现，建议按下面顺序推进：

1. 把 `thinking level` 从 UI 骨架接到真实发送配置
2. 把 `permission` 从 UI 选择接到执行约束逻辑
3. 明确附件在发送请求中的真实拼装方式
4. 给多会话和按 `pwd` 分组补持久化或恢复逻辑
5. 继续微调 `provider / model / permission / send` 控件的尺寸、间距和视觉层级

## 重要约定

后续接手时请记住以下约定：

1. 优先中文
2. 改文件优先 `functions.apply_patch`
3. Windows 11 环境，只用 PowerShell 语法
4. 不启动开发服务器
5. HTML 原型放在 `docs/superpowers/spec-html/`
6. 这个界面的目标不是简单复刻 codex，而是在 codex 骨架上突出多 provider 差异能力

## 简短结论

这轮已经完成了“从原型讨论进入真实项目实现”的关键跨越：界面骨架、交互位置和状态模型都已经搭起来了，接下来不是再讨论大方向，而是把 `thinking / permission / attachments / 会话持久化` 这些还停留在 UI 层的能力继续往下打通。
