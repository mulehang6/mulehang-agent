# KMP Desktop Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把仓库从旧的 `runtime + cli + vendor/kilocode` 主线重置为 `Koog 1.0.0 + KMP + Compose Multiplatform Desktop` 的 Windows Desktop first 工程，并打通最小聊天闭环。

**Architecture:** 仓库收敛为 `composeApp/` 与 `shared/` 两个模块。`composeApp/` 只负责 Desktop UI 与窗口生命周期；`shared/` 负责配置模型、双层配置加载、按项目记忆的 profile 选择状态、Koog 1.0.0 最小执行入口以及面向 UI 的发送消息用例。UI 只消费仓库自己的状态与事件模型，不直接依赖 Koog 原始对象。

**Tech Stack:** Kotlin 2.4.0, Compose Multiplatform 1.11.1, Kotlin Multiplatform, Koog 1.0.0, kotlinx.serialization, kotlinx.coroutines, kotlin.test, JUnit 5

---

## File Structure

### Remove

- `runtime/`
- `cli/`
- `vendor/kilocode`
- `.gitmodules`
- `docs/superpowers/specs/01-runtime-foundation-and-contracts.md`
- `docs/superpowers/specs/02-provider-byok-and-model-discovery.md`
- `docs/superpowers/specs/03-agent-strategy-and-capability-integration.md`
- `docs/superpowers/specs/04-cli-streaming-and-output.md`
- `docs/superpowers/specs/05-acp-protocol-bridge.md`
- `docs/superpowers/specs/06-memory-features-and-hardening.md`
- `docs/superpowers/specs/07-client-surfaces-optional.md`
- `docs/superpowers/specs/2026-04-16-koog-agent-architecture-design.md`
- `docs/superpowers/specs/2026-05-06-kilo-style-cli-http-server-design.md`
- `docs/superpowers/specs/2026-05-14-idea-mcp-priority-skill-design.md`
- `docs/superpowers/specs/2026-05-14-koog-built-in-file-tools-design.md`
- `docs/superpowers/plans/01-runtime-foundation-and-contracts.md`
- `docs/superpowers/plans/02-provider-byok-and-model-discovery.md`
- `docs/superpowers/plans/03-agent-strategy-and-capability-integration.md`
- `docs/superpowers/plans/04-cli-streaming-and-output.md`
- `docs/superpowers/plans/05-acp-protocol-bridge.md`
- `docs/superpowers/plans/06-memory-features-and-hardening.md`
- `docs/superpowers/plans/07-client-surfaces-optional.md`
- `docs/superpowers/plans/2026-04-16-koog-agent-implementation-plan.md`
- `docs/superpowers/plans/2026-05-06-kilo-style-cli-http-server.md`
- `docs/superpowers/plans/2026-05-14-koog-built-in-file-tools-implementation-plan.md`

### Modify

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `.gitignore`
- `README.md`
- `AGENTS.md`
- `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`

### Create

- `composeApp/build.gradle.kts`
- `composeApp/src/desktopMain/kotlin/com/agent/app/Main.kt`
- `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt`
- `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
- `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
- `shared/build.gradle.kts`
- `shared/src/commonMain/kotlin/com/agent/shared/config/ProviderType.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/config/AgentProfile.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/config/SettingsDocument.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/config/ConfigProfile.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/config/ConfigLayer.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/config/SettingsMerger.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/config/ProfileSelectionResolver.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/state/ChatMessage.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/state/ExecutionState.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/state/ConversationState.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/state/AppError.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentStreamEvent.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentGateway.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/application/SendMessageUseCase.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/application/LoadAppSessionUseCase.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/application/AppSessionSnapshot.kt`
- `shared/src/commonMain/kotlin/com/agent/shared/application/AppSessionRepository.kt`
- `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopSettingsRepository.kt`
- `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopEnvironmentOverrides.kt`
- `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopPathResolver.kt`
- `shared/src/desktopMain/kotlin/com/agent/shared/state/DesktopUiStateStore.kt`
- `shared/src/desktopMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt`
- `shared/src/commonTest/kotlin/com/agent/shared/config/SettingsMergerTest.kt`
- `shared/src/commonTest/kotlin/com/agent/shared/config/ProfileSelectionResolverTest.kt`
- `shared/src/commonTest/kotlin/com/agent/shared/application/SendMessageUseCaseTest.kt`
- `shared/src/desktopTest/kotlin/com/agent/shared/config/DesktopSettingsRepositoryTest.kt`
- `shared/src/desktopTest/kotlin/com/agent/shared/state/DesktopUiStateStoreTest.kt`
- `mulehang/settings.json.example`

### Keep

