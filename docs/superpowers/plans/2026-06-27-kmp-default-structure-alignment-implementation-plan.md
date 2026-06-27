# KMP Default Structure Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前 `shared + composeApp` 的桌面工程迁移为与 JetBrains 2026 新 KMP 默认结构一致的 `shared + desktopApp` 布局，同时保持当前桌面功能行为不变。

**Architecture:** 本轮只做工程结构重构，不改业务语义。`desktopApp` 收敛为纯 JVM Compose Desktop 应用模块，`shared` 保持 Kotlin Multiplatform 共享模块，但 JVM 侧源码集统一迁移为 `jvmMain` / `jvmTest`。所有修改围绕模块名、源码目录、Gradle 配置、CI 命令和当前主线文档同步展开。

**Tech Stack:** Kotlin 2.4.0、Compose Multiplatform 1.11.1、Kotlin Multiplatform、Kotlin JVM、kotlin.test、JUnit 5、Gradle Wrapper、PowerShell、IDEA MCP

---

## File Structure

### Move

- `composeApp/` -> `desktopApp/`
- `desktopApp/src/desktopMain/` -> `desktopApp/src/main/`
- `desktopApp/src/desktopTest/` -> `desktopApp/src/test/`
- `shared/src/desktopMain/` -> `shared/src/jvmMain/`
- `shared/src/desktopTest/` -> `shared/src/jvmTest/`

### Modify

- `settings.gradle.kts`
- `build.gradle.kts`
- `desktopApp/build.gradle.kts`
- `shared/build.gradle.kts`
- `.github/workflows/windows-desktop.yml`
- `desktopApp/src/test/kotlin/com/agent/app/DesktopProjectRootResolverTest.kt`
- `README.md`
- `AGENTS.md`
- `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
- `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`

### Verify

- `desktopApp/src/main/kotlin/com/agent/app/Main.kt`
- `desktopApp/src/main/kotlin/com/agent/app/MulehangDesktopApp.kt`
- `desktopApp/src/main/kotlin/com/agent/app/DesktopProjectRootResolver.kt`
- `desktopApp/src/main/kotlin/com/agent/app/ui/ChatScreen.kt`
- `desktopApp/src/main/kotlin/com/agent/app/ui/ChatWindowState.kt`
- `desktopApp/src/main/kotlin/com/agent/app/ui/ToolInteractionCards.kt`
- `desktopApp/src/main/kotlin/com/agent/app/ui/DesktopToolInteractionCoordinator.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/application/DesktopAppSessionRepository.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/config/DesktopEnvironmentOverrides.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/config/DesktopPathResolver.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/config/DesktopSettingsRepository.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/state/DesktopUiStateStore.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopFileToolSupport.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopGlobTool.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopGrepTool.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopPowerShellTool.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopPromptExecutorFactory.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopReadWriteTools.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopToolRegistryFactory.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopToolSet.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt`
- `shared/src/jvmMain/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamer.kt`

## Notes

- 本计划不包含 `git commit` 步骤。仓库规则要求只有在用户明确允许时才提交。
- 文件移动在 IDEA MCP 没有对应能力时，允许使用 PowerShell `Move-Item`。
- 格式化优先使用 IDEA MCP 或项目既有格式化方式，不启动开发服务器。

### Task 1: 重命名桌面应用模块并改成纯 JVM Compose 模块

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Move: `composeApp/build.gradle.kts` -> `desktopApp/build.gradle.kts`
- Move: `composeApp/src/desktopMain/kotlin/com/agent/app/Main.kt` -> `desktopApp/src/main/kotlin/com/agent/app/Main.kt`
- Move: `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt` -> `desktopApp/src/main/kotlin/com/agent/app/MulehangDesktopApp.kt`
- Move: `composeApp/src/desktopMain/kotlin/com/agent/app/DesktopProjectRootResolver.kt` -> `desktopApp/src/main/kotlin/com/agent/app/DesktopProjectRootResolver.kt`
- Move: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt` -> `desktopApp/src/main/kotlin/com/agent/app/ui/ChatScreen.kt`
- Move: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt` -> `desktopApp/src/main/kotlin/com/agent/app/ui/ChatWindowState.kt`
- Move: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ToolInteractionCards.kt` -> `desktopApp/src/main/kotlin/com/agent/app/ui/ToolInteractionCards.kt`
- Move: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/DesktopToolInteractionCoordinator.kt` -> `desktopApp/src/main/kotlin/com/agent/app/ui/DesktopToolInteractionCoordinator.kt`
- Move: `composeApp/src/desktopMain/resources/logback.xml` -> `desktopApp/src/main/resources/logback.xml`
- Move: `composeApp/src/desktopTest/kotlin/com/agent/app/DesktopProjectRootResolverTest.kt` -> `desktopApp/src/test/kotlin/com/agent/app/DesktopProjectRootResolverTest.kt`
- Move: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt` -> `desktopApp/src/test/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`
- Move: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ToolInteractionCardsTest.kt` -> `desktopApp/src/test/kotlin/com/agent/app/ui/ToolInteractionCardsTest.kt`
- Move: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatScreenPresentationTest.kt` -> `desktopApp/src/test/kotlin/com/agent/app/ui/ChatScreenPresentationTest.kt`

