# Air 磨砂玻璃侧栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 Compose Desktop 原生背景图层重放和 Skia 模糊，将不透明任务侧栏改为与 Air 参考图一致的暗色磨砂玻璃。

**Architecture:** `ChatScreen` 将侧栏后方的主界面录制到可重放 `GraphicsLayer`，正常绘制该图层，同时把图层及根坐标传给 `AirSidebarSurface`。侧栏在自身圆角范围内把来源图层对齐到相同屏幕位置，录入独立模糊图层并叠加暗色染色、顶部微光、低对比边界和清晰前景。

**Tech Stack:** Kotlin 2.4.0、Compose Multiplatform Desktop 1.11.1、Material 3 1.9.0、Skia `BlurEffect`、`kotlin.test`、JUnit 5。

## Global Constraints

- 仅修改侧栏材质、工作区底色和必要的背景采样路径；保留现有宽度、开关、滑动路径、外部点击关闭及任务列表行为。
- 模糊半径从 20–24px 区间起调；后方内容只能感知位置，不能清晰阅读。
- 不加入折射、色散、噪点、鼠标跟随效果，不引入 WebGL、SVG、JNA 或第三方玻璃依赖。
- 侧栏关闭时不绘制模糊副本；打开和关闭动画只改变水平位置。
- 新增或修改的生产类、数据类及函数必须带说明约束和意图的适当 KDoc。
- 使用 `apply_patch` 编辑文件，使用 IDEA MCP 导航、格式化、检查和构建；不得启动桌面应用或开发服务器。
- 仓库存在用户未提交改动；只触碰本计划列出的文件，不覆盖无关修改。
- 未经用户明确授权不得创建 Git 提交，因此本计划不包含提交步骤。

---

### Task 1: 锁定 Air 材质与工作区颜色 token

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/AirSidebarSurface.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Consumes: 现有 `AirSidebarStyleTokens`、`AirSidebarStyle` 和共享颜色 token。
- Produces: `blurRadiusPx: Float`、`tintAlpha: Float`、`borderAlpha: Float`、`fallbackColor: Color` 以及 `AppWorkspaceBackground: Color`。

- [ ] **Step 1: 把材质预期写成失败测试**

将现有 `should use air sidebar material tokens` 测试替换为：

```kotlin
/**
 * Air 侧栏应使用真实磨砂所需的模糊、染色和低对比边界参数。
 */
@Test
fun `should use air sidebar glass material tokens`() {
    assertEquals(12, AirSidebarStyle.cornerRadiusDp)
    assertEquals(16, AirSidebarStyle.shadowElevationDp)
    assertEquals(22f, AirSidebarStyle.blurRadiusPx)
    assertEquals(0.78f, AirSidebarStyle.tintAlpha)
    assertEquals(0.075f, AirSidebarStyle.borderAlpha)
    assertEquals(Color(0xFF1D1F21), AirSidebarStyle.fallbackColor)
    assertEquals(Color(0xFF151719), AppWorkspaceBackground)
}
```

同时补充测试 imports：

```kotlin
import androidx.compose.ui.graphics.Color
import com.agent.app.design.AppWorkspaceBackground
```

- [ ] **Step 2: 运行定向测试并确认因新字段而失败**

先通过 IDEA `get_run_configurations` 确认配置；没有对应单测配置时执行：

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should use air sidebar glass material tokens"
```

Expected: FAIL，错误包含未解析的 `blurRadiusPx`、`tintAlpha`、`fallbackColor` 或 `AppWorkspaceBackground`。

- [ ] **Step 3: 实现最小 token 变更**

把 `AirSidebarStyleTokens` 和默认值调整为：

```kotlin
/**
 * Air 浮动侧栏的暗色磨砂材质参数。
 */
@Immutable
internal data class AirSidebarStyleTokens(
    val cornerRadiusDp: Int,
    val shadowElevationDp: Int,
    val blurRadiusPx: Float,
    val tintAlpha: Float,
    val borderAlpha: Float,
    val fallbackColor: Color,
)

/**
 * Air 浮动侧栏的默认磨砂材质。
 */
