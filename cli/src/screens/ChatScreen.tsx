import type { InputProps } from "@opentui/react";

import type { AppState } from "../app-state";
import { Composer } from "../components/Composer";
import { Sidebar } from "../components/Sidebar";
import { TranscriptView } from "../components/TranscriptView";
import type { ChatLayout } from "../layout";

/**
 * 渲染参考kilo风格的会话页。
 */
export function ChatScreen(props: {
  state: AppState;
  draft: string;
  title: string;
  providerLabel: string;
  modelLabel: string;
  hasProvider: boolean;
  footerText: string;
  layout: ChatLayout;
  onInput: (value: string) => void;
  onSubmit: NonNullable<InputProps["onSubmit"]>;
  onToggleTranscriptEntry: (entryIndex: number) => void;
}) {
  return (
    <box
      style={{
        width: "100%",
        height: "100%",
        flexDirection: "row",
        backgroundColor: "#1f1f1f",
      }}
    >
      <box
        style={{
          flexGrow: 1,
          minHeight: 0,
          flexDirection: "column",
          padding: props.layout.padding,
          gap: props.layout.gap,
        }}
      >
        <TranscriptView
          entries={props.state.transcript}
          onToggleEntry={props.onToggleTranscriptEntry}
        />

        <Composer
          draft={props.draft}
          placeholder="Send a prompt to runtime..."
          onInput={props.onInput}
          onSubmit={props.onSubmit}
          commandPalette={props.state.commandPalette}
          compact={props.layout.density !== "spacious"}
          footerText={props.footerText}
          helperText={props.layout.showHelperText ? "tab agents   ctrl+p commands" : ""}
        />
      </box>

      <Sidebar
        title={props.title}
        runtime={props.state.runtime}
        providerLabel={props.providerLabel}
        modelLabel={props.modelLabel}
        hasProvider={props.hasProvider}
        mode={props.layout.sidebarMode}
      />
    </box>
  );
}
