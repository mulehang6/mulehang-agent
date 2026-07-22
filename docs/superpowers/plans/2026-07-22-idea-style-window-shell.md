# IDEA Style Window Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Compose Desktop 窗口改成 IDEA 风格的深色一体化顶部外壳与右侧终端栏，优先使用 JBR 原生窗口按钮和系统最小化/最大化过渡，并落实截图标注的图标、字号和任务卡可读性调整，同时保持现有业务行为不变。

**Architecture:** 运行时检测 JBR `WindowDecorations`：可用时使用系统装饰窗口并安装 48dp 的原生 `CustomTitleBar`，让 JBR/Windows 提供一致的最小化、最大化/还原按钮与系统过渡；不可用时回退到 Compose `WindowDecoration.Undecorated()` 和现有自绘按钮。标题栏独占最上方整行，工作区与右侧栏从标题栏下方开始；终端、任务和 Composer 不新增业务状态，只在现有展示组件中应用明确的尺寸 token。

**Tech Stack:** Kotlin、Compose Multiplatform Desktop 1.11.1、Material 3、kotlin.test、JUnit 5、Gradle Wrapper、JDK 21。

## Global Constraints

- 仅修改 `desktopApp` 展示层和本计划文档，不修改 `shared`、React 原型或 vendor 子模块。
- 保留窗口拖动、缩放、最小化、最大化/还原和关闭能力。
- 不改变终端、聊天、任务创建、搜索、权限选择和消息发送逻辑。
- 右侧只保留终端工具栏，不新增左侧或底部 IDEA 工具栏。
- 所有新增生产函数写简短 KDoc；使用 `apply_patch` 修改文件。
- 未经用户明确授权不提交，不启动 Desktop 应用或其他长期运行服务。

**Execution Status:** Tasks 1–5 已按首版设计实现；Tasks 6–8 已按第二轮截图与窗口过渡反馈实现并通过验证；Tasks 9–11 是根据第三轮红字反馈追加的菜单命中、原生 hover 边界和 rail 配色修订。

## File Map

- `desktopApp/src/main/kotlin/com/agent/app/bootstrap/Main.kt`：启用无系统装饰窗口并传递窗口状态。
- `desktopApp/src/main/kotlin/com/agent/app/bootstrap/NativeWindowTitleBar.kt`：选择原生/回退窗口外壳并安装 JBR 自定义标题栏。
- `desktopApp/build.gradle.kts`：显式声明生产代码使用的 JBR API 版本。
- `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt`：在 `WindowScope` 中装配应用。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`：连接标题栏、工作区和右侧栏。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`：绘制可拖动标题栏与窗口控制按钮。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/AuxiliaryPanels.kt`：绘制贴边连续右侧栏。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`：扩大终端关闭按钮。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt`：调整分组标题、路径字体和任务卡间距。
- `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`：调整 rail 按钮并修正 glyph Canvas 尺寸。
- `desktopApp/src/test/kotlin/com/agent/app/bootstrap/MainTest.kt`：覆盖窗口放置转换。
- `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`：锁定关键视觉尺寸。

---

### Task 1: 自绘窗口标题栏并保留窗口能力

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/bootstrap/MainTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/Main.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`

**Interfaces:**
- Produces: `toggleWindowPlacement(current: WindowPlacement): WindowPlacement`
- Produces: `WindowScope.MulehangDesktopApp(initialProjectRoot: Path?, windowState: WindowState, onCloseRequest: () -> Unit)`
- Produces: `WindowScope.ChatScreen(state: ChatWindowState, windowState: WindowState, onCloseRequest: () -> Unit)`
- Produces: `WindowScope.ChatHeader(sidebarVisible: Boolean, onToggleSidebar: () -> Unit, windowState: WindowState, onCloseRequest: () -> Unit)`

- [ ] **Step 1: 写窗口放置状态的失败测试**

```kotlin
import androidx.compose.ui.window.WindowPlacement

@Test
fun `should toggle between floating and maximized placement`() {
    assertEquals(WindowPlacement.Maximized, toggleWindowPlacement(WindowPlacement.Floating))
    assertEquals(WindowPlacement.Floating, toggleWindowPlacement(WindowPlacement.Maximized))
    assertEquals(WindowPlacement.Maximized, toggleWindowPlacement(WindowPlacement.Fullscreen))
}
```