- [ ] **Step 1: 改写根工程模块声明，加入 JVM 插件入口**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "mulehang-agent"

include(":shared")
include(":desktopApp")
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}

group = "com.agent"
version = "0.1.0"

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

- [ ] **Step 2: 用 PowerShell 移动桌面模块目录和源码目录**

Run:

```powershell
Move-Item -LiteralPath .\composeApp -Destination .\desktopApp
Move-Item -LiteralPath .\desktopApp\src\desktopMain -Destination .\desktopApp\src\main
Move-Item -LiteralPath .\desktopApp\src\desktopTest -Destination .\desktopApp\src\test
```

Expected: `desktopApp\src\main` 与 `desktopApp\src\test` 存在，根目录不再存在 `composeApp\`。

- [ ] **Step 3: 把 `desktopApp/build.gradle.kts` 改成纯 JVM Compose Desktop 模块**

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.agent.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "mulehang-agent"
            packageVersion = "0.1.0"
        }
    }
}
```

- [ ] **Step 4: 运行桌面模块任务列表，确认新模块已被 Gradle 识别**

Run:

```powershell
.\gradlew.bat :desktopApp:tasks --all --no-daemon
```

Expected: PASS，并能看到 `test`、`run`、`packageDistributionForCurrentOS` 等 `desktopApp` 任务。

### Task 2: 把 `shared` 的桌面源码集迁移为 `jvmMain` / `jvmTest`

**Files:**
- Modify: `shared/build.gradle.kts`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/application/DesktopAppSessionRepository.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/application/DesktopAppSessionRepository.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopEnvironmentOverrides.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/config/DesktopEnvironmentOverrides.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopPathResolver.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/config/DesktopPathResolver.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopSettingsRepository.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/config/DesktopSettingsRepository.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/state/DesktopUiStateStore.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/state/DesktopUiStateStore.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopFileToolSupport.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopFileToolSupport.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopGlobTool.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopGlobTool.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopGrepTool.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopGrepTool.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopPowerShellTool.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopPowerShellTool.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopPromptExecutorFactory.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopPromptExecutorFactory.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopReadWriteTools.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopReadWriteTools.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopToolRegistryFactory.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopToolRegistryFactory.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopToolSet.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DesktopToolSet.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt`
- Move: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamer.kt` -> `shared/src/jvmMain/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamer.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/config/DesktopSettingsRepositoryTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/config/DesktopSettingsRepositoryTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/state/DesktopUiStateStoreTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/state/DesktopUiStateStoreTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopFileToolSupportTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopFileToolSupportTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopGlobToolTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopGlobToolTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopGrepToolTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopGrepToolTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopKoogHttpClientFactoryProviderTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopKoogHttpClientFactoryProviderTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopPowerShellToolTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopPowerShellToolTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopReadOnlyToolsTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopReadOnlyToolsTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopToolRegistryFactoryTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopToolRegistryFactoryTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopWriteToolsTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DesktopWriteToolsTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamerTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamerTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/KoogAgentGatewayTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/KoogAgentGatewayTest.kt`
- Move: `shared/src/desktopTest/kotlin/com/agent/shared/agent/KoogPromptTest.kt` -> `shared/src/jvmTest/kotlin/com/agent/shared/agent/KoogPromptTest.kt`

- [ ] **Step 1: 用 PowerShell 迁移 `shared` 的 JVM 源码目录**

Run:

```powershell
Move-Item -LiteralPath .\shared\src\desktopMain -Destination .\shared\src\jvmMain
Move-Item -LiteralPath .\shared\src\desktopTest -Destination .\shared\src\jvmTest
```

Expected: `shared\src\jvmMain` 与 `shared\src\jvmTest` 存在，`shared\src\desktopMain` 与 `shared\src\desktopTest` 消失。

- [ ] **Step 2: 把 `shared/build.gradle.kts` 改成新的 JVM 源码集命名**

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.oshai:kotlin-logging:8.0.03")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("ai.koog:koog-agents:1.0.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.17")
                implementation("ch.qos.logback:logback-classic:1.5.18")
                implementation("io.ktor:ktor-client-java:3.3.3")
            }
        }
        val jvmTest by getting
    }
}
```

- [ ] **Step 3: 运行共享模块的 JVM 编译与代表性测试**

Run:

```powershell
.\gradlew.bat :shared:compileKotlinJvm :shared:jvmTest --tests "com.agent.shared.config.DesktopSettingsRepositoryTest" --tests "com.agent.shared.state.DesktopUiStateStoreTest" --no-daemon
```

Expected: PASS，说明 `jvmMain` / `jvmTest` 迁移后，JVM 代码和测试仍然可编译执行。

### Task 3: 修复路径敏感引用、CI 和当前主线文档

**Files:**
- Modify: `.github/workflows/windows-desktop.yml`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/DesktopProjectRootResolverTest.kt`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
- Modify: `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`

