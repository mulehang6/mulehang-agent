import {
  formatRuntimeCliValue,
  type RuntimeCliOutboundMessage,
} from "./protocol";

/**
 * 表示 TUI 会话区里的一条可见消息。
 */
export interface TranscriptEntry {
  kind: "user" | "status" | "event" | "result" | "failure";
  text: string;
}

/**
 * 表示当前 runtime 子进程在 TUI 里的摘要状态。
 */
export interface RuntimeSummaryState {
  phase: "idle" | "starting" | "running" | "completed" | "failed";
  mode: string;
  sessionId?: string;
  requestId?: string;
  detail?: string;
}

/**
 * 表示最小 TUI 页面需要维护的全部状态。
 */
export interface AppState {
  transcript: TranscriptEntry[];
  runtime: RuntimeSummaryState;
}

/**
 * 创建第一版 CLI 会话的初始状态。
 */
export function createInitialAppState(): AppState {
  return {
    transcript: [],
    runtime: {
      phase: "idle",
      mode: "demo",
      detail: "waiting for input",
    },
  };
}

/**
 * 把用户刚刚提交的 prompt 追加到会话区。
 */
export function appendUserPrompt(state: AppState, prompt: string): AppState {
  return {
    ...state,
    transcript: [
      ...state.transcript,
      {
        kind: "user",
        text: prompt,
      },
    ],
    runtime: {
      ...state.runtime,
      phase: "starting",
      detail: "sending prompt",
    },
  };
}

/**
 * 把 runtime 协议消息归并到 TUI 的状态和可见 transcript 中。
 */
export function applyRuntimeCliMessage(
  state: AppState,
  message: RuntimeCliOutboundMessage,
): AppState {
  switch (message.type) {
    case "status":
      return {
        ...state,
        transcript: [
          ...state.transcript,
          {
            kind: "status",
            text: message.status,
          },
        ],
        runtime: {
          ...state.runtime,
          phase: message.status === "run.started" ? "running" : "starting",
          mode: message.mode ?? state.runtime.mode,
          sessionId: message.sessionId ?? state.runtime.sessionId,
          requestId: message.requestId ?? state.runtime.requestId,
          detail: message.status,
        },
      };
    case "event":
      return {
        ...state,
        transcript: [
          ...state.transcript,
          {
            kind: "event",
            text: `${message.event.message}: ${formatRuntimeCliValue(message.event.payload)}`.trimEnd(),
          },
        ],
        runtime: {
          ...state.runtime,
          sessionId: message.sessionId,
          requestId: message.requestId,
          detail: message.event.message,
        },
      };
    case "result":
      return {
        ...state,
        transcript: [
          ...state.transcript,
          {
            kind: "result",
            text: formatRuntimeCliValue(message.output),
          },
        ],
        runtime: {
          phase: "completed",
          mode: message.mode,
          sessionId: message.sessionId,
          requestId: message.requestId,
          detail: "completed",
        },
      };
    case "failure":
      return {
        ...state,
        transcript: [
          ...state.transcript,
          {
            kind: "failure",
            text: `${message.kind}: ${message.message}`,
          },
        ],
        runtime: {
          ...state.runtime,
          phase: "failed",
          sessionId: message.sessionId ?? state.runtime.sessionId,
          requestId: message.requestId ?? state.runtime.requestId,
          detail: message.message,
        },
      };
  }
}
