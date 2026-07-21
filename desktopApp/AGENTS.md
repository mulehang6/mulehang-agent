# Repository Guidelines

## Module Scope

`desktopApp` is the Compose Multiplatform Desktop application. It owns application bootstrap, window lifecycle, chat presentation and state, tool-interaction UI, platform integration, and design components. Source is under `src/main`; tests are under `src/test`.

This module may depend on `shared`; do not move desktop UI types into shared domain models. Keep display behavior and state transitions here, and prefer extracting non-visual state logic where it can be unit tested.

## Build and Test

Run commands from the repository root:

```powershell
.\gradlew.bat :desktopApp:compileKotlin
.\gradlew.bat :desktopApp:test
.\gradlew.bat :desktopApp:packageDistributionForCurrentOS
```

Check IntelliJ IDEA run configurations before running targets, and use the narrowest relevant task. Do not launch the desktop app or other long-running development services during automated work.

## Kotlin and Compose Style

Use Kotlin official style: four-space indentation, trailing commas, no tabs, `PascalCase` types and `camelCase` functions and properties. Place new code in the closest existing functional package (`bootstrap`, `chat`, `tool`, `design`, or `platform`). Keep composables small and let `shared` own reusable domain rules.

Production classes, data classes, objects, and functions need concise KDoc. Comments should explain constraints and intent rather than restating code. Avoid unrelated refactors or broad formatting changes.

## Testing

Use `kotlin.test` with JUnit 5. Test files should match the subject name, for example `ChatWindowStateTest.kt`; test names may use readable backticked sentences. Cover new state transitions, errors, and interaction ordering. Favor unit tests for presentation-state transformations over fragile pixel or timing assertions.

## Contribution and Safety

Never expose API keys, tokens, logs, user settings, or real local paths. Do not commit without explicit authorization. Use commit subjects such as `fix(chat): 修复工具调用状态同步`; PRs must describe the change, verification, and provide screenshots when visual behavior changes.
