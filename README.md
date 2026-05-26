# mulehang-agent

一个基于 Kotlin Multiplatform、Compose Multiplatform Desktop 与 JetBrains Koog 1.0.0 的 Windows Desktop first agent 应用仓库。

## 当前主线

仓库当前主线只有两部分：

1. `shared/`：配置、状态、Koog 接入与应用用例
2. `composeApp/`：Desktop UI 与窗口生命周期

## 文档入口

1. `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
2. `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`

## 本地配置

配置采用双层 JSON：

1. 用户级配置：`~/.mulehang/settings.json`
2. 项目级配置：`./mulehang/settings.json`
3. 示例文件：`./mulehang/settings.json.example`

优先级固定为：`环境变量 > 项目级配置 > 用户级配置 > 默认值`

## 环境要求

使用 JDK 21、Gradle Wrapper、Kotlin Multiplatform 与 Compose Multiplatform Desktop。当前只聚焦 Windows Desktop，不启动开发服务器。

## 构建与测试

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat clean
```
