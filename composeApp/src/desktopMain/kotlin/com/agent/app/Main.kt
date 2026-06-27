package com.agent.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 桌面应用入口。
 */
fun main(args: Array<String>): Unit = application {
    val initialProjectRoot = resolveInitialProjectRoot(args)
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
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
