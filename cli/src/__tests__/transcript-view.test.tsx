import { describe, expect, mock, test } from "bun:test";
import { TranscriptView } from "../components/TranscriptView";

describe("transcript view", () => {
  test("keeps sticky scrolling for long replies and anchors short conversations above the composer", () => {
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
              minHeight?: string;
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
    expect(element.props.children?.props?.style?.justifyContent).toBe("flex-end");
    expect(element.props.children?.props?.style?.minHeight).toBe("100%");
  });

  test("renders thinking entries as light expanded toggle regions", () => {
    const onToggleEntry = mock();
    const element = TranscriptView({
      entries: [
        {
          kind: "thinking",
          title: "Thinking",
          expanded: true,
          text: "I need to inspect the context.",
        },
      ],
      onToggleEntry,
    }) as {
      props: {
        children?: {
          props?: {
            children?: Array<{
              props?: {
                onMouseDown?: () => void;
                children?: Array<{
                  props?: { fg?: string; children?: string | string[] };
                }>;
              };
            }>;
          };
        };
      };
    };

    const thinkingBlock = element.props.children?.props?.children?.[0];
    const title = thinkingBlock?.props?.children?.[0];
    const content = thinkingBlock?.props?.children?.[1];

    expect(title?.props?.fg).toBe("#8aa0a8");
    expect(title?.props?.children).toEqual(["v", " ", "Thinking"]);
    expect(content?.props?.fg).toBe("#9aaab0");

    thinkingBlock?.props?.onMouseDown?.();
    expect(onToggleEntry).toHaveBeenCalledWith(0);
  });
});
