import {
  formatRuntimeValue,
  isRuntimeToolCallPayload,
  type RuntimeEventPayload,
  type RuntimeMessage,
  type RuntimeToolCallPayload,
} from "./runtime-events";

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
    | "tool"
    | "thinking"
    | "result"
    | "failure"
    | "system";
  text: string;
  title?: string;
  subtitle?: string;
  args?: string[];
  expanded?: boolean;
  toolCallId?: string;
  toolName?: string;
  status?: RuntimeToolCallPayload["status"];
  input?: unknown;
  output?: unknown;
  error?: string;
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
  providerLabel: string;
  modelLabel: string;
  reasoningEffort?: string;
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
      providerLabel: "runtime managed",
      modelLabel: "runtime default",
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
    runtime: {
      ...state.runtime,
      phase: "idle",
      sessionId: undefined,
      requestId: undefined,
      detail: "waiting for input",
    },
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
  message: RuntimeMessage,
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
    case "metadata":
      return {
        ...state,
        runtime: {
          ...state.runtime,
          sessionId: message.sessionId,
          requestId: message.requestId,
          providerLabel: message.providerLabel,
          modelLabel: message.modelLabel,
          reasoningEffort: message.reasoningEffort,
        },
      };
    case "result":
      if (isDuplicateFinalOutput(state.transcript, message.output)) {
        return {
          ...state,
          screen: "chat",
          runtime: {
            ...state.runtime,
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
            text: formatRuntimeValue(message.output),
          },
        ],
        runtime: {
          ...state.runtime,
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
  event: RuntimeEventPayload,
): TranscriptEntry[] {
  const text = formatRuntimeValue(event.delta ?? event.payload);
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

  if (event.channel === "tool") {
    const toolCall = isRuntimeToolCallPayload(event.payload)
      ? event.payload
      : undefined;
    if (toolCall != null) {
      return upsertToolTranscriptEntry(transcript, toolCall);
    }

    return text.length === 0
      ? transcript
      : [
          ...transcript,
          {
            kind: "event",
            text,
          },
        ];
  }

  return transcript;
}

/**
 * 把同一个 tool call 的多次生命周期事件归并成一个可折叠块。
 */
function upsertToolTranscriptEntry(
  transcript: TranscriptEntry[],
  toolCall: RuntimeToolCallPayload,
): TranscriptEntry[] {
  const existingIndex = transcript.findIndex(
    (entry) => entry.kind === "tool" && entry.toolCallId === toolCall.toolCallId,
  );
  const input = asRecord(toolCall.input);
  const nextEntry: TranscriptEntry = {
    kind: "tool",
    text: "",
    title: `Called ${toolCall.toolName}`,
    subtitle: toolSubtitle(input),
    args: toolArgs(input),
    expanded:
      existingIndex >= 0
        ? (transcript[existingIndex]?.expanded ?? false)
        : false,
    toolCallId: toolCall.toolCallId,
    toolName: toolCall.toolName,
    status: toolCall.status,
    input: toolCall.input,
    output: toolCall.output,
    error: toolCall.error,
  };

  if (existingIndex < 0) {
    return [...transcript, nextEntry];
  }

  return transcript.map((entry, index) =>
    index === existingIndex
      ? {
          ...entry,
          ...nextEntry,
        }
      : entry,
  );
}

/**
 * 提取 kilo GenericTool 同步使用的副标题字段。
 */
function toolSubtitle(input: Record<string, unknown> | undefined): string | undefined {
  if (input == null) {
    return undefined;
  }
  const keys = ["description", "query", "url", "filePath", "path", "pattern", "name"];
  return keys
    .map((key) => input[key])
    .find((value): value is string => typeof value === "string" && value.length > 0);
}

/**
 * 提取 kilo GenericTool 同步使用的参数标签字段。
 */
function toolArgs(input: Record<string, unknown> | undefined): string[] {
  if (input == null) {
    return [];
  }
  const skip = new Set(["description", "query", "url", "filePath", "path", "pattern", "name"]);
  return Object.entries(input)
    .filter(([key]) => !skip.has(key))
    .flatMap(([key, value]) => {
      if (typeof value === "string") {
        return [`${key}=${value}`];
      }
      if (typeof value === "number") {
        return [`${key}=${value}`];
      }
      if (typeof value === "boolean") {
        return [`${key}=${value}`];
      }
      return [];
    })
    .slice(0, 3);
}

/**
 * 把动态输入限制为普通对象，避免数组和原始值误参与工具标签推导。
 */
function asRecord(value: unknown): Record<string, unknown> | undefined {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return undefined;
  }
  return value as Record<string, unknown>;
}

/**
 * 判断最终结果是否已经通过普通文本 delta 完整展示过。
 */
function isDuplicateFinalOutput(
  transcript: TranscriptEntry[],
  output: unknown,
): boolean {
  const text = formatRuntimeValue(output);
  return text.length > 0 && transcript.at(-1)?.kind === "assistant" && transcript.at(-1)?.text === text;
}

/**
 * 把 runtime 失败压缩成主消息流里的一行摘要。
 */
function formatRuntimeFailureSummary(kind: string, message: string): string {
  return `${kind}: ${message}`;
}
