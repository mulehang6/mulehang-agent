/**
 * 表示 CLI 通过本地 `stdio` 协议传给 runtime 的 provider binding。
 */
export interface RuntimeCliProviderBinding {
  providerId: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  modelId: string;
}

/**
 * 表示 CLI 发往 runtime 的最小运行请求。
 */
export interface RuntimeCliRunRequest {
  type: "run";
  sessionId?: string;
  prompt: string;
  provider?: RuntimeCliProviderBinding;
}

/**
 * 表示 CLI 发往 runtime 的所有入站消息联合类型。
 */
export type RuntimeCliInboundMessage = RuntimeCliRunRequest;

/**
 * 表示 runtime 返回的状态消息。
 */
export interface RuntimeCliStatusMessage {
  type: "status";
  status: string;
  sessionId?: string;
  requestId?: string;
  mode?: string;
}

/**
 * 表示 runtime 返回的事件载荷。
 */
export interface RuntimeCliEventPayload {
  message: string;
  payload?: unknown;
}

/**
 * 表示 runtime 返回的流式事件消息。
 */
export interface RuntimeCliEventMessage {
  type: "event";
  sessionId: string;
  requestId: string;
  event: RuntimeCliEventPayload;
}

/**
 * 表示 runtime 返回的最终结果消息。
 */
export interface RuntimeCliResultMessage {
  type: "result";
  sessionId: string;
  requestId: string;
  output?: unknown;
  mode: string;
}

/**
 * 表示 runtime 返回的结构化失败消息。
 */
export interface RuntimeCliFailureDetails {
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
export interface RuntimeCliFailureMessage {
  type: "failure";
  sessionId?: string;
  requestId?: string;
  kind: string;
  message: string;
  details?: RuntimeCliFailureDetails;
}

/**
 * 表示 runtime 发回 CLI 的所有出站消息联合类型。
 */
export type RuntimeCliOutboundMessage =
  | RuntimeCliStatusMessage
  | RuntimeCliEventMessage
  | RuntimeCliResultMessage
  | RuntimeCliFailureMessage;

/**
 * 把一条入站消息编码成单行 JSON，供 `stdin` 协议直接发送。
 */
export function serializeRuntimeCliInbound(
  message: RuntimeCliInboundMessage,
): string {
  return JSON.stringify(message);
}

/**
 * 把 runtime 发回的一行 JSON 解析为协议消息，并校验消息类型。
 */
export function parseRuntimeCliOutbound(
  line: string,
): RuntimeCliOutboundMessage {
  const message = JSON.parse(line) as RuntimeCliOutboundMessage;

  switch (message.type) {
    case "status":
    case "event":
    case "result":
    case "failure":
      return message;
    default:
      throw new Error(`Unsupported runtime cli message type: ${(message as { type?: string }).type ?? "unknown"}`);
  }
}

/**
 * 把协议里的动态载荷压平为可直接展示的字符串。
 */
export function formatRuntimeCliValue(value: unknown): string {
  if (value == null) {
    return "";
  }

  return typeof value === "string" ? value : JSON.stringify(value);
}
