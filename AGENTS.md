# Repository Guidelines

## 项目结构与模块组织
本仓库包含 Kotlin/JVM runtime 与独立 Bun/OpenTUI CLI。`runtime` 是 Gradle 子模块：生产代码位于 `runtime/src/main/kotlin`，测试代码位于 `runtime/src/test/kotlin`，资源文件位于 `runtime/src/main/resources`。`cli` 不是 Gradle 子模块，终端界面代码位于 `cli/src`，测试位于 `cli/src/__tests__`，依赖和脚本由 `cli/package.json` 管理。

根目录的 `build.gradle.kts` 与 `settings.gradle.kts` 负责 runtime 聚合与模块声明。设计文档与实施计划统一放在 `docs/superpowers/specs` 和 `docs/superpowers/plans`。总设计文档与总 implementation plan 用日期命名；阶段文档按 `01-...` 到 `07-...` 命名。`vendor/kilocode` 通过 git submodule 引入，不参与当前项目构建，主要用于对照阅读和局部方案参考。

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

```powershell
.\gradlew.bat :runtime:installDist
```

生成共享本地 runtime HTTP server 所需的本地分发脚本与依赖；CLI 当前会在需要时自动调用这条链路。

```powershell
Push-Location .\cli
bun test
bun run typecheck
Pop-Location
```

运行 CLI 单元测试与 TypeScript 类型检查。不要把 `bun run dev` 写入常规验证流程，也不要主动启动交互式开发进程。

## 编码风格与命名约定
Kotlin 遵循官方风格，使用 4 空格缩进，不使用制表符。类名和对象名使用 `PascalCase`，函数与变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。TypeScript/TSX 侧沿用现有 React hook + 函数组件风格，文件名按职责使用 `kebab-case` 或组件 `PascalCase`，共享状态和协议类型优先放在 `cli/src/app-state.ts`、`cli/src/runtime-events.ts`、`cli/src/runtime-server-manager.ts` 等边界清晰的文件中。测试名称可使用反引号包裹行为描述，例如 ``fun `should retry with fallback runner`()``。保持文件职责单一。生产 Kotlin 代码中的类、对象、数据类和方法应补充简短 KDoc，说明职责、输入输出或关键副作用；测试代码至少补类级注释，只有在测试意图不直观时才为单个测试方法补说明。

## 测试规范
runtime 测试栈为 `kotlin.test` + JUnit 5，`runtime/build.gradle.kts` 已启用 `useJUnitPlatform()`。CLI 测试使用 `bun test`，测试文件放在 `cli/src/__tests__`。新增功能或缺陷修复时，应补充对应单元测试，优先覆盖错误分支、回退流程、协议解析和边界条件。测试文件名建议与被测对象对应，例如 `MySimpleAgentTest.kt` 或 `runtime-http-client.test.ts`。

## 提交与 Pull Request 规范
- 现有提交历史同时包含简洁主题和 Conventional Commits 风格，例如 `fix(git): ...`、`refactor(agent): ...`。建议统一采用 `<type>(<scope>): <summary>`，例如 `feat(agent): 添加流式回退指导`。type和scope只能使用英文，而summary应尽量使用中文。PR 应说明变更目的、核心实现与验证方式；如果输出行为有变化，附上示例输入输出。使用中文
- 不要擅自提交，即使是 skill 中明确说明了。只有用户明确说明可以时才提交。

## 安全与配置提示
结构化配置放在本地 `mulehang-agent.json`，可参考仓库内的 `mulehang-agent.json.example`。敏感配置优先使用环境变量，不要提交真实密钥；项目根目录的 `.env` 仅作为本地开发辅助。修改代理执行链路时，优先保持流式输出、回退处理和错误提示的一致性。

## 文档入口
新对话开始后，优先读取：

1. `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
2. `docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md`
3. 当前阶段对应的 `docs/superpowers/specs/*.md`
4. 当前阶段对应的 `docs/superpowers/plans/*.md`

## 注意事项
- 生产代码中的每个类、对象、数据类和方法都应该有一段注释；如果实现较复杂，注释应明确说明职责、边界条件和副作用
- 新增本地配置文件时，默认提交 `mulehang-agent.json.example`，不要提交真实的 `mulehang-agent.json`
- `vendor/kilocode` 仅用于对照阅读与局部参考，不要把其中代码整段迁入当前主线
- 涉及到Koog相关代码开发，应该先去查找文档，使用`context7 mcp`或者直接搜索均可
