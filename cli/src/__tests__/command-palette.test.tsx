import { describe, expect, test } from "bun:test";

import { CommandPanel } from "../components/CommandPanel";

describe("command palette", () => {
  test("renders the selected command with strong contrast and a marker", () => {
    const element = CommandPanel({
      items: [
        { name: "/status", description: "Show runtime status" },
        { name: "/model", description: "Show provider and model" },
      ],
      selectedIndex: 0,
    }) as {
      props: {
        children: Array<{
          props: {
            style?: { backgroundColor?: string };
            children: Array<{ props?: { fg?: string; children?: string } }>;
          };
        }>;
      };
    };

    const firstRow = element.props.children[0];
    const leftGroup = firstRow.props.children[0] as {
      props?: {
        children?: Array<{ props?: { fg?: string; children?: string } }>;
      };
    };
    const description = firstRow.props.children[1] as {
      props?: { fg?: string };
    };

    expect(firstRow.props.style?.backgroundColor).toBe("#1d9bf0");
    expect(leftGroup.props?.children?.[0].props?.children).toBe("›");
    expect(leftGroup.props?.children?.[1].props?.fg).toBe("#f8fbff");
    expect(description.props?.fg).toBe("#f8fbff");
  });
});
