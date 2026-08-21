# Repository Guidelines

## 项目结构与边界

本仓库是 Windows Desktop first 的 Agent 应用：生产主线为 Kotlin Multiplatform、Compose Multiplatform Desktop 与 JetBrains Koog。根 Gradle 工程包含 `shared/`（跨平台领域、配置、会话与工具协议）和 `desktopApp/`（桌面窗口、Compose UI 与展示状态）。`desktopApp` 可以依赖 `shared`，反向依赖不允许。

`agent-ui-prototype1/` 是独立的 React/Vite/Ring UI 原型；`docs/` 存放设计、计划、总结和参考资料。参考项目位于此项目的同级目录，例如：[kilo](../kilocode)。都可以正常使用 IDEA MCP 分析；禁止修改它们。

IDEA开源版的源码位于[idea](../../intellij-community)

## 构建、测试与检查

使用 [JetBrains Runtime 25（JDK 25）](D:\jdk\jbrsdk-JCEF)和仓库 Gradle Wrapper，在根目录执行：

```powershell
.\\gradlew.bat :shared:jvmTest :desktopApp:test
.\\gradlew.bat :desktopApp:compileKotlin
.\\gradlew.bat :desktopApp:packageDistributionForCurrentOS
```

优先运行与改动范围匹配的最小任务。原型在 `agent-ui-prototype1/` 下使用 `pnpm build` 和 `pnpm lint`。先查看适用的 IDEA 运行配置；不要启动 Desktop、Vite 或其他长期运行服务。完成后检查受影响文件的问题与 diff。

## 编码与测试规范

Kotlin 使用 4 空格、尾随逗号、无制表符；类型为 `PascalCase`，成员为 `camelCase`，常量为 `UPPER_SNAKE_CASE`。生产类、对象、数据类和函数写简短 KDoc，注释说明约束或原因。可复用规则放入 `shared/src/commonMain`，JVM 实现放入 `shared/src/jvmMain`，UI 类型留在 `desktopApp`。

## 代码结构与大文件治理

每个文件应围绕单一、内聚的职责组织。生产 Kotlin 文件超过 500 行、测试 Kotlin 文件超过 800 行时，必须在同次改动中按职责或被测主题拆分；即使未到阈值，只要一个文件混合了渲染、交互策略、平台适配、状态转换或无关测试场景，也应优先拆分。阈值是评审触发条件，不是保留混杂职责的上限。

入口 Composable 和状态门面只负责装配协作者；渲染叶子、纯交互策略、平台适配与状态转换应放在聚焦文件中。不得为了满足行数限制新增无意义的透传包装；拆分时保持 `shared` 到 `desktopApp` 的单向依赖，并同步迁移或补充聚焦的回归测试与 KDoc。

测试使用 `kotlin.test` 与 JUnit 5，协程测试使用 `kotlinx-coroutines-test`。测试文件以被测对象命名，例如 `SettingsMergerTest.kt`；新功能与缺陷修复必须覆盖错误分支和状态流转。

每个类及函数都应该写上适当长度的注释

## 配置、文档与交付

配置优先级为环境变量、`.mulehang/settings.json`、`~/.mulehang/settings.json`、默认值。禁止提交密钥、Token、用户配置、日志或真实路径；新增配置项同步更新示例文件并只使用占位值。

设计、实施与总结等计划文档统一置于 `docs/plans/`，不再使用 `docs/superpowers/specs/` 或 `docs/superpowers/plans/`
（历史文档位于 `backup/superpower-docs`）。未经明确授权不得提交。提交采用 `feat(agent): 添加流式回退指导` 格式；PR
使用中文说明目的、实现和验证，视觉变化附截图。

## 其他
- 涉及到界面UI相关工作，使用 emil-design-eng，并遵循 [Islands 设计规范](docs/design/islands.md)。
- git 提交格式：type(scope): summary, type和scope英文，summary中文
- 本地日志在 `desktopAPP/logs` 下，需要时自己看
- worktree 新建到[worktrees](../worktrees) 下

## Kotlin Multiplatform library selection

When adding or recommending Kotlin Multiplatform dependencies,
query the klibs.io MCP (https://api.klibs.io/mcp) before choosing a library.

Use it to verify dependency metadata:

- supported targets,
- maven coordinate,
- latest versions or latest stable versions,
- license,
- maintenance/activity signals,
- comparable alternatives
- etc.