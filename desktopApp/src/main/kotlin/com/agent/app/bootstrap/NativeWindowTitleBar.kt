@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.bootstrap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowScope
import com.jetbrains.JBR
import java.awt.Frame

internal const val APP_TITLE_BAR_HEIGHT_DP = 48

/**
 * 桌面窗口可使用的标题栏实现。
 */
internal enum class WindowChromeMode {
    JBR_NATIVE,
    COMPOSE_FALLBACK,
}

/**
 * 根据当前 JBR 能力选择原生标题栏或 Compose 回退实现。
 */
internal fun resolveWindowChromeMode(nativeDecorationsSupported: Boolean): WindowChromeMode =
    if (nativeDecorationsSupported) WindowChromeMode.JBR_NATIVE else WindowChromeMode.COMPOSE_FALLBACK

/**
 * 返回与标题栏模式匹配的 Compose 窗口装饰。
 */
internal fun windowDecorationFor(mode: WindowChromeMode): WindowDecoration = when (mode) {
    WindowChromeMode.JBR_NATIVE -> WindowDecoration.SystemDefault
    WindowChromeMode.COMPOSE_FALLBACK -> WindowDecoration.Undecorated()
}

/**
 * 安全探测当前运行时是否支持 JBR 自定义标题栏。
 */
internal fun isNativeWindowDecorationsSupported(): Boolean =
    runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)

/**
 * 返回 JBR/AWT 客户区使用的标题栏高度，不重复应用 Compose density。
 */
internal fun nativeTitleBarHeightPx(): Float = APP_TITLE_BAR_HEIGHT_DP.toFloat()

/**
 * 将 Compose 标题栏交互桥接到 JBR 的逐事件命中测试。
 */
internal class NativeTitleBarHandle(
    private val forceHitTest: (Boolean) -> Unit,
) {
    /**
     * 将当前鼠标事件标记为客户区交互。
     */
    fun forceClientArea() {
        forceHitTest(true)
    }
}

/**
 * 在窗口存续期间安装 JBR 原生按钮，并返回菜单命中桥接句柄。
 */
@Composable
internal fun WindowScope.rememberNativeWindowTitleBar(mode: WindowChromeMode): NativeTitleBarHandle? {
    var handle by remember(mode) { mutableStateOf<NativeTitleBarHandle?>(null) }
    DisposableEffect(window, mode) {
        if (mode != WindowChromeMode.JBR_NATIVE) {
            return@DisposableEffect onDispose { }
        }

        val frame = window as? Frame ?: return@DisposableEffect onDispose { }
        val decorations = runCatching { JBR.getWindowDecorations() }.getOrNull()
            ?: return@DisposableEffect onDispose { }
        val titleBar = runCatching {
            val configuredTitleBar = decorations.createCustomTitleBar()
            configuredTitleBar.setHeight(nativeTitleBarHeightPx())
            configuredTitleBar.putProperty("controls.dark", true)
            decorations.setCustomTitleBar(frame, configuredTitleBar)
            configuredTitleBar
        }.getOrNull()

        if (titleBar != null) {
            handle = NativeTitleBarHandle(titleBar::forceHitTest)
        }
        onDispose {
            handle = null
            if (titleBar != null) {
                runCatching { decorations.setCustomTitleBar(frame, null) }
            }
        }
    }
    return handle
}
