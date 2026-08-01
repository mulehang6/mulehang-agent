# Native Markdown Extensions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不嵌入浏览器的前提下，让桌面聊天正确显示链接、常用语言代码、远程图片、脚注、定义列表与安全 HTML 子集。

**Architecture:** 保留成熟 RichText 对基础 Markdown 与表格的渲染；在其前面加入可测试的块级分流器，将代码、图片、脚注、定义列表和安全 HTML 块交由 Compose 原生组件显示。模型文本永不执行 JavaScript、任意 CSS、外部页面或系统命令。

**Tech Stack:** Kotlin、Compose Desktop、Compose RichText、Coil 3、kotlin.test。

## Global Constraints

- 不引入 JCEF、Chromium、Node.js 或任意浏览器内核。
- HTML 仅支持文本语义、颜色和对齐的安全子集；脚本、事件属性、iframe 与任意 CSS 均不执行。
- 图表仅在回复完成且代码围栏闭合后渲染。
- 每项新行为先写失败测试；不启动桌面应用。

---

### Task 1: 链接与代码块主题

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownStyle.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownRenderPolicyTest.kt`

- [ ] **Step 1: 锁定链接颜色与下划线测试**

断言 `assistantMarkdownLinkStyle()` 不使用 `Color.Blue`，使用 `AppMarkdownLink` 且保留下划线。

- [ ] **Step 2: 在 RichText 字符串样式中注入链接样式**

将 `RichTextStringStyle(linkStyle = assistantMarkdownLinkStyle())` 包在现有 `Markdown` 调用外。

- [ ] **Step 3: 验证窄测试**

运行：`.\\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.AssistantMarkdownRenderPolicyTest"`

### Task 2: 原生块分流与高亮代码

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownBlocks.kt`
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantCodeBlock.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownRenderPolicyTest.kt`

- [ ] **Step 1: 写围栏语言提取与 Python token 着色失败测试**

对 ` ```python ` 围栏断言产生 `Code(language = "python")`；对 `def`、字符串和关键字断言产生不同的 Compose span 色。

- [ ] **Step 2: 实现有限语言词法高亮**

支持 Python、Kotlin、Java、JavaScript/TypeScript、JSON、Bash、SQL、XML/HTML；未知语言使用同一可读的单色代码块。

- [ ] **Step 3: 使用 Compose 原生代码组件渲染 Code 块**

显示语言标签、深色代码面、等宽字体和可换行内容；PlantUML 分支保持不变。

### Task 3: 图片、脚注与定义列表

**Files:**
- Modify: `desktopApp/build.gradle.kts`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownBlocks.kt`
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownExtensions.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownRenderPolicyTest.kt`

- [ ] **Step 1: 写图片、两个脚注和定义列表失败测试**

断言独立图片和带链接图片产生 `Image` 块；两个 `[^id]:` 定义均被收集；`Term` 加 `: explanation` 产生定义列表项。

- [ ] **Step 2: 接入 Coil Desktop 图片加载器**

使用 `AsyncImage` 显示 http/https 图片、限制高度、在加载与失败时显示替代文本。

- [ ] **Step 3: 原生渲染脚注和定义列表**

正文删除脚注定义，回复末尾显示所有脚注；定义列表使用术语加粗、定义缩进的 Compose 布局。

### Task 4: 安全 HTML 子集

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownBlocks.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownRenderPolicyTest.kt`

- [ ] **Step 1: 写颜色 span 与居中 div 失败测试**

断言 `<span style="color:red">` 产生红色文本模型，`<div align="center">` 产生居中模型；`script` 内容不进入模型。

- [ ] **Step 2: 实现白名单 HTML 解析与 Compose 映射**

映射 `br`、`b/strong`、`i/em`、`u`、`code/kbd`、`span color` 与 `div/p align`；拒绝其他属性与标签的行为能力。

- [ ] **Step 3: 运行完整回归**

运行：`.\\gradlew.bat :shared:jvmTest :desktopApp:test`。
