import { describe, expect, mock, test } from "bun:test";
import { TranscriptView } from "../components/TranscriptView";

describe("transcript view", () => {
  test("keeps transcript content at natural height while the scroll region sits above the composer", () => {
    const element = TranscriptView({
      entries: [{ kind: "result", text: "hello" }],
    }) as {
      type: string;
      props: {
        style?: {
          flexGrow?: number;
          flexDirection?: string;
          justifyContent?: string;
          minHeight?: number;
        };
        children?: {
          type?: string;
          props?: {
            stickyScroll?: boolean;
            stickyStart?: string;
            style?: {
              flexShrink?: number;
              minHeight?: number;
              flexDirection?: string;
            };
            children?: {
              props?: {
                style?: {
                  justifyContent?: string;
                  minHeight?: string;
                  gap?: number;
                };
              };
            };
          };
        };
      };
    };

    const scrollbox = element.props.children;
    const content = scrollbox?.props?.children;

    expect(element.type).toBe("box");
    expect(element.props.style).toMatchObject({
      flexGrow: 1,
      minHeight: 0,
      flexDirection: "column",
      justifyContent: "flex-end",
    });
    expect(scrollbox?.type).toBe("scrollbox");
    expect(scrollbox?.props?.stickyScroll).toBe(true);
    expect(scrollbox?.props?.stickyStart).toBe("bottom");
    expect(scrollbox?.props?.style).toMatchObject({
      flexShrink: 1,
      minHeight: 0,
      flexDirection: "column",
    });
    expect(content?.props?.style).toMatchObject({
      gap: 1,
    });
    expect(content?.props?.style?.justifyContent).toBeUndefined();
    expect(content?.props?.style?.minHeight).toBeUndefined();
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
            children?: {
              props?: {
                children?: Array<{
                  props?: {
                    onMouseUp?: () => void;
                    children?: Array<{
                      props?: { fg?: string; children?: string | string[] };
                    }>;
                  };
                }>;
              };
            };
          };
        };
      };
    };

    const scrollbox = element.props.children;
    const content = scrollbox?.props?.children;
    const thinkingBlock = content?.props?.children?.[0];
    const title = thinkingBlock?.props?.children?.[0];
    const contentText = thinkingBlock?.props?.children?.[1];

    expect(title?.props?.fg).toBe("#8aa0a8");
    expect(title?.props?.children).toEqual(["v", " ", "Thinking"]);
    expect(contentText?.props?.fg).toBe("#9aaab0");

    thinkingBlock?.props?.onMouseUp?.();
    expect(onToggleEntry).toHaveBeenCalledWith(0);
  });
});