- `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
- `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`

### Note

本计划不包含 `git commit` 步骤。仓库规则要求只有在用户明确允许时才提交。

本计划中的 `vendor` 清理目标仅限旧主线依赖的 `vendor/kilocode`。如果后续重新引入其他独立的 `vendor/*` 项目作为参考、镜像或隔离实验，这不构成对本计划的回滚；关键约束仍然是仓库主线不能再次建立在 `vendor/kilocode` 或同类外置主模块之上。

## Task 1: 清理旧主线并改写仓库入口文档

**Files:**
- Remove: `runtime/`
- Remove: `cli/`
- Remove: `vendor/kilocode`
- Remove: `.gitmodules`
- Remove: 旧 `docs/superpowers/specs/*.md` 与 `docs/superpowers/plans/*.md`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `.gitignore`

- [ ] **Step 1: 先确认待删除目录与文档存在**

Run:

```powershell
Get-ChildItem .\
Get-ChildItem .\docs\superpowers\specs
Get-ChildItem .\docs\superpowers\plans
```

Expected: 能看到 `runtime/`、`cli/`、`vendor/kilocode`、旧 `specs/plans` 文档，以及新的 `2026-05-26-kmp-desktop-reset-design.md`。

- [ ] **Step 2: 删除旧目录和旧文档，只保留新的 reset design 与新的 implementation plan**

Run:

```powershell
Remove-Item -LiteralPath .\runtime -Recurse -Force
Remove-Item -LiteralPath .\cli -Recurse -Force
Remove-Item -LiteralPath .\vendor\kilocode -Recurse -Force
Remove-Item -LiteralPath .\.gitmodules -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\01-runtime-foundation-and-contracts.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\02-provider-byok-and-model-discovery.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\03-agent-strategy-and-capability-integration.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\04-cli-streaming-and-output.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\05-acp-protocol-bridge.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\06-memory-features-and-hardening.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\07-client-surfaces-optional.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\2026-04-16-koog-agent-architecture-design.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\2026-05-06-kilo-style-cli-http-server-design.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\2026-05-14-idea-mcp-priority-skill-design.md -Force
Remove-Item -LiteralPath .\docs\superpowers\specs\2026-05-14-koog-built-in-file-tools-design.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\01-runtime-foundation-and-contracts.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\02-provider-byok-and-model-discovery.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\03-agent-strategy-and-capability-integration.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\04-cli-streaming-and-output.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\05-acp-protocol-bridge.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\06-memory-features-and-hardening.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\07-client-surfaces-optional.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\2026-04-16-koog-agent-implementation-plan.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\2026-05-06-kilo-style-cli-http-server.md -Force
Remove-Item -LiteralPath .\docs\superpowers\plans\2026-05-14-koog-built-in-file-tools-implementation-plan.md -Force
```

Expected: 旧目录和旧文档被删除，`docs/superpowers` 中只剩新的 reset design 与当前 implementation plan。

- [ ] **Step 3: 改写 `.gitignore`，转向新的本地配置与桌面构建产物**

```gitignore
.gradle
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/main/**/build/
!**/src/test/**/build/

.idea/
*.iws
*.iml
*.ipr
out/
!**/src/main/**/out/
!**/src/test/**/out/

.kotlin
.DS_Store

/mulehang/settings.json
/.env
/.mulehang/
```

- [ ] **Step 4: 改写 `README.md` 为新主线入口**

```md
# mulehang-agent

一个基于 Kotlin Multiplatform、Compose Multiplatform Desktop 与 JetBrains Koog 1.0.0 的 Windows Desktop first agent 应用仓库。

## 当前主线

仓库当前主线只有一条：

1. `shared/`：配置、状态、Koog 接入与应用用例
2. `composeApp/`：Desktop UI 与窗口生命周期

## 文档入口

1. `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
2. `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`

## 本地配置

1. 用户级配置：`~/.mulehang/settings.json`
2. 项目级配置：`./mulehang/settings.json`
3. 示例文件：`./mulehang/settings.json.example`

优先级：`环境变量 > 项目级配置 > 用户级配置 > 默认值`

## 构建与测试

```powershell
.\gradlew.bat build
.\gradlew.bat test
```
```

- [ ] **Step 5: 改写 `AGENTS.md`，把仓库规则和文档入口切到 KMP Desktop 主线**

```md
# Repository Guidelines

## 项目结构与模块组织
本仓库采用 Kotlin Multiplatform 与 Compose Multiplatform Desktop。`shared` 承载配置模型、状态模型、Koog 接入和应用用例；`composeApp` 负责 Windows Desktop UI 与窗口生命周期。

## 构建、测试与本地开发命令
```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat clean
```

## 安全与配置提示
项目级配置路径为 `mulehang/settings.json`，用户级配置路径为 `~/.mulehang/settings.json`。提交时只保留 `mulehang/settings.json.example`，不要提交真实密钥。

## 文档入口
1. `docs/superpowers/specs/2026-05-26-kmp-desktop-reset-design.md`
2. `docs/superpowers/plans/2026-05-26-kmp-desktop-reset-implementation-plan.md`
```

- [ ] **Step 6: 检查目录收缩结果**

Run:

```powershell
Get-ChildItem .\
Get-ChildItem .\docs\superpowers\specs
Get-ChildItem .\docs\superpowers\plans
```

Expected: 根目录不再有 `runtime/`、`cli/`、`vendor/kilocode`；`docs/superpowers/specs` 与 `docs/superpowers/plans` 只剩新的 reset design 与新的 implementation plan。后续如果存在其他 `vendor/*` 目录，不属于这一步的失败条件。

