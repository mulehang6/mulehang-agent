# Desktop UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性完成桌面聊天界面的交互、信息层级、视觉一致性和响应式打磨。

**Architecture:** 保留现有状态与功能域边界，把可测试的展示规则放在 presentation/component 纯函数中，把 hover、press、tooltip 和折叠状态限制在 Compose 组件内部。只增加任务所需的少量通用 UI 壳，不改 shared 协议。

**Tech Stack:** Kotlin 2.x、Compose Multiplatform Desktop、Material 3、kotlin.test、JUnit 5

## Global Constraints

- 直接在当前 main 工作区修改，不创建 worktree，不创建提交。
- 不启动 Desktop 应用或开发服务器。
- 新增生产类和方法保留简短 KDoc。
- 高频键盘和流式动作不动画；按压反馈保持快速且克制。

---

### Task 1: 展示规则与回归测试

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/presentation/ComposerPresentationTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/presentation/ConversationPresentationTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/presentation/ComposerPresentation.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/presentation/ConversationPresentation.kt`

- [ ] 写入 Plan 状态、工具默认展开、Context tooltip 和窗口密度的失败测试。
- [ ] 运行定向测试并确认因新规则缺失而失败。
- [ ] 添加最小纯函数实现并运行定向测试至通过。

### Task 2: 通用交互壳与语义 token

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt`

- [ ] 为按钮、select 和 rail 增加 hover/press、tooltip 与 semantics。
- [ ] 统一应用配色 token，移除组件间冲突的硬编码主色。
- [ ] 使用 IDEA problems 与编译验证通用壳。

### Task 3: Composer、Plan 与附件

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/PlanCard.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowState.kt`

- [ ] 保持独立 Reasoning 下拉框并恢复 Context Ring。
- [ ] 将主动作改为图标按钮，附件增加移除动作。
- [ ] 将 Plan 改为可展开的紧凑进度摘要。

### Task 4: 时间线、反馈、空态和响应式布局

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AuxiliaryPanels.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt`

- [ ] 用户消息右对齐，Reasoning/Tool Event 详情可折叠。
- [ ] 自动跟随改为即时滚动，反馈改为覆盖式短时消息。
- [ ] 空态动作填充草稿，窄窗口收敛侧栏、rail 和内边距。
- [ ] 统一可见文案并移除无行为的 header 图标。

### Task 5: 验证

**Files:**
- Verify all modified Kotlin files.

- [ ] 对改动文件运行 IDEA problems 检查。
- [ ] 运行 `:desktopApp:test`。
- [ ] 运行 `:desktopApp:compileKotlin`。
- [ ] 检查 git diff，确保没有 vendor、配置或无关格式化漂移。

### Task 6: 恢复推理强度下拉框并统一菜单视觉

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/presentation/ComposerPresentation.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/presentation/ComposerPresentationTest.kt`

**Interfaces:**
- Produces: `reasoningControlLabel(ReasoningEffort?): String`、`RingDropdownMenuItem(...)` 和独立 Reasoning `RingSelectChip`。

- [ ] 增加 `reasoningControlLabel` 的失败测试，要求 `HIGH` 显示为 `High`、空值显示为“推理强度”。
- [ ] 运行定向测试，确认因函数尚不存在而失败。
- [ ] 实现展示标签，并将 Reasoning 从 Model popup 移回独立 trigger。
- [ ] 为 `RingSelectChip` popup 设置 `AppSidebarBackground`、10dp 圆角、`AppLine` 边框、8dp 阴影和 trigger 最小宽度。
- [ ] 使用 `RingDropdownMenuItem` 统一 40dp 菜单项、hover、选中底色和勾选标记。
- [ ] 运行定向测试、IDEA problems、`:desktopApp:test` 与 `:desktopApp:compileKotlin`。

### Task 7: 修正菜单间距并接通系统终端

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AuxiliaryPanels.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/platform/DesktopPlatformActions.kt`
- Create: `desktopApp/src/test/kotlin/com/agent/app/platform/DesktopPlatformActionsTest.kt`

**Interfaces:**
- Produces: `buildSystemTerminalCommand(String): List<String>` 与 `openSystemTerminal(String): Boolean`。

- [ ] 先增加 Windows 系统终端命令的失败测试，并确认因函数缺失而失败。
- [ ] 用参数列表构造 `cmd.exe /c start` 命令，在当前工作区打开可交互终端。
- [ ] 将 TERMINAL rail 项改为一次性动作，移除因此失去入口的内部终端记录面板。
- [ ] 将 tooltip 改为“终端”，并把菜单纵向偏移由 `4.dp` 改为 `-4.dp`。
- [ ] 运行定向测试、IDEA problems、`:desktopApp:test` 与 `:desktopApp:compileKotlin`。

