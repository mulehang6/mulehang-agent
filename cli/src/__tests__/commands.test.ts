import { describe, expect, test } from "bun:test";

import { executeCommand } from "../commands";
import {
  appendUserPrompt,
  createInitialAppState,
} from "../app-state";

describe("commands", () => {
  test("clear command empties transcript", () => {
    const state = appendUserPrompt(createInitialAppState(), "hello");

    const next = executeCommand(state, "/clear", {
      modeLabel: "Code",
      agentLabel: "Default Agent",
      modelLabel: "openai/gpt-oss-120b:free",
      providerLabel: "provider-openai",
    });

    expect(next.transcript).toEqual([]);
  });

  test("status command appends runtime summary", () => {
    const next = executeCommand(createInitialAppState(), "/status", {
      modeLabel: "Code",
      agentLabel: "Default Agent",
      modelLabel: "openai/gpt-oss-120b:free",
      providerLabel: "provider-openai",
    });

    expect(next.transcript.at(-1)).toEqual({
      kind: "system",
      text: "Status: phase=idle | mode=agent | session=- | request=- | detail=waiting for input",
    });
  });

  test("model command appends provider and model info", () => {
    const next = executeCommand(createInitialAppState(), "/model", {
      modeLabel: "Code",
      agentLabel: "Default Agent",
      modelLabel: "openai/gpt-oss-120b:free",
      providerLabel: "provider-openai",
    });

    expect(next.transcript.at(-1)).toEqual({
      kind: "system",
      text: "Model: provider-openai · openai/gpt-oss-120b:free",
    });
  });
});
