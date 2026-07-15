# 2026-07-15 项目包结构整理设计

## 背景

当前生产主线已经采用 `shared` 与 `desktopApp` 双模块结构：`shared` 是 Kotlin Multiplatform 共享模块，`desktopApp` 是纯 JVM Compose Desktop 应用模块。模块与源码集布局已经符合现有工程设计，本轮不再调整 Gradle 模块边界。

当前主要结构问题集中在包职责和超大文件：

1. `com.agent.shared.agent` 同时包含 Agent API、Prompt 执行、Koog 运行时、Provider 协议和桌面工具实现。
2. `com.agent.shared.state` 同时包含聊天领域状态、执行状态、工具事件和权限模型。
3. `com.agent.app.ui` 同时包含页面组装、状态容器、展示转换、工具交互与设计控件。
4. `ChatScreen.kt`、`ChatWindowState.kt`、`KoogAgentGateway.kt` 和 `DeepSeekChatCompletionsStreamer.kt` 已经分别承担多类职责，增加了导航、测试和局部修改成本。

因此，本轮整理不改变产品方向，而是在现有双模块内部按功能域重新组织包，并拆分职责过宽的文件。

## 目标

1. 保持 `shared + desktopApp` 双模块结构不变。
2. 将生产代码改为功能域优先、域内按职责细分的包结构。
3. 拆分四个职责过宽的生产文件，使每个文件可以用一句话说明其职责。
4. 明确共享模型、应用用例、JVM 基础设施、桌面状态和 Compose 展示之间的依赖方向。
5. 直接迁移内部包名和项目内引用，不保留旧包兼容转发层。
6. 保持现有 UI、配置格式、Agent 执行语义和工具事件顺序。
7. 允许修复整理过程中发现且可由测试稳定复现的直接相关小问题。

## 非目标

1. 不新增 Gradle 模块。
2. 不引入新的依赖、框架或通用架构层。
3. 不重新设计聊天交互、窗口布局、配置格式或 Provider 行为。
4. 不修改 `agent-ui-prototype1` 或 `vendor` 参考项目。
5. 不为单一实现提前创建接口。
6. 不以固定行数为目标机械拆分紧密逻辑。
7. 不增加旧包类型别名、转发类或弃用兼容层。

## 方案选择

### 方案 1：功能域优先，域内职责细分

以 `chat`、`agent`、`settings`、`session` 和 `tool` 为主要功能域，在域内仅按已经存在的职责进一步拆分。

优点：

1. 同一功能的模型、用例和实现更容易定位。
2. 可以消除当前宽泛的 `state`、`application` 和 `ui` 包。
3. 不需要增加 Gradle 模块或引入复杂分层。

缺点：

1. 包名、文件路径和 import 迁移范围较大。
2. 需要谨慎控制功能域之间的依赖方向。

### 方案 2：严格技术分层

将代码统一拆为 `domain`、`application`、`infrastructure` 与 `presentation`。

优点是依赖方向明确；缺点是当前项目规模下目录层级偏深，同一聊天功能会散落在多个顶层包。

### 方案 3：只细分现有大包

保留现有 `agent`、`state` 和 `ui`，只增加少量子包。

优点是迁移较小；缺点是原有顶层包的职责交叉仍然存在，之后仍可能需要再次迁移。

### 结论

采用方案 1。该方案能解决当前导航与职责混杂问题，同时避免把本轮扩大为模块化或严格分层重构。

## 目标包结构

### shared

`shared` 继续使用 `commonMain`、`commonTest`、`jvmMain` 与 `jvmTest`。相同功能域在不同源码集中使用一致的包前缀，由源码集表达平台差异。

```text
com.agent.shared/
├── agent/
│   ├── api/
│   ├── prompt/
│   ├── koog/
│   └── provider/deepseek/
├── chat/
│   ├── model/
│   └── usecase/
├── session/
├── settings/
│   ├── model/
│   ├── resolver/
│   └── persistence/
└── tool/
    ├── model/
    ├── interaction/
    ├── policy/
    ├── plan/
    └── runtime/
```

各包职责如下：