## Task 2: 重建根 Gradle 和 KMP/Compose Desktop 模块骨架

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle.properties`
- Create: `shared/build.gradle.kts`
- Create: `composeApp/build.gradle.kts`
- Create: `composeApp/src/desktopMain/kotlin/com/agent/app/Main.kt`

- [ ] **Step 1: 更新 `settings.gradle.kts`，只声明新模块**

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mulehang-agent"

include(":shared")
include(":composeApp")
```

- [ ] **Step 2: 更新根 `build.gradle.kts`，切到 multiplatform + compose 主线**

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}

group = "com.agent"
version = "0.1.0"
```

- [ ] **Step 3: 扩展 `gradle.properties` 为 KMP/Compose 友好配置**

```properties
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx4096m
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.jetbrains.compose.experimental.uikit.enabled=false
```

- [ ] **Step 4: 创建 `shared/build.gradle.kts`**

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("ai.koog:koog-agents:1.0.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val desktopMain by getting
        val desktopTest by getting
    }
}
```

- [ ] **Step 5: 创建 `composeApp/build.gradle.kts`**

```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.agent.app.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "mulehang-agent"
            packageVersion = "0.1.0"
        }
    }
}
```

- [ ] **Step 6: 先放一个最小 desktop 入口，验证工程骨架能编译**

```kotlin
package com.agent.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * 桌面应用入口。
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "mulehang-agent",
    ) {
        MulehangDesktopApp()
    }
}

/**
 * 最小桌面应用根节点。
 */
@Composable
fun MulehangDesktopApp() {
    MaterialTheme {
        Text("mulehang-agent desktop reset")
    }
}
```

- [ ] **Step 7: 运行构建，确认新骨架可解析**

Run:

```powershell
.\gradlew.bat :shared:desktopTest
.\gradlew.bat :composeApp:compileKotlinDesktop
```

Expected: `shared` 测试任务可发现但尚无测试；`composeApp:compileKotlinDesktop` PASS。

## Task 3: 定义共享状态模型与配置模型

**Files:**
- Create: `shared/src/commonMain/kotlin/com/agent/shared/config/ProviderType.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/config/AgentProfile.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/config/SettingsDocument.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/config/ConfigProfile.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/config/ConfigLayer.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/state/ChatMessage.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/state/ExecutionState.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/state/ConversationState.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/state/AppError.kt`
- Test: `shared/src/commonTest/kotlin/com/agent/shared/config/SettingsMergerTest.kt`

- [ ] **Step 1: 先写配置合并与默认启用规则的失败测试**

```kotlin
package com.agent.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 settings 文档的默认启用与层级覆盖规则。
 */
class SettingsMergerTest {

    @Test
    fun `should treat missing enabled as true`() {
        val profile = AgentProfile(
            id = "openai-main",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "json-key",
            model = "gpt-4.1",
        )

        assertTrue(profile.isEnabled())
    }

    @Test
    fun `should let project layer override user layer`() {
        val userSettings = SettingsDocument(
            profiles = listOf(
                AgentProfile(
                    id = "main",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "user-key",
                    model = "gpt-4.1",
                )
            )
        )
        val projectSettings = SettingsDocument(
            profiles = listOf(
                AgentProfile(
                    id = "main",
                    providerType = ProviderType.OPENAI_RESPONSES,
                    baseUrl = "https://custom.example/v1",
                    apiKey = "project-key",
                    model = "gpt-4.1-mini",
                )
            )
        )

        val merged = SettingsMerger.merge(
            user = userSettings,
            project = projectSettings,
            environment = emptyMap(),
        )

        assertEquals("https://custom.example/v1", merged.single().baseUrl)
        assertEquals("project-key", merged.single().apiKey)
        assertEquals("gpt-4.1-mini", merged.single().model)
    }

    @Test
    fun `should allow profile to be disabled explicitly`() {
        val disabled = AgentProfile(
            id = "anthropic-work",
            providerType = ProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            apiKey = "hidden",
            model = "claude-sonnet-4",
            enabled = false,
        )

        assertFalse(disabled.isEnabled())
    }
}
```

- [ ] **Step 2: 运行测试，确认类型尚不存在**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.config.SettingsMergerTest"
```

Expected: FAIL，报 `AgentProfile`、`ProviderType`、`SettingsMerger` 等类型不存在。

- [ ] **Step 3: 写最小配置模型与状态模型**

```kotlin
package com.agent.shared.config

import kotlinx.serialization.Serializable

/**
 * 支持的 provider 协议兼容类型。
 */
@Serializable
enum class ProviderType {
    OPENAI_RESPONSES,
    OPENAI_CHAT_COMPLETIONS,
    ANTHROPIC,
    GOOGLE,
}

/**
 * 单个 agent profile 配置项。
 */
@Serializable
data class AgentProfile(
    val id: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean? = null,
) {
    /**
     * 缺省视为启用。
     */
    fun isEnabled(): Boolean = enabled ?: true
}

/**
 * settings.json 文档。
 */
@Serializable
data class SettingsDocument(
    val profiles: List<AgentProfile> = emptyList(),
)

/**
 * 配置来源层级。
 */
