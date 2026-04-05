package agent

import ai.koog.prompt.streaming.StreamFrame

/**
 * 跟踪流式输出过程中各类增量帧是否已经出现过。
 */
internal data class StreamingFrameState(
    val hasTextDelta: Boolean = false,
    val hasReasoningTextDelta: Boolean = false,
    val hasReasoningSummaryDelta: Boolean = false
)

/**
 * 以不换行方式输出流式文本片段。
 */
internal fun printStreamingChunk(text: String) {
    print(text)
    System.out.flush()
}

/**
 * 以换行方式输出完整的流式文本片段。
 */
internal fun printStreamingLine(text: String) {
    println(text)
    System.out.flush()
}

/**
 * 将流式文本压缩为便于日志查看的预览内容。
 */
private fun previewStreamingText(text: String?, maxLength: Int = 40): String {
    if (text == null) return "null"

    val normalized = text.replace("\n", "\\n")
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength) + "..."
    }
}

/**
 * 生成流式帧的调试描述文本。
 */
internal fun describeStreamingFrame(frame: StreamFrame): String {
    return when (frame) {
        is StreamFrame.TextDelta -> {
            "[调试] 收到流式帧: TextDelta(text=${previewStreamingText(frame.text)})"
        }

        is StreamFrame.TextComplete -> {
            "[调试] 收到流式帧: TextComplete(text=${previewStreamingText(frame.text)})"
        }

        is StreamFrame.ReasoningDelta -> {
            "[调试] 收到流式帧: ReasoningDelta(text=${previewStreamingText(frame.text)}, summary=${previewStreamingText(frame.summary)})"
        }

        is StreamFrame.ReasoningComplete -> {
            val reasoningText = frame.text.joinToString(separator = "")
            val summaryText = frame.summary?.joinToString(separator = "")
            "[调试] 收到流式帧: ReasoningComplete(text=${previewStreamingText(reasoningText)}, summary=${previewStreamingText(summaryText)})"
        }

        is StreamFrame.End -> {
            "[调试] 收到流式帧: End(finishReason=${frame.finishReason})"
        }

        else -> {
            "[调试] 收到流式帧: ${frame::class.simpleName ?: frame::class.qualifiedName ?: "UnknownFrame"}"
        }
    }
}

/**
 * 处理单个流式帧，并维护最终回复与推理输出状态。
 */
internal fun processStreamingFrame(
    frame: StreamFrame,
    responseBuilder: StringBuilder,
    reasoningSteps: MutableList<String>,
    summarySteps: MutableList<String>,
    state: StreamingFrameState,
    emitChunk: (String) -> Unit = ::printStreamingChunk,
    emitLine: (String) -> Unit = ::printStreamingLine
): StreamingFrameState {
    return when (frame) {
        is StreamFrame.TextDelta -> {
            responseBuilder.append(frame.text)
            emitChunk(frame.text)
            state.copy(hasTextDelta = true)
        }

        is StreamFrame.TextComplete -> {
            if (!state.hasTextDelta) {
                responseBuilder.append(frame.text)
                emitChunk(frame.text)
            }
            state
        }

        is StreamFrame.ReasoningDelta -> {
            var nextState = state

            frame.text?.let {
                reasoningSteps.add(it)
                emitChunk(it)
                nextState = nextState.copy(hasReasoningTextDelta = true)
            }

            frame.summary?.let {
                summarySteps.add(it)
                emitChunk(it)
                nextState = nextState.copy(hasReasoningSummaryDelta = true)
            }

            nextState
        }

        is StreamFrame.ReasoningComplete -> {
            val reasoningText = frame.text.joinToString(separator = "")
            val summaryText = frame.summary?.joinToString(separator = "")

            if (reasoningText.isNotBlank() && !state.hasReasoningTextDelta) {
                emitLine(reasoningText)
            }

            if (!summaryText.isNullOrBlank() && !state.hasReasoningSummaryDelta) {
                emitLine(summaryText)
            }

            state
        }

        else -> state
    }
}
