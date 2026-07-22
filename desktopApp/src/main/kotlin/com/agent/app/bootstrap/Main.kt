@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.bootstrap

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 桌面应用入口。
 */
fun main(args: Array<String>) {
    configureDesktopRendering()
    application {
        val initialProjectRoot = resolveInitialProjectRoot(args)
        val windowChromeMode = remember {
            resolveWindowChromeMode(isNativeWindowDecorationsSupported())
        }
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val defaultTransform = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .defaultTransform
        val windowState = rememberWindowState(
            width = calculateWindowSizeDp(screenPixels = screenSize.width, uiScale = defaultTransform.scaleX.toFloat()).dp,
            height = calculateWindowSizeDp(screenPixels = screenSize.height, uiScale = defaultTransform.scaleY.toFloat()).dp,
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "mulehang-agent",
            decoration = windowDecorationFor(windowChromeMode),
        ) {
            MulehangDesktopApp(
                initialProjectRoot = initialProjectRoot,
                desktopWindowState = windowState,
                windowChromeMode = windowChromeMode,
                onCloseRequest = ::exitApplication,
            )
        }
    }
}

internal const val COMPOSE_INTEROP_BLENDING_PROPERTY = "compose.interop.blending"

/**
 * 在 Compose 初始化前启用 Swing 互操作同层合成，避免动态裁剪区域闪烁。
 */
internal fun configureDesktopRendering() {
    System.setProperty(COMPOSE_INTEROP_BLENDING_PROPERTY, "true")
}

/**
 * 解析启动参数中的项目根目录；没有显式工作区时交给 UI 选择流程。
 */
fun resolveInitialProjectRoot(args: Array<String>): Path? =
    args.firstOrNull()?.let(Paths::get)?.let(DesktopProjectRootResolver::resolve)

/**
 * 将物理像素尺寸按当前 UI 缩放换算成 Compose Desktop 逻辑 dp，并应用默认窗口占比。
 */
internal fun calculateWindowSizeDp(
    screenPixels: Int,
    uiScale: Float,
    fraction: Float = 0.8f,
): Float = screenPixels * fraction / uiScale.coerceAtLeast(1f)

/**
 * 返回点击最大化按钮后的窗口放置状态。
 */
internal fun toggleWindowPlacement(current: WindowPlacement): WindowPlacement =
    if (current == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
