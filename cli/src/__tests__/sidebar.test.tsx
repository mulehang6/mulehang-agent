import { describe, expect, test } from "bun:test";

import { Sidebar } from "../components/Sidebar";

describe("sidebar", () => {
  test("does not expose request identifiers in visible context rows", () => {
    const element = Sidebar({
      title: "你好",
      runtime: {
        phase: "completed",
        mode: "agent",
        sessionId: "session-1",
        requestId: "request-1",
        detail: "completed",
      },
      providerLabel: "runtime managed",
      modelLabel: "runtime default",
      hasProvider: true,
      mode: "full",
    });

    const textContent = collectTextContent(element);

    expect(textContent).toContain("session: session-1");
    expect(textContent).not.toContain("request:");
    expect(textContent).not.toContain("request-1");
  });
});

function collectTextContent(node: unknown): string {
  if (typeof node === "string") {
    return node;
  }

  if (Array.isArray(node)) {
    return node.map(collectTextContent).join("");
  }

  if (node != null && typeof node === "object" && "props" in node) {
    const props = (node as { props?: { children?: unknown } }).props;
    return collectTextContent(props?.children);
  }

  return "";
}
