import { describe, expect, test } from "bun:test";

import { createRuntimeRunRequest } from "../runtime-request";

describe("runtime request", () => {
  test("creates runtime request without provider binding", () => {
    expect(createRuntimeRunRequest("hello")).toEqual({
      type: "run",
      prompt: "hello",
    });
  });
});
