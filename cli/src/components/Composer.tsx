import type { InputProps } from "@opentui/react";

import type { CommandPaletteState } from "../app-state";
import { CommandPanel } from "./CommandPanel";

/**
 * 渲染底部输入栏和命令面板。
 */
export function Composer(props: {
  draft: string;
  placeholder: string;
  footerText: string;
  helperText: string;
  belowLeftText?: string;
  belowRightText?: string;
  compact: boolean;
  commandPalette: CommandPaletteState;
  onInput: (value: string) => void;
  onSubmit: NonNullable<InputProps["onSubmit"]>;
}) {
  const boxPadding = 1;
  const minComposerHeight = 3;
  const showFooter = Boolean(props.footerText || props.helperText);
  const showBelowHints = Boolean(props.belowLeftText || props.belowRightText);

  return (
    <box
      style={{
        flexDirection: "column",
        gap: props.compact ? 0 : 1,
        flexShrink: 0,
        minHeight: minComposerHeight,
      }}
    >
      {props.commandPalette.isOpen ? (
        <CommandPanel
          items={props.commandPalette.items}
          selectedIndex={props.commandPalette.selectedIndex}
        />
      ) : null}

      <box
        style={{
          backgroundColor: "#2d2d31",
          flexDirection: "column",
          paddingLeft: 3,
          paddingRight: boxPadding,
          paddingTop: boxPadding,
          paddingBottom: boxPadding,
          gap: props.compact ? 0 : 1,
          flexShrink: 0,
          minHeight: minComposerHeight,
        }}
      >
        <input
          value={props.draft}
          focused
          placeholder={props.placeholder}
          onInput={props.onInput}
          onSubmit={props.onSubmit}
        />

        {showFooter ? (
          <box style={{ flexDirection: "row", justifyContent: "space-between", minHeight: 1 }}>
            <text fg="#1d9bf0">{props.footerText}</text>
            {props.helperText ? <text fg="#d0d0d0">{props.helperText}</text> : <text />}
          </box>
        ) : null}
      </box>

      {showBelowHints ? (
        <box style={{ flexDirection: "row", justifyContent: "space-between", minHeight: 1 }}>
          <text fg="#d0d0d0">{props.belowLeftText ?? ""}</text>
          <text fg="#d0d0d0">{props.belowRightText ?? ""}</text>
        </box>
      ) : null}
    </box>
  );
}
