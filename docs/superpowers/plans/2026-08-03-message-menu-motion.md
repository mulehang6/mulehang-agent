# Message And Menu Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户消息从输入框准确飞到最终时间线卡片，并让下拉菜单和右键菜单从各自触发点自然生长。

**Architecture:** 消息飞行继续使用独立覆盖层，但取消临时落点，只有时间线滚动完成并测得真实卡片边界后才启动。菜单动效集中到 `design` 包的共享状态函数，业务组件只选择下拉或右键原点并应用 `graphicsLayer`。

**Tech Stack:** Kotlin、Compose Multiplatform Desktop、Material 3、`kotlin.test`、JUnit 5。

## Global Constraints

- 只修改消息飞行和菜单动效，不覆盖工作区中的其他已有改动。
- 动画只使用位移、透明度和缩放，单次 UI 动画不超过 260ms。
- 进入使用强 ease-out，退出更快；下拉菜单从触发器顶部边缘生长，右键菜单从点击点左上角生长。
- 每个新增生产类型和函数写简短 KDoc。
- 未经用户明确授权不创建 Git 提交，因此计划中的实现步骤不包含提交操作。

---

### Task 1: 修复消息飞行目标

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/MessageFlightGeometryTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/MessageFlightGeometry.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`

**Interfaces:**
- Consumes: `messageFlightPath(composerBounds: Rect, targetBounds: Rect): MessageFlightPath`
- Produces: `shouldStartMessageFlight(targetBounds: Rect?): Boolean`

- [ ] **Step 1: 写等待真实目标的失败测试**

```kotlin
@Test
fun `should start message flight only after the real target is measured`() {
    assertFalse(shouldStartMessageFlight(null))
    assertTrue(shouldStartMessageFlight(Rect(428f, 352f, 704f, 404f)))
}
```

- [ ] **Step 2: 运行定向测试并确认 RED**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.MessageFlightGeometryTest"`

Expected: FAIL，原因是 `shouldStartMessageFlight` 尚不存在。

- [ ] **Step 3: 写最小目标门控实现**

```kotlin
/** 仅在最终用户消息卡片已完成测量后启动飞行动画。 */
internal fun shouldStartMessageFlight(targetBounds: Rect?): Boolean = targetBounds != null
```

在 `WorkspacePanel.kt` 中完成以下外科式修改：

```kotlin
hiddenUserMessageContent = messageFlight?.content
```

```kotlin
if (flight == null || composerBounds == null || targetBounds == null || workspaceOrigin == null) return
val renderedTargetBounds = targetBounds
```

删除 `fallbackMessageFlightTargetBounds` 及其测试；把飞行时长从 `360` 调整为 `240`，保留路径冻结，并使用 `CubicBezierEasing(0.16f, 1f, 0.3f, 1f)`。覆盖层颜色改为与最终用户卡片相同的 `AppUserCardBackground`。

- [ ] **Step 4: 运行定向测试并确认 GREEN**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.MessageFlightGeometryTest"`

Expected: PASS。

- [ ] **Step 5: 用 IDEA 检查三个改动文件**

Run: 对三个文件分别执行 `get_file_problems`。

Expected: 无新增 error。

---

### Task 2: 建立共享菜单生长动效

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/design/MenuGrowthMotion.kt`
- Create: `desktopApp/src/test/kotlin/com/agent/app/design/MenuGrowthMotionTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Produces: `MenuGrowthOrigin`, `MenuGrowthTargets`, `menuGrowthTargets(expanded: Boolean)`, `menuGrowthTransformOrigin(origin: MenuGrowthOrigin)`, `rememberMenuGrowthMotion(expanded: Boolean, label: String)`

- [ ] **Step 1: 写菜单目标值和原点的失败测试**

```kotlin
@Test
fun `should grow menus from compact transparent state`() {
    assertEquals(MenuGrowthTargets(0.96f, 0f, -4f), menuGrowthTargets(false))
    assertEquals(MenuGrowthTargets(1f, 1f, 0f), menuGrowthTargets(true))
}

@Test
fun `should use trigger and pointer menu origins`() {
    assertEquals(TransformOrigin(0.5f, 0f), menuGrowthTransformOrigin(MenuGrowthOrigin.Dropdown))
    assertEquals(TransformOrigin(0f, 0f), menuGrowthTransformOrigin(MenuGrowthOrigin.Context))
}
```

- [ ] **Step 2: 运行新测试并确认 RED**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.design.MenuGrowthMotionTest"`

Expected: FAIL，原因是共享菜单动效 API 尚不存在。

