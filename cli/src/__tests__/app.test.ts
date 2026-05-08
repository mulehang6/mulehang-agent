import { describe, expect, test } from "bun:test";

import { resolveSubmittedPrompt } from "../app";

describe("app submit handling", () => {
  test("uses current draft when OpenTUI submit event does not pass a value", () => {
    expect(resolveSubmittedPrompt(undefined, "hello")).toBe("hello");
  });

  test("uses explicit submit value when a renderer provides one", () => {
    expect(resolveSubmittedPrompt("from event", "from draft")).toBe("from event");
  });
});
