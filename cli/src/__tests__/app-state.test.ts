import { describe, expect, test } from "bun:test";

import {
  appendUserPrompt,
  applyRuntimeCliMessage,
  createInitialAppState,
} from "../app-state";

describe("app state", () => {
  test("appends user prompts into transcript", () => {
    const next = appendUserPrompt(createInitialAppState(), "hello");

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
      mode: "demo",
    });
    state = applyRuntimeCliMessage(state, {
      type: "event",
      sessionId: "session-1",
      requestId: "request-1",
      event: {
        message: "runtime.cli.demo",
        payload: "hello",
      },
    });
    state = applyRuntimeCliMessage(state, {
      type: "result",
      sessionId: "session-1",
      requestId: "request-1",
      output: "echo:hello",
      mode: "demo",
    });

    expect(state.runtime.phase).toBe("completed");
    expect(state.runtime.mode).toBe("demo");
    expect(state.runtime.sessionId).toBe("session-1");
    expect(state.transcript).toEqual([
      {
        kind: "status",
        text: "run.started",
      },
      {
        kind: "event",
        text: "runtime.cli.demo: hello",
      },
      {
        kind: "result",
        text: "echo:hello",
      },
    ]);
  });
});
