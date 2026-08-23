# mulehang-agent

一个基于 Kotlin Multiplatform、Compose Multiplatform Desktop 与 JetBrains Koog 1.0.0 的 Windows Desktop first agent 应用仓库。

## 当前主线

仓库当前主线只有两部分：

1. `shared/`：按 `agent`、`chat`、`session`、`settings`、`tool` 功能域组织跨平台契约与 JVM 实现
2. `desktopApp/`：按 `bootstrap`、`chat`、`tool`、`design`、`platform` 功能域组织 Desktop UI 与窗口生命周期

## 文档入口

1. `docs/superpowers/specs/2026-07-15-project-package-structure-refactor-design.md`
2. `docs/superpowers/plans/2026-07-15-project-package-structure-refactor-implementation-plan.md`
3. `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
4. `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`

## 本地配置

配置采用双层 JSON：

1. 用户级配置：`~/.mulehang/settings.json`
2. 项目级配置：`./.mulehang/settings.json`
3. 示例文件：`./.mulehang/settings.json.example`

优先级固定为：`环境变量 > 项目级配置 > 用户级配置 > 默认值`

自定义 OpenAI-compatible 模型不会按模型名推断思考能力。需要时，在对应 `models[]` 项中声明 `reasoningEfforts`（可选值为 `none`、`low`、`medium`、`high`、`xhigh`、`max`）和可选的 `defaultReasoningEffort`；后者必须出现在前者列表中。值会按 `providerType` 原样编码到相应请求字段，因此仅声明目标服务实际支持的档位。省略 `reasoningEfforts` 表示不声明能力，空数组表示明确关闭思考等级。

## 环境要求

使用 JetBrains Runtime 25（JDK 25）、Gradle Wrapper、Kotlin 2.3.20、Compose Multiplatform 1.11.0 与 Jewel `0.39.1-262.9437.29`。Gradle JVM 与运行时都必须指向 JBR 25；当前只聚焦 Windows Desktop，不启动开发服务器。

本地 JCEF 路径写在根目录未提交的 `.env` 中：以 `.env.example` 为模板，将 `JCEF_HOME` 指向带 JCEF 的 JBR 25。构建会自动读取该值；`-PjcefHome` 与系统 `JCEF_HOME` 可覆盖它，CI 不依赖本地 `.env`。

## 构建与测试

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test
.\gradlew.bat :desktopApp:compileKotlin
.\gradlew.bat :desktopApp:packageDistributionForCurrentOS
```