enum class ConfigLayer {
    USER,
    PROJECT,
    ENVIRONMENT,
}

/**
 * 合并后的最终 profile。
 */
data class ResolvedAgentProfile(
    val id: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean,
    val layer: ConfigLayer,
)
```

```kotlin
package com.agent.shared.state

/**
 * 聊天消息角色。
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

/**
 * 聊天消息。
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
)

/**
 * 执行状态。
 */
sealed interface ExecutionState {
    data object Idle : ExecutionState
    data object Running : ExecutionState
    data class Failed(val error: AppError) : ExecutionState
}

/**
 * UI 可消费的错误模型。
 */
data class AppError(
    val title: String,
    val message: String,
)

/**
 * 会话状态。
 */
data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val executionState: ExecutionState = ExecutionState.Idle,
    val activeProfileId: String? = null,
)
```

- [ ] **Step 4: 写最小 `SettingsMerger` 实现**

```kotlin
package com.agent.shared.config

/**
 * 负责把用户级、项目级与环境变量覆盖合并为最终 profile 列表。
 */
object SettingsMerger {

    fun merge(
        user: SettingsDocument?,
        project: SettingsDocument?,
        environment: Map<String, String>,
    ): List<ResolvedAgentProfile> {
        val mergedProfiles = linkedMapOf<String, AgentProfile>()

        user?.profiles.orEmpty().forEach { mergedProfiles[it.id] = it }
        project?.profiles.orEmpty().forEach { mergedProfiles[it.id] = it }

        return mergedProfiles.values.map { profile ->
            ResolvedAgentProfile(
                id = environment["MULEHANG_PROFILE_ID"] ?: profile.id,
                providerType = environment["MULEHANG_PROVIDER_TYPE"]?.let(ProviderType::valueOf) ?: profile.providerType,
                baseUrl = environment["MULEHANG_BASE_URL"] ?: profile.baseUrl,
                apiKey = environment["MULEHANG_API_KEY"] ?: profile.apiKey,
                model = environment["MULEHANG_MODEL"] ?: profile.model,
                enabled = environment["MULEHANG_ENABLED"]?.toBooleanStrictOrNull() ?: profile.isEnabled(),
                layer = if (project?.profiles.orEmpty().any { it.id == profile.id }) ConfigLayer.PROJECT else ConfigLayer.USER,
            )
        }
    }
}
```

- [ ] **Step 5: 重新运行测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.config.SettingsMergerTest"
```

Expected: PASS。

## Task 4: 实现双层配置加载、环境变量覆盖与按项目记忆的 profile 选择

**Files:**
- Create: `shared/src/commonMain/kotlin/com/agent/shared/config/ProfileSelectionResolver.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopPathResolver.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopEnvironmentOverrides.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopSettingsRepository.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/state/DesktopUiStateStore.kt`
- Create: `shared/src/commonTest/kotlin/com/agent/shared/config/ProfileSelectionResolverTest.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/config/DesktopSettingsRepositoryTest.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/state/DesktopUiStateStoreTest.kt`
- Create: `mulehang/settings.json.example`

- [ ] **Step 1: 先写 profile 选择回退逻辑测试**

```kotlin
package com.agent.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证按项目记忆的 profile 选择逻辑。
 */
class ProfileSelectionResolverTest {

    @Test
    fun `should restore remembered profile when enabled`() {
        val profiles = listOf(
            ResolvedAgentProfile("openai-main", ProviderType.OPENAI_RESPONSES, "https://api.openai.com/v1", "key", "gpt-4.1", true, ConfigLayer.USER),
            ResolvedAgentProfile("google-main", ProviderType.GOOGLE, "https://generativelanguage.googleapis.com", "key", "gemini-2.5-pro", true, ConfigLayer.USER),
        )

        val selected = ProfileSelectionResolver.selectActiveProfile(
            profiles = profiles,
            rememberedProfileId = "google-main",
        )

        assertEquals("google-main", selected?.id)
    }

    @Test
    fun `should fall back to first enabled profile when remembered profile is disabled`() {
        val profiles = listOf(
            ResolvedAgentProfile("openai-main", ProviderType.OPENAI_RESPONSES, "https://api.openai.com/v1", "key", "gpt-4.1", true, ConfigLayer.USER),
            ResolvedAgentProfile("anthropic-main", ProviderType.ANTHROPIC, "https://api.anthropic.com", "key", "claude-sonnet-4", false, ConfigLayer.USER),
        )

        val selected = ProfileSelectionResolver.selectActiveProfile(
            profiles = profiles,
            rememberedProfileId = "anthropic-main",
        )

        assertEquals("openai-main", selected?.id)
    }
}
```

- [ ] **Step 2: 运行测试，确认选择器尚不存在**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.config.ProfileSelectionResolverTest"
```

Expected: FAIL，报 `ProfileSelectionResolver` 不存在。

- [ ] **Step 3: 实现 profile 选择器**

```kotlin
package com.agent.shared.config

/**
 * 根据记忆状态与启用列表选择当前活动 profile。
 */
object ProfileSelectionResolver {

