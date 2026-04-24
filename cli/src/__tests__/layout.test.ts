import { describe, expect, test } from "bun:test";

import { resolveChatLayout, resolveWelcomeLayout } from "../layout";

describe("layout", () => {
  test("welcome layout mirrors kilo spacing on short screens", () => {
    expect(resolveWelcomeLayout(24)).toMatchObject({
      showLogo: true,
      showTip: true,
      promptMaxWidth: 75,
      topSpacerHeight: 3,
      tipHeight: 4,
    });
  });

  test("welcome layout still keeps logo and prompt on very short screens", () => {
    expect(resolveWelcomeLayout(18)).toMatchObject({
      showLogo: true,
      showTip: false,
      promptMaxWidth: 75,
      topSpacerHeight: 1,
      tipPaddingTop: 3,
    });
  });

  test("chat layout trims padding and helper text on short screens", () => {
    expect(resolveChatLayout(18)).toMatchObject({
      padding: 1,
      gap: 0,
      showHelperText: false,
      sidebarMode: "compact",
    });
  });
});