- [ ] **Step 2: 运行测试并确认因缺少函数而失败**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.bootstrap.MainTest"
```

Expected: FAIL，错误包含 `Unresolved reference 'toggleWindowPlacement'`。

- [ ] **Step 3: 实现窗口入口**

在 `Main.kt` 为 `Window` 设置 `decoration = WindowDecoration.Undecorated()`，保留默认 8dp resizer，并把 `windowState` 和 `::exitApplication` 传给根 composable。添加：

```kotlin
/** 返回点击最大化按钮后的窗口放置状态。 */
internal fun toggleWindowPlacement(current: WindowPlacement): WindowPlacement =
    if (current == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
```

- [ ] **Step 4: 将 `WindowScope` 与窗口动作透传到标题栏**

把 `MulehangDesktopApp`、`ChatScreen` 和 `ChatHeader` 改为上述 `WindowScope` 扩展签名。`ChatHeader` 使用 48dp 高度：菜单按钮在左，产品名位于填满剩余宽度的 `WindowDraggableArea` 中，右侧依次为 46×48dp 的最小化、最大化/还原、关闭按钮。按钮分别设置 `windowState.isMinimized = true`、`windowState.placement = toggleWindowPlacement(...)` 和 `onCloseRequest()`；提供语义描述，关闭按钮只在 hover 时使用危险色。

- [ ] **Step 5: 运行 MainTest**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.bootstrap.MainTest"
```

Expected: PASS，0 failed tests。

- [ ] **Step 6: IDEA 检查 Task 1 文件**

对五个生产文件执行 `get_file_problems`。Expected: 无新增 error；会话、侧栏和终端状态代码没有行为差异。

---

### Task 2: 将终端入口做成 IDEA 风格右侧栏

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AuxiliaryPanels.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`

**Interfaces:**
- Produces: `TOOL_RAIL_WIDTH_DP = 48`
- Produces: `RAIL_ACTION_SIZE_DP = 40`

- [ ] **Step 1: 写尺寸失败测试**

```kotlin
@Test
fun `should expose idea style right rail metrics`() {
    assertEquals(48, TOOL_RAIL_WIDTH_DP)
    assertEquals(40, RAIL_ACTION_SIZE_DP)
}
```

- [ ] **Step 2: 运行并确认 token 缺失**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should expose idea style right rail metrics"
```

Expected: FAIL with `Unresolved reference`。

- [ ] **Step 3: 实现贴边连续外壳**

在 `AuxiliaryPanels.kt` 定义 48dp rail token，让 `ToolRail` 固定宽度、填满高度、使用 `AppRailBackground`，左边绘制 1dp `AppLine` 分隔线，顶部内边距 8dp。`ChatScreen.kt` 移除硬编码的 42dp。

在 `RingUiShells.kt` 定义 40dp action token，将 `RingRailActionButton` 从 34×34dp 改为 40×40dp；保留现有 20dp 图标、active、hover、pressed、tooltip 和 `onClick`。

- [ ] **Step 4: 运行测试并检查文件**

重复 Step 2 命令，Expected: PASS。随后对三个生产文件执行 `get_file_problems`，确认无新增 error，终端开关和无工作区提示逻辑未改变。

---

### Task 3: 修正发送箭头与终端关闭按钮比例

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`

**Interfaces:**
- Produces: `COMPOSER_PRIMARY_GLYPH_SIZE_DP = 18`
- Produces: `TERMINAL_CLOSE_BUTTON_SIZE_DP = 36`

- [ ] **Step 1: 写动作尺寸失败测试**

```kotlin
@Test
fun `should expose readable composer and terminal action metrics`() {
    assertEquals(18, COMPOSER_PRIMARY_GLYPH_SIZE_DP)
    assertEquals(36, TERMINAL_CLOSE_BUTTON_SIZE_DP)
}
```

- [ ] **Step 2: 运行并确认 token 缺失**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should expose readable composer and terminal action metrics"
```

Expected: FAIL with `Unresolved reference`。

- [ ] **Step 3: 让 glyph 请求尺寸传递到 Canvas**

在 `RingUiShells.kt` 定义 18dp token。为 `HeaderGlyphIcon` 增加 `size: Dp = 16.dp` 参数并将 Canvas 改为 `Modifier.size(size)`；`RingGlyphIcon` 把已有 `size` 继续传入。`RingPrimaryButton` 使用 18dp token，因此发送/停止图标真实绘制为 18dp，其他 header glyph 保持 16dp。

- [ ] **Step 4: 扩大终端关闭按钮**

在 `EmbeddedTerminalPanel.kt` 定义 36dp token；关闭 Box 使用该尺寸，符号使用 `titleLarge` 与 `AppMuted`。保持 `clickable(onClick = onClose)`、语义描述和终端释放逻辑不变。

- [ ] **Step 5: 运行测试并检查文件**

重复 Step 2 命令，Expected: PASS。随后对两个生产文件执行 `get_file_problems`，确认 Composer 发送/停止和终端关闭语义未改变。

---

### Task 4: 提升任务侧栏文字与卡片层级

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt`

**Interfaces:**
- Produces: `TASK_SECTION_TITLE_FONT_SIZE_SP = 13`
- Produces: `TASK_PATH_FONT_SIZE_SP = 13`

- [ ] **Step 1: 写排版失败测试**

```kotlin
@Test
fun `should expose readable task sidebar typography`() {
    assertEquals(13, TASK_SECTION_TITLE_FONT_SIZE_SP)
    assertEquals(13, TASK_PATH_FONT_SIZE_SP)
}
```

- [ ] **Step 2: 运行并确认 token 缺失**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should expose readable task sidebar typography"
```

Expected: FAIL with `Unresolved reference`。

- [ ] **Step 3: 应用排版与间距**

在 `TaskSidebar.kt` 定义两个 13sp token。分组标题使用 `bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.SemiBold)`。任务卡内边距改为水平 14dp、垂直 12dp，内部间距 5dp；路径基于 `bodySmall`，显式使用 `FontFamily.SansSerif`、13sp、18sp 行高和 `AppMuted.copy(alpha = 0.92f)`，继续单行省略。标题、统计、点击和选中逻辑不变。

- [ ] **Step 4: 运行测试并检查文件**

重复 Step 2 命令，Expected: PASS。对 `TaskSidebar.kt` 执行 `get_file_problems`，确认搜索、过滤、新建任务和选择逻辑无差异。

---

### Task 5: 全量验证与交付

**Files:**
- Inspect: File Map 中所有 Kotlin 文件
- Inspect: `docs/superpowers/specs/2026-07-22-idea-style-window-shell-design.md`

**Interfaces:**
- Consumes: Tasks 1–4 的窗口壳、右侧栏和排版 token。
- Produces: 可编译、测试通过且无越界改动的变更集。

- [ ] **Step 1: 运行全部桌面测试**

```powershell
.\gradlew.bat :desktopApp:test
```

Expected: `BUILD SUCCESSFUL`，0 failed tests。

- [ ] **Step 2: 编译桌面应用**

```powershell
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: `BUILD SUCCESSFUL`，无 Kotlin 编译错误。

- [ ] **Step 3: IDEA 检查全部受影响 Kotlin 文件**

对 File Map 中九个生产文件执行 `get_file_problems`。Expected: 无新增 error；既有 warning 单独记录。

- [ ] **Step 4: 检查 diff**

```powershell
git diff --check
git diff -- desktopApp docs/superpowers/specs docs/superpowers/plans
```

Expected: `git diff --check` 无输出；diff 仅覆盖顶部一体化、右侧终端栏、发送/关闭图标和侧栏排版。

- [ ] **Step 5: 报告验证证据与限制**

交付说明列出精确测试、编译与 IDEA 检查结果。仓库禁止启动桌面应用，因此明确说明未进行运行时截图验证；不使用主观判断替代构建和测试证据。

---

### Task 6: 使用 JBR 原生窗口按钮恢复系统过渡

**Files:**
- Modify: `desktopApp/build.gradle.kts`
- Create: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/NativeWindowTitleBar.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/bootstrap/MainTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/Main.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`

**Interfaces:**
- Produces: `WindowChromeMode { JBR_NATIVE, COMPOSE_FALLBACK }`
- Produces: `resolveWindowChromeMode(nativeDecorationsSupported: Boolean): WindowChromeMode`
- Produces: `windowDecorationFor(mode: WindowChromeMode): WindowDecoration`
- Produces: `APP_TITLE_BAR_HEIGHT_DP = 48`

- [x] **Step 1: 写窗口外壳选择的失败测试**

在 `MainTest.kt` 添加：

```kotlin
@Test
fun `should prefer native title bar and retain compose fallback`() {
    assertEquals(WindowChromeMode.JBR_NATIVE, resolveWindowChromeMode(true))
    assertEquals(WindowChromeMode.COMPOSE_FALLBACK, resolveWindowChromeMode(false))
    assertEquals(WindowDecoration.SystemDefault, windowDecorationFor(WindowChromeMode.JBR_NATIVE))
    assertEquals(48, APP_TITLE_BAR_HEIGHT_DP)
}
```

- [x] **Step 2: 运行并确认缺少外壳选择接口**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.bootstrap.MainTest.should prefer native title bar and retain compose fallback"
```

Expected: FAIL，错误包含 `Unresolved reference 'WindowChromeMode'` 或其他新增接口名。

- [x] **Step 3: 实现可测试的原生/回退选择**

在 `desktopApp/build.gradle.kts` 显式添加 `implementation("org.jetbrains.runtime:jbr-api:1.9.0")`，与 Compose Desktop 已解析的 JBR API 版本一致。在 `NativeWindowTitleBar.kt` 定义模式、48dp 高度 token、纯选择函数和窗口装饰映射。`isNativeWindowDecorationsSupported()` 使用 `runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)`，避免不兼容运行时在启动阶段中断。

```kotlin
internal fun resolveWindowChromeMode(nativeDecorationsSupported: Boolean): WindowChromeMode =
    if (nativeDecorationsSupported) WindowChromeMode.JBR_NATIVE else WindowChromeMode.COMPOSE_FALLBACK

