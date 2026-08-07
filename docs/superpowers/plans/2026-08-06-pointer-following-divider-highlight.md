# 跟随指针的分隔高亮 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为桌面端可拖拽分隔条提供按方向跟随鼠标、覆盖完整分隔轴且以峰值向两端衰减的可复用 Air 蓝线。

**Architecture:** 设计层组件负责方向和绘制，不持有面板尺寸或拖拽状态。`ResizableWorkspaceLayout` 继续拥有终端高度与拖拽逻辑，并将指针位置及悬停状态传给组件；组件以自身完整尺寸为轨道。

**Tech Stack:** Kotlin Multiplatform、Compose Desktop、kotlin.test、JUnit 5。

## Global Constraints

- 保持现有 10dp 命中区域和终端高度钳制公式。
- 光带峰值位置直接跟手，不使用位置动画；颜色沿完整主轴向两端平滑衰减。
- 横向分隔条使用 X 轴；纵向消费者使用 Y 轴。
- 不创建、切换或重命名 Git 分支；未经用户明确授权不提交。

---

### Task 1: 分隔蓝线的方向、峰值归一化与 Air 渐变

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/design/PointerFollowingDividerHighlight.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Produces: `DividerHighlightAxis`、`dividerHighlightPeakFraction(pointerPositionPx, trackLengthPx)` 和 `PointerFollowingDividerHighlight(axis, pointerPositionPx, visible, modifier)`；组件内部绘制贯穿完整轨道的渐变峰值与两端淡出蓝线。
- Consumes: `DividerAirBlue`、Compose `Canvas` 与 `Modifier`。

- [ ] **Step 1: 写出峰值位置的失败测试**

```kotlin
assertEquals(0.5f, dividerHighlightPeakFraction(100f, 200f))
assertEquals(0f, dividerHighlightPeakFraction(-10f, 200f))
assertEquals(1f, dividerHighlightPeakFraction(240f, 200f))
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew.bat :desktopApp:test --tests com.agent.app.chat.component.ChatScreenPresentationTest`

Expected: 新峰值函数尚不存在，测试失败。

- [ ] **Step 3: 实现最小的方向感知绘制组件**

```kotlin
internal fun dividerHighlightPeakFraction(
    pointerPositionPx: Float,
    trackLengthPx: Float,
): Float = (pointerPositionPx / trackLengthPx).coerceIn(0f, 1f)
```

组件在 `Horizontal` 时以指针为峰值绘制覆盖完整 X 轴的渐变蓝线，在 `Vertical` 时改为完整 Y 轴；`visible` 为假时不绘制。

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew.bat :desktopApp:test --tests com.agent.app.chat.component.ChatScreenPresentationTest`

Expected: 峰值归一化与既有界面展示测试全部通过。

### Task 2: 接入终端下方的横向 Air 分隔光带

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ResizableWorkspaceLayout.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Consumes: `PointerFollowingDividerHighlight(axis = DividerHighlightAxis.Horizontal, ...)`。
- Produces: 终端与主区域之间按指针 X 坐标显示、贯穿整个间隙的 Air 渐隐蓝线，不改变 `clampTerminalHeight`。

- [ ] **Step 1: 写出完整轴峰值与边缘的失败测试**

```kotlin
assertEquals(0f, dividerHighlightPeakFraction(0f, 64f))
assertEquals(1f, dividerHighlightPeakFraction(64f, 64f))
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew.bat :desktopApp:test --tests com.agent.app.chat.component.ChatScreenPresentationTest`

Expected: 完整轴峰值函数尚不存在，测试失败。

- [ ] **Step 3: 在现有分隔条收集指针位置与悬停状态**

在 10dp 的 `Box` 上处理 Enter、Exit 和 Move；用 `PointerEvent.changes.first().position.x` 记录本地 X 坐标。保留 `pointerHoverIcon` 与 `detectDragGestures`，并在拖拽回调中更新同一位置状态。以组件自身完整宽度绘制 Air 光带，同时保留 1dp 基线。

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew.bat :desktopApp:test --tests com.agent.app.chat.component.ChatScreenPresentationTest`

Expected: 新高亮测试和终端高度钳制测试全部通过。

- [ ] **Step 5: 编译桌面模块并人工验证**

Run: `./gradlew.bat :desktopApp:compileKotlin`

Expected: 编译成功；在桌面窗口打开终端后，横向 Air 蓝线峰值随鼠标 X 坐标移动并向完整间隙两端渐暗，拖拽期间持续可见且终端尺寸调整不受影响。

## 自检

- 覆盖了规格中的横向与未来纵向复用、完整轴两端淡出、无位置缓动和不改变拖拽热区。
- 不含占位步骤；所有接口名称与测试调用一致。
- 未把标题栏 Swing 命中层或权限菜单样式混入本计划。
