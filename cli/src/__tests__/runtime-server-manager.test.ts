import { describe, expect, mock, test } from "bun:test";

import {
  RuntimeServerManager,
  type RuntimeServerConnection,
  type RuntimeServerManagerDeps,
  type RuntimeServerManagerOptions,
} from "../runtime-server-manager";

describe("runtime server manager", () => {
  test("reuses an existing healthy shared runtime server", async () => {
    const stateFilePath = "C:/Users/test/AppData/Local/mulehang-agent/runtime-server.json";
    const existing = {
      pid: 42,
      port: 38123,
      baseUrl: "http://127.0.0.1:38123",
      token: "existing-token",
      startedAt: "2026-05-06T12:00:00.000Z",
      protocolVersion: "2026-05-06",
      serverVersion: "test",
      authMode: "token",
      runtimeInstallationId: "runtime-current",
    };
    const deps = createDeps({
      readStateFile: () => JSON.stringify(existing),
      probeServer: mock(async () => ({
        service: "mulehang-agent",
        protocolVersion: "2026-05-06",
        serverVersion: "test",
        authMode: "token",
      })),
    });
    const manager = new RuntimeServerManager(createOptions(stateFilePath), deps);

    const connection = await manager.getServer();

    expect(connection).toEqual<RuntimeServerConnection>({
      ...existing,
      service: "mulehang-agent",
    });
    expect(deps.spawnServer).not.toHaveBeenCalled();
    expect(deps.writeStateFile).not.toHaveBeenCalled();
  });

  test("starts a fresh server when the existing state belongs to an older runtime installation", async () => {
    const stateFilePath = "C:/Users/test/AppData/Local/mulehang-agent/runtime-server.json";
    const writes: Array<{ path: string; contents: string }> = [];
    const existing = {
      pid: 42,
      port: 38123,
      baseUrl: "http://127.0.0.1:38123",
      token: "existing-token",
      startedAt: "2026-05-06T12:00:00.000Z",
      protocolVersion: "2026-05-06",
      serverVersion: "test",
      authMode: "token",
      runtimeInstallationId: "runtime-old",
    };
    const deps = createDeps({
      readStateFile: () => JSON.stringify(existing),
      writeStateFile: (path, contents) => {
        writes.push({ path, contents });
      },
      probeServer: mock(async () => ({
        service: "mulehang-agent",
        protocolVersion: "2026-05-06",
        serverVersion: "dev",
        authMode: "token",
      })),
    });
    const manager = new RuntimeServerManager(createOptions(stateFilePath), deps);

    const connection = await manager.getServer();

    expect(deps.removeStateFile).toHaveBeenCalledWith(stateFilePath);
    expect(deps.spawnServer).toHaveBeenCalled();
    expect(connection.runtimeInstallationId).toBe("runtime-current");
    expect(JSON.parse(writes[0]!.contents).runtimeInstallationId).toBe("runtime-current");
  });

  test("starts a new shared runtime server and persists state when no healthy server exists", async () => {
    const stateFilePath = "C:/Users/test/AppData/Local/mulehang-agent/runtime-server.json";
    const writes: Array<{ path: string; contents: string }> = [];
    const probeServer = mock(async () => ({
      service: "mulehang-agent",
      protocolVersion: "2026-05-06",
      serverVersion: "dev",
      authMode: "token",
    }));
    const deps = createDeps({
      readStateFile: () => undefined,
      writeStateFile: (path, contents) => {
        writes.push({ path, contents });
      },
      probeServer,
    });
    const manager = new RuntimeServerManager(createOptions(stateFilePath), deps);

    const connection = await manager.getServer();

    expect(deps.ensureServerInstalled).toHaveBeenCalledWith("D:/repo");
    expect(deps.pickFreePort).toHaveBeenCalled();
    expect(deps.randomToken).toHaveBeenCalled();
    expect(deps.spawnServer).toHaveBeenCalledWith({
      command: "java.exe",
      args: [
        "-classpath",
        "C:/repo/runtime/build/install/runtime/lib/*",
        "com.agent.runtime.server.RuntimeHttpServerKt",
      ],
      cwd: "D:/repo",
      env: {
        MULEHANG_RUNTIME_HOST: "127.0.0.1",
        MULEHANG_RUNTIME_PORT: "43125",
        MULEHANG_RUNTIME_TOKEN: "generated-token",
      },
    });
    expect(connection).toEqual({
      pid: 128,
      port: 43125,
      baseUrl: "http://127.0.0.1:43125",
      token: "generated-token",
      startedAt: "2026-05-06T12:00:00.000Z",
      protocolVersion: "2026-05-06",
      serverVersion: "dev",
      authMode: "token",
      service: "mulehang-agent",
      runtimeInstallationId: "runtime-current",
    });
    expect(writes).toHaveLength(1);
    expect(writes[0]?.path).toBe(stateFilePath);
    expect(JSON.parse(writes[0]!.contents)).toEqual({
      pid: 128,
      port: 43125,
      baseUrl: "http://127.0.0.1:43125",
      token: "generated-token",
      startedAt: "2026-05-06T12:00:00.000Z",
      protocolVersion: "2026-05-06",
      serverVersion: "dev",
      authMode: "token",
      runtimeInstallationId: "runtime-current",
    });
    expect(probeServer).toHaveBeenCalledWith({
      baseUrl: "http://127.0.0.1:43125",
      token: "generated-token",
      expectedProtocolVersion: "2026-05-06",
    });
  });

  test("prefers JAVA_HOME java executable when starting a new shared runtime server", async () => {
    const deps = createDeps({
      readStateFile: () => undefined,
      probeServer: mock(async () => ({
        service: "mulehang-agent",
        protocolVersion: "2026-05-06",
        serverVersion: "dev",
        authMode: "token",
      })),
      resolveJavaCommand: mock(() => "C:/Java/jdk-21/bin/java.exe"),
    });
    const manager = new RuntimeServerManager(
      {
        ...createOptions("C:/Users/test/AppData/Local/mulehang-agent/runtime-server.json"),
        projectRoot: "D:/repo",
      },
      deps,
    );

    await manager.getServer();

    expect(deps.spawnServer).toHaveBeenCalledWith({
      command: "C:/Java/jdk-21/bin/java.exe",
      args: [
        "-classpath",
        "C:/repo/runtime/build/install/runtime/lib/*",
        "com.agent.runtime.server.RuntimeHttpServerKt",
      ],
      cwd: "D:/repo",
      env: {
        MULEHANG_RUNTIME_HOST: "127.0.0.1",
        MULEHANG_RUNTIME_PORT: "43125",
        MULEHANG_RUNTIME_TOKEN: "generated-token",
      },
    });
  });

});

function createOptions(stateFilePath: string): RuntimeServerManagerOptions {
  return {
    projectRoot: "D:/repo",
    stateFilePath,
    expectedProtocolVersion: "2026-05-06",
    startupTimeoutMs: 1_000,
    pollIntervalMs: 1,
  };
}

function createDeps(
  overrides: Partial<RuntimeServerManagerDeps> = {},
): RuntimeServerManagerDeps {
  return {
    ensureServerInstalled: mock(() => "C:/repo/runtime/build/install/runtime/bin/runtime.bat"),
    readStateFile: mock(() => undefined),
    writeStateFile: mock(() => undefined),
    removeStateFile: mock(() => undefined),
    ensureStateDirectory: mock(() => undefined),
    pickFreePort: mock(async () => 43125),
    randomToken: mock(() => "generated-token"),
    spawnServer: mock(() => ({ pid: 128 })),
    probeServer: mock(async () => null),
    getRuntimeInstallationId: mock(() => "runtime-current"),
    now: mock(() => "2026-05-06T12:00:00.000Z"),
    sleep: mock(async () => undefined),
    resolveJavaCommand: mock(() => "java.exe"),
    ...overrides,
  };
}
