## 先说结论：推荐技术路线

如果你主要想做桌面 App，我建议这样选：

**KMP + Compose Multiplatform Desktop + Material 3 自定义设计系统**

不要一上来追求“全平台完全一套 UI”。更合理的是：

```text
commonMain
  domain / model / usecase / repository interface
  theme tokens / icons / shared composables
  network / storage abstraction

desktopMain
  桌面窗口
  菜单栏 / 快捷键 / 托盘 / 文件选择器
  桌面端布局适配
```

也就是说，**业务逻辑尽量共享，桌面体验单独打磨**。精致感主要来自桌面端细节，而不是来自 KMP 本身。

---

## 精致桌面 App 的关键不是“组件多”，而是这几件事

### 1. 不要把移动端 UI 直接搬到桌面

Compose 很容易写出“Android 放大版桌面应用”：大按钮、大圆角、大间距、Bottom Navigation、卡片堆满屏幕。桌面端应该更像：

```text
左侧导航栏 / 顶部工具栏 / 中央工作区 / 右侧详情面板
```

如果是你之前一直在做的 Agent、ADE、信息工作台这类产品，我会优先考虑这几种布局：

```text
IDE 型：
左侧项目/会话列表 + 中央编辑/对话区 + 右侧上下文/工具面板

Raycast 型：
居中命令面板 + 快速搜索 + 键盘优先

Linear 型：
左侧导航 + 中央列表 + 右侧详情抽屉

ChatGPT Desktop 型：
侧边栏 + 主对话区 + 顶部模型/工作区控制
```

Compose Multiplatform 官方也有 adaptive layout 文档，建议复杂界面使用 list-detail、supporting pane 这类 canonical layout，并把 padding、typography 等样式抽成可复用 token。([Kotlin][2])

---

### 2. 建一套自己的 Design System

不要到处写：

```kotlin
Modifier.padding(12.dp)
Color(0xFF...)
RoundedCornerShape(8.dp)
```

应该统一成：

```kotlin
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object AppRadius {
    val sm = 6.dp
    val md = 10.dp
    val lg = 16.dp
}

object AppAlpha {
    const val hover = 0.08f
    const val pressed = 0.12f
    const val disabled = 0.38f
}
```

然后所有组件都走统一规范：

```text
AppButton
AppTextField
AppSidebarItem
AppCard
AppToolbar
AppDialog
AppEmptyState
AppLoadingState
AppSplitPane
AppCommandPalette
```

这才会有“产品感”。否则页面之间会越来越像临时拼的 Demo。

---

### 3. 桌面端一定要重视交互状态

精致桌面 App 必须有这些状态：

```text
hover
pressed
focused
selected
disabled
loading
empty
error
drag over
resizing
active window / inactive window
```

尤其是：

```text
鼠标悬浮反馈
键盘焦点框
右键菜单
快捷键提示
列表选中态
面板拖拽宽度
输入框自动聚焦
```

很多 Compose Demo 看起来“不精致”，不是因为颜色差，而是因为没有桌面交互状态。

---

### 4. 字体、间距、圆角要克制

桌面端建议：

```text
正文：13px / 14px 感觉更像桌面软件
标题：16px / 18px 足够
列表项高度：32-44dp
按钮高度：30-36dp
侧边栏宽度：220-280dp
右侧详情面板：320-420dp
圆角：6-10dp 为主
```

不要所有东西都是 16dp 圆角、24dp padding、巨大的 Material Button。那种更像移动 App。

---

### 5. 做好窗口体验

Compose Desktop 不是只放一个 Window 就完事了。真正像桌面应用，需要处理：

```text
窗口初始大小
最小窗口大小
窗口位置记忆
浅色 / 深色主题
菜单栏
快捷键
系统托盘
文件拖入
文件选择器
复制粘贴
系统通知
多窗口
应用图标
打包安装包
```

Compose Multiplatform Desktop 可以通过 Gradle/native distribution 做桌面端分发；官方兼容性文档也说明，Desktop 运行需要 JDK 11+，而打包 native distributions 因 jpackage 限制需要 JDK 17+。([Kotlin][3])

---

## 现成资源推荐

### 官方入口

最重要的是这几个：

| 资源                               | 用途                        |
| -------------------------------- | ------------------------- |
| Compose Multiplatform 官方文档       | 主文档，了解整体能力                |
| Kotlin Multiplatform Wizard      | 快速生成 KMP 项目               |
| Compose Desktop Template         | 桌面端模板                     |
| Compose compatibility/versioning | 查 Kotlin、Compose、JDK 版本兼容 |
| Native distributions 文档          | 打包 exe、dmg、deb、rpm 等      |