internal fun windowDecorationFor(mode: WindowChromeMode): WindowDecoration = when (mode) {
    WindowChromeMode.JBR_NATIVE -> WindowDecoration.SystemDefault
    WindowChromeMode.COMPOSE_FALLBACK -> WindowDecoration.Undecorated()
}
```

- [x] **Step 4: 在窗口生命周期中安装 JBR `CustomTitleBar`**

`Main.kt` 在 `application` 作用域中只解析一次 `WindowChromeMode`，将 `windowDecorationFor(mode)` 传给 `Window`，再把模式传到 `MulehangDesktopApp`。

在 `MulehangDesktopApp.kt` 使用 `DisposableEffect(window, windowChromeMode, density)`：仅在 `JBR_NATIVE` 模式创建 `CustomTitleBar`，将高度设为 `APP_TITLE_BAR_HEIGHT_DP * density`，设置 `controls.dark = true`，然后调用 `setCustomTitleBar(window, titleBar)`；释放时调用 `setCustomTitleBar(window, null)`。JBR 服务意外为空时保持窗口可用，不安装半成品标题栏。

- [x] **Step 5: 原生路径移除自绘字符按钮，回退路径保持功能**

将 `windowChromeMode` 透传到 `ChatScreen` 和 `ChatHeader`。`JBR_NATIVE` 路径不渲染三个 Compose 字符按钮，为右上角 JBR 原生按钮保留空间；`COMPOSE_FALLBACK` 路径继续使用现有最小化、放置切换和关闭动作。保留 `WindowDraggableArea`，不实现窗口 bounds 插值或内容缩放动画。

- [x] **Step 6: 运行定向测试并检查文件**

重复 Step 2 命令，Expected: PASS。对本 Task 的五个生产文件及新文件执行 IDEA `get_file_problems`，Expected: 无 error。

---

### Task 7: 让标题栏覆盖整宽并将右侧栏对齐到工作区

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AuxiliaryPanels.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`

