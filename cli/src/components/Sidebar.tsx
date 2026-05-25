import type { ReactNode } from "react";

import type { RuntimeSummaryState } from "../app-state";

/**
 * 渲染贴近 kilo 信息架构的会话页右侧状态栏。
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
        width: 42,
        height: "100%",
        flexDirection: "column",
        backgroundColor: "#262626",
        paddingTop: 1,
        paddingBottom: 1,
        paddingLeft: 2,
        paddingRight: 2,
      }}
    >
      <scrollbox
        flexGrow={1}
        style={{
          verticalScrollbarOptions: {
            width: 1,
          },
        }}
      >
        <box
          style={{
            flexDirection: "column",
            flexShrink: 0,
            gap: 1,
            paddingRight: 1,
          }}
        >
          <box style={{ paddingRight: 1 }}>
            <text fg="#f0f0f0">{props.title}</text>
          </box>

          <SidebarSection title="Context">
            <SidebarMetricRow label="Total Used" value={formatMissingMetric()} />
            <SidebarMetricRow label="Context Used" value={formatMissingMetric()} />
            <SidebarMetricRow label="Cost" value={formatMissingMetric()} />
          </SidebarSection>

          <SidebarSection title="Token Usage">
            <SidebarMetricRow label="Input" value={formatMissingMetric()} />
            <SidebarMetricRow label="Output" value={formatMissingMetric()} />
            <SidebarMetricRow label="Cached" value={formatMissingMetric()} />
          </SidebarSection>

          <SidebarSection title="MCP">
            <SidebarMetricRow label="Available" value={formatAvailableMcp(props.mode)} />
          </SidebarSection>
        </box>
      </scrollbox>

      <box
        style={{
          flexShrink: 0,
          paddingTop: 1,
        }}
      >
        <text fg="#9a9a9a">Mulehang Agent</text>
      </box>
    </box>
  );
}

/**
 * 渲染一个侧栏 section，包括标题和内部行。
 */
function SidebarSection(props: {
  title: string;
  children: ReactNode;
}) {
  return (
    <box style={{ flexDirection: "column" }}>
      <text fg="#f0f0f0">{props.title}</text>
      {props.children}
    </box>
  );
}

/**
 * 渲染 kilo 风格的单行指标，左右对齐标签和值。
 */
function SidebarMetricRow(props: {
  label: string;
  value: string;
}) {
  return (
    <box
      style={{
        flexDirection: "row",
        justifyContent: "space-between",
      }}
    >
      <text fg="#9a9a9a">{props.label}</text>
      <text fg="#f0f0f0">{props.value}</text>
    </box>
  );
}

/**
 * 为尚未接通的运行时指标返回统一占位值。
 */
function formatMissingMetric(): string {
  return "-";
}

/**
 * 为可用 MCP 数量返回当前阶段的展示文案。
 */
function formatAvailableMcp(mode: "full" | "compact"): string {
  return mode === "compact" ? "-" : "CLI host only";
}
