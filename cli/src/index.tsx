import { createCliRenderer } from "@opentui/core";
import { createRoot } from "@opentui/react";

import { App } from "./app";

/**
 * 启动最小 OpenTUI renderer，并把运行时会话挂载到终端界面上。
 */
const renderer = await createCliRenderer({
  exitOnCtrlC: true,
});

createRoot(renderer).render(<App />);
