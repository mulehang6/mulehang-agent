/**
 * 表示基于终端高度得到的布局密度。
 */
export type ViewportDensity = "spacious" | "compact" | "minimal";

/**
 * 表示欢迎页在当前高度下应该采用的布局策略。
 */
export interface WelcomeLayout {
  density: ViewportDensity;
  showLogo: boolean;
  showTip: boolean;
  horizontalPadding: number;
  promptMaxWidth: number;
  topSpacerHeight: number;
  promptPaddingTop: number;
  tipHeight: number;
  tipPaddingTop: number;
  footerPaddingX: number;
  footerPaddingY: number;
}

/**
 * 表示聊天页在当前高度下应该采用的布局策略。
 */
export interface ChatLayout {
  density: ViewportDensity;
  padding: number;
  gap: number;
  showHelperText: boolean;
  sidebarMode: "full" | "compact";
}

/**
 * 根据终端高度决定当前 UI 的密度级别。
 */
export function resolveViewportDensity(height: number): ViewportDensity {
  if (height <= 18) {
    return "minimal";
  }

  if (height <= 26) {
    return "compact";
  }

  return "spacious";
}

/**
 * 解析欢迎页布局；参考 kilo 的首页骨架，优先压缩上下留白和低优先级信息，
 * 再考虑裁掉装饰内容，确保 Logo 与输入框在矮屏中仍然保持可见。
 */
export function resolveWelcomeLayout(height: number): WelcomeLayout {
  const density = resolveViewportDensity(height);

  switch (density) {
    case "minimal":
      return {
        density,
        showLogo: true,
        showTip: false,
        horizontalPadding: 1,
        promptMaxWidth: 75,
        topSpacerHeight: 1,
        promptPaddingTop: 1,
        tipHeight: 4,
        tipPaddingTop: 3,
        footerPaddingX: 1,
        footerPaddingY: 1,
      };
    case "compact":
      return {
        density,
        showLogo: true,
        showTip: true,
        horizontalPadding: 1,
        promptMaxWidth: 75,
        topSpacerHeight: 3,
        promptPaddingTop: 1,
        tipHeight: 4,
        tipPaddingTop: 3,
        footerPaddingX: 2,
        footerPaddingY: 1,
      };
    case "spacious":
      return {
        density,
        showLogo: true,
        showTip: true,
        horizontalPadding: 2,
        promptMaxWidth: 75,
        topSpacerHeight: 4,
        promptPaddingTop: 1,
        tipHeight: 4,
        tipPaddingTop: 3,
        footerPaddingX: 2,
        footerPaddingY: 1,
      };
  }
}

/**
 * 解析聊天页布局；矮屏时收紧内边距和侧栏信息，确保输入框完整可见。
 */
export function resolveChatLayout(height: number): ChatLayout {
  const density = resolveViewportDensity(height);

  switch (density) {
    case "minimal":
    case "compact":
      return {
        density,
        padding: 1,
        gap: 0,
        showHelperText: false,
        sidebarMode: "compact",
      };
    case "spacious":
      return {
        density,
        padding: 2,
        gap: 1,
        showHelperText: true,
        sidebarMode: "full",
      };
  }
}