### Task 8: 将终端修正为底部内嵌 PowerShell

**Files:**
- Modify: `desktopApp/build.gradle.kts`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/platform/DesktopPlatformActions.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/platform/DesktopPlatformActionsTest.kt`

**Interfaces:**
- Produces: `buildPowerShellCommand(): List<String>`、`resolveActiveRailGlyph(...)` 与底部 `EmbeddedTerminalPanel`。

- [ ] 先把 shell 命令测试改为 `powershell.exe -NoLogo`，并增加终端打开时 rail 选中的失败测试。
- [ ] 接入 JediTerm 与 Pty4J，用当前工作区作为 PowerShell 的 PTY 工作目录。
- [ ] 把终端作为主工作区底部独立面板，按钮点击负责开关，打开期间按钮保持选中。
- [ ] 关闭或切换工作区时释放终端 session，不保留外部 `cmd.exe` 启动逻辑。
- [ ] 运行定向测试、IDEA problems、`:desktopApp:test` 与 `:desktopApp:compileKotlin`。

### Task 9: 统一终端字体与滚动条

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Produces: `terminalFont(): Font` 与 `shouldShowTerminalScrollbar(Int, Int, Int): Boolean`。

- [ ] 先增加 Maple Mono 字体与内容溢出判断的失败测试。
- [ ] 将终端字体改为 `Maple Mono NF CN SemiBold`、14px。
- [ ] 覆盖 JediTerm 的 Swing 滚动条：无溢出时隐藏，溢出时显示透明轨道、窄圆角滑块且不显示箭头。
- [ ] 运行定向测试、IDEA problems、`:desktopApp:test` 与 `:desktopApp:compileKotlin`。

### Task 10: 独立可拖动工作区与终端

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ResizableWorkspaceLayout.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Produces: `clampTerminalHeight(Float, Float, Float, Float): Float` 与 `ResizableWorkspaceLayout(...)`。

- [ ] 先增加终端高度最小值、最大值和窗口过小时的失败测试，并运行定向测试确认缺少实现。
- [ ] 实现高度夹取纯函数与 8dp 指针拖动分隔带，拖动期间按像素一比一更新高度。
- [ ] 将主工作区和终端拆成独立 `Surface`，关闭终端时主区占满，重新打开时保留会话高度。
- [ ] 清除 JediTerm 根组件及其子组件的 Swing 默认边框，消除左侧白线。

### Task 11: Air 式浮动侧栏

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/design/AirSidebarSurface.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Add submodule: `vendor/liquid-glass`

**Interfaces:**
- Produces: `AirSidebarStyleTokens`、`AirSidebarSurface(...)`、`sidebarHiddenOffsetPx(...)` 与 `airSidebarWidthDp(Boolean): Int`。

- [ ] 先增加侧栏宽度、Air 材质 token、隐藏偏移和紧凑布局参数的失败测试并确认失败。
- [ ] 用 Compose `Surface` 实现半透明深灰遮罩、12dp 圆角、细亮边和柔和阴影，不对背景做模糊或折射。
- [ ] 将根布局改为 `Box`：主区域保持完整尺寸，侧栏覆盖在左上方，右侧 rail 保持独立。
- [ ] 在 header 增加侧栏开关；侧栏用无回弹 spring 从左侧完整滑入/滑出，不叠加透明度动画。

### Task 12: 收敛工具栏、侧栏与下拉框交互

- [x] 右侧 rail 删除终端之外的所有按钮，并保留终端打开时的选中态。
- [x] 侧栏默认关闭，并在指针于侧栏外释放时关闭，不吞掉目标控件的点击。
- [x] Composer 下拉框改为共享展开状态，允许从一个菜单单击切换到另一个菜单。
- [x] 增加右侧 rail、侧栏外部点击和下拉框切换的回归测试。

### Task 13: 最终验证

**Files:**
- Verify all modified Kotlin, Gradle, docs and submodule metadata.

- [ ] 对受影响 Kotlin 文件运行 IDEA problems 检查。
- [ ] 运行定向测试、`:desktopApp:test`、`:desktopApp:compileKotlin` 和 `:desktopApp:packageDistributionForCurrentOS`。
- [ ] 检查 git diff、`.gitmodules` 和 submodule 状态，确保保留用户的根 `build.gradle.kts` 改动且没有无关漂移。

### Task 14: 将 Air 侧栏改为半透明暗色浮层

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/AirSidebarSurface.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Consumes: `AirSidebarStyleTokens.backgroundAlpha: Float` 与 `AirSidebarSurface(...)`。
- Produces: `AirSidebarStyle.backgroundAlpha == 0.58f`；保留现有圆角、阴影、细亮边和顶部微光。

- [x] **Step 1: 写入失败测试**

  将 `should use air sidebar material tokens` 中的背景透明度断言改为：

  ```kotlin
  assertEquals(0.58f, AirSidebarStyle.backgroundAlpha)
  ```

- [x] **Step 2: 运行测试并确认失败**

  Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

  Expected: FAIL，实际值仍为 `0.96f`。

- [x] **Step 3: 写入最小实现**

  将 `AirSidebarStyle` 的材质参数改为：

  ```kotlin
  internal val AirSidebarStyle = AirSidebarStyleTokens(
      cornerRadiusDp = 12,
      shadowElevationDp = 18,
      backgroundAlpha = 0.58f,
      borderAlpha = 0.08f,
  )
  ```

  不修改 `TaskSidebar`、侧栏动画、布局、阴影、边框或顶部渐变。

- [x] **Step 4: 运行定向测试并确认通过**

  Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

  Expected: PASS。

- [x] **Step 5: 完成静态检查与全量验证**

  对两个改动文件运行 IDEA problems 检查，然后执行：

  ```powershell
  .\gradlew.bat :desktopApp:test :desktopApp:compileKotlin
  ```

  Expected: IDEA 无新增问题，Gradle `BUILD SUCCESSFUL`。

### Task 15: 修复 Composer 下拉框重复打开竞态

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt:293-370`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt:256-360`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:274-294`

