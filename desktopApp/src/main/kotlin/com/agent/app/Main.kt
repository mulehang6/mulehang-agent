package com.agent.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 桌面应用入口。
 */
fun main(args: Array<String>): Unit = application {
    val initialProjectRoot = resolveInitialProjectRoot(args)
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
    ) {
        MulehangDesktopApp(initialProjectRoot)
    }
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
