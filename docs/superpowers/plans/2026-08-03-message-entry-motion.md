# Message Entry Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将发送消息的跨区域飞行替换为时间线目标位置的轻微上移、淡入和展开。

**Architecture:** `WorkspacePanel` 只记录本次发送标识与内容，`ConversationTimeline` 精确选择最后一条匹配的用户消息。`UserMessageCard` 在原位使用 `Animatable` 驱动 GPU 图层属性，完成后回调清理标识；飞行覆盖层和几何测量全部删除。

**Tech Stack:** Kotlin、Compose Multiplatform Desktop、`kotlin.test`、JUnit 5。

## Global Constraints

- 只动画 `translationY`、`alpha`、`scaleX` 和 `scaleY`。
- 进入时长 200ms，初始位移 8dp，初始缩放 0.98，采用强 ease-out。
- 历史消息和重复内容的更早消息不能误动画。
- 保留现有自动滚动、菜单动效和其他未提交改动。
- 未经授权不提交。

---

### Task 1: 原位消息进入

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/MessageEntryMotion.kt`
- Create: `desktopApp/src/test/kotlin/com/agent/app/chat/component/MessageEntryMotionTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Delete: `desktopApp/src/main/kotlin/com/agent/app/chat/component/MessageFlightGeometry.kt`
- Delete: `desktopApp/src/test/kotlin/com/agent/app/chat/component/MessageFlightGeometryTest.kt`

**Interfaces:**
- Produces: `messageEntryVisuals(progress: Float, travelDistancePx: Float): MessageEntryVisuals`
- Produces: `latestMatchingUserMessage(items: List<ConversationItem>, content: String?): ChatMessageItem?`

- [x] **Step 1: 写失败测试**

```kotlin
assertEquals(MessageEntryVisuals(0f, 0.98f, 8f), messageEntryVisuals(0f, 8f))
assertSame(newest, latestMatchingUserMessage(listOf(older, assistant, newest), "same"))
```

- [x] **Step 2: 运行定向测试确认 RED**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.MessageEntryMotionTest"`

Expected: FAIL，原因是消息进入 API 尚不存在。

- [x] **Step 3: 实现最小纯函数与 Compose 接入**

```kotlin
internal fun messageEntryVisuals(progress: Float, travelDistancePx: Float) = MessageEntryVisuals(
    alpha = progress,
    scale = 0.98f + progress * 0.02f,
    translationY = (1f - progress) * travelDistancePx,
)
```

`WorkspacePanel` 发送时记录 `PendingMessageEntry`，`ConversationTimeline` 只把标识传给最后一条匹配用户消息；`UserMessageCard` 以 200ms 强 ease-out 从进度 0 动画到 1，随后清理标识。

- [x] **Step 4: 删除飞行覆盖层与几何代码**

输入框边界、目标边界、窗口原点和飞行覆盖层已删除，自动滚动行为未变；`MessageFlightGeometry.kt` 与其测试已无引用，删除文件待用户确认。

- [x] **Step 5: 验证**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.MessageEntryMotionTest"`

Run: `.\gradlew.bat :desktopApp:test`

Run: `.\gradlew.bat :desktopApp:compileKotlin`

Expected: 全部成功，相关文件 IDEA 问题检查为 0 error。