**Interfaces:**
- Produces: `desiredSelectExpandedState(Boolean?, Boolean): Boolean`。
- Changes: `RingSelectChip.onExpandedChange` 从 `() -> Unit` 改为 `(Boolean) -> Unit`，参数表示本次点击期望的最终展开状态。

- [ ] **Step 1: 写入指针按下快照的失败测试**

  在 `should switch composer menus in one click` 中追加：

  ```kotlin
  assertEquals(
      false,
      desiredSelectExpandedState(
          expandedAtPointerPress = true,
          expandedAtClick = false,
      ),
  )
  assertEquals(
      true,
      desiredSelectExpandedState(
          expandedAtPointerPress = false,
          expandedAtClick = false,
      ),
  )
  assertEquals(
      false,
      desiredSelectExpandedState(
          expandedAtPointerPress = null,
          expandedAtClick = true,
      ),
  )
  ```

- [ ] **Step 2: 运行测试并确认失败**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should switch composer menus in one click"
  ```

  Expected: FAIL，`desiredSelectExpandedState` 尚不存在。

- [ ] **Step 3: 实现最小状态决策函数**

  在 `RingUiShells.kt` 中添加：

  ```kotlin
  /**
   * 以按下 trigger 时的展开状态为准，避免 popup 的外部关闭先改写受控状态。
   */
  internal fun desiredSelectExpandedState(
      expandedAtPointerPress: Boolean?,
      expandedAtClick: Boolean,
  ): Boolean = !(expandedAtPointerPress ?: expandedAtClick)
  ```

- [ ] **Step 4: 让 RingSelectChip 传递期望状态**

  把 `RingSelectChip` 的回调签名改为：

  ```kotlin
  onExpandedChange: (Boolean) -> Unit,
  ```

  在组件内部保存按下快照，并让点击只产生一个最终状态：

  ```kotlin
  var expandedAtPointerPress by remember { mutableStateOf<Boolean?>(null) }

  // 放在 clickable 之前，使用 Initial pass 抢先记录 popup dismiss 前的状态。
  .onPointerEvent(
      eventType = PointerEventType.Press,
      pass = PointerEventPass.Initial,
  ) {
      expandedAtPointerPress = expanded
  }
  .clickable(
      interactionSource = interactionSource,
      indication = null,
  ) {
      val shouldExpand = desiredSelectExpandedState(
          expandedAtPointerPress = expandedAtPointerPress,
          expandedAtClick = expanded,
      )
      expandedAtPointerPress = null
      onExpandedChange(shouldExpand)
  }
  ```

  同时导入 `androidx.compose.ui.input.pointer.PointerEventPass`。

- [ ] **Step 5: 更新四个受控菜单调用方**

  Provider、Model、Reasoning、Permission 均使用同一模式；以下以 Provider 为完整示例：

  ```kotlin
  onExpandedChange = { shouldExpand ->
      expandedMenu = ComposerMenu.PROVIDER.takeIf { shouldExpand }
  },
  ```

  Model、Reasoning、Permission 分别替换为自己的 `ComposerMenu` 值。保留 `dismissComposerMenu`，确保旧 popup 的关闭回调不能清空刚切换的新菜单。

- [ ] **Step 6: 运行定向测试并确认通过**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should switch composer menus in one click"
  ```

  Expected: PASS。

