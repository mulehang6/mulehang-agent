# Repository Guidelines

## Module Scope

`shared` contains the Kotlin Multiplatform core: configuration, sessions, agent abstractions, chat use cases, and tool protocols. Put platform-neutral contracts and business rules in `src/commonMain`; keep filesystem, environment, PowerShell, persistence, networking, Koog wiring, and provider implementations in `src/jvmMain`.

Tests live in `src/commonTest` and `src/jvmTest`. Do not depend on Compose Desktop or `desktopApp` from this module.

## Build and Test

Run commands from the repository root:

```powershell
.\gradlew.bat :shared:jvmTest
.\gradlew.bat :shared:compileKotlinJvm
.\gradlew.bat :shared:check
```

Use the smallest relevant task first. Before executing a configuration, inspect available IntelliJ IDEA run configurations. Do not start long-running services.

## Kotlin Style

Follow Kotlin official formatting: four-space indentation, trailing commas, no tabs, `PascalCase` types and `camelCase` members. Keep files focused and avoid speculative abstractions. Public production classes, data classes, objects, and functions need brief KDoc describing their responsibility, inputs/outputs, or notable side effects.

## Testing

Use `kotlin.test`, executed by JUnit 5; use `kotlinx-coroutines-test` for coroutine behavior. Name tests after the subject, such as `SettingsMergerTest.kt`, and use descriptive backticked test names. New behavior and fixes require tests, especially configuration precedence, provider selection, session transitions, permission decisions, and tool-event ordering.

## Configuration and Contributions

Configuration precedence is environment variables, project `.mulehang/settings.json`, user `~/.mulehang/settings.json`, then defaults. Never commit keys, tokens, user settings, logs, or personal paths. Add only placeholder values to `.mulehang/settings.json.example` when introducing settings.

Do not commit unless explicitly authorized. Keep commit subjects in the form `feat(agent): 添加流式回退指导`; PR descriptions should state purpose, implementation, and verification results.
