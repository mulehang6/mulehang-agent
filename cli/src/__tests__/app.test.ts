import { describe, expect, test } from "bun:test";

import { buildChatFooterText, resolveSubmittedPrompt } from "../app";

describe("app submit handling", () => {
  test("uses current draft when OpenTUI submit event does not pass a value", () => {
    expect(resolveSubmittedPrompt(undefined, "hello")).toBe("hello");
  });

  test("uses explicit submit value when a renderer provides one", () => {
    expect(resolveSubmittedPrompt("from event", "from draft")).toBe("from event");
  });

  test("builds chat footer text without a reasoning label when the runtime does not expose one", () => {
    expect(
      buildChatFooterText({
        modeLabel: "Code",
        providerLabel: "OpenAI",
        modelLabel: "gpt-5",
      }),
    ).toBe("Code   OpenAI gpt-5");
  });

  test("builds chat footer text with a reasoning label when the runtime exposes one", () => {
    expect(
      buildChatFooterText({
        modeLabel: "Code",
        providerLabel: "OpenAI",
        modelLabel: "gpt-5",
        reasoningEffort: "medium",
      }),
    ).toBe("Code   OpenAI gpt-5 medium");
  });
});