### Task 16: 实体侧栏与精简标题栏

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/AirSidebarSurface.kt:21-57`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt:30-92`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt:107-111`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:232-245`

**Interfaces:**
- Produces: `AirSidebarStyle.backgroundAlpha == 1f`。
- Changes: `ChatHeader` 不再接收 `ChatWindowState`，只接收 `sidebarVisible` 与 `onToggleSidebar`。

- [ ] **Step 1: 将侧栏材质测试改为实体背景并确认失败**

  ```kotlin
  assertEquals(1f, AirSidebarStyle.backgroundAlpha)
  ```

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should use air sidebar material tokens"
  ```

  Expected: FAIL，实际值为 `0.58f`。

- [ ] **Step 2: 只提高侧栏背景不透明度**

  ```kotlin
  internal val AirSidebarStyle = AirSidebarStyleTokens(
      cornerRadiusDp = 12,
      shadowElevationDp = 18,
      backgroundAlpha = 1f,
      borderAlpha = 0.08f,
  )
  ```

  保留 `Color(0xFF232529)`、圆角、阴影、细亮边与顶部微光，不修改侧栏动画和尺寸。

- [ ] **Step 3: 移除标题栏中央信息组**

  将 `ChatHeader` 签名收敛为：

  ```kotlin
  internal fun ChatHeader(
      sidebarVisible: Boolean,
      onToggleSidebar: () -> Unit,
  )
  ```

  删除 `activeConversation`、`workspace`、中央 `Row` 及其专用 import。外层标题栏只保留菜单按钮和 `MH Agent`；`ChatScreen` 调用同步删除 `state = state`。

- [ ] **Step 4: 运行侧栏测试并编译**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should use air sidebar material tokens"
  .\gradlew.bat :desktopApp:compileKotlin
  ```

  Expected: 测试 PASS，编译 `BUILD SUCCESSFUL`。

### Task 17: 将发送/停止按钮改为矢量图标

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt:55-65,235-265,500-590`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt:362-375`

**Interfaces:**
- Produces: `HeaderGlyph.SEND`、`HeaderGlyph.STOP`。
- Changes: `RingPrimaryButton` 新增可选参数 `iconGlyph: HeaderGlyph? = null`；未传入时保持现有文字按钮行为。

- [ ] **Step 1: 扩展主按钮而不改变现有调用**

  在 `HeaderGlyph` 增加：

  ```kotlin
  SEND,
  STOP,
  ```

  在 `RingPrimaryButton` 的 `enabled` 参数前增加：

  ```kotlin
  iconGlyph: HeaderGlyph? = null,
  ```

  将按钮内容改为：

  ```kotlin
  if (iconGlyph != null) {
      RingGlyphIcon(
          glyph = iconGlyph,
          tint = Color.White,
          size = 18.dp,
      )
  } else {
      Text(text)
  }
  ```

- [ ] **Step 2: 用 Canvas 绘制发送与停止图标**

  在 `HeaderGlyphIcon` 的 `when` 中增加：

  ```kotlin
  HeaderGlyph.SEND -> {
      drawLine(tint, Offset(size.width * 0.5f, size.height * 0.78f), Offset(size.width * 0.5f, size.height * 0.24f), strokeWidth, StrokeCap.Round)
      drawLine(tint, Offset(size.width * 0.28f, size.height * 0.46f), Offset(size.width * 0.5f, size.height * 0.24f), strokeWidth, StrokeCap.Round)
      drawLine(tint, Offset(size.width * 0.72f, size.height * 0.46f), Offset(size.width * 0.5f, size.height * 0.24f), strokeWidth, StrokeCap.Round)
  }

  HeaderGlyph.STOP -> {
      drawRoundRect(
          color = tint,
          topLeft = Offset(size.width * 0.29f, size.height * 0.29f),
          size = Size(size.width * 0.42f, size.height * 0.42f),
          cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
      )
  }
  ```

