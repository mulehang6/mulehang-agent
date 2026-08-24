package com.agent.app.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 为由业务状态持有的字符串保留 Compose 编辑选择区，并在外部值变化时安全同步。
 */
@Composable
internal fun rememberExternalTextFieldValue(value: String): MutableState<TextFieldValue> {
    val editorValue = remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (editorValue.value.text != value) {
            editorValue.value = TextFieldValue(value, selection = TextRange(value.length))
        }
    }
    return editorValue
}
