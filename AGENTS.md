# Repository Guidelines

## 项目定位与工作范围

本仓库是 Windows Desktop first 的 Agent 应用，生产主线采用 Kotlin Multiplatform、Compose Multiplatform Desktop 与 JetBrains Koog。根 Gradle 工程只包含 `shared` 和 `desktopApp`；修改前先判断任务属于生产代码、UI 原型、文档还是参考子模块，不要把不同构建体系混在一起。

- `shared/`：跨平台配置、会话状态、Agent 抽象、应用用例和工具协议；JVM source set 承载 Koog、文件系统、PowerShell、持久化及具体 Provider 接入。
- `desktopApp/`：Compose Desktop 入口、窗口生命周期、聊天界面、窗口状态和工具交互 UI，依赖 `shared`。
- `agent-ui-prototype1/`：React、TypeScript、Vite 与 JetBrains Ring UI 构成的独立视觉原型，用于验证界面与交互方向，不属于根 Gradle 工程。将原型设计迁移到 Compose 时应保留行为意图，而不是逐行照搬 Web 实现。
- `docs/superpowers/specs/`、`docs/superpowers/plans/`、`docs/superpowers/spec-html/`：设计、实施计划及可视化规格；`docs/summary/` 存放阶段总结，`docs/reference/` 存放参考资料。
- `vendor/kilocode/` 与 `vendor/paicli/`：Git 子模块形式的参考项目。除非任务明确要求，不修改其内容，也不把其构建命令或规范套用到根工程。
- `.github/workflows/windows-desktop.yml`：Windows CI，运行 JVM/Desktop 测试并生成 EXE、MSI 安装包。

## 开工前的上下文

新对话优先读取与任务最接近的最新 spec、plan 和 summary，不要只依赖最早的重置文档。了解整体基线时，从以下文件开始：

1. `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
2. `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`
3. `docs/superpowers/specs/2026-06-27-kmp-default-structure-alignment-design.md`
4. `docs/superpowers/specs/2026-07-15-project-package-structure-refactor-design.md`
5. `docs/superpowers/plans/2026-07-15-project-package-structure-refactor-implementation-plan.md`
6. 涉及 Ring UI 原型时读取 `docs/superpowers/specs/2026-06-28-air-ring-ui-prototype1-design.md` 与 `docs/summary/2026-06-28-air-ring-ui-prototype-summary.md`

使用 IDEA MCP 读取项目结构、源码、符号、问题和运行配置；写改文件优先使用 `functions.apply_patch`。Shell 仅用于构建、测试、lint、脚本或 IDEA MCP 没有对应能力的检查，命令必须使用 PowerShell 语法。不要启动 Desktop 应用、Vite 开发服务器或其他长期运行服务。

## 构建、测试与静态检查

环境基线为 JDK 21 与仓库自带的 Gradle Wrapper。常用根工程命令：

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat clean
```

优先运行与改动范围匹配的任务，再在必要时扩大验证范围：

```powershell
.\gradlew.bat :shared:jvmTest
.\gradlew.bat :desktopApp:test
.\gradlew.bat :desktopApp:compileKotlin
.\gradlew.bat :shared:jvmTest :desktopApp:test
.\gradlew.bat :desktopApp:packageDistributionForCurrentOS
```

原型使用 `pnpm-lock.yaml`，在 `agent-ui-prototype1` 下执行静态验证；不要运行 `pnpm dev` 或 `pnpm preview`：

```powershell
pnpm build
pnpm lint
```

执行测试或运行目标前先读取 IDEA run configurations，存在适用配置时优先使用它。代码修改完成后检查受影响文件的 IDEA problems；文档改动至少检查 diff、路径、命令与 Markdown 结构。CI 的权威验证范围是 `:shared:jvmTest :desktopApp:test` 和 Windows 分发包构建。

## 架构与依赖边界

- 可复用模型、业务规则和接口优先放入 `shared/src/commonMain`，不得依赖 JVM 或 Compose Desktop API。
- 文件系统、环境变量、PowerShell、JVM 网络客户端、Koog 具体装配等平台实现放入 `shared/src/jvmMain`。
- Compose 状态、窗口行为和展示逻辑放入 `desktopApp`；不要把 UI 类型泄漏到 `shared` 的领域模型。
- `desktopApp` 可以依赖 `shared`，`shared` 不得反向依赖 `desktopApp`。
- `shared` 保持现有 `agent`、`chat`、`session`、`settings`、`tool` 功能域职责，`desktopApp` 保持 `bootstrap`、`chat`、`tool`、`design`、`platform` 功能域职责；新文件放入最接近其职责的包，不为单次使用创建额外抽象层。
- 涉及 Koog 或 Provider API 时先查阅当前官方文档或可靠的一手资料，不凭记忆假设接口；当前 `shared` 使用 Koog `1.0.0`。