- [ ] **Step 3: 接入 Composer 主动作**

  保持 40dp 点击区域、10dp 圆角、tooltip、危险色和 120ms/0.97 按压反馈，只增加：

  ```kotlin
  iconGlyph = if (primaryActionVisual.danger) HeaderGlyph.STOP else HeaderGlyph.SEND,
  ```

  `text = primaryActionVisual.symbol` 继续保留为模型输出与无图标回退内容。

- [ ] **Step 4: 编译验证所有 enum 分支完整**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`，无遗漏的 `when` 分支。

### Task 18: 持续清理 JediTerm 的迟到边框

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt:41-55,138-154`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:196-214`

**Interfaces:**
- Produces: `installSwingBorderCleanup(Component): Unit`，替代一次性的 `removeSwingBorders(Component): Unit`。

- [ ] **Step 1: 写入迟到边框与迟到子组件的失败测试**

  用以下测试替换只验证一次性递归清理的现有断言：

  ```kotlin
  @Test
  fun `should keep the terminal tree free from late swing borders`() {
      val root = JPanel()
      val existingChild = JPanel()
      root.add(existingChild)

      installSwingBorderCleanup(root)

      existingChild.border = BorderFactory.createLineBorder(java.awt.Color.WHITE)
      val lateChild = JPanel().apply {
          border = BorderFactory.createLineBorder(java.awt.Color.WHITE)
      }
      root.add(lateChild)

      assertNull(root.border)
      assertNull(existingChild.border)
      assertNull(lateChild.border)
  }
  ```

- [ ] **Step 2: 运行测试并确认失败**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should keep the terminal tree free from late swing borders"
  ```

  Expected: FAIL，`installSwingBorderCleanup` 尚不存在。

- [ ] **Step 3: 安装递归且持续生效的边框清理器**

  增加 `java.awt.event.ContainerAdapter` 与 `java.awt.event.ContainerEvent` import，并实现：

  ```kotlin
  /**
   * 清除现有 Swing 边框，并拦截 Look & Feel 或迟到子组件重新注入的边框。
   */
  internal fun installSwingBorderCleanup(component: java.awt.Component) {
      if (component is JComponent) {
          component.border = null
          component.addPropertyChangeListener("border") {
              if (component.border != null) component.border = null
          }
      }
      if (component is Container) {
          component.components.forEach(::installSwingBorderCleanup)
          component.addContainerListener(
              object : ContainerAdapter() {
                  override fun componentAdded(event: ContainerEvent) {
                      installSwingBorderCleanup(event.child)
                  }
              },
          )
      }
  }
  ```

  在 `createPowerShellTerminal` 中用 `installSwingBorderCleanup(this)` 替换一次性的 `removeSwingBorders(this)`，并删除已无调用方的旧函数，避免保留两套边框清理路径。

- [ ] **Step 4: 运行定向测试并确认通过**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should keep the terminal tree free from late swing borders"
  ```

  Expected: PASS。

### Task 19: 静态检查与完整验证

**Files:**
- Verify: `desktopApp/src/main/kotlin/com/agent/app/design/AirSidebarSurface.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Verify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

- [ ] **Step 1: 使用 IDEA 检查全部受影响文件**

  对上述 Kotlin 文件运行 `get_file_problems` 或一次 `lint_files`。Expected: 无新增 error；只报告与本轮无关的既有 warning。

- [ ] **Step 2: 运行桌面模块测试与编译**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test :desktopApp:compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 检查工作区差异**

  使用 IDEA `git_status` 和只读 diff 确认：仅包含计划内文件与用户原有改动；不修改 `vendor/`、密钥、日志、用户配置，不创建 commit。

### Task 20: 延迟并下置 Composer 下拉框 Tooltip

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt:314-402,491-510`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Produces: `SELECT_TOOLTIP_DELAY_MILLIS: Long = 1500L`。
- Changes: `RingTooltip` 新增 `belowAnchor: Boolean` 与 `hoverDelayMillis: Long` 可选参数；Material 的实验性位置类型只保留在函数内部，默认值保持按钮和图标 tooltip 的现有行为。

- [ ] **Step 1: 写入下拉框 tooltip 配置的失败测试**

  在 `ChatScreenPresentationTest` 中增加：

  ```kotlin
  /**
   * Composer 下拉框说明需要延迟出现，避免快速经过控件时产生视觉噪声。
   */
  @Test
  fun `should delay composer select tooltips`() {
      assertEquals(1500L, SELECT_TOOLTIP_DELAY_MILLIS)
  }
  ```

  同时导入 `com.agent.app.design.SELECT_TOOLTIP_DELAY_MILLIS`。

- [ ] **Step 2: 运行定向测试并确认失败**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should delay composer select tooltips"
  ```

  Expected: FAIL，`SELECT_TOOLTIP_DELAY_MILLIS` 尚不存在。

