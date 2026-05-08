process.stdin.setEncoding("utf8");

export {};

let buffer = "";

/**
 * 模拟 runtime 把诊断日志输出到标准错误，同时通过标准输出继续发送协议消息。
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
    process.stderr.write(
      "2026-05-06 20:18:00.000 INFO runtime - cli host started\n",
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
