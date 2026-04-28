import type { InputProps } from "@opentui/react";

import type { CommandPaletteState } from "../app-state";
import { Composer } from "../components/Composer";
import type { WelcomeLayout } from "../layout";

/**
 * 渲染参考kilo风格的欢迎页。
 */
export function WelcomeScreen(props: {
  draft: string;
  commandPalette: CommandPaletteState;
  onInput: (value: string) => void;
  onSubmit: NonNullable<InputProps["onSubmit"]>;
  statusText: string;
  footerText: string;
  layout: WelcomeLayout;
}) {
  return (
    <>
      <box
        style={{
          width: "100%",
          flexGrow: 1,
          minHeight: 0,
          flexDirection: "column",
          alignItems: "center",
          backgroundColor: "#1f1f1f",
          paddingLeft: props.layout.horizontalPadding,
          paddingRight: props.layout.horizontalPadding,
        }}
      >
        <box style={{ flexGrow: 1, minHeight: 0 }} />
        <box style={{ height: props.layout.topSpacerHeight, minHeight: 0, flexShrink: 1 }} />

        {props.layout.showLogo ? (
          <box style={{ flexShrink: 0 }}>
            <ascii-font text="MuleHang" font="block" color="#DA7958" />
          </box>
        ) : null}

        <box style={{ height: 1, minHeight: 0, flexShrink: 1 }} />

        <box
          style={{
            width: "100%",
            maxWidth: props.layout.promptMaxWidth,
            paddingTop: props.layout.promptPaddingTop,
            flexShrink: 0,
          }}
        >
          <Composer
            draft={props.draft}
            placeholder="Ask anything..."
            onInput={props.onInput}
            onSubmit={props.onSubmit}
            commandPalette={props.commandPalette}
            compact={false}
            footerText=""
            helperText=""
          />
        </box>

        <box
          style={{
            width: "100%",
            maxWidth: props.layout.promptMaxWidth,
            height: props.layout.tipHeight,
            minHeight: 0,
            alignItems: "center",
            paddingTop: props.layout.tipPaddingTop,
            flexShrink: 1,
          }}
        >
          {props.layout.showTip ? (
            <text fg="#f7d154">● Tip Start a message with / to open the command palette.</text>
          ) : null}
        </box>

        <box style={{ flexGrow: 1, minHeight: 0 }} />
      </box>

      <box style={{ width: "100%", flexShrink: 0, backgroundColor: "#1f1f1f" }}>
        <box
          style={{
            width: "100%",
            paddingTop: props.layout.footerPaddingY,
            paddingBottom: props.layout.footerPaddingY,
            paddingLeft: props.layout.footerPaddingX,
            paddingRight: props.layout.footerPaddingX,
            flexDirection: "row",
            gap: 2,
          }}
        >
          <text fg="#8f8f8f">{props.statusText}</text>
          <box style={{ flexGrow: 1 }} />
          <text fg="#8f8f8f">{props.footerText}</text>
        </box>
      </box>
    </>
  );
}
