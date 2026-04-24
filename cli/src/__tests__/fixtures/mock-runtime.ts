process.stdin.setEncoding("utf8");

let buffer = "";

/**
 * 按最小 NDJSON 协议模拟 runtime 子进程，供 CLI 子进程桥测试使用。
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
      `${JSON.stringify({
        type: "status",
        status: "session.started",
        sessionId: "session-fixture",
        requestId: "request-fixture",
        mode: "demo",
      })}\n`,
    );
    process.stdout.write(
      `${JSON.stringify({
        type: "event",
        sessionId: "session-fixture",
        requestId: "request-fixture",
        event: {
          message: "runtime.cli.demo",
          payload: request.prompt,
        },
      })}\n`,
    );
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
