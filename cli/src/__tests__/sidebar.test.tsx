import { describe, expect, test } from "bun:test";

import { Sidebar } from "../components/Sidebar";

describe("sidebar", () => {
  test("renders kilo-style sections with placeholders and no lsp block", () => {
    const element = Sidebar({
      title: "你好",
      runtime: {
        phase: "completed",
        mode: "agent",
        sessionId: "session-1",
        requestId: "request-1",
        detail: "completed",
        providerLabel: "runtime managed",
        modelLabel: "runtime default",
      },
      providerLabel: "DeepSeek",
      modelLabel: "deepseek-v4-flash",
      hasProvider: true,
      mode: "full",
    });

    const textContent = collectTextContent(element);

    expect(textContent).toContain("你好");
    expect(textContent).toContain("Context");
    expect(textContent).toContain("Total Used");
    expect(textContent).toContain("Context Used");
    expect(textContent).toContain("Cost");
    expect(textContent).toContain("Token Usage");
    expect(textContent).toContain("Input");
    expect(textContent).toContain("Output");
    expect(textContent).toContain("Cached");
    expect(textContent).toContain("MCP");
    expect(textContent).toContain("Available");
    expect(textContent).not.toContain("LSP");
    expect(textContent).not.toContain("request:");
    expect(textContent).not.toContain("request-1");
  });

  test("wraps sidebar content in a scrollbox and keeps footer outside it", () => {
    const element = Sidebar({
      title: "你好",
      runtime: {
        phase: "completed",
        mode: "agent",
        sessionId: "session-1",
        requestId: "request-1",
        detail: "completed",
        providerLabel: "runtime managed",
        modelLabel: "runtime default",
      },
      providerLabel: "DeepSeek",
      modelLabel: "deepseek-v4-flash",
      hasProvider: true,
      mode: "compact",
    }) as {
      props?: {
        style?: {
          width?: number;
          backgroundColor?: string;
        };
        children?: Array<{
          type?: string;
          props?: {
            flexGrow?: number;
            style?: {
              paddingTop?: number;
            };
            children?: unknown;
          };
        }>;
      };
    };

    expect(element.props?.style).toMatchObject({
      width: 42,
      backgroundColor: "#262626",
    });

    const scrollRegion = element.props?.children?.[0];
    const footerRegion = element.props?.children?.[1];

    expect(scrollRegion?.type).toBe("scrollbox");
    expect(scrollRegion?.props?.flexGrow).toBe(1);
    expect(collectTextContent(scrollRegion)).toContain("Context");
    expect(collectTextContent(footerRegion)).toContain("Mulehang Agent");
    expect(footerRegion?.props?.style?.paddingTop).toBe(1);
  });
});

function collectTextContent(node: unknown): string {
  if (typeof node === "string") {
    return node;
  }

  if (Array.isArray(node)) {
    return node.map(collectTextContent).join("");
  }

  if (node != null && typeof node === "object" && "type" in node && "props" in node) {
    const element = node as {
      type?: string | ((props: Record<string, unknown>) => unknown);
      props?: Record<string, unknown> & { children?: unknown };
    };
    if (typeof element.type === "function") {
      return collectTextContent(element.type(element.props ?? {}));
    }
    const props = element.props;
    return collectTextContent(props?.children);
  }

  return "";
}
