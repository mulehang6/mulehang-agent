import type { RuntimeSummaryState } from "../app-state";

/**
 * 渲染会话页右侧状态栏。
 */
export function Sidebar(props: {
  title: string;
  runtime: RuntimeSummaryState;
  providerLabel: string;
  modelLabel: string;
  hasProvider: boolean;
  mode: "full" | "compact";
}) {
  return (
    <box
      style={{
        width: 32,
        flexDirection: "column",
        padding: props.mode === "compact" ? 1 : 2,
        gap: props.mode === "compact" ? 0 : 1,
        backgroundColor: "#262626",
      }}
    >
      <text fg="#f0f0f0">
        {props.title}
      </text>

      <text fg="#f0f0f0">
        Context
      </text>
      <text fg="#9a9a9a">mode: {props.runtime.mode}</text>
      <text fg="#9a9a9a">phase: {props.runtime.phase}</text>
      {props.mode === "full" ? (
        <>
          <text fg="#9a9a9a">session: {props.runtime.sessionId ?? "-"}</text>
          <text fg="#9a9a9a">request: {props.runtime.requestId ?? "-"}</text>
          <text fg="#9a9a9a">detail: {props.runtime.detail ?? "-"}</text>
        </>
      ) : null}

      <text fg="#f0f0f0">
        Provider
      </text>
      <text fg={props.hasProvider ? "#9fd3c7" : "#ff8b94"}>
        {props.hasProvider ? "Configured" : "Missing"}
      </text>
      <text fg="#9a9a9a">{props.providerLabel}</text>
      <text fg="#9a9a9a">{props.modelLabel}</text>

      {props.mode === "full" ? (
        <>
          <text fg="#f0f0f0">
            MCP
          </text>
          <text fg="#9a9a9a">CLI host only</text>

          <text fg="#f0f0f0">
            LSP
          </text>
          <text fg="#9a9a9a">Not connected</text>
        </>
      ) : null}
    </box>
  );
}