- [ ] **Step 3: 添加带说明的延迟配置与 RingTooltip 参数**

  在 `RingUiShells.kt` 的 Select 配置附近添加：

  ```kotlin
  /** Composer 下拉框说明在持续悬停多久后显示。 */
  internal const val SELECT_TOOLTIP_DELAY_MILLIS = 1500L
  ```

  将 `RingTooltip` 签名扩展为：

  ```kotlin
  /**
   * 为控件提供桌面 tooltip；可选延迟只用于需要降低悬停噪声的控件。
   */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  internal fun RingTooltip(
      text: String?,
      belowAnchor: Boolean = false,
      hoverDelayMillis: Long = 0L,
      content: @Composable () -> Unit,
  )
  ```

  保留 `text == null` 的直接返回分支。默认 `hoverDelayMillis == 0L` 时继续使用 Material 的原生用户输入处理，确保按钮 tooltip 行为不变。

- [ ] **Step 4: 手动控制延迟 tooltip 的悬停生命周期**

  为延迟分支增加 `rememberTooltipState(isPersistent = true)`、`LaunchedEffect` 和不消费事件的 Enter/Exit 监听：

  ```kotlin
  val state = rememberTooltipState(isPersistent = true)
  var hovered by remember { mutableStateOf(false) }

  LaunchedEffect(hovered, hoverDelayMillis) {
      if (hovered) {
          delay(hoverDelayMillis)
          state.show()
      } else {
          state.dismiss()
      }
  }

  TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
          if (belowAnchor) TooltipAnchorPosition.Below else TooltipAnchorPosition.Above,
      ),
      tooltip = { PlainTooltip { Text(text) } },
      state = state,
      enableUserInput = false,
  ) {
      Box(
          modifier = Modifier
              .onPointerEvent(PointerEventType.Enter) { hovered = true }
              .onPointerEvent(PointerEventType.Exit) { hovered = false },
      ) {
          content()
      }
  }
  ```

  增加 `androidx.compose.runtime.LaunchedEffect`、`kotlinx.coroutines.delay` import。注释只解释“禁用 Material 默认立即显示，以反转为延迟且持久的悬停行为”，不逐行复述实现。

- [ ] **Step 5: 仅为 RingSelectChip 启用下方延迟策略**

  将现有的 `RingTooltip(tooltip) {` 调用头替换为：

  ```kotlin
  RingTooltip(
      text = tooltip,
      belowAnchor = true,
      hoverDelayMillis = SELECT_TOOLTIP_DELAY_MILLIS,
  ) {
  ```

  花括号后的现有 `RingSelectChip` 内容保持原样。Provider、Model、Reasoning、Permission 都通过 `RingSelectChip` 自动获得一致行为；其他 `RingTooltip` 调用不传新参数。

- [ ] **Step 6: 运行定向测试与编译**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should delay composer select tooltips" :desktopApp:compileKotlin
  ```

  Expected: 测试 PASS，编译 `BUILD SUCCESSFUL`。

### Task 21: Tooltip 改动静态检查与回归验证

**Files:**
- Verify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Verify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Verify: `docs/superpowers/specs/2026-07-21-desktop-ui-polish-design.md`
- Verify: `docs/superpowers/plans/2026-07-21-desktop-ui-polish-implementation-plan.md`

- [ ] **Step 1: 检查注释与 IDEA 问题**

  确认新增常量、扩展后的 `RingTooltip` 和新测试均有说明意图的 KDoc/注释；对两个 Kotlin 文件运行 IDEA `get_file_problems` 或 `lint_files`。Expected: 无新增 error 或 warning。

- [ ] **Step 2: 运行桌面模块完整测试与编译**

  Run:

  ```powershell
  .\gradlew.bat :desktopApp:test :desktopApp:compileKotlin
  ```

  Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 复核工作区差异**

  使用 IDEA `git_status` 和 `git diff --check` 确认没有无关格式化、vendor 改动或注释缺失；保留用户原有未提交内容，不创建 commit。