**Interfaces:**
- Produces: `TOOL_RAIL_TOP_PADDING_DP = 16`
- Changes: `AppRailBackground == AppWorkspaceBackground`

- [x] **Step 1: 写右侧栏层级与视觉 token 的失败测试**

在 `ChatScreenPresentationTest.kt` 添加：

```kotlin
@Test
fun `should align the right rail with the workspace below the title bar`() {
    assertEquals(16, TOOL_RAIL_TOP_PADDING_DP)
    assertEquals(AppWorkspaceBackground, AppRailBackground)
}
```

- [x] **Step 2: 运行并确认新 token 缺失或颜色不一致**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should align the right rail with the workspace below the title bar"
```

Expected: FAIL，原因是 `TOOL_RAIL_TOP_PADDING_DP` 不存在或 rail 背景仍为独立颜色。

- [x] **Step 3: 调整页面层级**

在 `ChatScreen.kt` 将根内容改为 `Column`：先渲染整宽 `ChatHeader`，再渲染占剩余高度的 `Row`；该 Row 内左侧是 `WorkspacePanel(weight = 1f)`，右侧是 `ToolRail`。这样右侧栏从标题栏底部开始，终端入口不会再与窗口按钮处于同一横向层级。保持侧栏 overlay、窗口失焦遮罩和 backdrop 捕获逻辑不变。

- [x] **Step 4: 统一背景并对齐第一个按钮**

在 `RingUiShells.kt` 令 `AppRailBackground = AppWorkspaceBackground`。在 `AuxiliaryPanels.kt` 定义 `TOOL_RAIL_TOP_PADDING_DP = 16` 并应用到 rail 顶部 padding，使首个 40dp 工具按钮与非紧凑模式工作区 Surface 的 16dp 上边距对齐；宽度、分隔线、hover、active 和终端开关行为保持不变。

- [x] **Step 5: 运行定向测试并检查文件**

重复 Step 2 命令，Expected: PASS。对本 Task 的三个生产文件执行 IDEA `get_file_problems`，Expected: 无 error。

---

### Task 8: 修订版全量验证与交付

**Files:**
- Inspect: Tasks 6–7 的全部 Kotlin 文件
- Inspect: `docs/superpowers/specs/2026-07-22-idea-style-window-shell-design.md`
- Inspect: 本计划文档

**Interfaces:**
- Consumes: 原生标题栏选择、回退按钮、整宽标题栏和右侧栏对齐 token。
- Produces: 测试与编译通过、IDEA 无错误、无格式问题且不越界的修订版变更集。

- [x] **Step 1: 运行全部桌面测试**

```powershell
.\gradlew.bat :desktopApp:test
```

Expected: `BUILD SUCCESSFUL`，0 failed tests。

- [x] **Step 2: 编译桌面应用**

```powershell
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: `BUILD SUCCESSFUL`，无 Kotlin 编译错误。

