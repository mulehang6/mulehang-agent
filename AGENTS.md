# Repository Guidelines

## 项目结构与模块组织
本仓库是基于 Kotlin/JVM 的 Gradle 项目。业务代码位于 `src/main/kotlin`，当前主要集中在 `src/main/kotlin/agent`，通用辅助代码放在 `src/main/kotlin/utils`。资源文件位于 `src/main/resources`。测试代码位于 `src/test/kotlin/agent`，测试资源位于 `src/test/resources`。设计文档与计划文档统一放在 `docs/superpowers/specs` 和 `docs/superpowers/plans`。

## 构建、测试与本地开发命令
在 PowerShell 中使用以下命令：

```powershell
.\gradlew.bat build
```

编译项目并运行全部测试，适合作为提交前检查。

```powershell
.\gradlew.bat test
```

仅运行测试，适合快速验证逻辑修改。

```powershell
.\gradlew.bat clean
```

清理构建产物，用于排查缓存或构建异常。不要在协作文档中要求启动开发服务器，本仓库以构建和测试验证为主。

## 编码风格与命名约定
遵循 Kotlin 官方风格，使用 4 空格缩进，不使用制表符。类名和对象名使用 `PascalCase`，函数与变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。测试名称可使用反引号包裹行为描述，例如 ``fun `should retry with fallback runner`()``。保持文件职责单一，仅在逻辑不直观时添加简短注释。

## 测试规范
测试栈为 `kotlin.test` + JUnit 5，`build.gradle.kts` 已启用 `useJUnitPlatform()`。新增功能或缺陷修复时，应补充对应单元测试，优先覆盖错误分支、回退流程和边界条件。测试文件名建议与被测对象对应，例如 `MySimpleAgentTest.kt`。

## 提交与 Pull Request 规范
现有提交历史同时包含简洁主题和 Conventional Commits 风格，例如 `fix(git): ...`、`refactor(agent): ...`。建议统一采用 `<type>(<scope>): <summary>`，例如 `feat(agent): add streaming fallback guard`。PR 应说明变更目的、核心实现与验证方式；如果输出行为有变化，附上示例输入输出。

## 安全与配置提示
敏感配置放在 `.env`，不要提交真实密钥。升级依赖前先确认 `koog-agents` 与 Kotlin 版本兼容。修改代理执行链路时，优先保持流式输出、回退处理和错误提示的一致性。
