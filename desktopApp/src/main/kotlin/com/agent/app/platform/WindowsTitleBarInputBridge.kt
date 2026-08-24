package com.agent.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Platform
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import org.jetbrains.skiko.SkiaLayer

private const val COMPOSE_WINDOW_PANEL_CLASS_NAME = "androidx.compose.ui.awt.ComposeWindowPanel"

/**
 * 将 Windows JBR 投递到 ComposeWindowPanel 的自定义标题栏鼠标事件交回 Compose 画布。
 *
 * Compose Desktop 1.11 会把 [ComposeWindow.addMouseListener] 最终注册到内部 SkiaLayer 的 Canvas，
 * 而当前 JBR 会把 CustomTitleBar 的事件投递到 ComposeWindowPanel。本桥接只重分发该
 * Panel 作为事件源的鼠标事件，不创建标题栏、覆盖层或新的命中区域。
 */
@Composable
internal fun BridgeWindowsTitleBarInputToCompose(window: ComposeWindow) {
    DisposableEffect(window) {
        if (!Platform.isWindows()) {
            return@DisposableEffect onDispose {}
        }

        val composeWindowPanel = window.contentPane.components.singleOrNull {
            it.javaClass.name == COMPOSE_WINDOW_PANEL_CLASS_NAME
        } ?: return@DisposableEffect onDispose {}
        val skiaLayer = composeWindowPanel.findSkiaLayer() ?: return@DisposableEffect onDispose {}
        val inputComponent = skiaLayer.canvas
        val listener = object : AWTEventListener {
            override fun eventDispatched(event: AWTEvent) {
                val mouseEvent = event as? MouseEvent ?: return
                if (mouseEvent.source !== composeWindowPanel) return

                inputComponent.dispatchTitleBarMouseEvent(mouseEvent)
            }
        }

        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)

        onDispose {
            toolkit.removeAWTEventListener(listener)
        }
    }
}

/** 查找 ComposeWindowPanel 当前承载输入的 SkiaLayer，结构变化时安全地不安装桥接。 */
private fun Component.findSkiaLayer(): SkiaLayer? {
    if (this is SkiaLayer) return this
    return (this as? Container)?.components?.firstNotNullOfOrNull { it.findSkiaLayer() }
}

/**
 * 将 JBR 投递到 ComposeWindowPanel 的原始事件交给 Skia Canvas 上现有的 Compose/Jewel 监听器。
 *
 * JBR 要求 [com.jetbrains.WindowDecorations.CustomTitleBar.forceHitTest] 在当前原始事件的处理期间调用。
 * SkiaLayer 把这些监听器委托给 Canvas；直接派发克隆事件会丢失这层关联，尤其会让空白标题栏
 * 无法恢复 native drag。ComposeWindowPanel 与该 Canvas 在 Compose Desktop 1.11 中同源于窗口层，
 * 因此标题栏坐标可直接复用。
 */
private fun Component.dispatchTitleBarMouseEvent(event: MouseEvent) {
    when (event.id) {
        MouseEvent.MOUSE_CLICKED -> mouseListeners.forEach { it.mouseClicked(event) }
        MouseEvent.MOUSE_PRESSED -> mouseListeners.forEach { it.mousePressed(event) }
        MouseEvent.MOUSE_RELEASED -> mouseListeners.forEach { it.mouseReleased(event) }
        MouseEvent.MOUSE_ENTERED -> mouseListeners.forEach { it.mouseEntered(event) }
        MouseEvent.MOUSE_EXITED -> mouseListeners.forEach { it.mouseExited(event) }
        MouseEvent.MOUSE_MOVED -> mouseMotionListeners.forEach { it.mouseMoved(event) }
        MouseEvent.MOUSE_DRAGGED -> mouseMotionListeners.forEach { it.mouseDragged(event) }
    }
}
