import type { RuntimeMessage } from "./runtime-events";
import type { RuntimeRunRequest } from "./runtime-request";
import type { RuntimeServerConnection } from "./runtime-server-manager";

/**
 * 表示获取共享 runtime server 连接信息的最小依赖。
 */
export interface RuntimeServerLocator {
  getServer(): Promise<RuntimeServerConnection>;
}

/**
 * 表示可注入的 HTTP 依赖，便于测试替换。
 */
export interface RuntimeHttpClientDeps {
  fetch(input: string, init: RequestInit): Promise<Response>;
}

interface RuntimeSseEvent {
  event: string;
  sessionId?: string;
  requestId?: string;
  channel?: "text" | "thinking" | "tool" | "status";
  message?: string;
  delta?: string;
  output?: unknown;
  failureKind?: string;
}

/**
 * 通过共享本地 HTTP server 与 runtime 通信，并把 SSE 映射为现有 UI 可消费的消息。
 */
export class RuntimeHttpClient {
  private readonly messageListeners = new Set<(message: RuntimeMessage) => void>();
  private readonly errorListeners = new Set<(error: string) => void>();
  private readonly activeControllers = new Set<AbortController>();

  constructor(
    private readonly serverLocator: RuntimeServerLocator,
    private readonly deps: RuntimeHttpClientDeps = defaultRuntimeHttpClientDeps,
  ) {}

  /**
   * 预热共享 runtime server；调用方可在 UI 启动阶段先完成连接准备。
   */
  async start(): Promise<void> {
    await this.serverLocator.getServer();
  }

  /**
   * 注册 runtime 消息监听器。
   */
  onMessage(listener: (message: RuntimeMessage) => void): () => void {
    this.messageListeners.add(listener);
    return () => {
      this.messageListeners.delete(listener);
    };
  }

  /**
   * 注册错误监听器。
   */
  onError(listener: (error: string) => void): () => void {
    this.errorListeners.add(listener);
    return () => {
      this.errorListeners.delete(listener);
    };
  }

  /**
   * 发送一次运行请求，并增量消费 SSE 响应。
   */
  async send(request: RuntimeRunRequest): Promise<void> {
    const server = await this.serverLocator.getServer();
    const controller = new AbortController();
    this.activeControllers.add(controller);

    try {
      const response = await this.deps.fetch(`${server.baseUrl}/runtime/run/stream`, {
        method: "POST",
        headers: {
          ...(server.token == null
            ? {}
            : { authorization: `Bearer ${server.token}` }),
          "content-type": "application/json",
        },
        body: JSON.stringify(request),
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(await extractHttpFailure(response));
      }
      if (response.body == null) {
        throw new Error("Runtime HTTP stream did not return a readable body.");
      }

      await consumeSseStream(response.body, (event) => {
        const message = mapSseEventToRuntimeMessage(event);
        if (message == null) {
          return;
        }
        this.messageListeners.forEach((listener) => listener(message));
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.errorListeners.forEach((listener) => listener(message));
      throw error;
    } finally {
      this.activeControllers.delete(controller);
    }
  }

  /**
   * 中止当前所有活跃的 HTTP 流请求。
   */
  dispose(): void {
    this.activeControllers.forEach((controller) => controller.abort());
    this.activeControllers.clear();
  }
}

const defaultRuntimeHttpClientDeps: RuntimeHttpClientDeps = {
  fetch(input, init) {
    return fetch(input, init);
  },
};

export async function consumeSseStream(
  stream: ReadableStream<Uint8Array>,
  onEvent: (event: RuntimeSseEvent) => void,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";
    for (const frame of frames) {
      const event = parseSseFrame(frame);
      if (event != null) {
        onEvent(event);
      }
    }
  }

  buffer += decoder.decode();
  if (buffer.trim().length > 0) {
    const event = parseSseFrame(buffer);
    if (event != null) {
      onEvent(event);
    }
  }
}

function parseSseFrame(frame: string): RuntimeSseEvent | undefined {
  const lines = frame
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  if (lines.length === 0) {
    return undefined;
  }

  const dataLine = lines.find((line) => line.startsWith("data:"));
  if (dataLine == null) {
    return undefined;
  }
  return JSON.parse(dataLine.slice("data:".length).trim()) as RuntimeSseEvent;
}

function mapSseEventToRuntimeMessage(event: RuntimeSseEvent): RuntimeMessage | undefined {
  switch (event.event) {
    case "status":
      return {
        type: "status",
        status: event.message ?? "run.started",
        sessionId: event.sessionId,
        requestId: event.requestId,
        mode: "agent",
      };
    case "thinking.delta":
    case "text.delta":
      if (event.sessionId == null || event.requestId == null) {
        return undefined;
      }
      return {
        type: "event",
        sessionId: event.sessionId,
        requestId: event.requestId,
        event: {
          message: event.message ?? event.event,
          channel: event.channel,
          delta: event.delta,
        },
      };
    case "run.completed":
      if (event.sessionId == null || event.requestId == null) {
        return undefined;
      }
      return {
        type: "result",
        sessionId: event.sessionId,
        requestId: event.requestId,
        output: event.output,
        mode: "agent",
      };
    case "run.failed":
      return {
        type: "failure",
        sessionId: event.sessionId,
        requestId: event.requestId,
        kind: event.failureKind ?? "runtime",
        message: event.message ?? "Runtime HTTP stream failed.",
      };
    default:
      return undefined;
  }
}

async function extractHttpFailure(response: Response): Promise<string> {
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    try {
      const payload = await response.json() as { message?: string };
      if (payload.message != null) {
        return payload.message;
      }
    } catch {
      // ignore parse failure
    }
  }
  return `Runtime HTTP request failed with status ${response.status}.`;
}