    fun selectActiveProfile(
        profiles: List<ResolvedAgentProfile>,
        rememberedProfileId: String?,
    ): ResolvedAgentProfile? {
        val enabledProfiles = profiles.filter { it.enabled }
        val remembered = enabledProfiles.firstOrNull { it.id == rememberedProfileId }

        return remembered ?: enabledProfiles.firstOrNull()
    }
}
```

- [ ] **Step 4: 写桌面配置仓库与 UI 状态存储测试**

```kotlin
package com.agent.shared.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证双层 settings 加载与环境变量覆盖。
 */
class DesktopSettingsRepositoryTest {

    @Test
    fun `should merge user and project settings with project precedence`() {
        val root = Files.createTempDirectory("mulehang-settings-test")
        val userHome = root.resolve("user-home")
        val projectRoot = root.resolve("workspace")
        Files.createDirectories(userHome.resolve(".mulehang"))
        Files.createDirectories(projectRoot.resolve("mulehang"))

        Files.writeString(
            userHome.resolve(".mulehang/settings.json"),
            """{"profiles":[{"id":"main","providerType":"OPENAI_RESPONSES","baseUrl":"https://api.openai.com/v1","apiKey":"user","model":"gpt-4.1"}]}"""
        )
        Files.writeString(
            projectRoot.resolve("mulehang/settings.json"),
            """{"profiles":[{"id":"main","providerType":"OPENAI_RESPONSES","baseUrl":"https://project.example/v1","apiKey":"project","model":"gpt-4.1-mini"}]}"""
        )

        val repository = DesktopSettingsRepository(
            pathResolver = DesktopPathResolver(userHome, projectRoot),
            environmentOverrides = DesktopEnvironmentOverrides(emptyMap()),
        )

        val profiles = repository.loadResolvedProfiles()

        assertEquals("https://project.example/v1", profiles.single().baseUrl)
        assertEquals("project", profiles.single().apiKey)
    }
}
```

```kotlin
package com.agent.shared.state

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证按项目记忆的 UI 状态存储。
 */
class DesktopUiStateStoreTest {

    @Test
    fun `should remember last selected profile for each project`() {
        val root = Files.createTempDirectory("mulehang-ui-state-test")
        val store = DesktopUiStateStore(root.resolve(".mulehang/ui-state.json"))

        store.saveSelectedProfile(
            projectPath = "D:/workspace/demo",
            profileId = "openai-main",
        )

        val remembered = store.loadSelectedProfile("D:/workspace/demo")

        assertEquals("openai-main", remembered)
    }
}
```

- [ ] **Step 5: 实现桌面路径解析、环境变量覆盖、配置仓库和 UI 状态存储**

```kotlin
package com.agent.shared.config

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * 解析用户级与项目级路径。
 */
data class DesktopPathResolver(
    val userHome: Path,
    val projectRoot: Path,
) {
    fun userSettingsPath(): Path = userHome.resolve(".mulehang/settings.json")
    fun projectSettingsPath(): Path = projectRoot.resolve("mulehang/settings.json")
}

/**
 * 提供环境变量覆盖。
 */
class DesktopEnvironmentOverrides(
    private val source: Map<String, String> = System.getenv(),
) {
    fun asMap(): Map<String, String> = source
}

/**
 * 负责读取双层 settings 并输出最终 profile 列表。
 */
class DesktopSettingsRepository(
    private val pathResolver: DesktopPathResolver,
    private val environmentOverrides: DesktopEnvironmentOverrides,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    fun loadResolvedProfiles(): List<ResolvedAgentProfile> {
        val user = readDocument(pathResolver.userSettingsPath())
        val project = readDocument(pathResolver.projectSettingsPath())
        return SettingsMerger.merge(user, project, environmentOverrides.asMap())
    }

    fun writeExampleSettings(exampleContent: String) {
        val target = pathResolver.projectRoot.resolve("mulehang/settings.json.example")
        target.parent.createDirectories()
        Files.writeString(target, exampleContent)
    }

    private fun readDocument(path: Path): SettingsDocument? {
        if (!path.exists()) return null
        return json.decodeFromString(SettingsDocument.serializer(), path.readText())
    }
}
```

```kotlin
package com.agent.shared.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * 按项目保存 UI 级最近选择状态。
 */
class DesktopUiStateStore(
    private val statePath: Path,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    fun loadSelectedProfile(projectPath: String): String? {
        val state = readState() ?: return null
        return state.projectSelections[projectPath]
    }

    fun saveSelectedProfile(projectPath: String, profileId: String) {
        val current = readState() ?: UiStateDocument()
        val updated = current.copy(
            projectSelections = current.projectSelections + (projectPath to profileId),
        )
        statePath.parent.createDirectories()
        Files.writeString(statePath, json.encodeToString(UiStateDocument.serializer(), updated))
    }

    private fun readState(): UiStateDocument? {
        if (!statePath.exists()) return null
        return json.decodeFromString(UiStateDocument.serializer(), statePath.readText())
    }

    @Serializable
    private data class UiStateDocument(
        val projectSelections: Map<String, String> = emptyMap(),
    )
}
```

- [ ] **Step 6: 写项目级示例配置**

```json
{
  "profiles": [
    {
      "id": "openai-main",
      "providerType": "OPENAI_RESPONSES",
      "baseUrl": "https://api.openai.com/v1",
      "apiKey": "your-api-key",
      "model": "gpt-4.1"
    },
    {
      "id": "google-main",
      "providerType": "GOOGLE",
      "baseUrl": "https://generativelanguage.googleapis.com",
      "apiKey": "your-api-key",
      "model": "gemini-2.5-pro",
      "enabled": false
    }
  ]
}
```

- [ ] **Step 7: 运行配置相关测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.config.ProfileSelectionResolverTest"
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.config.DesktopSettingsRepositoryTest"
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.state.DesktopUiStateStoreTest"
```

