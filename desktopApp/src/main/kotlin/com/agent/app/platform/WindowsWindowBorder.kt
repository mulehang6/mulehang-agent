package com.agent.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.EventQueue
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private const val DWMWA_BORDER_COLOR = 34
private const val DWMWA_COLOR_NONE = -2
private const val DWMWA_COLOR_DEFAULT = -1
private const val S_OK = 0

/**
 * 为 Windows 11 的 JBR 窗口隐藏 DWM 绘制的外框，避免自定义标题栏在右侧和底部露出系统亮边。
 */
@Composable
internal fun SuppressWindowsWindowBorder(window: ComposeWindow, frameColor: Color) {
    DisposableEffect(window, frameColor) {
        val listener = object : ComponentAdapter() {
            override fun componentShown(event: ComponentEvent?) {
                WindowsWindowBorder.applyFrame(window, frameColor)
            }

            override fun componentResized(event: ComponentEvent?) {
                WindowsWindowBorder.applyFrame(window, frameColor)
            }
        }
        val stateListener = object : WindowAdapter() {
            override fun windowStateChanged(event: WindowEvent?) {
                WindowsWindowBorder.applyFrame(window, frameColor)
            }
        }
        window.addComponentListener(listener)
        window.addWindowStateListener(stateListener)
        WindowsWindowBorder.applyFrame(window, frameColor)

        onDispose {
            window.removeComponentListener(listener)
            window.removeWindowStateListener(stateListener)
            WindowsWindowBorder.restore(window)
        }
    }
}

/**
 * 将 Compose Window 的 HWND 适配为 DWM 所需参数，并将不支持的平台或系统调用失败安全降级。
 */
internal object WindowsWindowBorder {
    /** 同步 AWT 背景并重施 DWM 边框抑制，避免状态切换期间露出窗口默认白底。 */
    fun applyFrame(window: ComposeWindow, frameColor: Color) {
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater { applyFrame(window, frameColor) }
            return
        }
        val awtFrameColor = java.awt.Color(frameColor.red, frameColor.green, frameColor.blue, frameColor.alpha)
        window.background = awtFrameColor
        window.contentPane.background = awtFrameColor
        suppress(window)
    }

    /** 隐藏可见窗口的 DWM 边框；系统不支持时不影响应用继续运行。 */
    fun suppress(window: ComposeWindow): Boolean = setBorderColor(window, DWMWA_COLOR_NONE)

    /** 恢复窗口的默认 DWM 边框策略。 */
    fun restore(window: ComposeWindow): Boolean = setBorderColor(window, DWMWA_COLOR_DEFAULT)

    private fun setBorderColor(window: ComposeWindow, color: Int): Boolean =
        Platform.isWindows() && window.isDisplayable && runCatching {
            val hwnd = HWND(Native.getWindowPointer(window))
            val colorReference = IntByReference(color)
            DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                DWMWA_BORDER_COLOR,
                colorReference.pointer,
                Int.SIZE_BYTES,
            ) == S_OK
        }.getOrDefault(false)
}

/** DWM 的最小 JNA 声明，仅覆盖本窗口所需的边框颜色属性。 */
private interface DwmApi : StdCallLibrary {
    /** 设置 DWM 管理的窗口属性。 */
    @Suppress("FunctionName")
    fun DwmSetWindowAttribute(
        window: HWND,
        attribute: Int,
        value: Pointer,
        valueSize: Int,
    ): Int

    companion object {
        /** 当前进程可复用的 DWM 库句柄。 */
        val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
