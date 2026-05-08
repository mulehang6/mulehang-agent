import { describe, expect, test } from "bun:test";

import { Composer } from "../components/Composer";

describe("composer", () => {
  test("gives compact chat composer enough padding and height to stay readable", () => {
    const element = Composer({
      draft: "",
      placeholder: "Send a prompt to runtime...",
      footerText: "Code",
      helperText: "",
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
                };
              };
            }
        >;
      };
    };

    const composerBox = element.props.children?.[1];

    expect(element.props.style?.minHeight).toBe(3);
    expect(composerBox?.props?.style).toMatchObject({
      minHeight: 3,
      paddingTop: 1,
      paddingBottom: 1,
    });
  });
});