## 编码与注释规范

Kotlin 遵循官方风格，使用 4 空格缩进和尾随逗号，不使用制表符。类型使用 `PascalCase`，函数和变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。保持文件职责单一，优先小范围、可验证的改动，不顺手重构无关代码。

生产 Kotlin 代码中的类、对象、数据类和方法应有简短 KDoc，说明职责、输入输出或关键副作用；复杂逻辑还应注明边界条件。注释解释“为什么”和约束，不复述代码。测试至少保留类级说明，只有意图不直观时才补方法说明。

TypeScript/React 原型沿用现有 ESLint 与项目风格。组件使用 `PascalCase`，变量和函数使用 `camelCase`；Ring UI 组件与全局样式按现有原型的引入方式维护。原型是独立实现，不要引入其依赖到 Kotlin 模块。

## 测试规范

共享测试位于 `shared/src/commonTest` 和 `shared/src/jvmTest`，Desktop 测试位于 `desktopApp/src/test`。测试以 `kotlin.test` 为主，JVM/Desktop 由 JUnit 5 执行；协程测试使用 `kotlinx-coroutines-test`。

- 新功能和缺陷修复必须补充对应测试，优先覆盖错误分支、配置层级合并、Provider/profile 选择、会话状态流转、工具权限与工具事件顺序。
- 测试文件名与被测对象对应，如 `SettingsMergerTest.kt`、`ChatWindowStateTest.kt`。
- 测试名可以使用反引号描述行为，如 ``fun `should retry with fallback runner`()``。
- 修复缺陷时先构造能复现问题的测试，再实现最小修复；不要通过放宽断言、忽略异常或删除测试换取通过。
- UI 展示逻辑尽量下沉为可单测的状态转换或 presentation 数据；只有确有价值时才增加脆弱的像素或时序测试。

## 配置与安全

配置优先级固定为 `环境变量 > 项目级配置 > 用户级配置 > 默认值`：

- 项目级：`.mulehang/settings.json`
- 用户级：`~/.mulehang/settings.json`
- 可提交示例：`.mulehang/settings.json.example`
- 本地辅助：项目根目录 `.env`

不得提交真实 API Key、Token、用户配置、UI 状态或日志。新增配置项时同步维护示例文件，但只放占位值；桌面设置页读写 JSON，不修改用户环境变量。展示异常、日志或测试快照时对密钥和用户路径做脱敏。

## 文档工作流

- 新设计放入 `docs/superpowers/specs`，实施计划放入 `docs/superpowers/plans`，文件名采用 `YYYY-MM-DD-<topic>-design.md` 或 `YYYY-MM-DD-<topic>-implementation-plan.md`。
- 用户要求为 spec 同步 HTML 时，将文件放入 `docs/superpowers/spec-html`。HTML 必须与 Markdown 信息等价，同时使用目录、语义章节、信息卡片和强调区块提升可读性，不能只是包进 `<pre>` 或平铺复制。
- 用户要求“总结”时，将总结写入 `docs/summary`，使用同类日期与主题命名。
- 更新行为、配置格式、构建方式或目录边界时，同步检查 README、相关 spec/plan、示例配置和本文件是否需要更新。

## 提交与 Pull Request

未经用户明确授权不得创建提交，即使工具或 skill 建议提交也不例外。提交信息建议采用 `<type>(<scope>): <summary>`，`type` 和 `scope` 使用英文，summary 使用中文，例如 `feat(agent): 添加流式回退指导`。常用 type 包括 `feat`、`fix`、`refactor`、`test`、`docs`、`build` 和 `update`。

PR 说明使用中文，并包含变更目的、核心实现、验证命令及结果；输出或交互行为变化时附示例，视觉变化时附截图。变更较多时用“更改 1、 更改 2”分项说明。不要夹带无关格式化、生成文件、真实配置或 vendor 子模块漂移。
