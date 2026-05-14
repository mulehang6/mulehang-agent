import { describe, expect, mock, test } from "bun:test";
import { TranscriptView } from "../components/TranscriptView";

describe("transcript view", () => {
  test("keeps scroll support but hides visible scrollbars", () => {
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
              gap?: number;
              flexDirection?: string;
              verticalScrollbarOptions?: {
                visible?: boolean;
                width?: number;
              };
              horizontalScrollbarOptions?: {
                visible?: boolean;
                height?: number;
              };
            };
            children?: {
              props?: {
                style?: {
                  gap?: number;
                  flexDirection?: string;
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
      verticalScrollbarOptions: {
        visible: false,
        width: 0,
      },
      horizontalScrollbarOptions: {
        visible: false,
        height: 0,
      },
    });
    expect(content?.props?.style).toMatchObject({
      gap: 1,
    });
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

  test("renders assistant entries with markdown and enables streaming on the latest assistant block", () => {
    const element = TranscriptView({
      entries: [
        {
          kind: "assistant",
          text: "## Summary\n\n- first item",
        },
      ],
      isStreamingOutput: true,
    }) as {
      props: {
        children?: {
          props?: {
            children?: {
              props?: {
                children?: Array<{
                  type?: string;
                  props?: {
                    content?: string;
                    streaming?: boolean;
                  };
                }>;
              };
            };
          };
        };
      };
    };

    const markdownBlock =
      element.props.children?.props?.children?.props?.children?.[0];

    expect(markdownBlock?.type).toBe("markdown");
    expect(markdownBlock?.props?.content).toBe("## Summary\n\n- first item");
    expect(markdownBlock?.props?.streaming).toBe(true);
  });

  test("renders result entries with markdown and keeps final output non-streaming", () => {
    const element = TranscriptView({
      entries: [
        {
          kind: "result",
          text: "| name | value |\n| --- | --- |\n| foo | bar |",
        },
      ],
      isStreamingOutput: true,
    }) as {
      props: {
        children?: {
          props?: {
            children?: {
              props?: {
                children?: Array<{
                  type?: string;
                  props?: {
                    content?: string;
                    streaming?: boolean;
                  };
                }>;
              };
            };
          };
        };
      };
    };

    const markdownBlock =
      element.props.children?.props?.children?.props?.children?.[0];

    expect(markdownBlock?.type).toBe("markdown");
    expect(markdownBlock?.props?.content).toBe(
      "| name | value |\n| --- | --- |\n| foo | bar |",
    );
    expect(markdownBlock?.props?.streaming).toBe(false);
  });

  test("renders tool entries as collapsible cards with subtitle and args", () => {
    const onToggleEntry = mock();
    const element = TranscriptView({
      entries: [
        {
          kind: "tool",
          text: "",
          title: "Called __read_file__",
          subtitle: "docs/spec.md",
          args: ["encoding=utf-8"],
          expanded: false,
          toolCallId: "tool-call-1",
          toolName: "__read_file__",
          status: "completed",
          input: {
            filePath: "docs/spec.md",
          },
          output: "spec body",
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

    const toolBlock =
      element.props.children?.props?.children?.props?.children?.[0];
    const header = toolBlock?.props?.children?.[0];

    expect(header?.props?.fg).toBe("#7dcfff");
    expect(header?.props?.children).toBe(
      "> Called __read_file__ docs/spec.md [encoding=utf-8]",
    );

    toolBlock?.props?.onMouseUp?.();
    expect(onToggleEntry).toHaveBeenCalledWith(0);
  });
});
