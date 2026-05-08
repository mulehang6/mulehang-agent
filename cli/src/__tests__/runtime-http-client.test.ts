import { describe, expect, mock, test } from "bun:test";

import {
  consumeSseStream,
  RuntimeHttpClient,
} from "../runtime-http-client";
import type { RuntimeMessage } from "../runtime-events";
import type { RuntimeRunRequest } from "../runtime-request";
import type { RuntimeServerConnection } from "../runtime-server-manager";

describe("runtime http client", () => {
  test("sends runtime requests to the shared local server and maps SSE frames into runtime messages", async () => {
    const connection = createConnection();
    const getServer = mock(async () => connection);
    const fetchImpl = mock(async () => new Response(
      [
        "event: status",
        'data: {"event":"status","sessionId":"session-1","requestId":"request-1","message":"run.started"}',
        "",
        "event: thinking.delta",
        'data: {"event":"thinking.delta","sessionId":"session-1","requestId":"request-1","channel":"thinking","message":"agent.reasoning.delta","delta":"first thought"}',
        "",
        "event: text.delta",
        'data: {"event":"text.delta","sessionId":"session-1","requestId":"request-1","channel":"text","message":"agent.text.delta","delta":"hello"}',
        "",
        "event: run.completed",
        'data: {"event":"run.completed","sessionId":"session-1","requestId":"request-1","output":"hello"}',
        "",
      ].join("\n"),
      {
        status: 200,
        headers: {
          "content-type": "text/event-stream",
        },
      },
    ));
    const client = new RuntimeHttpClient(
      { getServer },
      { fetch: fetchImpl },
    );
    const received: RuntimeMessage[] = [];
    client.onMessage((message) => {
      received.push(message);
    });

    await client.send(createRequest());

    expect(received).toEqual([
      {
        type: "status",
        status: "run.started",
        sessionId: "session-1",
        requestId: "request-1",
        mode: "agent",
      },
      {
        type: "event",
        sessionId: "session-1",
        requestId: "request-1",
        event: {
          message: "agent.reasoning.delta",
          channel: "thinking",
          delta: "first thought",
        },
      },
      {
        type: "event",
        sessionId: "session-1",
        requestId: "request-1",
        event: {
          message: "agent.text.delta",
          channel: "text",
          delta: "hello",
        },
      },
      {
        type: "result",
        sessionId: "session-1",
        requestId: "request-1",
        output: "hello",
        mode: "agent",
      },
    ]);
    expect(getServer).toHaveBeenCalled();
    expect(fetchImpl).toHaveBeenCalledWith("http://127.0.0.1:43125/runtime/run/stream", {
      method: "POST",
      headers: {
        authorization: "Bearer shared-token",
        "content-type": "application/json",
      },
      body: JSON.stringify(createRequest()),
      signal: expect.any(AbortSignal),
    });
  });

  test("consumes SSE streams incrementally instead of waiting for the full body", async () => {
    const received: string[] = [];
    let releaseFinalChunk = () => undefined;
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          encoder.encode(
            [
              "event: status",
              'data: {"event":"status","message":"run.started"}',
              "",
              "",
            ].join("\n"),
          ),
        );
        releaseFinalChunk = () => {
          controller.enqueue(
            encoder.encode(
              [
                "event: run.completed",
                'data: {"event":"run.completed","message":"done"}',
                "",
                "",
              ].join("\n"),
            ),
          );
          controller.close();
        };
      },
    });

    const consuming = consumeSseStream(stream, (event) => {
      received.push(event.event);
    });

    await waitFor(() => received.length === 1);
    expect(received).toEqual(["status"]);

    releaseFinalChunk();
    await consuming;

    expect(received).toEqual(["status", "run.completed"]);
  });
});

function createConnection(): RuntimeServerConnection {
  return {
    pid: 128,
    port: 43125,
    baseUrl: "http://127.0.0.1:43125",
    token: "shared-token",
    startedAt: "2026-05-06T12:00:00.000Z",
    protocolVersion: "2026-05-06",
    serverVersion: "dev",
    authMode: "token",
    service: "mulehang-agent",
    runtimeInstallationId: "runtime-current",
  };
}

function createRequest(): RuntimeRunRequest {
  return {
    sessionId: "session-1",
    prompt: "hello",
  };
}

async function waitFor(predicate: () => boolean): Promise<void> {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (predicate()) {
      return;
    }
    await new Promise((resolve) => {
      setTimeout(resolve, 1);
    });
  }
  throw new Error("Timed out waiting for SSE messages.");
}

const encoder = new TextEncoder();
