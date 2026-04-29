import {
  formatRuntimeCliValue,
  type RuntimeCliEventPayload,
  type RuntimeCliOutboundMessage,
} from "./protocol";

/**
 * 表示 TUI 当前处于欢迎页还是会话页。
 */
export type AppScreen = "welcome" | "chat";

/**
 * 表示 TUI 会话区里的一条可见消息。
 */
export interface TranscriptEntry {
  kind:
    | "user"
    | "assistant"
    | "status"
    | "event"
    | "thinking"
    | "result"
    | "failure"
    | "system";
  text: string;
  title?: string;
  expanded?: boolean;
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
 * 表示 `/` 命令面板中的单个命令项。
 */
export interface CommandItem {
  name: string;
  description: string;
}

/**
 * 表示命令面板的当前可见状态。
 */
export interface CommandPaletteState {
  isOpen: boolean;
  query: string;
  items: CommandItem[];
  selectedIndex: number;
}

/**
 * 表示最小 TUI 页面需要维护的全部状态。
 */
export interface AppState {
  screen: AppScreen;
  transcript: TranscriptEntry[];
  runtime: RuntimeSummaryState;
  commandPalette: CommandPaletteState;
}

/**
 * 创建第一版 CLI 会话的初始状态。
 */
export function createInitialAppState(): AppState {
  return {
    screen: "welcome",
    transcript: [],
    runtime: {
      phase: "idle",
      mode: "agent",
      detail: "waiting for input",
    },
    commandPalette: {
      isOpen: false,
      query: "",
      items: [],
      selectedIndex: 0,
    },
  };
}

/**
 * 根据输入的命令元数据生成稳定命令列表。
 */
export function createCommandItems(items: CommandItem[]): CommandItem[] {
  return items.slice();
}

/**
 * 把用户刚刚提交的 prompt 追加到会话区，并切到会话页。
 */
export function appendUserPrompt(state: AppState, prompt: string): AppState {
  return {
    ...state,
    screen: "chat",
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
 * 追加一条系统提示到会话区。
 */
export function appendSystemMessage(state: AppState, text: string): AppState {
  return {
    ...state,
    transcript: [
      ...state.transcript,
      {
        kind: "system",
        text,
      },
    ],
  };
}

/**
 * 清空当前会话区内容。
 */
export function clearTranscript(state: AppState): AppState {
  return {
    ...state,
    transcript: [],
  };
}

/**
 * 切换一条可折叠 transcript entry 的展开状态。
 */
export function toggleTranscriptEntryExpanded(
  state: AppState,
  entryIndex: number,
): AppState {
  const entry = state.transcript[entryIndex];
  if (entry == null || entry.expanded == null) {
    return state;
  }

  return {
    ...state,
    transcript: state.transcript.map((item, index) =>
      index === entryIndex
        ? {
            ...item,
            expanded: !item.expanded,
          }
        : item,
    ),
  };
}

/**
 * 根据用户当前输入打开命令面板，并筛选匹配的命令。
 */
export function openCommandPalette(
  state: AppState,
  query: string,
  allItems: CommandItem[],
): AppState {
  const normalizedQuery = query.trim().toLowerCase();
  const items = allItems.filter((item) =>
    normalizedQuery === "/"
      ? true
      : item.name.toLowerCase().startsWith(normalizedQuery),
  );

  return {
    ...state,
    commandPalette: {
      isOpen: true,
      query,
      items,
      selectedIndex: 0,
    },
  };
}

/**
 * 关闭命令面板并重置其临时状态。
 */
export function closeCommandPalette(state: AppState): AppState {
  return {
    ...state,
    commandPalette: {
      isOpen: false,
      query: "",
      items: [],
      selectedIndex: 0,
    },
  };
}

/**
 * 把命令面板的当前选择下移一项。
 */
export function selectNextCommand(state: AppState): AppState {
  if (!state.commandPalette.isOpen || state.commandPalette.items.length === 0) {
    return state;
  }

  return {
    ...state,
    commandPalette: {
      ...state.commandPalette,
      selectedIndex:
        (state.commandPalette.selectedIndex + 1) %
        state.commandPalette.items.length,
    },
  };
}

/**
 * 把命令面板的当前选择上移一项。
 */
export function selectPreviousCommand(state: AppState): AppState {
  if (!state.commandPalette.isOpen || state.commandPalette.items.length === 0) {
    return state;
  }

  return {
    ...state,
    commandPalette: {
      ...state.commandPalette,
      selectedIndex:
        (state.commandPalette.selectedIndex - 1 +
          state.commandPalette.items.length) %
        state.commandPalette.items.length,
    },
  };
}

/**
 * 读取命令面板当前高亮的命令项。
 */
export function getSelectedCommand(state: AppState): CommandItem | undefined {
  if (!state.commandPalette.isOpen || state.commandPalette.items.length === 0) {
    return undefined;
  }

  return state.commandPalette.items[state.commandPalette.selectedIndex];
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
        screen: "chat",
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
        screen: "chat",
        transcript: applyRuntimeEventToTranscript(
          state.transcript,
          message.event,
        ),
        runtime: {
          ...state.runtime,
          sessionId: message.sessionId,
          requestId: message.requestId,
          detail: message.event.message,
        },
      };
    case "result":
      if (isDuplicateFinalOutput(state.transcript, message.output)) {
        return {
          ...state,
          screen: "chat",
          runtime: {
            phase: "completed",
            mode: message.mode,
            sessionId: message.sessionId,
            requestId: message.requestId,
            detail: "completed",
          },
        };
      }

      return {
        ...state,
        screen: "chat",
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
        screen: "chat",
        transcript: [
          ...state.transcript,
          {
            kind: "failure",
            text: formatRuntimeFailureSummary(message.kind, message.message),
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

/**
 * 把 runtime event 的可见文本归并到 transcript，thinking delta 会合并到同一个默认展开区块。
 */
function applyRuntimeEventToTranscript(
  transcript: TranscriptEntry[],
  event: RuntimeCliEventPayload,
): TranscriptEntry[] {
  const text = formatRuntimeCliValue(event.delta ?? event.payload);
  if (text.length === 0) {
    return transcript;
  }

  if (event.channel === "thinking") {
    const lastEntry = transcript.at(-1);
    if (lastEntry?.kind === "thinking") {
      return [
        ...transcript.slice(0, -1),
        {
          ...lastEntry,
          text: `${lastEntry.text}${text}`,
        },
      ];
    }

    return [
      ...transcript,
      {
        kind: "thinking",
        title: "Thinking",
        expanded: true,
        text,
      },
    ];
  }

  if (event.channel === "text") {
    const lastEntry = transcript.at(-1);
    if (lastEntry?.kind === "assistant") {
      return [
        ...transcript.slice(0, -1),
        {
          ...lastEntry,
          text: `${lastEntry.text}${text}`,
        },
      ];
    }

    return [
      ...transcript,
      {
        kind: "assistant",
        text,
      },
    ];
  }

  return transcript;
}

/**
 * 判断最终结果是否已经通过普通文本 delta 完整展示过。
 */
function isDuplicateFinalOutput(
  transcript: TranscriptEntry[],
  output: unknown,
): boolean {
  const text = formatRuntimeCliValue(output);
  return text.length > 0 && transcript.at(-1)?.kind === "assistant" && transcript.at(-1)?.text === text;
}

/**
 * 把 runtime 失败压缩成主消息流里的一行摘要。
 */
function formatRuntimeFailureSummary(kind: string, message: string): string {
  return `${kind}: ${message}`;
}
