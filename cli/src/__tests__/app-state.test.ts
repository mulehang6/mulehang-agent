import { describe, expect, test } from "bun:test";

import {
  appendUserPrompt,
  applyRuntimeCliMessage,
  clearTranscript,
  closeCommandPalette,
  createCommandItems,
  createInitialAppState,
  openCommandPalette,
  selectNextCommand,
  selectPreviousCommand,
  toggleTranscriptEntryExpanded,
} from "../app-state";

describe("app state", () => {
  test("appends user prompts into transcript and switches to chat screen", () => {
    const next = appendUserPrompt(createInitialAppState(), "hello");

    expect(next.screen).toBe("chat");
    expect(next.transcript).toEqual([
      {
        kind: "user",
        text: "hello",
      },
    ]);
  });

  test("tracks runtime status event and result messages", () => {
    let state = createInitialAppState();

    state = applyRuntimeCliMessage(state, {
      type: "status",
      status: "run.started",
      sessionId: "session-1",
      requestId: "request-1",
      mode: "agent",
    });
    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.run.started",
        payload: "hello",
      },
    });
    state = applyRuntimeCliMessage(state, {
      type: "result",
      sessionId: "session-1",
      requestId: "request-1",
      output: "done:hello",
      mode: "agent",
    });

    expect(state.runtime.phase).toBe("completed");
    expect(state.runtime.mode).toBe("agent");
    expect(state.runtime.sessionId).toBe("session-1");
    expect(state.transcript).toEqual([
      {
        kind: "result",
        text: "done:hello",
      },
    ]);
  });

  test("updates runtime display metadata from run metadata messages without touching transcript", () => {
    const state = applyRuntimeCliMessage(createInitialAppState(), {
      type: "metadata",
      sessionId: "session-1",
      requestId: "request-1",
      providerLabel: "OpenAI",
      modelLabel: "gpt-5",
      reasoningEffort: "medium",
    });

    expect(state.runtime.providerLabel).toBe("OpenAI");
    expect(state.runtime.modelLabel).toBe("gpt-5");
    expect(state.runtime.reasoningEffort).toBe("medium");
    expect(state.transcript).toEqual([]);
  });

  test("opens command palette for slash commands and supports selection", () => {
    const commands = createCommandItems([
      {
        name: "/help",
        description: "Show command help",
      },
      {
        name: "/clear",
        description: "Clear transcript",
      },
      {
        name: "/status",
        description: "Show runtime status",
      },
    ]);

    let state = openCommandPalette(createInitialAppState(), "/c", commands);

    expect(state.commandPalette.isOpen).toBe(true);
    expect(state.commandPalette.items.map((item) => item.name)).toEqual([
      "/clear",
    ]);

    state = openCommandPalette(state, "/", commands);
    state = selectNextCommand(state);
    state = selectPreviousCommand(state);

    expect(state.commandPalette.selectedIndex).toBe(0);

    state = closeCommandPalette(state);
    expect(state.commandPalette.isOpen).toBe(false);
  });

  test("shows only one-line failure summary in transcript", () => {
    const state = applyRuntimeCliMessage(createInitialAppState(), {
      type: "failure",
      kind: "agent",
      message: "OpenAILLMClient Status code: 404",
      sessionId: "session-1",
      requestId: "request-1",
      details: {
        source: "runtime-default",
        providerType: "OPENAI_COMPATIBLE",
        baseUrl: "https://openrouter.ai/api/v1",
        modelId: "openai/gpt-oss-120b:free",
        apiKeyPresent: true,
      },
    });

    expect(state.transcript.at(-1)).toEqual({
      kind: "failure",
      text: "agent: OpenAILLMClient Status code: 404",
    });
  });

  test("clears transcript and resets runtime session context", () => {
    const state = clearTranscript({
      ...createInitialAppState(),
      screen: "chat",
      transcript: [
        { kind: "user", text: "hello" },
        { kind: "assistant", text: "world" },
      ],
      runtime: {
        ...createInitialAppState().runtime,
        phase: "running",
        sessionId: "session-1",
        requestId: "request-1",
        detail: "agent.run.started",
      },
    });

    expect(state.transcript).toEqual([]);
    expect(state.runtime.phase).toBe("idle");
    expect(state.runtime.sessionId).toBeUndefined();
    expect(state.runtime.requestId).toBeUndefined();
    expect(state.runtime.detail).toBe("waiting for input");
  });

  test("groups thinking deltas into one expanded transcript block", () => {
    let state = createInitialAppState();

    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.reasoning.delta",
        channel: "thinking",
        delta: "I need to inspect ",
      },
    });
    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.reasoning.delta",
        channel: "thinking",
        delta: "the available context first.",
      },
    });

    expect(state.transcript).toEqual([
      {
        kind: "thinking",
        title: "Thinking",
        expanded: true,
        text: "I need to inspect the available context first.",
      },
    ]);
  });

  test("groups text deltas into one assistant transcript block and skips duplicate final output", () => {
    let state = createInitialAppState();

    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.text.delta",
        channel: "text",
        delta: "hello ",
      },
    });
    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.text.delta",
        channel: "text",
        delta: "world",
      },
    });
    state = applyRuntimeCliMessage(state, {
      type: "result",
      sessionId: "session-1",
      requestId: "request-1",
      output: "hello world",
      mode: "agent",
    });

    expect(state.transcript).toEqual([
      {
        kind: "assistant",
        text: "hello world",
      },
    ]);
  });

  test("keeps appended thinking deltas collapsed when user collapsed the block", () => {
    let state = createInitialAppState();

    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.reasoning.delta",
        channel: "thinking",
        delta: "First thought. ",
      },
    });
    state = toggleTranscriptEntryExpanded(state, 0);
    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.reasoning.delta",
        channel: "thinking",
        delta: "Second thought.",
      },
    });

    expect(state.transcript).toEqual([
      {
        kind: "thinking",
        title: "Thinking",
        expanded: false,
        text: "First thought. Second thought.",
      },
    ]);
  });

  test("groups structured tool events into one collapsible tool transcript block", () => {
    let state = createInitialAppState();

    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.tool.started",
        channel: "tool",
        payload: {
          toolCallId: "tool-call-1",
          toolName: "__read_file__",
          status: "running",
          input: {
            filePath: "docs/spec.md",
            encoding: "utf-8",
          },
        },
      },
    });
    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "agent.tool.completed",
        channel: "tool",
        payload: {
          toolCallId: "tool-call-1",
          toolName: "__read_file__",
          status: "completed",
          input: {
            filePath: "docs/spec.md",
            encoding: "utf-8",
          },
          output: "spec body",
        },
      },
    });

    expect(state.transcript).toEqual([
      {
        kind: "tool",
        text: "",
        title: "Called __read_file__",
        subtitle: "docs/spec.md",
        args: ["encoding=utf-8"],
        expanded: false,
        toolCallId: "tool-call-1",
        toolName: "__read_file__",
        status: "completed",
        input: {
          filePath: "docs/spec.md",
          encoding: "utf-8",
        },
        output: "spec body",
      },
    ]);
  });
});