- `agent/api`：不依赖 Koog 的 Agent Gateway、请求、流事件和对话历史契约。
- `agent/prompt`：Prompt 执行、角色映射与通用执行参数。
- `agent/koog`：`jvmMain` 中的 Koog 装配、消息映射和流式执行策略。
- `agent/provider/deepseek`：`jvmMain` 中的 DeepSeek Chat Completions 协议实现。
- `chat/model`：聊天消息、会话项目、时间线工具事件、执行状态和领域状态；`ToolEventItem` 与密封的 `ConversationItem` 保持同包。
- `chat/usecase`：面向调用方的发送消息用例。
- `session`：应用会话快照、仓库接口、加载用例及 JVM 仓库实现。
- `settings/model`：Provider、Model、Profile 和 Settings 文档模型。
- `settings/resolver`：设置合并、Profile 选择与模型能力解析。
- `settings/persistence`：`jvmMain` 中的路径、环境变量和 JSON 设置仓库。
- `tool/model`：工具询问/审批请求与权限模型，不承载会话时间线中的 `ToolEventItem`。
- `tool/interaction`：工具询问、审批及交互桥接。
- `tool/policy`：工具权限决策。
- `tool/plan`：`update_plan` 预览解析。
- `tool/runtime`：`jvmMain` 中的文件、搜索、PowerShell 和工具注册实现；仅在确有多类实现时继续使用 `filesystem`、`shell` 或 `registry` 子包。

### desktopApp

```text
com.agent.app/
├── bootstrap/
├── chat/
│   ├── state/
│   ├── presentation/
│   ├── component/
│   └── export/
├── tool/
│   ├── interaction/
│   └── component/
├── design/
└── platform/
```

各包职责如下：

- `bootstrap`：程序入口、应用装配和项目根目录解析。
- `chat/state`：`ChatWindowState`、桌面聊天 UI 状态与状态入口。
- `chat/presentation`：标题、状态、统计、自动滚动和其他纯展示转换。
- `chat/component`：聊天页面、侧栏、时间线、编辑器和辅助面板等 Composable。
- `chat/export`：会话 Markdown 生成与导出。
- `tool/interaction`：桌面工具交互协调。
- `tool/component`：工具事件、询问和审批卡片。
- `design`：可跨页面复用的 Ring 风格 Compose 控件。
- `platform`：文件选择、剪贴板等桌面平台能力。

## 依赖边界

1. `desktopApp` 可以依赖 `shared`，`shared` 不得依赖 `desktopApp`。
2. `commonMain` 不得依赖 JVM、Compose Desktop 或 Koog 具体实现。
3. `agent/api` 不依赖 Koog；Koog 类型只存在于 `jvmMain/agent/koog` 及其内部边界。
4. `chat/usecase` 依赖 `agent/api`、`chat/model` 和必要的设置模型，不依赖桌面 UI。
5. `settings/resolver` 可以使用 Agent 执行参数模型表达模型能力，但不得依赖 Koog 或 Provider 运行时。
6. `desktopApp/chat/presentation` 只执行纯数据转换，不持有协程、文件系统或 Compose 状态。
7. `desktopApp/chat/component` 负责展示与事件转发，不实现 Agent 事件归并算法。
8. JVM Provider、持久化和工具实现依赖 common 契约，common 契约不得反向依赖 JVM 实现。
9. 不新增宽泛的 `core`、`util` 或 `manager` 包；无法明确归属的代码应先检查其职责，而不是放入兜底包。

## 大文件拆分

### ChatScreen.kt

目标文件：

```text
chat/component/ChatScreen.kt
chat/component/ChatHeader.kt
chat/component/TaskSidebar.kt
chat/component/WorkspacePanel.kt
chat/component/ConversationTimeline.kt
chat/component/ComposerPanel.kt
chat/component/PlanCard.kt
chat/component/AuxiliaryPanels.kt
chat/presentation/ConversationPresentation.kt
chat/presentation/ComposerPresentation.kt
chat/presentation/TaskPresentation.kt
chat/presentation/AutoScrollPolicy.kt
chat/export/ConversationMarkdownExporter.kt
```

`ChatScreen.kt` 只保留页面骨架、区域组装和必要的 Compose 状态协调。文件选择、剪贴板和导出写入等桌面能力移出 Composable 文件。

### ChatWindowState.kt

目标文件：

```text
chat/state/ChatWindowState.kt
chat/state/ChatWindowUiState.kt
chat/state/AgentEventReducer.kt
chat/state/ConversationHistoryReducer.kt
chat/state/ConversationFactory.kt
chat/state/ContextUsageEstimator.kt
```

`ChatWindowState` 保留公开操作、当前状态、协程生命周期和外部交互编排。Agent 流事件归并、历史消息归并、会话创建和上下文估算提取为可独立测试的纯函数或无状态对象。

### KoogAgentGateway.kt

目标文件：

```text
agent/koog/KoogAgentGateway.kt
agent/koog/KoogStreamingStrategy.kt
agent/koog/KoogPromptMapper.kt
agent/koog/KoogAssistantMessageMapper.kt
agent/koog/KoogStreamAccumulators.kt
```

Gateway 只保留协议选择、执行入口和事件流编排。Koog Prompt 转换、助手消息转换、历史工具调用补全和流片段累积分别迁移到对应文件。

### DeepSeekChatCompletionsStreamer.kt

目标文件：