internal val AirSidebarStyle = AirSidebarStyleTokens(
    cornerRadiusDp = 12,
    shadowElevationDp = 16,
    blurRadiusPx = 22f,
    tintAlpha = 0.78f,
    borderAlpha = 0.075f,
    fallbackColor = Color(0xFF1D1F21),
)
```

在 `RingUiShells.kt` 的颜色 token 区新增：

```kotlin
internal val AppWorkspaceBackground = Color(0xFF151719)
```

- [ ] **Step 4: 重跑定向测试**

Run: 与 Step 2 相同。

Expected: PASS。

---

### Task 2: 建立可重放工作区背景层和坐标规则

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/design/WorkspaceBackdrop.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Consumes: Compose `GraphicsLayer`、`rememberGraphicsLayer()`、`Modifier.drawWithContent`。
- Produces: `WorkspaceBackdropState`、`rememberWorkspaceBackdropState()`、`Modifier.captureWorkspaceBackdrop(state)`、`workspaceBackdropOffset(workspaceOrigin, sidebarOrigin): Offset`。

- [ ] **Step 1: 为背景对齐写失败测试**

添加 import 和测试：

```kotlin
import com.agent.app.design.workspaceBackdropOffset

/**
 * 玻璃副本必须按工作区与侧栏的根坐标差对齐到原始屏幕位置。
 */
@Test
fun `should align workspace backdrop inside sidebar coordinates`() {
    assertEquals(
        Offset(-12f, -8f),
        workspaceBackdropOffset(
            workspaceOrigin = Offset(0f, 48f),
            sidebarOrigin = Offset(12f, 56f),
        ),
    )
    assertEquals(
        Offset(-8f, -56f),
        workspaceBackdropOffset(
            workspaceOrigin = Offset.Zero,
            sidebarOrigin = Offset(8f, 56f),
        ),
    )
}
```

- [ ] **Step 2: 运行定向测试并确认辅助函数不存在**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should align workspace backdrop inside sidebar coordinates"
```

Expected: FAIL，错误包含未解析的 `workspaceBackdropOffset`。

- [ ] **Step 3: 新建背景状态和捕获 modifier**

创建 `WorkspaceBackdrop.kt`：

```kotlin
package com.agent.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * 保存主界面的可重放绘制层和其根坐标，供浮动玻璃表面采样。
 */
@Stable
internal class WorkspaceBackdropState internal constructor(
    val layer: GraphicsLayer,
) {
    var originInRoot by mutableStateOf(Offset.Zero)
        internal set
}

/**
 * 创建随组合生命周期释放的工作区背景采样状态。
 */
@Composable
internal fun rememberWorkspaceBackdropState(): WorkspaceBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { WorkspaceBackdropState(layer) }
}

/**
 * 通过缓存绘制作用域重定向内容画布，将工作区录入可重放图层并正常显示。
 *
 * 普通 DrawScope 的 record 不会重定向 ContentDrawScope，必须使用 CacheDrawScope 提供的扩展。
 */
internal fun Modifier.captureWorkspaceBackdrop(
    state: WorkspaceBackdropState,
): Modifier =
    onGloballyPositioned { coordinates ->
        state.originInRoot = coordinates.positionInRoot()
    }.drawWithCache {
        onDrawWithContent capture@{
            if (size.width > 0f && size.height > 0f) {
                state.layer.record {
                    this@capture.drawContent()
                }
                drawLayer(state.layer)
            } else {
                drawContent()
            }
        }
    }

/**
 * 返回工作区副本在侧栏局部坐标中的平移量。
 */
internal fun workspaceBackdropOffset(
    workspaceOrigin: Offset,
    sidebarOrigin: Offset,
): Offset = workspaceOrigin - sidebarOrigin
```

- [ ] **Step 4: 重跑背景对齐测试**

Run: 与 Step 2 相同。

Expected: PASS。

- [ ] **Step 5: 使用 IDEA 检查新文件 API 是否与 Compose 1.11.1 匹配**

使用 IDEA `get_file_problems` 检查 `WorkspaceBackdrop.kt`。

Expected: 无错误；若 `GraphicsLayer.record` 的接收者推断失败，只把调用改为显式命名参数，不改变接口或增加依赖。

---

### Task 3: 在侧栏中绘制真实磨砂背景

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/design/AirSidebarSurface.kt`

**Interfaces:**
- Consumes: `WorkspaceBackdropState`、`workspaceBackdropOffset(...)`、`AirSidebarStyle`。
- Produces: `AirSidebarSurface(backdropState, sidebarOrigin, modifier, content)`，前景调用方式保持 slot API。

- [ ] **Step 1: 用已有 token 测试作为材质回归门槛**

Run:

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should use air sidebar glass material tokens"
```

Expected: PASS；这一步建立修改绘制代码前的稳定基线。

