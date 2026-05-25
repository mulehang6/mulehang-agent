import {
  useKeyboard,
  useRenderer,
  useTerminalDimensions,
  type InputProps,
} from "@opentui/react";
import { useEffect, useRef, useState } from "react";

import {
  appendSystemMessage,
  appendUserPrompt,
  applyRuntimeCliMessage,
  closeCommandPalette,
  createCommandItems,
  createInitialAppState,
  getSelectedCommand,
  openCommandPalette,
  selectNextCommand,
  selectPreviousCommand,
  toggleTranscriptEntryExpanded,
  type AppState,
} from "./app-state";
import {
  DEFAULT_COMMAND_ITEMS,
  executeCommand,
  formatRuntimeStatus,
} from "./commands";
import { registerCtrlCPress } from "./exit-guard";
import { resolveChatLayout, resolveWelcomeLayout } from "./layout";
import {
  RuntimeHttpClient,
} from "./runtime-http-client";
import { createRuntimeRunRequest } from "./runtime-request";
import {
  createDefaultRuntimeServerManagerOptions,
  RuntimeServerManager,
} from "./runtime-server-manager";
import { ChatScreen } from "./screens/ChatScreen";
import { WelcomeScreen } from "./screens/WelcomeScreen";
import { createWelcomeMetadata, readCurrentGitBranch } from "./welcome-metadata";

const COMMAND_ITEMS = createCommandItems(DEFAULT_COMMAND_ITEMS);
const EXIT_WINDOW_MS = 2_000;

/**
 * 渲染新的双屏 TUI：欢迎页和会话页共享同一条真实 runtime 调用链。
 */
