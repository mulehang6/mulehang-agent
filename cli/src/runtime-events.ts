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
  | RuntimeEventMessage
  | RuntimeResultMessage
  | RuntimeFailureMessage;

/**
 * 把动态载荷压平成可直接展示的字符串。
 */
export function formatRuntimeValue(value: unknown): string {
  if (value == null) {
    return "";
  }
  return typeof value === "string" ? value : JSON.stringify(value);
}