- [x] **Step 3: IDEA 检查全部受影响 Kotlin 文件**

对 Tasks 6–7 的所有生产与测试 Kotlin 文件执行 `get_file_problems`。Expected: 无 error；既有 warning 单独记录。

- [x] **Step 4: 检查变更边界与格式**

```powershell
git diff --check
git status --short
git diff -- desktopApp docs/superpowers/specs docs/superpowers/plans
```

Expected: `git diff --check` 无格式错误；diff 仅覆盖已确认的窗口标题栏、系统过渡、右侧栏、图标与任务侧栏样式。不得提交。

- [x] **Step 5: 报告验证限制**

仓库规则禁止启动 Desktop 应用，因此交付时明确说明未做运行时截图验证；原生过渡由 JBR/Windows 控制，不以人工 bounds 动画代替。请用户在本机运行时重点核对三项：原生按钮外观、最小化/最大化系统过渡、右侧首按钮与主内容顶边对齐。

---

### Task 9: 修复 JBR 标题栏高度与菜单命中

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/bootstrap/MainTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/NativeWindowTitleBar.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`

**Interfaces:**
- Produces: `nativeTitleBarHeightPx(): Float`
- Produces: `NativeTitleBarHandle(forceHitTest: (Boolean) -> Unit)`
- Produces: `NativeTitleBarHandle.forceClientArea(): Unit`
- Produces: `WindowScope.rememberNativeWindowTitleBar(mode: WindowChromeMode): NativeTitleBarHandle?`
- Consumes: `APP_TITLE_BAR_HEIGHT_DP = 48`

- [x] **Step 1: 写 JBR 高度与客户端命中的失败测试**

在 `MainTest.kt` 添加：

```kotlin
@Test
fun `should keep native title bar height in awt coordinates`() {
    assertEquals(48f, nativeTitleBarHeightPx())
}