export function App() {
  const [state, setState] = useState<AppState>(() => createInitialAppState());
  const [draft, setDraft] = useState("");
  const [lastCtrlCAt, setLastCtrlCAt] = useState<number | undefined>(undefined);
  const runtimeClientRef = useRef<RuntimeHttpClient | null>(null);
  const renderer = useRenderer();
  const { height } = useTerminalDimensions();
  const [gitBranch] = useState(() => readCurrentGitBranch());

  useKeyboard((key) => {
    if (key.ctrl && key.name === "c") {
      const attempt = registerCtrlCPress(lastCtrlCAt, Date.now(), EXIT_WINDOW_MS);
      setLastCtrlCAt(attempt.lastPressedAt);
      if (attempt.shouldExit) {
        renderer.destroy();
      } else {
        setState((previous) => appendSystemMessage(previous, attempt.message));
      }
      return;
    }

    if (key.name === "escape" && state.commandPalette.isOpen) {
      setState((previous) => closeCommandPalette(previous));
      return;
    }

    if (!state.commandPalette.isOpen) {
      return;
    }

    if (key.name === "down") {
      setState((previous) => selectNextCommand(previous));
      return;
    }

    if (key.name === "up") {
      setState((previous) => selectPreviousCommand(previous));
    }
  });

  useEffect(() => {
    const client = new RuntimeHttpClient(
      new RuntimeServerManager(createDefaultRuntimeServerManagerOptions()),
    );
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

    void client.start()
      .then((server) => {
        setState((previous) => ({
          ...previous,
          runtime: {
            ...previous.runtime,
            providerLabel: server.providerLabel ?? previous.runtime.providerLabel,
            modelLabel: server.modelLabel ?? previous.runtime.modelLabel,
            reasoningEffort: server.reasoningEffort ?? previous.runtime.reasoningEffort,
          },
        }));
      })
      .catch((error) => {
        setState((previous) =>
          applyRuntimeCliMessage(previous, {
            type: "failure",
            kind: "server",
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
   * 在用户输入变化时同步更新草稿和命令面板。
   */
  function handleInput(value: string) {
    setDraft(value);
    setState((previous) =>
      value.trim().startsWith("/")
        ? openCommandPalette(previous, value, COMMAND_ITEMS)
        : closeCommandPalette(previous),
    );
  }

  /**
   * 根据当前草稿内容发送真实请求，或执行一条本地 `/` 命令。
   */
  const submitPrompt = ((rawValue?: string) => {
    const prompt = resolveSubmittedPrompt(rawValue, draft);
    if (!prompt) {
      return;
    }

    if (prompt.startsWith("/")) {
      const selectedCommand =
        getSelectedCommand(state)?.name ??
        COMMAND_ITEMS.find((item) => item.name === prompt)?.name ??
        prompt;

      setDraft("");
      setState((previous) =>
        closeCommandPalette(
          executeCommand(previous, selectedCommand, {
            modeLabel: "Code",
            agentLabel: "Runtime Agent",
            modelLabel: previous.runtime.modelLabel,
            providerLabel: previous.runtime.providerLabel,
          }),
        ),
      );
      return;
    }

    if (runtimeClientRef.current == null) {
      setState((previous) =>
        appendSystemMessage(previous, "Runtime process is not ready yet."),
      );
      return;
    }

    const request = createRuntimeRunRequest(prompt, state.runtime.sessionId);
    setDraft("");
    setState((previous) => closeCommandPalette(appendUserPrompt(previous, prompt)));
    void runtimeClientRef.current.send(request).catch((error) => {
      setState((previous) =>
        appendSystemMessage(
          previous,
          error instanceof Error ? error.message : String(error),
        ),
      );
    });
  }) as NonNullable<InputProps["onSubmit"]>;

  /**
   * 切换 transcript 中可折叠区块的展开状态。
   */
  function handleTranscriptToggle(entryIndex: number) {
    setState((previous) => toggleTranscriptEntryExpanded(previous, entryIndex));
  }

  const modeLabel = "Code";
  const modelLabel = state.runtime.modelLabel;
  const providerLabel = state.runtime.providerLabel;
  const chatFooterText = buildChatFooterText({
    modeLabel,
    providerLabel,
    modelLabel,
    reasoningEffort: state.runtime.reasoningEffort,
  });
  const welcomeMetadata = createWelcomeMetadata({
    gitBranch,
    modeLabel,
    modelLabel,
    providerLabel,
    reasoningEffort: state.runtime.reasoningEffort,
  });
  const welcomeLayout = resolveWelcomeLayout(height);
  const chatLayout = resolveChatLayout(height);

  return state.screen === "welcome" ? (
    <WelcomeScreen
      draft={draft}
      onInput={handleInput}
      onSubmit={submitPrompt}
      commandPalette={state.commandPalette}
      composerFooterText={welcomeMetadata.composerFooterText}
      composerHelperText={welcomeMetadata.composerHelperText}
      workspaceText={welcomeMetadata.workspaceText}
      versionText={welcomeMetadata.versionText}
      layout={welcomeLayout}
    />
  ) : (
    <ChatScreen
      state={state}
      draft={draft}
      title={state.transcript.find((entry) => entry.kind === "user")?.text ?? "Conversation"}
      providerLabel={providerLabel}
      modelLabel={modelLabel}
      hasProvider={true}
      footerText={chatFooterText}
      layout={chatLayout}
      onInput={handleInput}
      onSubmit={submitPrompt}
      onToggleTranscriptEntry={handleTranscriptToggle}
    />
  );
}

/**
 * 输出一行当前 runtime 概要，供后续需要时直接插入命令或提示。
 */
export function buildStatusLine(state: AppState): string {
  return formatRuntimeStatus(state.runtime);
}

/**
 * 解析 OpenTUI submit 事件对应的提交文本；React binding 的 onSubmit 不保证传入 value。
 */
export function resolveSubmittedPrompt(rawValue: unknown, draft: string): string {
  return (typeof rawValue === "string" ? rawValue : draft).trim();
}

/**
 * 组装聊天页输入框内的左侧元信息；仅在存在明确思考等级时追加该字段。
 */
export function buildChatFooterText(input: {
  modeLabel: string;
  providerLabel: string;
  modelLabel: string;
  reasoningEffort?: string;
}): string {
  const runtimeIdentity = [
    input.providerLabel,
    input.modelLabel,
    input.reasoningEffort,
  ]
    .filter((part) => part != null && part.length > 0)
    .join(" ");

  return runtimeIdentity.length > 0
    ? `${input.modeLabel}   ${runtimeIdentity}`
    : input.modeLabel;
}