- [ ] **Step 2: 将实色 Surface 替换为模糊副本、染色和清晰前景**

保留原文件 package，替换 imports 和 composable 主体，使核心实现为：

```kotlin
/**
 * 绘制接近 Air 的暗色磨砂侧栏；背景副本被模糊和染色，前景内容保持清晰。
 */
@Composable
internal fun AirSidebarSurface(
    backdropState: WorkspaceBackdropState,
    sidebarOrigin: Offset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(AirSidebarStyle.cornerRadiusDp.dp)
    val blurredLayer = rememberGraphicsLayer()
    Surface(
        modifier = modifier.shadow(
            elevation = AirSidebarStyle.shadowElevationDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.28f),
            spotColor = Color.Black.copy(alpha = 0.38f),
        ),
        shape = shape,
        color = AirSidebarStyle.fallbackColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = AirSidebarStyle.borderAlpha)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val recordedSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    val sourceReady = backdropState.layer.size.width > 0 && backdropState.layer.size.height > 0
                    if (sourceReady && recordedSize.width > 0 && recordedSize.height > 0) {
                        val offset = workspaceBackdropOffset(backdropState.originInRoot, sidebarOrigin)
                        blurredLayer.record(this, layoutDirection, recordedSize) {
                            translate(offset.x, offset.y) {
                                drawLayer(backdropState.layer)
                            }
                        }
                        blurredLayer.renderEffect = BlurEffect(
                            radiusX = AirSidebarStyle.blurRadiusPx,
                            radiusY = AirSidebarStyle.blurRadiusPx,
                            edgeTreatment = TileMode.Clamp,
                        )
                        drawLayer(blurredLayer)
                    }
                    drawRect(AirSidebarStyle.fallbackColor.copy(alpha = AirSidebarStyle.tintAlpha))
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.045f),
                            0.22f to Color.White.copy(alpha = 0.012f),
                            0.55f to Color.Transparent,
                        ),
                    )
                    drawContent()
                },
        ) {
            content()
        }
    }
}
```

需要的 imports 限定为 Compose 的 `BlurEffect`、`TileMode`、`Offset`、`drawWithContent`、`GraphicsLayer.drawLayer`、`rememberGraphicsLayer`、`IntSize`、`translate` 和 `roundToInt`；删除不再使用的 `background` import。

- [ ] **Step 3: 使用 IDEA 格式化并检查文件**

依次使用 IDEA `reformat_file` 和 `get_file_problems` 检查 `AirSidebarSurface.kt`。

Expected: 无错误；没有未使用 import；所有生产类型和函数保留 KDoc。

- [ ] **Step 4: 编译确认桌面 Skia 支持模糊效果**

优先使用 IDEA `build_project` 重建 `AirSidebarSurface.kt` 和 `WorkspaceBackdrop.kt`；若结构化构建无法限定 Gradle 模块，则执行：

```powershell
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: BUILD SUCCESSFUL。

---

### Task 4: 将背景采样层接入 ChatScreen

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`

**Interfaces:**
- Consumes: `rememberWorkspaceBackdropState()`、`captureWorkspaceBackdrop(...)` 和新的 `AirSidebarSurface` 参数。
- Produces: 侧栏打开时正确对齐的动态工作区背景；现有交互函数签名不变。

- [ ] **Step 1: 在 ChatScreen 中持有背景状态和侧栏根坐标**

在 `ChatScreen` 的状态区添加：

```kotlin
val workspaceBackdropState = rememberWorkspaceBackdropState()
var sidebarOrigin by remember { mutableStateOf(Offset.Zero) }
```

添加 design imports：

```kotlin
import com.agent.app.design.captureWorkspaceBackdrop
import com.agent.app.design.rememberWorkspaceBackdropState
```

- [ ] **Step 2: 只录制侧栏后方的主内容层**

将包含 header、workspace 和 tool rail 的现有 `Row` modifier 调整为：

```kotlin
Row(
    modifier = Modifier
        .fillMaxSize()
        .captureWorkspaceBackdrop(workspaceBackdropState),
) {
    // 保留现有 Column、WorkspacePanel 和 ToolRail 内容不变。
}
```

- [ ] **Step 3: 传入来源图层并记录侧栏坐标**

把 `AirSidebarSurface` 调用调整为：

```kotlin
AirSidebarSurface(
    backdropState = workspaceBackdropState,
    sidebarOrigin = sidebarOrigin,
    modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { coordinates ->
            sidebarOrigin = coordinates.positionInRoot()
            sidebarBounds = coordinates.boundsInRoot()
        },
) {
    TaskSidebar(
        state = state,
        compact = compact,
        modifier = Modifier.fillMaxSize(),
    )
}
```

