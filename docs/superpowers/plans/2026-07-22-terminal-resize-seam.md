# Terminal Resize Border Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除导致终端左侧亮边和拖动时顶部变色横块的完整 Compose 外框。

**Architecture:** 保留现有 Compose 可拖动布局、Swing `JediTermWidget`、圆角裁剪和深色背景，只删除 `EmbeddedTerminalPanel` 的完整外框绘制。高度约束、PTY、滚动条和终端生命周期保持不变。

**Tech Stack:** Kotlin 2.2、Compose Multiplatform Desktop、Swing/JediTerm。

## Global Constraints

- 只修改 `desktopApp` 的终端外层样式。
- 不启动 Desktop 应用或其他长期运行服务。
- 不新增依赖，不修改 PTY、滚动条或拖动高度规则。
- 不创建 Git 提交。

---

### Task 1: 移除终端完整外框

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`

**Interfaces:**
- Consumes: existing `EmbeddedTerminalPanel` modifier chain.
- Produces: borderless clipped terminal surface.

- [x] **Step 1: Remove the full outer border**

删除以下边框修饰符及未使用导入：

```kotlin
.border(1.dp, AppLine, RoundedCornerShape(10.dp))
```

保留以下表面处理：

```kotlin
.clip(RoundedCornerShape(10.dp))
.background(AppPanelBackground)
```

- [x] **Step 2: Inspect the changed file**

运行 IDEA `get_file_problems`。预期：没有新增问题。

- [x] **Step 3: Run module verification**

运行 `.\gradlew.bat :desktopApp:test :desktopApp:compileKotlin --rerun-tasks`。预期：命令以退出码 0 完成。
