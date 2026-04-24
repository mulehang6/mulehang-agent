import { afterEach, describe, expect, test } from "bun:test";
import { join } from "node:path";

import { RuntimeProcessClient } from "../runtime-process";
import type { RuntimeCliOutboundMessage } from "../protocol";

describe("runtime process client", () => {
  let client: RuntimeProcessClient | undefined;

  afterEach(() => {
    client?.dispose();
    client = undefined;
  });

  test("starts a child process and streams parsed runtime messages", async () => {
    const received: RuntimeCliOutboundMessage[] = [];
    const fixturePath = join(import.meta.dir, "fixtures", "mock-runtime.ts");

    client = new RuntimeProcessClient({
      command: "bun",
      args: [fixturePath],
      cwd: process.cwd(),
    });

    const done = new Promise<RuntimeCliOutboundMessage[]>((resolve) => {
      client!.onMessage((message) => {
        received.push(message);
        if (received.length === 3) {
          resolve(received);
        }
      });
    });

    await client.start();
    client.send({
      type: "run",
      prompt: "hello",
    });

    const messages = await done;

    expect(messages.map((message) => message.type)).toEqual([
      "status",
      "event",
      "result",
    ]);
    expect(messages[2]).toEqual({
      type: "result",
      sessionId: "session-fixture",
      requestId: "request-fixture",
      output: "echo:hello",
      mode: "demo",
    });
  });
});
