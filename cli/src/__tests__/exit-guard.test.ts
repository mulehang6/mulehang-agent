import { describe, expect, test } from "bun:test";

import { registerCtrlCPress } from "../exit-guard";

describe("exit guard", () => {
  test("requires two ctrl-c presses within the timeout window", () => {
    let state = registerCtrlCPress(undefined, 1000, 2_000);

    expect(state.shouldExit).toBe(false);
    expect(state.message).toBe("再按一次 Ctrl+C 退出");

    state = registerCtrlCPress(state.lastPressedAt, 2_200, 2_000);
    expect(state.shouldExit).toBe(true);
  });

  test("resets when the second press is too late", () => {
    let state = registerCtrlCPress(undefined, 1000, 2_000);
    state = registerCtrlCPress(state.lastPressedAt, 4_500, 2_000);

    expect(state.shouldExit).toBe(false);
    expect(state.message).toBe("再按一次 Ctrl+C 退出");
  });
});