Expected: PASS。

## Task 5: 接入 Koog 1.0.0 最小执行入口与发送消息用例

**Files:**
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentStreamEvent.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentGateway.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/application/SendMessageUseCase.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/application/LoadAppSessionUseCase.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/application/AppSessionSnapshot.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/application/AppSessionRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/agent/shared/application/SendMessageUseCaseTest.kt`

- [ ] **Step 1: 先写发送消息用例测试**

```kotlin
package com.agent.shared.application

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ProviderType
import com.agent.shared.config.ConfigProfile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证发送消息用例的事件流转。
 */
class SendMessageUseCaseTest {

    @Test
    fun `should emit delta and completed events from gateway`() = runTest {
        val gateway = object : AgentGateway {
            override fun run(prompt: String, profile: ResolvedAgentProfile) = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.Delta("hello"),
                AgentStreamEvent.Completed("hello world"),
            )
        }
        val profile = ResolvedAgentProfile(
            id = "openai-main",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "key",
            model = "gpt-4.1",
            enabled = true,
            layer = ConfigLayer.PROJECT,
        )

        val events = SendMessageUseCase(gateway).invoke("hi", profile).toList()

        assertEquals(3, events.size)
        assertEquals(AgentStreamEvent.Completed("hello world"), events.last())
    }
}
```

- [ ] **Step 2: 运行测试，确认 gateway 与 use case 尚不存在**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.application.SendMessageUseCaseTest"
```

Expected: FAIL，报 `AgentGateway`、`SendMessageUseCase` 不存在。

- [ ] **Step 3: 实现公共 agent 事件模型与用例层**

```kotlin
package com.agent.shared.agent

/**
 * UI 可消费的 agent 事件。
 */
sealed interface AgentStreamEvent {
    data object Started : AgentStreamEvent
    data class Delta(val text: String) : AgentStreamEvent
    data class Completed(val text: String) : AgentStreamEvent
    data class Failed(val reason: String) : AgentStreamEvent
}
```

```kotlin
package com.agent.shared.agent

import com.agent.shared.config.ConfigProfile
import kotlinx.coroutines.flow.Flow

/**
 * 对 Koog 执行入口的最小抽象。
 */
interface AgentGateway {
    fun run(prompt: String, profile: ResolvedAgentProfile): Flow<AgentStreamEvent>
}
```

```kotlin
package com.agent.shared.application

import com.agent.shared.agent.AgentGateway
import com.agent.shared.config.ConfigProfile

/**
 * 发送消息用例。
 */
class SendMessageUseCase(
    private val agentGateway: AgentGateway,
) {
    operator fun invoke(
        prompt: String,
        profile: ResolvedAgentProfile,
    ) = agentGateway.run(prompt, profile)
}
```

- [ ] **Step 4: 实现最小 `KoogAgentGateway`**

```kotlin
package com.agent.shared.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.params.LLMParams
import com.agent.shared.config.ProviderType
import com.agent.shared.config.ConfigProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 基于 Koog 1.0.0 的最小执行入口。
 */
class KoogAgentGateway : AgentGateway {

    override fun run(prompt: String, profile: ResolvedAgentProfile): Flow<AgentStreamEvent> = flow {
        emit(AgentStreamEvent.Started)

        if (profile.providerType != ProviderType.OPENAI_RESPONSES) {
            emit(AgentStreamEvent.Failed("Current phase only supports OPENAI_RESPONSES."))
            return@flow
        }

        val executor = simpleOpenAIExecutor(
            apiToken = profile.apiKey,
            baseUrl = profile.baseUrl,
        )
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = chatAgentStrategy(),
            agentConfig = AIAgentConfig(
                prompt = prompt("chat", params = LLMParams()) {},
                model = profile.model,
            ),
        )

        val result = agent.run(prompt).toString()
        emit(AgentStreamEvent.Delta(result))
        emit(AgentStreamEvent.Completed(result))
    }
}
```

- [ ] **Step 5: 重新运行用例测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.application.SendMessageUseCaseTest"
```

Expected: PASS。

## Task 6: 实现桌面 UI、会话状态装配和按项目恢复选择

**Files:**
- Create: `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt`
- Create: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
- Create: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/Main.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/application/LoadAppSessionUseCase.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/application/AppSessionSnapshot.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/application/AppSessionRepository.kt`

- [ ] **Step 1: 定义会话快照与加载用例**

