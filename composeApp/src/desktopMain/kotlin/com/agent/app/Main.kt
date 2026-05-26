package com.agent.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * 桌面应用入口。
 */
fun main(): Unit = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "mulehang-agent",
    ) {
        MulehangDesktopApp()
    }
}