Kotlin Multiplatform Wizard 可以创建 Android、iOS、Desktop 项目，也提供预置模板。([Kotlin Multiplatform Wizard][4]) Compose Multiplatform Desktop Template 是 JetBrains 官方的桌面应用模板，适合直接拿来起步。([GitHub][5])

---

### UI / 组件 / 设计系统资源

#### 1. Material 3

Compose 里最稳的选择还是 Material 3。Google 文档里也说明 Jetpack Compose 提供 Material Design 组件实现，Material 是完整的数字界面设计系统。([Android Developers][6])

但建议不要“原封不动 Material”。更好的做法是：

```text
底层用 Material3
外层封装自己的 AppTheme / AppButton / AppTextField
颜色、圆角、字体、密度自己控制
```

#### 2. MaterialKolor

如果你想快速生成 Material 3 动态色板，可以看 MaterialKolor。它是 Compose Multiplatform 的动态 Material Design 3 调色板库，支持从任意颜色生成色彩方案。([GitHub][7])

适合做：

```text
主题色自定义
浅色 / 深色主题
根据品牌色生成完整色板
```

#### 3. Carbon Compose

如果你想做偏企业级、ToB、后台、控制台风格，可以参考 Carbon Compose。它是 IBM Carbon Design System 的 Compose Multiplatform 实现，支持 Android、iOS、Desktop、Web。([Gabriel Drn][8])

这个对你之前做的 toG / 工作台 / 数据治理类界面比较有参考价值。

#### 4. Compose Icons / Lucide

图标建议统一风格。Lucide 是一套开源一致性较强的图标库，官方介绍是 1600+ SVG 图标，强调轻量、可定制、风格一致。([GitHub][9])

Compose 里可以看 Compose Icons，它收集了很多适用于 Android 和 Compose Multiplatform 的图标包。([GitHub][10])

---

### 图片加载 / 资源管理

如果 App 里需要头像、封面、远程图片，建议用 Coil。Coil 官方仓库现在定位为 Android 和 Compose Multiplatform 的图片加载库，并支持缓存、下采样、自动取消请求等能力。([GitHub][11])

Coil 文档也说明了可以在 Compose Multiplatform 中通过 `Res.getUri(...)` 加载资源。([Coil][12])

---

### 导航与架构

#### 简单项目：Voyager

Voyager 是一个与 Jetpack Compose 集成的多平台导航库，支持 Android、iOS、Desktop、Web。([GitHub][13])

适合：

```text
页面数量不多
导航结构不复杂
希望 API 简单
快速做出成品
```

#### 复杂项目：Decompose

Decompose 更偏工程化。它是 KMP 的生命周期感知组件拆分库，带 routing，UI 可插拔，支持 Compose、Android Views、SwiftUI、Kotlin/React 等。([GitHub][14])

适合：

```text
多窗口
复杂导航
可恢复状态
多模块
长期维护
桌面 + 移动共用业务逻辑
```

如果你要做类似 Agent IDE / 工作台 / 管理端，我更推荐 Decompose。

---

## 我建议的项目骨架

可以这样组织：

```text
app/
  composeApp/
    src/
      commonMain/
        kotlin/
          app/
            App.kt
            AppTheme.kt
            navigation/
            design/
              AppColors.kt
              AppTypography.kt
              AppSpacing.kt
              AppComponents.kt
            feature/
              home/
              chat/
              settings/
              workspace/
            core/
              model/
              data/
              domain/
              util/

      desktopMain/
        kotlin/
          Main.kt
          DesktopWindow.kt
          DesktopMenu.kt
          DesktopShortcuts.kt
          DesktopTray.kt
```

如果是桌面 Agent 类 App，可以进一步这样拆：

```text
feature/
  chat/
  workspace/
  command/
  settings/
  modelProvider/
  toolManager/
  fileManager/
  terminal/
  logs/
```

UI 结构：

```text
AppWindow
  AppScaffold
    Sidebar
    TopBar
    ContentArea
    RightInspector
    StatusBar
    CommandPalette
```

---

## “精致感”优先级清单

按性价比排序，我建议你先做这些：

```text
1. 统一主题：颜色、字体、圆角、间距
2. 统一组件：Button、TextField、Card、Dialog、ListItem
3. 深色模式：不是简单反色，而是重新调暗色层级
4. Hover / Focus / Selected 状态
5. 快捷键：Ctrl+K、Ctrl+,、Ctrl+N、Esc
6. 命令面板：桌面工具类 App 的高级感来源
7. 空状态 / 错误状态 / 加载状态
8. 右键菜单
9. 文件拖入
10. 原生打包：图标、版本号、安装包
```

尤其是 **Ctrl+K 命令面板**，很适合 Kotlin 桌面工具、Agent 客户端、开发者工具类 App。

