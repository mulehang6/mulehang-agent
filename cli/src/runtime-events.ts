/**
 * 表示 runtime 返回的状态消息。
 */
export interface RuntimeStatusMessage {
  type: "status";
  status: string;
  sessionId?: string;
  requestId?: string;
  mode?: string;
}

/**
 * 表示 runtime 返回的展示元信息消息。
 */
export interface RuntimeMetadataMessage {
  type: "metadata";
  sessionId: string;
  requestId: string;
  providerLabel: string;
  modelLabel: string;
  reasoningEffort?: string;
}

/**
 * 表示 runtime 返回的事件载荷。
 */
export type RuntimeToolCallStatus = "running" | "completed" | "error";

/**
 * 表示一条结构化工具调用生命周期载荷。
 */
export interface RuntimeToolCallPayload {
  toolCallId: string;
  toolName: string;
  status: RuntimeToolCallStatus;
  input?: unknown;
  output?: unknown;
  error?: string;
}

/**
 * 表示 runtime 返回的事件载荷。
 */
export interface RuntimeEventPayload {
  message: string;
  channel?: "text" | "thinking" | "tool" | "status";
  delta?: string;
  payload?: unknown;
}

/**
 * 表示 runtime 返回的流式事件消息。
 */
export interface RuntimeEventMessage {
  type: "event";
  sessionId: string;
  requestId: string;
  event: RuntimeEventPayload;
}

/**
 * 表示 runtime 返回的最终结果消息。
 */
export interface RuntimeResultMessage {
  type: "result";
  sessionId: string;
  requestId: string;
  output?: unknown;
  mode: string;
}

/**
 * 表示 runtime 返回的结构化失败消息。
 */
export interface RuntimeFailureDetails {
  source?: string;
  providerId?: string;
  providerType?: string;
  baseUrl?: string;
  modelId?: string;
  apiKeyPresent?: boolean;
}

/**
 * 表示 runtime 返回的结构化失败消息。
 */
export interface RuntimeFailureMessage {
  type: "failure";
  sessionId?: string;
  requestId?: string;
  kind: string;
  message: string;
  details?: RuntimeFailureDetails;
}

/**
 * 表示 runtime 发回 CLI 的统一消息联合类型。
 */
export type RuntimeMessage =
  | RuntimeStatusMessage
  | RuntimeMetadataMessage
  | RuntimeEventMessage
  | RuntimeResultMessage
  | RuntimeFailureMessage;

/**
 * 判断动态 payload 是否为结构化工具调用对象。
 */
export function isRuntimeToolCallPayload(
  value: unknown,
): value is RuntimeToolCallPayload {
  if (value == null || typeof value !== "object") {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.toolCallId === "string" &&
    typeof candidate.toolName === "string" &&
    (candidate.status === "running" ||
      candidate.status === "completed" ||
      candidate.status === "error")
  );
}

/**
 * 把动态载荷压平成可直接展示的字符串。
 */
export function formatRuntimeValue(value: unknown): string {
  if (value == null) {
    return "";
  }
  return typeof value === "string" ? value : JSON.stringify(value);
}