- [ ] **Step 1: 把 CI 命令和制品路径更新到 `desktopApp` / `jvmTest`**

```yaml
name: Windows Desktop

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]
  workflow_dispatch:

permissions:
  contents: read

env:
  GRADLE_OPTS: -Dorg.gradle.daemon=false

jobs:
  desktop:
    name: Build Windows desktop
    runs-on: windows-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Test desktop target
        run: .\gradlew.bat :shared:jvmTest :desktopApp:test --no-daemon

      - name: Package Windows desktop app
        run: .\gradlew.bat :desktopApp:packageDistributionForCurrentOS --no-daemon

      - name: Upload Windows installers
        uses: actions/upload-artifact@v4
        with:
          name: mulehang-agent-windows-desktop
          path: |
            desktopApp/build/compose/binaries/main/exe/*.exe
            desktopApp/build/compose/binaries/main/msi/*.msi
          if-no-files-found: error
```

- [ ] **Step 2: 更新桌面测试中的旧模块名示例，避免新结构下继续出现 `composeApp`**

```kotlin
@Test
fun `should keep the selected directory as project root`() {
    val repositoryRoot = Files.createTempDirectory("mulehang-project-root-test")
    val selectedRoot = Files.createDirectories(repositoryRoot.resolve("desktopApp"))
    Files.writeString(repositoryRoot.resolve("settings.gradle.kts"), "rootProject.name = \"test\"")

    val resolved = DesktopProjectRootResolver.resolve(selectedRoot)

    assertEquals(selectedRoot, resolved)
}
```

- [ ] **Step 3: 更新当前主线文档中的模块名和源码集路径**

```md
## 当前主线

仓库当前主线只有两部分：

1. `shared/`：配置、状态、Koog 接入与应用用例
2. `desktopApp/`：Desktop UI 与窗口生命周期
```

```md
### desktopApp

`desktopApp/` 负责：

1. Desktop 入口
2. Compose UI
3. 窗口生命周期
4. 页面状态绑定
5. 用户输入与交互
```

```text
shared/src/jvmMain/kotlin/.../config/
shared/src/jvmMain/kotlin/.../agent/
```

- [ ] **Step 4: 搜索残留旧路径，确认活动代码与主线文档不再依赖旧命名**

Run:

```powershell
Select-String -Path .\README.md,.\AGENTS.md,.\.github\workflows\windows-desktop.yml,.\docs\superpowers\specs\2026-05-26-kmp-desktop-reset-design.md,.\docs\superpowers\plans\2026-05-26-kmp-desktop-reset-implementation-plan.md -Pattern 'composeApp|src/desktopMain|src/desktopTest|:composeApp|:shared:desktopTest'
```

Expected: 无输出；如果仍有命中，只能出现在明确描述历史状态的文段，不能出现在当前主线说明、当前构建命令或当前路径示例里。

### Task 4: 完成整体验证并做 IDEA 问题检查

**Files:**
- Verify: `settings.gradle.kts`
- Verify: `build.gradle.kts`
- Verify: `desktopApp/build.gradle.kts`
- Verify: `shared/build.gradle.kts`
- Verify: `.github/workflows/windows-desktop.yml`
- Verify: `desktopApp/src/test/kotlin/com/agent/app/DesktopProjectRootResolverTest.kt`

- [ ] **Step 1: 运行完整测试集，确认模块改名没有破坏现有行为**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: PASS。

- [ ] **Step 2: 运行完整构建，确认桌面打包链路仍然可用**

Run:

```powershell
.\gradlew.bat build :desktopApp:packageDistributionForCurrentOS --no-daemon
```

Expected: PASS，并且 `desktopApp/build/compose/binaries/main/` 下生成桌面制品目录。

- [ ] **Step 3: 用 IDEA MCP 检查关键改动文件**

Use IDEA MCP `get_file_problems` for:

```text
settings.gradle.kts
build.gradle.kts
desktopApp/build.gradle.kts
shared/build.gradle.kts
desktopApp/src/test/kotlin/com/agent/app/DesktopProjectRootResolverTest.kt
```

Expected: 没有错误；若存在 warning，只保留与本轮结构迁移无关的既有 warning。

- [ ] **Step 4: 搜索确认代码目录中不再残留旧源码集命名**

Run:

```powershell
Get-ChildItem .\desktopApp\src
Get-ChildItem .\shared\src
```

Expected: `desktopApp\src` 只包含 `main` 与 `test`；`shared\src` 只包含 `commonMain`、`commonTest`、`jvmMain`、`jvmTest`。