```kotlin
package com.agent.shared.application

import com.agent.shared.config.ConfigProfile

/**
 * UI 初始装配所需的快照。
 */
data class AppSessionSnapshot(
    val profiles: List<ResolvedAgentProfile>,
    val activeProfile: ResolvedAgentProfile?,
)
```

```kotlin
package com.agent.shared.application

import com.agent.shared.config.ProfileSelectionResolver

/**
 * 负责装配应用启动时的会话快照。
 */
class LoadAppSessionUseCase(
    private val repository: AppSessionRepository,
) {
    suspend operator fun invoke(): AppSessionSnapshot {
        val profiles = repository.loadProfiles()
        val remembered = repository.loadRememberedProfileId()
        val activeProfile = ProfileSelectionResolver.selectActiveProfile(profiles, remembered)
        return AppSessionSnapshot(profiles, activeProfile)
    }
}
```

```kotlin
package com.agent.shared.application

import com.agent.shared.config.ConfigProfile

/**
 * 屏蔽配置与 UI 状态持久化实现细节。
 */
interface AppSessionRepository {
    suspend fun loadProfiles(): List<ResolvedAgentProfile>
    suspend fun loadRememberedProfileId(): String?
    suspend fun saveRememberedProfileId(profileId: String)
}
```

- [ ] **Step 2: 写最小桌面窗口状态持有者**

```kotlin
package com.agent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ConversationState
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 窗口级状态持有者。
 */
class ChatWindowState(
    private val sendMessageUseCase: SendMessageUseCase,
    snapshot: AppSessionSnapshot,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var state by mutableStateOf(
        ConversationState(activeProfileId = snapshot.activeProfile?.id)
    )
        private set

    fun send(message: String) {
        val profile = snapshot.activeProfile ?: return
        state = state.copy(
            messages = state.messages + ChatMessage(MessageRole.USER, message),
            executionState = ExecutionState.Running,
        )

        scope.launch {
            sendMessageUseCase(message, profile).collectLatest { event ->
                when (event) {
                    AgentStreamEvent.Started -> Unit
                    is AgentStreamEvent.Delta -> {
                        state = state.copy(
                            messages = state.messages + ChatMessage(MessageRole.ASSISTANT, event.text),
                        )
                    }
                    is AgentStreamEvent.Completed -> {
                        state = state.copy(executionState = ExecutionState.Idle)
                    }
                    is AgentStreamEvent.Failed -> {
                        state = state.copy(executionState = ExecutionState.Failed(
                            com.agent.shared.state.AppError("Agent failed", event.reason)
                        ))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: 写最小 Compose 聊天界面**

```kotlin
package com.agent.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 最小聊天界面。
 */
@Composable
fun ChatScreen(
    state: ChatWindowState,
) {
    val draft = remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Active profile: ${state.state.activeProfileId ?: "none"}")
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.state.messages) { message ->
                Text("${message.role}: ${message.content}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = draft.value,
                onValueChange = { draft.value = it },
                label = { Text("Message") },
            )
            Button(
                onClick = {
                    val value = draft.value.trim()
                    if (value.isNotEmpty()) {
                        state.send(value)
                        draft.value = ""
                    }
                },
            ) {
                Text("Send")
            }
        }
    }
}
```

- [ ] **Step 4: 把 `Main.kt` 接到真正的 app 根节点**

```kotlin
package com.agent.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.agent.app.ui.ChatScreen
import com.agent.app.ui.ChatWindowState
import com.agent.shared.agent.KoogAgentGateway
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase

/**
 * 桌面应用入口。
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "mulehang-agent",
    ) {
        MulehangDesktopApp()
    }
}

/**
 * 根 composable。
 */
@Composable
fun MulehangDesktopApp() {
    val windowState = remember {
        ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(KoogAgentGateway()),
            snapshot = AppSessionSnapshot(
                profiles = emptyList(),
                activeProfile = null,
            ),
        )
    }

    MaterialTheme {
        ChatScreen(windowState)
    }
}
```

- [ ] **Step 5: 编译桌面模块**

Run:

```powershell
.\gradlew.bat :composeApp:compileKotlinDesktop
```

Expected: PASS。

## Task 7: 打通真实配置装配并完成项目级 profile 恢复

**Files:**
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/Main.kt`
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/application/LoadAppSessionUseCase.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/application/AppSessionRepository.kt`
- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/config/DesktopSettingsRepository.kt`
- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/state/DesktopUiStateStore.kt`

- [ ] **Step 1: 把桌面仓库实现为 `AppSessionRepository`**

```kotlin
package com.agent.shared.application

import com.agent.shared.config.DesktopPathResolver
import com.agent.shared.config.DesktopSettingsRepository
import com.agent.shared.config.ConfigProfile
import com.agent.shared.state.DesktopUiStateStore
import java.nio.file.Paths

/**
 * 基于桌面文件系统的应用会话仓库。
 */