@Test
fun `should mark menu pointer events as native title bar client area`() {
    var requestedClientArea: Boolean? = null
    val handle = NativeTitleBarHandle { client -> requestedClientArea = client }

    handle.forceClientArea()

    assertEquals(true, requestedClientArea)
}
```

- [x] **Step 2: 运行测试并确认新增接口缺失**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.bootstrap.MainTest"
```

Expected: FAIL，错误包含 `Unresolved reference 'nativeTitleBarHeightPx'` 或 `Unresolved reference 'NativeTitleBarHandle'`。

- [x] **Step 3: 提取不重复缩放的原生高度与可测试命中句柄**

在 `NativeWindowTitleBar.kt` 添加：

```kotlin
/** 返回 JBR/AWT 客户区使用的标题栏高度，不重复应用 Compose density。 */
internal fun nativeTitleBarHeightPx(): Float = APP_TITLE_BAR_HEIGHT_DP.toFloat()

/** 将 Compose 标题栏交互桥接到 JBR 的逐事件命中测试。 */
internal class NativeTitleBarHandle(
    private val forceHitTest: (Boolean) -> Unit,
) {
    /** 将当前鼠标事件标记为客户区交互。 */
    fun forceClientArea() {
        forceHitTest(true)
    }
}
```

- [x] **Step 4: 让安装函数返回当前 JBR 标题栏句柄**

使用 IDEA `rename_refactoring` 将 `rememberNativeWindowTitleBar` 重命名为 `rememberNativeWindowTitleBar`，返回 `NativeTitleBarHandle?`。移除 `LocalDensity` 和 `density` effect key；创建标题栏时使用 `titleBar.setHeight(nativeTitleBarHeightPx())`。仅在 `setCustomTitleBar` 成功后设置 `NativeTitleBarHandle(titleBar::forceHitTest)`，dispose 时先清空句柄再卸载标题栏。JBR 不可用或回退模式返回 `null`。

```kotlin
@Composable
internal fun WindowScope.rememberNativeWindowTitleBar(mode: WindowChromeMode): NativeTitleBarHandle? {
    var handle by remember(mode) { mutableStateOf<NativeTitleBarHandle?>(null) }
    DisposableEffect(window, mode) {
        if (mode != WindowChromeMode.JBR_NATIVE) {
            return@DisposableEffect onDispose { }
        }
        val frame = window as? Frame ?: return@DisposableEffect onDispose { }
        val decorations = runCatching { JBR.getWindowDecorations() }.getOrNull()
            ?: return@DisposableEffect onDispose { }
        val titleBar = runCatching {
            val configuredTitleBar = decorations.createCustomTitleBar()
            configuredTitleBar.setHeight(nativeTitleBarHeightPx())
            configuredTitleBar.putProperty("controls.dark", true)
            decorations.setCustomTitleBar(frame, configuredTitleBar)
            configuredTitleBar
        }.getOrNull()

        if (titleBar != null) {
            handle = NativeTitleBarHandle(titleBar::forceHitTest)
        }
        onDispose {
            handle = null
            if (titleBar != null) {
                runCatching { decorations.setCustomTitleBar(frame, null) }
            }
        }
    }
    return handle
}
```

- [x] **Step 5: 将菜单指针事件声明为 JBR 客户区**

`MulehangDesktopApp.kt` 保存 `rememberNativeWindowTitleBar(windowChromeMode)` 的返回值，并向 `ChatScreen`、`ChatHeader` 透传无参数回调 `onTitleBarClientPointerEvent: (() -> Unit)?`：

```kotlin
val nativeTitleBarHandle = rememberNativeWindowTitleBar(windowChromeMode)

ChatScreen(
    state = windowState,
    desktopWindowState = desktopWindowState,
    windowChromeMode = windowChromeMode,
    onTitleBarClientPointerEvent = nativeTitleBarHandle?.let { handle ->
        { handle.forceClientArea() }
    },
    onCloseRequest = onCloseRequest,
)
```

`ChatScreen` 仅原样透传该回调。IJ Debugger 运行时取证确认 JBR 会在 Compose 的 `SkiaLayer` 收到事件之前把菜单坐标判定为非客户区，因此不再使用 Compose `onPointerEvent`。在 `ChatHeader.kt` 的 36dp 菜单范围内改用 `SwingPanel` 承载 `NativeTitleBarMenuHitTarget`：

