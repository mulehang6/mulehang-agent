import type { CommandItem } from "../app-state";

/**
 * 在输入框上方显示 `/` 命令候选项。
 */
export function CommandPanel(props: {
  items: CommandItem[];
  selectedIndex: number;
}) {
  if (props.items.length === 0) {
    return (
      <box
        borderStyle="single"
        style={{ padding: 1, flexDirection: "column", backgroundColor: "#2a2a2a" }}
      >
        <text fg="#ff8b94">No matching commands</text>
      </box>
    );
  }

  return (
    <box
      borderStyle="single"
      style={{
        padding: 1,
        flexDirection: "column",
        backgroundColor: "#2a2a2a",
      }}
    >
      {props.items.map((item, index) => {
        const selected = index === props.selectedIndex;
        return (
          <box
            key={item.name}
            style={{
              flexDirection: "row",
              justifyContent: "space-between",
              paddingLeft: 1,
              paddingRight: 1,
              backgroundColor: selected ? "#1d9bf0" : "transparent",
            }}
          >
            <box style={{ flexDirection: "row", gap: 1 }}>
              <text fg={selected ? "#f8fbff" : "#5f6b76"}>{selected ? "›" : " "}</text>
              <text fg={selected ? "#f8fbff" : "#f2f2f2"}>{item.name}</text>
            </box>
            <text fg={selected ? "#f8fbff" : "#8b8b8b"}>{item.description}</text>
          </box>
        );
      })}
    </box>
  );
}
