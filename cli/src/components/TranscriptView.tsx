import type { TranscriptEntry } from "../app-state";

/**
 * 渲染左侧主消息流。
 */
export function TranscriptView(props: {
  entries: TranscriptEntry[];
  onToggleEntry?: (entryIndex: number) => void;
}) {
  if (props.entries.length === 0) {
    return (
      <box style={{ flexGrow: 1, minHeight: 0 }}>
        <text fg="#6a8a96">Send a prompt to start the conversation.</text>
      </box>
    );
  }

  return (
    <scrollbox
      stickyScroll
      stickyStart="bottom"
      style={{ flexGrow: 1, minHeight: 0, height: "100%", flexDirection: "column" }}
    >
      <box style={{ flexDirection: "column", gap: 1, minHeight: "100%", justifyContent: "flex-end" }}>
        {props.entries.map((entry, index) =>
          entry.kind === "user" ? (
            <box
              key={`${entry.kind}-${index}`}
              style={{
                backgroundColor: "#2b2b2b",
                padding: 1,
                marginBottom: 1,
              }}
            >
              <text fg="#f5f5f5">{entry.text}</text>
            </box>
          ) : entry.kind === "assistant" ? (
            <text key={`${entry.kind}-${index}`} fg="#f5f5f5">
              {entry.text}
            </text>
          ) : entry.kind === "thinking" ? (
            <box
              key={`${entry.kind}-${index}`}
              style={{
                flexDirection: "column",
                borderColor: "#3f5057",
                padding: 1,
                marginBottom: 1,
              }}
              onMouseDown={() => props.onToggleEntry?.(index)}
            >
              <text fg="#8aa0a8">
                {entry.expanded === false ? ">" : "v"} {entry.title ?? "Thinking"}
              </text>
              {entry.expanded === false ? null : (
                <text fg="#9aaab0">{entry.text}</text>
              )}
            </box>
          ) : (
            <text key={`${entry.kind}-${index}`} fg={colorForEntry(entry.kind)}>
              {prefixForEntry(entry.kind)}
              {entry.text}
            </text>
          ),
        )}
      </box>
    </scrollbox>
  );
}

function prefixForEntry(kind: TranscriptEntry["kind"]): string {
  switch (kind) {
    case "status":
      return "";
    case "assistant":
      return "";
    case "event":
      return "";
    case "thinking":
      return "";
    case "result":
      return "";
    case "failure":
      return "";
    case "system":
      return "";
    case "user":
      return "";
  }
}

function colorForEntry(kind: TranscriptEntry["kind"]): string {
  switch (kind) {
    case "status":
      return "#8f8f8f";
    case "assistant":
      return "#f5f5f5";
    case "event":
      return "#1d9bf0";
    case "thinking":
      return "#9aaab0";
    case "result":
      return "#f5f5f5";
    case "failure":
      return "#ff8b94";
    case "system":
      return "#f7d154";
    case "user":
      return "#f5f5f5";
  }
}