- [ ] **Step 3: 实现最小共享动效状态**

```kotlin
internal enum class MenuGrowthOrigin { Dropdown, Context }

internal data class MenuGrowthTargets(
    val scale: Float,
    val alpha: Float,
    val translationYDp: Float,
)

internal fun menuGrowthTargets(expanded: Boolean): MenuGrowthTargets =
    if (expanded) MenuGrowthTargets(1f, 1f, 0f) else MenuGrowthTargets(0.96f, 0f, -4f)

internal fun menuGrowthTransformOrigin(origin: MenuGrowthOrigin): TransformOrigin = when (origin) {
    MenuGrowthOrigin.Dropdown -> TransformOrigin(0.5f, 0f)
    MenuGrowthOrigin.Context -> TransformOrigin(0f, 0f)
}
```

`rememberMenuGrowthMotion` 使用三个 `animateFloatAsState`，展开 `180ms`、收起 `110ms`，缓动使用 `CubicBezierEasing(0.16f, 1f, 0.3f, 1f)`。将 `RingSelectChip` 现有单独 spring 缩放替换为共享状态，并在 `graphicsLayer` 同步应用 scale、alpha、translationY 和下拉原点。更新原有 `selectPopupScaleTarget` 测试，改为覆盖共享目标函数。

- [ ] **Step 4: 运行菜单测试并确认 GREEN**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.design.MenuGrowthMotionTest" --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: PASS。

- [ ] **Step 5: 用 IDEA 检查共享动效文件和 RingUiShells**

Run: 分别执行 `get_file_problems`。

Expected: 无新增 error。

---

### Task 3: 将右键菜单接入共享动效

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`

**Interfaces:**
- Consumes: Task 2 的 `MenuGrowthOrigin.Context`、`rememberMenuGrowthMotion`、`menuGrowthTransformOrigin`
- Produces: 三处右键菜单一致的点击点生长效果

- [ ] **Step 1: 为每个持久存在的菜单宿主创建动效状态**

```kotlin
val contextMenuMotion = rememberMenuGrowthMotion(
    expanded = contextMenuExpanded,
    label = "task-context-menu",
)
```

终端菜单使用 `contextMenuTabId != null`，标题栏使用 `taskContextMenuExpanded`。

- [ ] **Step 2: 在三处 DropdownMenu modifier 应用统一图层变换**

```kotlin
.graphicsLayer {
    transformOrigin = menuGrowthTransformOrigin(MenuGrowthOrigin.Context)
    scaleX = contextMenuMotion.scale
    scaleY = contextMenuMotion.scale
    alpha = contextMenuMotion.alpha
    translationY = contextMenuMotion.translationYDp * density.density
}
```

保留原有宽度、offset、shape、border、菜单行为和操作顺序。

- [ ] **Step 3: 每改一个文件立即执行 IDEA 问题检查**

Run: 对 `TaskSidebar.kt`、`ChatHeader.kt`、`EmbeddedTerminalPanel.kt` 逐个执行 `get_file_problems`。

Expected: 无新增 error。

- [ ] **Step 4: 运行 desktopApp 全量测试**

Run: `.\gradlew.bat :desktopApp:test`

Expected: BUILD SUCCESSFUL，0 failed tests。

---

### Task 4: 编译与最终动效审查

**Files:**
- Verify only: 上述所有改动文件

**Interfaces:**
- Consumes: Tasks 1–3 的完成状态
- Produces: 可编译且通过动效规范审查的实现

- [ ] **Step 1: 运行 Kotlin 编译**

Run: `.\gradlew.bat :desktopApp:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 检查工作区 diff**

Run: `git diff -- desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt desktopApp/src/main/kotlin/com/agent/app/chat/component/MessageFlightGeometry.kt desktopApp/src/main/kotlin/com/agent/app/design/MenuGrowthMotion.kt desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt desktopApp/src/test/kotlin/com/agent/app/chat/component/MessageFlightGeometryTest.kt desktopApp/src/test/kotlin/com/agent/app/design/MenuGrowthMotionTest.kt desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

Expected: 每一行改动都可追溯到已确认的消息或菜单动效要求。

- [ ] **Step 3: 按 review-animations 清单审查**

确认无 `scale(0)`、`ease-in`、超过 300ms、布局属性动画、错误菜单原点或不可解释的动效；进入和退出时长不对称。

- [ ] **Step 4: 汇总验证结果，不提交代码**

报告定向测试、全量测试、编译和 IDEA 问题检查结果；保留所有改动为未提交状态，等待用户后续指令。