class DesktopAppSessionRepository(
    projectRoot: String,
) : AppSessionRepository {
    private val pathResolver = DesktopPathResolver(
        userHome = Paths.get(System.getProperty("user.home")),
        projectRoot = Paths.get(projectRoot),
    )
    private val settingsRepository = DesktopSettingsRepository(pathResolver, com.agent.shared.config.DesktopEnvironmentOverrides())
    private val uiStateStore = DesktopUiStateStore(Paths.get(System.getProperty("user.home")).resolve(".mulehang/ui-state.json"))

    override suspend fun loadProfiles(): List<ResolvedAgentProfile> =
        settingsRepository.loadResolvedProfiles()

    override suspend fun loadRememberedProfileId(): String? =
        uiStateStore.loadSelectedProfile(pathResolver.projectRoot.toString())

    override suspend fun saveRememberedProfileId(profileId: String) {
        uiStateStore.saveSelectedProfile(pathResolver.projectRoot.toString(), profileId)
    }
}
```

- [ ] **Step 2: 在 app 启动时加载真实 snapshot**

```kotlin
package com.agent.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.agent.app.ui.ChatScreen
import com.agent.app.ui.ChatWindowState
import com.agent.shared.agent.KoogAgentGateway
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.DesktopAppSessionRepository
import com.agent.shared.application.LoadAppSessionUseCase
import com.agent.shared.application.SendMessageUseCase
import java.nio.file.Paths

@Composable
fun MulehangDesktopApp() {
    val projectRoot = remember { Paths.get("").toAbsolutePath().toString() }
    val snapshotState = remember {
        mutableStateOf(AppSessionSnapshot(emptyList(), null))
    }

    LaunchedEffect(projectRoot) {
        val repository = DesktopAppSessionRepository(projectRoot)
        snapshotState.value = LoadAppSessionUseCase(repository).invoke()
    }

    val windowState = remember(snapshotState.value) {
        ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(KoogAgentGateway()),
            snapshot = snapshotState.value,
        )
    }

    MaterialTheme {
        ChatScreen(windowState)
    }
}
```

- [ ] **Step 3: 在选择 profile 后保存记忆值**

```kotlin
package com.agent.shared.application

import com.agent.shared.config.ProfileSelectionResolver

class LoadAppSessionUseCase(
    private val repository: AppSessionRepository,
) {
    suspend operator fun invoke(): AppSessionSnapshot {
        val profiles = repository.loadProfiles()
        val remembered = repository.loadRememberedProfileId()
        val activeProfile = ProfileSelectionResolver.selectActiveProfile(profiles, remembered)
        if (activeProfile != null) {
            repository.saveRememberedProfileId(activeProfile.id)
        }
        return AppSessionSnapshot(profiles, activeProfile)
    }
}
```

- [ ] **Step 4: 运行桌面编译与共享测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest
.\gradlew.bat :composeApp:compileKotlinDesktop
```

Expected: PASS。

## Task 8: 最终校验与文件检查

**Files:**
- Check: `README.md`
- Check: `AGENTS.md`
- Check: `build.gradle.kts`
- Check: `settings.gradle.kts`
- Check: `composeApp/build.gradle.kts`
- Check: `shared/build.gradle.kts`
- Check: `composeApp/src/desktopMain/kotlin/com/agent/app/Main.kt`
- Check: `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt`
- Check: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
- Check: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
- Check: `shared/src/commonMain/kotlin/com/agent/shared/config/*.kt`
- Check: `shared/src/commonMain/kotlin/com/agent/shared/state/*.kt`
- Check: `shared/src/commonMain/kotlin/com/agent/shared/agent/*.kt`
- Check: `shared/src/commonMain/kotlin/com/agent/shared/application/*.kt`
- Check: `shared/src/desktopMain/kotlin/com/agent/shared/config/*.kt`
- Check: `shared/src/desktopMain/kotlin/com/agent/shared/state/*.kt`

- [ ] **Step 1: 对所有新建和修改的代码文件运行 IDEA inspection**

Expected: 无 error；warning 如为弱提示可忽略。

- [ ] **Step 2: 运行全量构建**

Run:

```powershell
.\gradlew.bat build
```

Expected: PASS。

- [ ] **Step 3: 记录验证结果**

最终汇报至少列出：

```text
1. 删除了哪些旧目录和旧文档
2. 新建了哪些 KMP/Compose 模块和核心文件
3. Koog 是否已升级到 1.0.0
4. 双层 settings 与按项目记忆是否可用
5. build 与 desktopTest 是否通过
```

## Self-Review

- Spec coverage:
  - 主线重置与旧结构清理：Task 1
  - KMP + Compose Desktop 新骨架：Task 2
  - 配置与状态模型：Task 3
  - 双层 settings、环境变量覆盖、项目级 profile 记忆：Task 4
  - Koog 1.0.0 最小消息执行：Task 5
  - Desktop UI 与最小聊天界面：Task 6
  - 真实装配与恢复逻辑：Task 7
  - 最终检查：Task 8
- Placeholder scan:
  - 没有 `TODO`、`TBD` 或“后续补充”占位语
  - 每个任务都给出了明确文件路径和验证命令
- Type consistency:
  - 统一使用 `ProviderType`、`AgentProfile`、`ResolvedAgentProfile`、`AgentStreamEvent`
  - profile 记忆统一走 `DesktopUiStateStore`
  - 加载入口统一走 `LoadAppSessionUseCase`
