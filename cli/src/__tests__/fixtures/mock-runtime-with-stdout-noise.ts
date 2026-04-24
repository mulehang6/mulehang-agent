process.stdin.setEncoding("utf8");

export {};

let buffer = "";

/**
 * 模拟 runtime 在标准输出里混入日志行，但仍会产出合法协议消息。
 */
process.stdin.on("data", (chunk) => {
  buffer += chunk;

  while (true) {
    const newlineIndex = buffer.indexOf("\n");
    if (newlineIndex === -1) {
      return;
    }

    const line = buffer.slice(0, newlineIndex).trim();
    buffer = buffer.slice(newlineIndex + 1);

    if (!line) {
      continue;
    }

    const request = JSON.parse(line) as { prompt: string };
    process.stdout.write(
      "kotlin-logging: initializing... active logger factory: Slf4jLoggerFactory\n",
    );
    process.stdout.write("2026-04-24 INFO runtime booted\n");
    process.stdout.write(
      `${JSON.stringify({
        type: "result",
        sessionId: "session-fixture",
        requestId: "request-fixture",
        output: `echo:${request.prompt}`,
        mode: "demo",
      })}\n`,
    );
  }
});
