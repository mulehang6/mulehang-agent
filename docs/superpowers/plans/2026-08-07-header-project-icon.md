# Header Project Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the selected glass-style lowercase `m` as a scalable SVG immediately left of the active workspace name in the desktop title bar.

**Architecture:** Bundle one transparent-background SVG under the desktop module's `composeResources/drawable` directory, then load its generated `DrawableResource` with Compose Desktop's `painterResource` from `ChatHeader`. The artwork owns its own glass-like layered gradients; `ChatHeader` owns only placement and accessibility metadata.

**Tech Stack:** Kotlin, Compose Multiplatform Desktop, SVG resources, kotlin.test.

## Global Constraints

- Use a transparent-background SVG, not the exploratory PNG bitmap.
- Keep the icon at 20 dp with a 6 dp gap before the workspace label.
- Do not modify title-bar interaction behavior, sidebar controls, or task handling.
- Do not create a Git commit without explicit user authorization.

---

### Task 1: Bundle and render the vector project icon

**Files:**
- Create: `desktopApp/src/main/composeResources/drawable/mulehang_agent.svg`
- Create: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ProjectIconResourceTest.kt`
- Modify: `desktopApp/build.gradle.kts`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`

**Interfaces:**
- Consumes: Compose Desktop `painterResource(Res.drawable.mulehang_agent)`.
- Produces: a 20 dp accessible `Image` immediately before the active workspace label.

- [ ] **Step 1: Write the failing resource-packaging test**

```kotlin
@Test
fun `标题栏项目图标以 SVG 资源打包`() {
    val resource = assertNotNull(
        javaClass.classLoader.getResource(
            "composeResources/mulehang_agent.desktopapp.generated.resources/drawable/mulehang_agent.svg",
        ),
    )
    val content = resource.openStream().use { it.readBytes().decodeToString() }

    assertTrue(content.contains("<svg"))
}
```

- [ ] **Step 2: Run the test to verify it fails because the SVG is absent**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ProjectIconResourceTest"`

Expected: FAIL because the SVG is not on the test runtime classpath.

- [ ] **Step 3: Add the transparent SVG and render it in `ChatHeader`**

Add `org.jetbrains.compose.components:components-resources:1.11.1`, then use layered SVG gradient strokes to form a clear lowercase `m`; load it with the generated `Res.drawable.mulehang_agent` resource; give the `Image` the content description `mulehang-agent 项目图标` and place it before the workspace label.

- [ ] **Step 4: Run the focused test and Kotlin compilation**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ProjectIconResourceTest"` and `.\gradlew.bat :desktopApp:compileKotlin`

Expected: both commands succeed.