---

## 可以参考的产品风格

你可以把目标分成三类：

### 开发者工具风

参考：

```text
JetBrains Toolbox
Warp
Raycast
Linear
Arc
Cursor / Windsurf / Trae 类 IDE 侧边栏结构
```

特点：

```text
暗色做得好
命令面板
键盘优先
侧边栏密度高
少用大卡片
状态反馈明确
```

### 企业工作台风

参考：

```text
Linear
Notion
Slack
飞书管理后台
Carbon Design System
```

特点：

```text
列表 + 详情
筛选器
右侧抽屉
表格
批量操作
状态标签
```

### 桌面小工具风

参考：

```text
Raycast
CleanShot X
iStat Menus
AltTab
Rectangle
```

特点：

```text
轻量
快捷键启动
少页面
高完成度
系统托盘 / 菜单栏
```

---

## 最推荐你从这些资源开始

按顺序看：

1. **Kotlin Multiplatform Wizard**：先生成项目，不要自己手写 Gradle。([Kotlin Multiplatform Wizard][4])
2. **Compose Multiplatform 官方教程**：跑通 Android/iOS/Desktop/Web 的基本结构。([Kotlin][15])
3. **Compose Desktop Template**：参考桌面端打包和项目结构。([GitHub][5])
4. **Material 3 Theming 文档**：先把主题系统做好。([Android Developers][16])
5. **Decompose 或 Voyager**：根据复杂度选导航架构。([GitHub][14])
6. **MaterialKolor / Carbon Compose / Lucide / Coil**：补齐视觉、图标、图片加载能力。([GitHub][7])

---

## 我的个人建议

你如果想做一个真的看起来“高级”的 KMP 桌面 App，不要从“我需要哪些组件库”开始，而是从这张图开始：

```text
窗口结构
  ├─ 左侧导航
  ├─ 顶部工具栏
  ├─ 中央主工作区
  ├─ 右侧详情 / 上下文面板
  └─ 底部状态栏

设计系统
  ├─ 颜色
  ├─ 字体
  ├─ 间距
  ├─ 圆角
  ├─ 图标
  └─ 交互状态

工程结构
  ├─ commonMain 共享业务
  ├─ desktopMain 桌面能力
  ├─ feature 模块
  └─ design system 模块
```

一句话：**Compose Multiplatform 负责让你能做出来，Design System 和桌面交互细节决定它精不精致。**

[1]: https://kotlinlang.org/compose-multiplatform/?utm_source=chatgpt.com "Compose Multiplatform – Beautiful UIs Everywhere"
[2]: https://kotlinlang.org/docs/multiplatform/compose-adaptive-layouts.html?utm_source=chatgpt.com "Adaptive layouts | Kotlin Multiplatform Documentation"
[3]: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html?utm_source=chatgpt.com "Compatibility and versions | Kotlin Multiplatform"
[4]: https://kmp.jetbrains.com/?utm_source=chatgpt.com "Kotlin Multiplatform Wizard | JetBrains"
[5]: https://github.com/JetBrains/compose-multiplatform-desktop-template?utm_source=chatgpt.com "Compose Multiplatform Desktop Application project template"
[6]: https://developer.android.com/develop/ui/compose/components?utm_source=chatgpt.com "Material Components | Jetpack Compose"
[7]: https://github.com/jordond/materialkolor?utm_source=chatgpt.com "jordond/MaterialKolor: 🎨 Generate a dynamic Material3 ..."
[8]: https://gabrieldrn.github.io/carbon-compose/?utm_source=chatgpt.com "Carbon Compose"
[9]: https://github.com/lucide-icons/lucide?utm_source=chatgpt.com "lucide-icons/lucide: Beautiful & consistent icon toolkit ..."
[10]: https://github.com/composablehorizons/compose-icons?utm_source=chatgpt.com "composablehorizons/compose-icons"
[11]: https://github.com/coil-kt/coil?utm_source=chatgpt.com "coil-kt/coil: Image loading for Android and Compose ..."
[12]: https://coil-kt.github.io/coil/compose/?utm_source=chatgpt.com "Compose - Coil"
[13]: https://github.com/adrielcafe/voyager?utm_source=chatgpt.com "adrielcafe/voyager: 🛸 A pragmatic navigation library for ..."
[14]: https://github.com/arkivanov/decompose?utm_source=chatgpt.com "arkivanov/Decompose: Kotlin Multiplatform lifecycle-aware ..."
[15]: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-create-first-app.html?utm_source=chatgpt.com "Create your Compose Multiplatform app"
[16]: https://developer.android.com/codelabs/jetpack-compose-theming?utm_source=chatgpt.com "Theming in Compose with Material 3"
