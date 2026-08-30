package com.agent.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeWindow
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * 可由全局外观快捷键触发的缩放调整方向。
 */
internal enum class UiScaleShortcutAction {
    INCREASE,
    DECREASE,
}

/**
 * 解析主键盘上的全局缩放快捷键；Shift 不影响 Ctrl+= 的识别，数字键盘不在支持范围内。
 */
internal fun resolveUiScaleShortcut(
    keyCode: Int,
    isControlDown: Boolean,
): UiScaleShortcutAction? {
    if (!isControlDown) return null
    return when (keyCode) {
        KeyEvent.VK_EQUALS -> UiScaleShortcutAction.INCREASE
        KeyEvent.VK_MINUS -> UiScaleShortcutAction.DECREASE
        else -> null
    }
}

/**
 * 跟踪已消费快捷键的完整按键序列，确保尾随的键入和抬起事件不会泄漏到当前焦点组件。
 */
internal class UiScaleShortcutKeySequence {
    private val consumedKeyCodes = mutableSetOf<Int>()

    /** 记录已经由全局缩放处理的按下按键。 */
    fun recordPressedKey(keyCode: Int) {
        consumedKeyCodes += keyCode
    }

    /** 返回尾随的 KEY_TYPED 事件是否仍属于已消费的快捷键序列。 */
    fun shouldConsumeTypedEvent(): Boolean = consumedKeyCodes.isNotEmpty()

    /** 移除并报告已消费快捷键的抬起事件。 */
    fun consumeReleasedKey(keyCode: Int): Boolean = consumedKeyCodes.remove(keyCode)

    /** 窗口不再接收输入时放弃尚未结束的快捷键序列。 */
    fun clear() {
        consumedKeyCodes.clear()
    }
}

/**
 * 在指定桌面窗口处于前台时注册全局界面缩放快捷键。
 *
 * 已识别的完整按键序列会被消费，避免按键字符进入终端或当前输入框。
 */
@Composable
internal fun RegisterGlobalAppearanceShortcuts(
    window: ComposeWindow,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    val currentOnIncrease by rememberUpdatedState(onIncrease)
    val currentOnDecrease by rememberUpdatedState(onDecrease)
    DisposableEffect(window) {
        val shortcutSequence = UiScaleShortcutKeySequence()
        val windowFocusListener = object : WindowAdapter() {
            override fun windowLostFocus(event: WindowEvent) {
                shortcutSequence.clear()
            }
        }
        val dispatcher = KeyEventDispatcher { event ->
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow !== window) {
                shortcutSequence.clear()
                return@KeyEventDispatcher false
            }

            when (event.id) {
                KeyEvent.KEY_PRESSED -> {
                    when (resolveUiScaleShortcut(event.keyCode, event.isControlDown)) {
                        UiScaleShortcutAction.INCREASE -> currentOnIncrease()
                        UiScaleShortcutAction.DECREASE -> currentOnDecrease()
                        null -> return@KeyEventDispatcher false
                    }
                    shortcutSequence.recordPressedKey(event.keyCode)
                    event.consume()
                    true
                }

                KeyEvent.KEY_TYPED -> {
                    if (!shortcutSequence.shouldConsumeTypedEvent()) return@KeyEventDispatcher false
                    event.consume()
                    true
                }

                KeyEvent.KEY_RELEASED -> {
                    if (!shortcutSequence.consumeReleasedKey(event.keyCode)) return@KeyEventDispatcher false
                    event.consume()
                    true
                }

                else -> false
            }
        }
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        window.addWindowFocusListener(windowFocusListener)
        focusManager.addKeyEventDispatcher(dispatcher)

        onDispose {
            focusManager.removeKeyEventDispatcher(dispatcher)
            window.removeWindowFocusListener(windowFocusListener)
        }
    }
}
