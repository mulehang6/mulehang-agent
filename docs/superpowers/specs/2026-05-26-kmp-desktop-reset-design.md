# 2026-05-26 KMP Desktop Reset Design

## 背景

当前仓库主线建立在 `runtime-first + Bun/OpenTUI + vendor/kilocode` 的结构之上。该结构参考了 Kilo 的整体思路，但对当前项目阶段来说复杂度过高，且大量 AI 生成代码已经明显拉高了维护成本。

本次调整不再沿用旧主线，也不再继续保留 `vendor/kilocode` 作为当前分支的工作基础。新的目标是把仓库重置为一个面向 Windows 桌面应用的 Kotlin 项目，以 Koog 1.0.0、Kotlin Multiplatform 和 Compose Multiplatform Desktop 为核心技术栈。

这里的清理目标特指旧主线依赖的 `vendor/kilocode`。这份设计不把 `vendor/` 目录本身定义为永久禁区；如果后续因为参考、镜像或并行实验再次引入其他独立项目，只要它们不重新成为仓库主线依赖，就不违背本设计。

## 目标

本轮重置的目标如下：

1. 删除旧 `runtime/`、`cli/`、`vendor/kilocode` 及围绕它们建立的主线文档体系。
2. 将仓库主线重置为 `Koog 1.0.0 + KMP + Compose Multiplatform Desktop`。
3. 建立一个足够小但可以继续演进的新骨架，避免再次提前抽象。
4. 第一阶段只打通桌面 UI、配置加载和最小 Koog 消息执行闭环。

## 非目标

本轮不做以下内容：

1. 不保留独立 `runtime` HTTP server。
2. 不保留 Bun/OpenTUI CLI 作为并行入口。
3. 不接入 ACP、MCP bridge、memory、snapshot、tracing。
4. 不实现 provider 自动探测与模型自动发现。
5. 不实现 kilo 风格的多模式 agent 系统。
6. 不以多平台同步交付为目标，当前只聚焦 Windows Desktop。

## 技术方向

新主线的明确方向如下：

1. UI 技术栈：Compose Multiplatform Desktop
2. 共享逻辑层：Kotlin Multiplatform
3. Agent 框架：Koog 1.0.0
4. 目标平台：Windows Desktop first

## 目标仓库结构

重置后的仓库主线采用最小双模块结构：

```text
composeApp/
shared/
docs/
build.gradle.kts
settings.gradle.kts
gradle.properties
mulehang/settings.json.example
```

上面的主线结构不要求 `vendor/` 必须不存在；它只说明当前产品主线围绕 `composeApp/` 与 `shared/` 两个模块展开，而不是建立在 `vendor/kilocode` 之上。

### composeApp

`composeApp/` 负责：

1. Desktop 入口
2. Compose UI
3. 窗口生命周期
4. 页面状态绑定
5. 用户输入与交互

`composeApp/` 不负责：

1. provider 配置解析
2. Koog 装配细节
3. 核心业务流程编排

### shared

`shared/` 负责核心共享逻辑，并按职责保持最小分层：

```text
shared/src/commonMain/kotlin/.../config/
shared/src/commonMain/kotlin/.../agent/
shared/src/commonMain/kotlin/.../application/
shared/src/commonMain/kotlin/.../state/
```

其中：

1. `config/`：配置模型、配置加载、分层合并与覆盖
2. `agent/`：Koog 1.0.0 最小接入与消息执行入口
3. `application/`：面向 UI 的用例层
4. `state/`：应用状态、会话状态、执行状态、错误状态

## 最小运行链路

第一阶段只打通一条最小闭环：

1. 用户在桌面 UI 输入消息
2. UI 调用 `shared.application` 的发送消息用例
3. 用例层读取当前配置并调用 `shared.agent`
4. `shared.agent` 使用 Koog 1.0.0 执行一次最小消息请求
5. 执行结果转换为仓库自己的事件模型
6. UI 消费事件流并更新消息列表与状态

### 事件模型

UI 不直接消费 Koog 原始事件，而是消费应用自己的事件模型。第一阶段只需要：

1. `Started`
2. `Delta`
3. `Completed`
4. `Failed`

## 配置模型与优先级

### 配置层级

配置分为三层，优先级固定如下：

```text
环境变量 > 项目级配置 > 用户级配置 > 内置默认值
```

对应路径为：