添加 `positionInRoot` import，保留 `boundsInRoot`，不修改现有点击外部关闭逻辑。

- [ ] **Step 4: 使用 IDEA 格式化、检查并运行侧栏交互测试**

用 IDEA 格式化并检查 `ChatScreen.kt`，然后执行：

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should dismiss visible sidebar only for outside pointer" --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should place hidden sidebar beyond left edge"
```

Expected: 两项 PASS，证明材质接入没有改变原有侧栏行为。

---

### Task 5: 建立 Air 的主工作区明暗层级

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`

**Interfaces:**
- Consumes: `AppWorkspaceBackground`。
- Produces: 仅聊天主工作区使用 `#151719`；header、chip、composer 和其他共享表面不被整体改色。

- [ ] **Step 1: 替换工作区表面 token**

将 import 和 `Surface` 颜色改为：

```kotlin
import com.agent.app.design.AppWorkspaceBackground

Surface(
    modifier = workspaceModifier,
    shape = RoundedCornerShape(14.dp),
    color = AppWorkspaceBackground,
    border = BorderStroke(1.dp, AppLine.copy(alpha = 0.42f)),
) {
    // 保留现有内容。
}
```

移除该文件不再使用的 `AppPanelBackground` import；边界透明度从 `0.52f` 降到 `0.42f`，避免深背景上出现硬轮廓。

- [ ] **Step 2: 使用 IDEA 格式化和检查**

对 `WorkspacePanel.kt` 使用 IDEA `reformat_file` 和 `get_file_problems`。

Expected: 无错误或未使用 import。

- [ ] **Step 3: 运行 token 测试与编译**

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest.should use air sidebar glass material tokens"
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: 两条命令均成功。

---

### Task 6: 消除旧规格矛盾并完成整体验证

**Files:**
- Modify: `docs/superpowers/specs/2026-07-21-desktop-ui-polish-design.md`
- Verify: `docs/superpowers/specs/2026-07-22-air-sidebar-glass-design.md`
- Verify: 本计划涉及的全部 Kotlin 文件。

**Interfaces:**
- Consumes: 已确认的 Air 磨砂侧栏规格和完成后的实现。
- Produces: 不再声称侧栏必须完全不透明或禁止背景模糊的旧规格；完整验证记录。

- [ ] **Step 1: 更新旧规格中的冲突条目**

把旧规格第 49 行和第 64 行分别改为：

```markdown
3. 侧栏使用与 Air 参考一致的暗色磨砂玻璃：后方工作区经高斯模糊后极淡透出，前景保持清晰，并使用 12dp 圆角、方向性微光、低对比亮边和柔和深色阴影；不使用液态折射、色散或高频噪点。
```

```markdown
- 侧栏呈现暗色磨砂浮层，后方工作区只能模糊感知位置、不能清晰阅读；不存在液态折射、色散或高频噪点。
```

- [ ] **Step 2: 检查受影响文件问题**

使用 IDEA `lint_files` 或逐个 `get_file_problems` 检查：

```text
desktopApp/src/main/kotlin/com/agent/app/design/WorkspaceBackdrop.kt
desktopApp/src/main/kotlin/com/agent/app/design/AirSidebarSurface.kt
desktopApp/src/main/kotlin/com/agent/app/design/RingUiShells.kt
desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt
desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt
desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt
```

Expected: 无新增 error；新增代码没有缺少 KDoc 的类或函数。

- [ ] **Step 3: 运行桌面模块测试**

```powershell
.\gradlew.bat :desktopApp:test
```

Expected: BUILD SUCCESSFUL，全部测试通过。

- [ ] **Step 4: 运行桌面模块编译**

```powershell
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 检查最终 diff 和注释范围**

使用 IDEA `git_status` 确认只新增/修改计划列出的目标文件，再使用只读 diff 检查：

- `AirSidebarSurface` 前景未进入模糊图层。
- `captureWorkspaceBackdrop` 没有录入侧栏自身。
- 侧栏关闭时 `AnimatedVisibility` 不组合玻璃表面，因此不会绘制模糊副本。
- 未修改侧栏宽度、动画路径、点击外部关闭或任务列表行为。
- 所有新增或修改的生产类和函数具有适当 KDoc。
- 没有新增依赖、启动配置、服务器或 Git 提交。
