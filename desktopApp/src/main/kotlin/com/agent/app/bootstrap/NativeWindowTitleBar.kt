@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.bootstrap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowScope
import com.jetbrains.JBR
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.RootPaneContainer

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
 * 判断屏幕坐标是否落在窗口的原生标题栏范围内。
 */
internal fun isNativeTitleBarPoint(
    screenPoint: Point,
    windowBounds: Rectangle,
    titleBarHeightPx: Int,
): Boolean =
    screenPoint.x in windowBounds.x until windowBounds.x + windowBounds.width &&
            screenPoint.y in windowBounds.y until windowBounds.y + titleBarHeightPx

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
internal fun WindowScope.rememberNativeWindowTitleBar(
    mode: WindowChromeMode,
    controlsDark: Boolean,
    background: Color,
): NativeTitleBarHandle? {
    var handle by remember(mode) { mutableStateOf<NativeTitleBarHandle?>(null) }
    DisposableEffect(window, mode, controlsDark, background) {
        if (mode != WindowChromeMode.JBR_NATIVE) {
            return@DisposableEffect onDispose { }
        }

        val frame = window as? Frame ?: return@DisposableEffect onDispose { }
        configureNativeWindowBackground(frame, background)
        val decorations = runCatching { JBR.getWindowDecorations() }.getOrNull()
            ?: return@DisposableEffect onDispose { }
        val titleBar = runCatching {
            val configuredTitleBar = decorations.createCustomTitleBar()
            configuredTitleBar.setHeight(nativeTitleBarHeightPx())
            configuredTitleBar.putProperty("controls.dark", controlsDark)
            decorations.setCustomTitleBar(frame, configuredTitleBar)
            configuredTitleBar
        }.getOrNull()

        if (titleBar != null) {
            handle = NativeTitleBarHandle(titleBar::forceHitTest)
        }
        val dragListener = titleBar?.let { configuredTitleBar ->
            AWTEventListener { event ->
                val mouseEvent = event as? MouseEvent ?: return@AWTEventListener
                if (mouseEvent.id == MouseEvent.MOUSE_EXITED) return@AWTEventListener
                val source = mouseEvent.component ?: return@AWTEventListener
                if (!source.belongsTo(frame)) return@AWTEventListener
                val screenPoint = runCatching { mouseEvent.locationOnScreen }.getOrNull()
                    ?: return@AWTEventListener
                if (
                    isNativeTitleBarPoint(
                        screenPoint = screenPoint,
                        windowBounds = frame.bounds,
                        titleBarHeightPx = nativeTitleBarHeightPx().toInt(),
                    )
                ) {
                    configuredTitleBar.forceHitTest(false)
                }
            }
        }
        dragListener?.let { listener ->
            Toolkit.getDefaultToolkit().addAWTEventListener(
                listener,
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
            )
        }
        onDispose {
            handle = null
            dragListener?.let(Toolkit.getDefaultToolkit()::removeAWTEventListener)
            if (titleBar != null) {
                runCatching { decorations.setCustomTitleBar(frame, null) }
            }
        }
    }
    return handle
}

/**
 * 同步 AWT 宿主背景，避免最大化后客户区边缘暴露默认浅色底色。
 */
private fun configureNativeWindowBackground(frame: Frame, background: Color) {
    val awtBackground = java.awt.Color(
        (background.red * 255).toInt(),
        (background.green * 255).toInt(),
        (background.blue * 255).toInt(),
        (background.alpha * 255).toInt(),
    )
    frame.background = awtBackground
    (frame as? RootPaneContainer)?.apply {
        rootPane.background = awtBackground
        rootPane.isOpaque = true
        contentPane.background = awtBackground
    }
}

/**
 * 判断鼠标事件源是否属于当前窗口，避免影响同一进程的其他窗口。
 */
private fun Component.belongsTo(frame: Frame): Boolean {
    var current: Component? = this
    while (current != null) {
        if (current === frame) return true
        current = current.parent
    }
    return false
}
