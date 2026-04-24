import { useKeyboard, useRenderer, type InputProps } from "@opentui/react";
import { useEffect, useRef, useState } from "react";

import {
  appendUserPrompt,
  applyRuntimeCliMessage,
  createInitialAppState,
  type AppState,
  type TranscriptEntry,
} from "./app-state";
import {
  type RuntimeCliProviderBinding,
  type RuntimeCliRunRequest,
} from "./protocol";
import {
  createDefaultRuntimeLaunchSpec,
  RuntimeProcessClient,
} from "./runtime-process";

/**
 * 渲染最小可交互的 CLI/TUI，会话输入通过本地 runtime 子进程闭环返回。
 */
export function App() {
  const [state, setState] = useState<AppState>(() => createInitialAppState());
  const [draft, setDraft] = useState("");
  const runtimeClientRef = useRef<RuntimeProcessClient | null>(null);
  const renderer = useRenderer();

  useKeyboard((key) => {
    if (key.name === "escape") {
      renderer.destroy();
    }
  });

  useEffect(() => {
    const client = new RuntimeProcessClient(createDefaultRuntimeLaunchSpec());
    runtimeClientRef.current = client;

    const offMessage = client.onMessage((message) => {
      setState((previous) => applyRuntimeCliMessage(previous, message));
    });
    const offError = client.onError((error) => {
      setState((previous) =>
        applyRuntimeCliMessage(previous, {
          type: "failure",
          kind: "protocol",
          message: error,
        }),
      );
    });

    void client.start().catch((error) => {
      setState((previous) =>
        applyRuntimeCliMessage(previous, {
          type: "failure",
          kind: "runtime",
          message: error instanceof Error ? error.message : String(error),
        }),
      );
    });

    return () => {
      offMessage();
      offError();
      client.dispose();
      runtimeClientRef.current = null;
    };
  }, []);

  /**
   * 提交当前输入框内容，并把它转成一条 runtime 请求。
   */
  const submitPrompt = ((nextPrompt: string) => {
    const prompt = nextPrompt.trim();
    if (!prompt || runtimeClientRef.current == null) {
      return;
    }

    setDraft("");
    setState((previous) => appendUserPrompt(previous, prompt));
    runtimeClientRef.current.send(buildRunRequest(prompt));
  }) as NonNullable<InputProps["onSubmit"]>;

  return (
    <box
      style={{
        width: "100%",
        height: "100%",
        flexDirection: "column",
        padding: 1,
        gap: 1,
        backgroundColor: "#08141b",
      }}
    >
      <box
        title="Status"
        borderStyle="rounded"
        style={{ padding: 1, flexDirection: "column", gap: 1 }}
      >
        <text fg="#9fd3c7">{buildStatusLine(state)}</text>
        <text fg="#7b9aa8">
          Demo mode is used when provider env vars are not set.
        </text>
      </box>

      <box
        title="Session"
        borderStyle="rounded"
        style={{ padding: 1, flexDirection: "column", gap: 1, flexGrow: 1 }}
      >
        {state.transcript.length === 0 ? (
          <text fg="#6a8a96">Type a prompt and press Enter.</text>
        ) : (
          state.transcript.map((entry, index) => (
            <text key={`${entry.kind}-${index}`} fg={colorForEntry(entry)}>
              {prefixForEntry(entry)}
              {entry.text}
            </text>
          ))
        )}
      </box>

      <box
        title="Input"
        borderStyle="rounded"
        style={{ padding: 1, flexDirection: "column", gap: 1 }}
      >
        <text fg="#6a8a96">Enter to send. Esc to quit.</text>
        <input
          value={draft}
          focused
          placeholder="Send a prompt to runtime..."
          onInput={setDraft}
          onSubmit={submitPrompt}
        />
      </box>
    </box>
  );
}

/**
 * 根据环境变量构造本次请求的 provider binding；缺省时返回 demo 模式。
 */
function readProviderFromEnv(): RuntimeCliProviderBinding | undefined {
  const providerId = process.env.MULEHANG_PROVIDER_ID;
  const providerType = process.env.MULEHANG_PROVIDER_TYPE;
  const baseUrl = process.env.MULEHANG_PROVIDER_BASE_URL;
  const apiKey = process.env.MULEHANG_PROVIDER_API_KEY;
  const modelId = process.env.MULEHANG_PROVIDER_MODEL_ID;

  if (
    !providerId ||
    !providerType ||
    !baseUrl ||
    !apiKey ||
    !modelId
  ) {
    return undefined;
  }

  return {
    providerId,
    providerType,
    baseUrl,
    apiKey,
    modelId,
  };
}

/**
 * 为 runtime 子进程生成一条最小运行请求。
 */
function buildRunRequest(prompt: string): RuntimeCliRunRequest {
  const provider = readProviderFromEnv();

  return provider == null
    ? {
        type: "run",
        prompt,
      }
    : {
        type: "run",
        prompt,
        provider,
      };
}

/**
 * 把运行时摘要信息压平成可显示的状态行。
 */
function buildStatusLine(state: AppState): string {
  return [
    `phase=${state.runtime.phase}`,
    `mode=${state.runtime.mode}`,
    `session=${state.runtime.sessionId ?? "-"}`,
    `request=${state.runtime.requestId ?? "-"}`,
    `detail=${state.runtime.detail ?? "-"}`,
  ].join(" | ");
}

/**
 * 为不同消息类型选择一致的前缀，便于快速扫描会话流。
 */
function prefixForEntry(entry: TranscriptEntry): string {
  switch (entry.kind) {
    case "user":
      return "you> ";
    case "status":
      return "status> ";
    case "event":
      return "event> ";
    case "result":
      return "result> ";
    case "failure":
      return "error> ";
  }
}

/**
 * 为不同消息类型提供最小但稳定的终端配色。
 */
function colorForEntry(entry: TranscriptEntry): string {
  switch (entry.kind) {
    case "user":
      return "#f7f4ea";
    case "status":
      return "#9fd3c7";
    case "event":
      return "#f4b860";
    case "result":
      return "#b7efc5";
    case "failure":
      return "#ff8b94";
  }
}
