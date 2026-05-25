import { describe, expect, test } from "bun:test";

import { Composer } from "../components/Composer";

describe("composer", () => {
  test("gives compact chat composer enough padding and height to stay readable", () => {
    const element = Composer({
      draft: "",
      placeholder: "Send a prompt to runtime...",
      footerText: "Code   OpenAI gpt-5 medium",
      helperText: "",
      belowLeftText: "tab agents",
      belowRightText: "ctrl+p commands",
      compact: true,
      commandPalette: {
        isOpen: false,
        query: "",
        items: [],
        selectedIndex: 0,
      },
      onInput: () => {},
      onSubmit: () => {},
    }) as {
      props: {
        style?: {
          minHeight?: number;
        };
        children?: Array<
          | null
          | {
              props?: {
                style?: {
                  minHeight?: number;
                  paddingTop?: number;
                  paddingBottom?: number;
                  justifyContent?: string;
                };
                children?: Array<{ props?: { children?: string; fg?: string } } | null>;
              };
            }
        >;
      };
    };

    const composerBox = element.props.children?.[1];
    const shortcutsRow = element.props.children?.[2];

    expect(element.props.style?.minHeight).toBe(3);
    expect(composerBox?.props?.style).toMatchObject({
      minHeight: 3,
      paddingTop: 1,
      paddingBottom: 1,
    });
    expect(shortcutsRow?.props?.style).toMatchObject({
      justifyContent: "space-between",
      minHeight: 1,
    });
    expect(shortcutsRow?.props?.children?.[0]?.props).toMatchObject({
      children: "tab agents",
      fg: "#d0d0d0",
    });
    expect(shortcutsRow?.props?.children?.[1]?.props).toMatchObject({
      children: "ctrl+p commands",
      fg: "#d0d0d0",
    });
  });
});
