# Repository Guidelines

## 项目结构与模块组织
本仓库采用 Kotlin Multiplatform 与 Compose Multiplatform Desktop。`shared` 承载配置模型、状态模型、Koog 接入和应用用例；`composeApp` 负责 Windows Desktop UI 与窗口生命周期。设计文档与实施计划统一放在 `docs/superpowers/specs` 和 `docs/superpowers/plans`。

## 构建、测试与本地开发命令
在 PowerShell 中使用以下命令：

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat clean
```

不要在协作文档中要求启动开发服务器，本仓库以构建和测试验证为主。

## 编码风格与命名约定
Kotlin 遵循官方风格，使用 4 空格缩进，不使用制表符。类名和对象名使用 `PascalCase`，函数与变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。测试名称可使用反引号包裹行为描述，例如 ``fun `should retry with fallback runner`()``。保持文件职责单一。生产 Kotlin 代码中的类、对象、数据类和方法应补充简短 KDoc，说明职责、输入输出或关键副作用；测试代码至少补类级注释，只有在测试意图不直观时才为单个测试方法补说明。

## 测试规范
当前测试栈以 `kotlin.test` 为主，Desktop/JVM 侧使用 JUnit 5。新增功能或缺陷修复时，应补充对应单元测试，优先覆盖错误分支、回退流程、配置层级合并、profile 选择与状态流转。测试文件名建议与被测对象对应，例如 `SettingsMergerTest.kt` 或 `SendMessageUseCaseTest.kt`。

## 提交与 Pull Request 规范
- 现有提交历史同时包含简洁主题和 Conventional Commits 风格，例如 `fix(git): ...`、`refactor(agent): ...`。建议统一采用 `<type>(<scope>): <summary>`，例如 `feat(agent): 添加流式回退指导`。type和scope只能使用英文，而summary应尽量使用中文。PR 应说明变更目的、核心实现与验证方式；如果输出行为有变化，附上示例输入输出。使用中文
- 不要擅自提交，即使是 skill 中明确说明了。只有用户明确说明可以时才提交。

## 安全与配置提示
项目级配置路径为 `mulehang/settings.json`，用户级配置路径为 `~/.mulehang/settings.json`。提交时只保留 `mulehang/settings.json.example`，不要提交真实密钥；项目根目录的 `.env` 仅作为本地开发辅助。环境变量优先级高于 JSON，但桌面设置页应基于 JSON，而不是修改环境变量。

## 文档入口
新对话开始后，优先读取：

1. `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
2. `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`

## 注意事项
- 生产代码中的每个类、对象、数据类和方法都应该有一段注释；如果实现较复杂，注释应明确说明职责、边界条件和副作用
- 新增本地配置文件时，默认提交 `mulehang/settings.json.example`，不要提交真实的 `mulehang/settings.json`
- 涉及到Koog相关代码开发，应该先去查找文档，使用`context7 mcp`或者直接搜索均可