```text
agent/provider/deepseek/DeepSeekChatCompletionsStreamer.kt
agent/provider/deepseek/DeepSeekProtocolModels.kt
agent/provider/deepseek/DeepSeekRequestMapper.kt
agent/provider/deepseek/DeepSeekResponseDecoder.kt
agent/provider/deepseek/DeepSeekSseClient.kt
```

Streamer 只负责编排一次 DeepSeek 流式请求。协议 DTO、历史和 Prompt 映射、响应解码及 SSE 连接分别承担独立职责。

上述文件列表是职责边界，不是强制制造空壳文件。如果实现过程中确认两个职责无法在不增加耦合的情况下独立测试，可以合并相邻职责，并在实施记录中说明原因。

## 状态与数据流

整理后的主要聊天链路保持不变：

```text
Compose component
  -> ChatWindowState
  -> chat/usecase/SendMessageUseCase
  -> agent/api/AgentGateway
  -> jvmMain agent implementation
  -> AgentStreamEvent
  -> AgentEventReducer
  -> ChatWindowUiState
  -> presentation
  -> Compose component
```

其中：

1. `ChatWindowState` 负责副作用边界和当前会话选择。
2. Reducer 负责确定性状态转换，不直接访问文件系统或启动协程。
3. Presentation 函数从 UI 状态生成展示文本、标签和视觉状态，不修改领域状态。
4. Composable 只读取展示状态并把用户动作转交给状态入口。

## 错误处理

1. 配置错误继续使用现有配置异常和 `AppError` 表达。
2. Agent 执行失败继续转换为 `AgentStreamEvent.Failed`。
3. 工具拒绝、失败、取消和完成的事件顺序保持不变。
4. 纯结构迁移不新增吞异常逻辑、通用 `catch` 或未经需求确认的 fallback。
5. 整理过程中发现的小问题只有同时满足以下条件才可修复：
   - 能由现有或新增测试稳定复现；
   - 与正在迁移的代码直接相关；
   - 不修改公开配置格式或主动扩展功能；
   - 在最终变更说明中与结构迁移分开列出。

## 测试策略

1. 迁移前运行 `:shared:jvmTest :desktopApp:test`，建立行为基线。
2. 测试文件随生产代码迁移到对应包，不保留只用于旧包路径的兼容测试。
3. `AgentEventReducer`、`ConversationHistoryReducer`、展示转换、Koog 映射和 DeepSeek 映射优先使用纯单元测试。
4. 现有事件顺序、消息拼接、Reasoning、工具调用、Profile 选择和上下文估算行为必须继续由测试覆盖。
5. 不新增脆弱的像素测试或依赖真实 Provider 的网络测试。
6. 若修复直接相关小问题，先增加能够失败的回归测试，再进行最小修复。

## 实施顺序

1. 读取 IDEA 运行配置并运行现有共享与桌面测试，记录基线。
2. 迁移 `shared/commonMain` 的模型、接口、用例和工具交互包，修复 import 后运行共享测试。
3. 提取 `ChatWindowState` 中的纯状态转换和 UI 状态模型，运行桌面状态测试。
4. 拆分 `shared/jvmMain` 的 Koog、DeepSeek、工具和持久化实现，运行共享 JVM 测试。
5. 拆分桌面展示转换、平台能力及 Composable，运行桌面测试。
6. 同步整理测试包和测试文件，删除旧空目录与残留引用。
7. 执行完整验证与 IDEA problems 检查。

## 验收标准

1. 工程仍然只有 `shared` 与 `desktopApp` 两个生产模块。
2. 原有宽泛的 `com.agent.shared.state`、`com.agent.shared.application` 和 `com.agent.app.ui` 包完成迁移，不保留兼容转发层。
3. `ChatScreen.kt`、`ChatWindowState.kt`、`KoogAgentGateway.kt` 和 `DeepSeekChatCompletionsStreamer.kt` 按本设计完成职责拆分。
4. `commonMain` 不引入 JVM、Koog 实现或 Compose Desktop 依赖。
5. 不新增产品依赖，不修改配置格式，不主动改变 UI 与 Agent 行为。
6. `:shared:jvmTest :desktopApp:test` 通过。
7. `:desktopApp:compileKotlin` 及必要的完整构建通过。
8. 所有受影响代码文件在 IDEA problems 检查中没有 error。
9. 文档、包声明、测试路径和 import 不残留已失效的旧结构引用。
10. 任何顺带修复均有回归测试，并在最终说明中单独列出。

## 文档与提交边界

实施完成后应同步检查 README、AGENTS、相关 spec/plan 和示例路径是否仍引用旧包结构。历史过程文档中仅作为历史记录的路径可以保留，但当前主线说明不得继续指向已删除的包。

根据仓库协作规则，设计、计划和实施变更均不得在未获得用户明确授权时自动提交。
