package com.agent.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeWindow
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent

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
        val consumedKeyCodes = mutableSetOf<Int>()
        val dispatcher = KeyEventDispatcher { event ->
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow !== window) {
                return@KeyEventDispatcher false
            }

            when (event.id) {
                KeyEvent.KEY_PRESSED -> {
                    when (resolveUiScaleShortcut(event.keyCode, event.isControlDown)) {
                        UiScaleShortcutAction.INCREASE -> currentOnIncrease()
                        UiScaleShortcutAction.DECREASE -> currentOnDecrease()
                        null -> return@KeyEventDispatcher false
                    }
                    consumedKeyCodes += event.keyCode
                    event.consume()
                    true
                }

                KeyEvent.KEY_TYPED -> {
                    if (consumedKeyCodes.isEmpty()) return@KeyEventDispatcher false
                    event.consume()
                    true
                }

                KeyEvent.KEY_RELEASED -> {
                    if (!consumedKeyCodes.remove(event.keyCode)) return@KeyEventDispatcher false
                    event.consume()
                    true
                }

                else -> false
            }
        }
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager.addKeyEventDispatcher(dispatcher)

        onDispose {
            focusManager.removeKeyEventDispatcher(dispatcher)
        }
    }
}
