/**
 * 表示一次 Ctrl+C 判定后的结果。
 */
export interface ExitAttempt {
  lastPressedAt: number;
  shouldExit: boolean;
  message: string;
}

/**
 * 要求在时间窗口内连续按两次 Ctrl+C 才真正退出。
 */
export function registerCtrlCPress(
  previousPressedAt: number | undefined,
  now: number,
  windowMs: number,
): ExitAttempt {
  if (
    previousPressedAt != null &&
    now - previousPressedAt <= windowMs
  ) {
    return {
      lastPressedAt: now,
      shouldExit: true,
      message: "退出中",
    };
  }

  return {
    lastPressedAt: now,
    shouldExit: false,
    message: "再按一次 Ctrl+C 退出",
  };
}
