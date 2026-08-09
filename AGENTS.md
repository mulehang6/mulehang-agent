# Repository Guidelines

## 项目结构与边界

本仓库是 Windows Desktop first 的 Agent 应用：生产主线为 Kotlin Multiplatform、Compose Multiplatform Desktop 与 JetBrains Koog。根 Gradle 工程包含 `shared/`（跨平台领域、配置、会话与工具协议）和 `desktopApp/`（桌面窗口、Compose UI 与展示状态）。`desktopApp` 可以依赖 `shared`，反向依赖不允许。

`agent-ui-prototype1/` 是独立的 React/Vite/Ring UI 原型；`docs/` 存放设计、计划、总结和参考资料；`vendor/kilocode/`、`vendor/paicli/` 是参考子模块，除非任务明确要求，否则不要修改。涉及某个主模块时，遵循其本地 [shared/AGENTS.md](shared/AGENTS.md) 或 [desktopApp/AGENTS.md](desktopApp/AGENTS.md)。

## 构建、测试与检查

使用 JDK 21 和仓库 Gradle Wrapper，在根目录执行：

```powershell
.\\gradlew.bat :shared:jvmTest :desktopApp:test
.\\gradlew.bat :desktopApp:compileKotlin
.\\gradlew.bat :desktopApp:packageDistributionForCurrentOS
```

优先运行与改动范围匹配的最小任务。原型在 `agent-ui-prototype1/` 下使用 `pnpm build` 和 `pnpm lint`。先查看适用的 IDEA 运行配置；不要启动 Desktop、Vite 或其他长期运行服务。完成后检查受影响文件的问题与 diff。

## 编码与测试规范

Kotlin 使用 4 空格、尾随逗号、无制表符；类型为 `PascalCase`，成员为 `camelCase`，常量为 `UPPER_SNAKE_CASE`。生产类、对象、数据类和函数写简短 KDoc，注释说明约束或原因。可复用规则放入 `shared/src/commonMain`，JVM 实现放入 `shared/src/jvmMain`，UI 类型留在 `desktopApp`。

测试使用 `kotlin.test` 与 JUnit 5，协程测试使用 `kotlinx-coroutines-test`。测试文件以被测对象命名，例如 `SettingsMergerTest.kt`；新功能与缺陷修复必须覆盖错误分支和状态流转。

每个类及函数都应该写上适当长度的注释

## 配置、文档与交付

配置优先级为环境变量、`.mulehang/settings.json`、`~/.mulehang/settings.json`、默认值。禁止提交密钥、Token、用户配置、日志或真实路径；新增配置项同步更新示例文件并只使用占位值。

设计、实施与总结等计划文档统一置于 `docs/plans/`，不再使用 `docs/superpowers/specs/` 或 `docs/superpowers/plans/`
（历史文档位于 `backup/superpower-docs`）。未经明确授权不得提交。提交采用 `feat(agent): 添加流式回退指导` 格式；PR
使用中文说明目的、实现和验证，视觉变化附截图。

## 其他
- 涉及到界面UI相关工作，使用 emil-design-eng
- git 提交格式：type(scope): summary, type和scope英文，summary中文
- 本地日志在 `desktopAPP/logs` 下，需要时自己看