1. 用户级：`~/.mulehang/settings.json`
2. 项目级：`<project>/mulehang/settings.json`

运行时加载顺序：

1. 读取用户级配置
2. 叠加项目级配置
3. 应用环境变量覆盖
4. 生成最终运行时配置快照

### 设置页保存策略

设置页不默认写入某一级，而是要求用户显式选择：

1. 保存到用户级
2. 保存到项目级

这样可以避免配置来源不透明的问题。

### settings.json 结构

配置文件使用多 profile 结构，而不是单一当前配置。文件中的 profile 默认视为启用，只有显式设置 `enabled: false` 才表示关闭。

示例结构：

```json
{
  "profiles": [
    {
      "id": "openai-main",
      "providerType": "openai-responses",
      "baseUrl": "https://api.openai.com/v1",
      "apiKey": "your-api-key",
      "model": "gpt-4.1"
    },
    {
      "id": "anthropic-work",
      "providerType": "anthropic",
      "baseUrl": "https://api.anthropic.com",
      "apiKey": "your-api-key",
      "model": "claude-sonnet-4",
      "enabled": false
    }
  ]
}
```

### profile 选择规则

当前选中的 profile 不写回 `settings.json`，而是作为独立的 UI/运行时状态保存。

状态持久化按项目隔离，存放在用户目录下的独立状态文件中，例如：

1. `~/.mulehang/ui-state.json`

其内部至少需要维护“项目路径 -> 上次选中的 profile id”的映射。

运行时选择规则：

1. 先按当前项目恢复上次选择的 profile
2. 如果该 profile 不存在或已禁用，则回退到第一个启用的 profile
3. 如果没有任何启用的 profile，则提示用户进入设置页配置

## providerType 设计

第一阶段不按厂商品牌分支，而按协议兼容类型分支。`providerType` 固定为四种：

1. `openai-responses`
2. `openai-chat-completions`
3. `anthropic`
4. `google`

该字段决定底层请求适配逻辑。这样后续接入第三方兼容服务时，不需要把系统做成品牌特判集合。

## Koog 接入边界

第一阶段只接入 Koog 1.0.0 的最小能力：

1. 最小 agent 执行
2. 单轮消息发送
3. 基础流式或增量输出
4. 最少量配置映射

第一阶段不接入：

1. MCP
2. ACP
3. memory feature
4. persistence 或 snapshot
5. tracing
6. planner 或 graph workflow
7. 大量 tool integration

## 错误处理

第一阶段错误只分三层：

1. 配置错误：缺失配置、无可用 profile、非法 providerType 等
2. 请求错误：网络失败、鉴权失败、远端返回异常
3. 执行错误：Koog 调用失败、结果解析失败

UI 层只展示用户可理解的错误信息，技术细节保留在内部日志或调试输出中。

## 测试策略

第一阶段测试保持克制，重点覆盖 `shared/`：

1. 配置解析与层级合并
2. profile 选择与回退逻辑
3. 用例层状态流转
4. agent 适配层的最小单元测试

不以复杂 UI 自动化或多 provider 集成矩阵作为当前阶段目标。

## 清理范围

本轮清理的目标范围如下：

1. 删除 `runtime/`
2. 删除 `cli/`
3. 删除 `vendor/kilocode`
4. 删除 `.gitmodules` 中的 `vendor/kilocode` 配置
5. 删除旧 `runtime-first`、`CLI first`、`ACP second` 文档体系
6. 用新的 KMP Desktop 文档替换当前主线设计说明

## 实施顺序

建议的实施顺序如下：

1. 删除旧模块、旧子模块和旧文档
2. 重建根 Gradle 与 KMP/Compose Desktop 工程骨架
3. 建立 `composeApp/` 与 `shared/` 两个模块
4. 升级并接入 Koog 1.0.0
5. 实现双层 `settings.json` 配置加载与环境变量覆盖
6. 实现按项目记忆的 profile 选择状态
7. 打通第一条桌面聊天闭环

## 成功标准

完成本轮后，应满足以下标准：

1. 仓库主线不再保留旧 `runtime + cli + kilo` 结构
2. 工程可以以 KMP + Compose Desktop 方式构建
3. 配置系统支持用户级、项目级和环境变量覆盖
4. profile 选择状态可以按项目记住上次选择
5. Koog 1.0.0 最小消息执行链路可用
6. 后续开发可以围绕 Desktop first 主线继续演进，而不再依赖旧参考架构
