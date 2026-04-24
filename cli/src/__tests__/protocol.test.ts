import { describe, expect, test } from "bun:test";

import {
  parseRuntimeCliOutbound,
  serializeRuntimeCliInbound,
  type RuntimeCliOutboundMessage,
} from "../protocol";

describe("protocol", () => {
  test("serializes run request with stable type field", () => {
    const encoded = serializeRuntimeCliInbound({
      type: "run",
      prompt: "hello",
    });

    expect(JSON.parse(encoded)).toEqual({
      type: "run",
      prompt: "hello",
    });
  });

  test("parses status event result and failure messages", () => {
    const lines = [
      JSON.stringify({
        type: "status",
        status: "session.started",
        sessionId: "session-1",
        requestId: "request-1",
        mode: "demo",
      }),
      JSON.stringify({
        type: "event",
        sessionId: "session-1",
        requestId: "request-1",
        event: {
          message: "runtime.cli.demo",
          payload: "hello",
        },
      }),
      JSON.stringify({
        type: "result",
        sessionId: "session-1",
        requestId: "request-1",
        output: "echo:hello",
        mode: "demo",
      }),
      JSON.stringify({
        type: "failure",
        sessionId: "session-1",
        requestId: "request-1",
        kind: "agent",
        message: "agent failed",
        details: {
          source: "runtime-default",
          providerType: "OPENAI_COMPATIBLE",
          baseUrl: "https://openrouter.ai/api/v1",
          modelId: "openai/gpt-oss-120b:free",
          apiKeyPresent: true,
        },
      }),
    ];

    const messages = lines.map((line) => parseRuntimeCliOutbound(line));

    expect(messages).toEqual<RuntimeCliOutboundMessage[]>([
      {
        type: "status",
        status: "session.started",
        sessionId: "session-1",
        requestId: "request-1",
        mode: "demo",
      },
      {
        type: "event",
        sessionId: "session-1",
        requestId: "request-1",
        event: {
          message: "runtime.cli.demo",
          payload: "hello",
        },
      },
      {
        type: "result",
        sessionId: "session-1",
        requestId: "request-1",
        output: "echo:hello",
        mode: "demo",
      },
      {
        type: "failure",
        sessionId: "session-1",
        requestId: "request-1",
        kind: "agent",
        message: "agent failed",
        details: {
          source: "runtime-default",
          providerType: "OPENAI_COMPATIBLE",
          baseUrl: "https://openrouter.ai/api/v1",
          modelId: "openai/gpt-oss-120b:free",
          apiKeyPresent: true,
        },
      },
    ]);
  });
});
