import {
  appendSystemMessage,
  clearTranscript,
  type AppState,
  type CommandItem,
  type RuntimeSummaryState,
} from "./app-state";

/**
 * 表示执行命令时需要的只读上下文信息。
 */
export interface CommandExecutionContext {
  modeLabel: string;
  agentLabel: string;
  modelLabel: string;
  providerLabel: string;
}

/**
 * 第一版 `/` 命令面板提供的稳定命令集合。
 */
export const DEFAULT_COMMAND_ITEMS: CommandItem[] = [
  { name: "/help", description: "Show command help" },
  { name: "/clear", description: "Clear transcript" },
  { name: "/agents", description: "Show current agent" },
  { name: "/status", description: "Show runtime status" },
  { name: "/model", description: "Show provider and model" },
];

/**
 * 执行一条命令，并把结果折叠回应用状态。
 */
export function executeCommand(
  state: AppState,
  commandName: string,
  context: CommandExecutionContext,
): AppState {
  switch (commandName) {
    case "/clear":
      return clearTranscript(state);
    case "/help":
      return appendSystemMessage(
        { ...state, screen: "chat" },
        `Commands: ${DEFAULT_COMMAND_ITEMS.map((item) => item.name).join(", ")}`,
      );
    case "/agents":
      return appendSystemMessage(
        { ...state, screen: "chat" },
        `Agent: ${context.modeLabel} · ${context.agentLabel}`,
      );
    case "/status":
      return appendSystemMessage(
        { ...state, screen: "chat" },
        `Status: ${formatRuntimeStatus(state.runtime)}`,
      );
    case "/model":
      return appendSystemMessage(
        { ...state, screen: "chat" },
        `Model: ${context.providerLabel} · ${context.modelLabel}`,
      );
    default:
      return appendSystemMessage(
        { ...state, screen: "chat" },
        `Unknown command: ${commandName}`,
      );
  }
}

/**
 * 把 runtime 状态压平为一行命令输出文本。
 */
export function formatRuntimeStatus(runtime: RuntimeSummaryState): string {
  return [
    `phase=${runtime.phase}`,
    `mode=${runtime.mode}`,
    `session=${runtime.sessionId ?? "-"}`,
    `detail=${runtime.detail ?? "-"}`,
  ].join(" | ");
}
