import { describe, expect, test } from "bun:test";
import { TranscriptView } from "../components/TranscriptView";

describe("transcript view", () => {
  test("keeps sticky scrolling for long replies but lays short conversations out from the top", () => {
    const element = TranscriptView({
      entries: [{ kind: "result", text: "hello" }],
    }) as {
      type: string;
      props: {
        stickyScroll?: boolean;
        stickyStart?: string;
        style?: {
          flexGrow?: number;
          height?: string;
          flexDirection?: string;
        };
        children?: {
          props?: {
            style?: {
              justifyContent?: string;
              height?: string;
            };
          };
        };
      };
    };

    expect(element.type).toBe("scrollbox");
    expect(element.props.stickyScroll).toBe(true);
    expect(element.props.stickyStart).toBe("bottom");
    expect(element.props.style).toMatchObject({
      flexGrow: 1,
      height: "100%",
      flexDirection: "column",
    });
    expect(element.props.children?.props?.style?.justifyContent).toBeUndefined();
    expect(element.props.children?.props?.style?.height).toBeUndefined();
  });
});
