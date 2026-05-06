import { afterEach, describe, expect, test } from "bun:test";
import { join } from "node:path";

import {
  ensureRuntimeHostInstalled,
  RuntimeProcessClient,
} from "../runtime-process";
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

  test("refreshes runtime host distribution even when launcher script already exists", () => {
    const projectRoot = "D:\\repo";
    const expectedScriptPath = join(
      projectRoot,
      "runtime",
      "build",
      "cli-host",
      "bin",
      "runtime-cli-host.bat",
    );
    const installCalls: string[] = [];

    const scriptPath = ensureRuntimeHostInstalled(projectRoot, {
      existsSync(path) {
        return path === expectedScriptPath;
      },
      install(root) {
        installCalls.push(root);
        return 0;
      },
    });

    expect(scriptPath).toBe(expectedScriptPath);
    expect(installCalls).toEqual([projectRoot]);
  });

  test("ignores non-json stdout noise and only emits protocol messages", async () => {
    const received: RuntimeCliOutboundMessage[] = [];
    const errors: string[] = [];
    const fixturePath = join(
      import.meta.dir,
      "fixtures",
      "mock-runtime-with-stdout-noise.ts",
    );

    client = new RuntimeProcessClient({
      command: "bun",
      args: [fixturePath],
      cwd: process.cwd(),
    });

    const done = new Promise<RuntimeCliOutboundMessage>((resolve) => {
      client!.onMessage((message) => {
        received.push(message);
        resolve(message);
      });
    });

    client.onError((error) => {
      errors.push(error);
    });

    await client.start();
    client.send({
      type: "run",
      prompt: "hello",
    });

    const message = await done;

    expect(message).toEqual({
      type: "result",
      sessionId: "session-fixture",
      requestId: "request-fixture",
      output: "echo:hello",
      mode: "demo",
    });
    expect(received).toHaveLength(1);
    expect(errors).toEqual([]);
  });

  test("forwards stderr diagnostics without turning them into protocol failures", async () => {
    const received: RuntimeCliOutboundMessage[] = [];
    const errors: string[] = [];
    const diagnostics: string[] = [];
    const fixturePath = join(
      import.meta.dir,
      "fixtures",
      "mock-runtime-with-stderr-logs.ts",
    );

    client = new RuntimeProcessClient(
      {
        command: "bun",
        args: [fixturePath],
        cwd: process.cwd(),
      },
      {
        writeDiagnostic(chunk) {
          diagnostics.push(chunk);
        },
      },
    );

    const done = new Promise<RuntimeCliOutboundMessage>((resolve) => {
      client!.onMessage((message) => {
        received.push(message);
        resolve(message);
      });
    });

    client.onError((error) => {
      errors.push(error);
    });

    await client.start();
    client.send({
      type: "run",
      prompt: "hello",
    });

    const message = await done;

    expect(message).toEqual({
      type: "result",
      sessionId: "session-fixture",
      requestId: "request-fixture",
      output: "echo:hello",
      mode: "demo",
    });
    expect(received).toHaveLength(1);
    expect(errors).toEqual([]);
    expect(diagnostics).toEqual([
      "2026-05-06 20:18:00.000 INFO runtime - cli host started\n",
    ]);
  });
});
