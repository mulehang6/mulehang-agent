import { RGBA, SyntaxStyle } from "@opentui/core";

import type { TranscriptEntry } from "../app-state";

/**
 * 渲染左侧主消息流。
 */
export function TranscriptView(props: {
  entries: TranscriptEntry[];
  isStreamingOutput?: boolean;
  onToggleEntry?: (entryIndex: number) => void;
}) {
  if (props.entries.length === 0) {
    return (
      <box style={{ flexGrow: 1, minHeight: 0 }}>
        <text fg="#6a8a96">Send a prompt to start the conversation.</text>
      </box>
    );
  }

  const latestAssistantIndex = findLatestAssistantIndex(props.entries);

  return (
    <box
      style={{
        flexGrow: 1,
        minHeight: 0,
        flexDirection: "column",
        justifyContent: "flex-end",
      }}
    >
      <scrollbox
        stickyScroll
        stickyStart="bottom"
        style={{
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
        }}
      >
        <box style={{ flexDirection: "column", gap: 1 }}>
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
            ) : entry.kind === "assistant" || entry.kind === "result" ? (
              <markdown
                key={`${entry.kind}-${index}`}
                content={entry.text}
                syntaxStyle={TRANSCRIPT_MARKDOWN_STYLE}
                streaming={
                  entry.kind === "assistant" &&
                  props.isStreamingOutput === true &&
                  index === latestAssistantIndex
                }
                style={{ width: "100%" }}
              />
            ) : entry.kind === "thinking" ? (
              <box
                key={`${entry.kind}-${index}`}
                style={{
                  flexDirection: "column",
                  borderColor: "#3f5057",
                  padding: 1,
                  marginBottom: 1,
                }}
                onMouseUp={() => props.onToggleEntry?.(index)}
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
    </box>
  );
}

/**
 * 读取当前 transcript 中最后一条 assistant 消息的位置。
 */
function findLatestAssistantIndex(entries: TranscriptEntry[]): number {
  for (let index = entries.length - 1; index >= 0; index -= 1) {
    if (entries[index]?.kind === "assistant") {
      return index;
    }
  }

  return -1;
}

const TRANSCRIPT_MARKDOWN_STYLE = SyntaxStyle.fromStyles({
  default: { fg: RGBA.fromHex("#f5f5f5") },
  strong: { fg: RGBA.fromHex("#ffffff"), bold: true },
  em: { fg: RGBA.fromHex("#d4d4d4"), italic: true },
  "markup.heading": { fg: RGBA.fromHex("#7dcfff"), bold: true },
  "markup.list": { fg: RGBA.fromHex("#c0caf5") },
  "markup.quote": { fg: RGBA.fromHex("#9ece6a"), italic: true },
  "markup.raw": { fg: RGBA.fromHex("#e0af68") },
  link: { fg: RGBA.fromHex("#73daca"), underline: true },
  code: { fg: RGBA.fromHex("#e0af68") },
});

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