```kotlin
internal fun createNativeTitleBarMenuHitTarget(
    onClientMouseEvent: () -> Unit,
    onClick: () -> Unit,
): NativeTitleBarMenuHitTarget
```

命中组件注册 MouseListener 与 MouseMotionListener，逐事件调用 `onClientMouseEvent`，只在有效左键释放时调用 `onClick`；组件与 Swing 互操作宿主都使用 `AppHeaderBackground`，避免透明互操作层露出白色画布。它只覆盖菜单按钮区域，不得应用到标题文字、空白拖动区或整个 header。回退模式仍使用原 Compose 按钮，行为保持不变。

- [x] **Step 6: 运行定向测试并检查文件**

重复 Step 2 命令，Expected: PASS。对本 Task 的五个生产文件和测试执行 IDEA `get_file_problems`，Expected: 无 error。

---

### Task 10: 将右侧工具栏背景改为标题栏颜色

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`

**Interfaces:**
- Changes: `AppRailBackground == AppHeaderBackground`
- Preserves: `TOOL_RAIL_TOP_PADDING_DP = 16`

- [x] **Step 1: 将 rail 配色断言改成第三轮红字要求**

在 `ChatScreenPresentationTest.kt` 导入 `AppHeaderBackground`，并将现有测试改为：

```kotlin
@Test
fun `should align the right rail with the workspace below the title bar`() {
    assertEquals(16, TOOL_RAIL_TOP_PADDING_DP)
    assertEquals(AppHeaderBackground, AppRailBackground)
}
```

- [x] **Step 2: 运行并确认旧配色导致失败**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should align the right rail with the workspace below the title bar"
```

Expected: FAIL，`AppRailBackground` 当前等于 `AppWorkspaceBackground`，与 `AppHeaderBackground` 不相等。

- [x] **Step 3: 应用标题栏背景 token**

在 `RingUiShells.kt` 使用单一 token 映射：

```kotlin
internal val AppRailBackground = AppHeaderBackground
```

不改 `ToolRail` 的宽度、16dp 顶部间距、终端按钮或开关逻辑；移除 rail 左边单独绘制的竖向分隔线，使其与窗口边缘工具区保持一体。

- [x] **Step 4: 运行定向测试并检查文件**

重复 Step 2 命令，Expected: PASS。对两个文件执行 IDEA `get_file_problems`，Expected: 无 error。

---

### Task 11: 第三轮修订全量验证

**Files:**
- Inspect: Tasks 9–10 的全部 Kotlin 文件
- Inspect: `docs/superpowers/specs/2026-07-22-idea-style-window-shell-design.md`
- Inspect: 本计划文档

**Interfaces:**
- Consumes: `NativeTitleBarHandle`、48px JBR 高度、菜单客户端命中和标题栏同色 rail。
- Produces: 测试与编译通过、IDEA 无错误、无格式问题的第三轮修订。

- [x] **Step 1: 运行全部桌面测试**

```powershell
.\gradlew.bat :desktopApp:test
```

Expected: `BUILD SUCCESSFUL`，0 failed tests。

- [x] **Step 2: 编译桌面应用**

```powershell
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: `BUILD SUCCESSFUL`，无 Kotlin 编译错误。

- [x] **Step 3: IDEA 检查全部受影响文件**

对 Tasks 9–10 的全部生产与测试 Kotlin 文件执行 `get_file_problems`。Expected: 无 error；既有 warning 单独记录。

- [x] **Step 4: 检查 diff**

```powershell
git diff --check
git status --short
git diff -- desktopApp docs/superpowers/specs docs/superpowers/plans
```

Expected: `git diff --check` 无格式错误；新增 diff 只覆盖 JBR 高度、菜单命中和 rail 配色。不得提交。

- [x] **Step 5: 记录运行时视觉核对项**

用户明确要求使用 IJ Debugger 后，以 `desktopApp [hot] 🔥` 调试配置启动并自动点击菜单：日志断点应显示 `forceHitTest(true)` 命中、有效释放为 `shouldClick=true`、`sidebarVisible` 从 `false` 变为 `true`。随后最大化窗口截图核对菜单无白色互操作底、侧栏已打开、右侧 rail 左边无额外竖向分隔线，并保留原生窗口控制。
