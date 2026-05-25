import { spawn, spawnSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash, randomBytes } from "node:crypto";
import { createServer } from "node:net";

/**
 * 表示共享本地 runtime server 的元信息与连接参数。
 */
export interface RuntimeServerConnection {
  pid: number;
  port: number;
  baseUrl: string;
  token?: string;
  startedAt: string;
  protocolVersion: string;
  serverVersion: string;
  authMode: string;
  service: string;
  runtimeInstallationId: string;
  providerLabel?: string;
  modelLabel?: string;
  reasoningEffort?: string;
}

/**
 * 表示用于启动本地 runtime server 进程的最小命令规格。
 */
export interface RuntimeServerLaunchSpec {
  command: string;
  args: string[];
  cwd: string;
  env: Record<string, string>;
}

/**
 * 表示 shared runtime server manager 的可调参数。
 */
export interface RuntimeServerManagerOptions {
  projectRoot: string;
  stateFilePath: string;
  expectedProtocolVersion: string;
  startupTimeoutMs: number;
  pollIntervalMs: number;
}

/**
 * 表示 `/meta` 探针返回的最小宿主元信息。
 */
export interface RuntimeServerMetadata {
  service: string;
  protocolVersion: string;
  serverVersion: string;
  authMode: string;
  providerLabel?: string;
  modelLabel?: string;
  reasoningEffort?: string;
}

/**
 * 表示启动或探测 server 所需的底层依赖，便于测试替换。
 */
export interface RuntimeServerManagerDeps {
  ensureServerInstalled(projectRoot: string): string;
  resolveJavaCommand(): string;
  readStateFile(path: string): string | undefined;
  writeStateFile(path: string, contents: string): void;
  removeStateFile(path: string): void;
  ensureStateDirectory(path: string): void;
  pickFreePort(): Promise<number>;
  randomToken(): string;
  spawnServer(spec: RuntimeServerLaunchSpec): { pid: number };
  getRuntimeInstallationId(projectRoot: string): string;
  probeServer(input: {
    baseUrl: string;
    token?: string;
    expectedProtocolVersion: string;
  }): Promise<RuntimeServerMetadata | null>;
  now(): string;
  sleep(ms: number): Promise<void>;
}

interface RuntimeServerStateRecord {
  pid: number;
  port: number;
  baseUrl: string;
  token?: string;
  startedAt: string;
  protocolVersion: string;
  serverVersion: string;
  authMode: string;
  runtimeInstallationId?: string;
}

/**
 * 管理 CLI 复用的共享本地 runtime HTTP server。
 */
export class RuntimeServerManager {
  private currentServerPromise?: Promise<RuntimeServerConnection>;

  constructor(
    private readonly options: RuntimeServerManagerOptions,
    private readonly deps: RuntimeServerManagerDeps = defaultRuntimeServerManagerDeps,
  ) {}

  /**
   * 返回当前可用的共享 server；存在健康实例时复用，否则启动新实例。
   */
  async getServer(): Promise<RuntimeServerConnection> {
    if (this.currentServerPromise == null) {
      this.currentServerPromise = this.resolveServer();
    }
    return this.currentServerPromise;
  }

  /**
   * 清理本地缓存的 server 解析结果，供后续重新探测。
   */
  reset(): void {
    this.currentServerPromise = undefined;
  }

  private async resolveServer(): Promise<RuntimeServerConnection> {
    const existing = await this.tryReuseExistingServer();
    if (existing != null) {
      return existing;
    }
    return this.startServer();
  }

  private async tryReuseExistingServer(): Promise<RuntimeServerConnection | undefined> {
    const encoded = this.deps.readStateFile(this.options.stateFilePath);
    if (encoded == null) {
      return undefined;
    }

    const record = parseServerStateRecord(encoded);
    if (record == null) {
      this.deps.removeStateFile(this.options.stateFilePath);
      return undefined;
    }
    const runtimeInstallationId = this.deps.getRuntimeInstallationId(this.options.projectRoot);
    if (record.runtimeInstallationId !== runtimeInstallationId) {
      this.deps.removeStateFile(this.options.stateFilePath);
      return undefined;
    }

    const metadata = await this.deps.probeServer({
      baseUrl: record.baseUrl,
      token: record.token,
      expectedProtocolVersion: this.options.expectedProtocolVersion,
    });
    if (metadata == null) {
      this.deps.removeStateFile(this.options.stateFilePath);
      return undefined;
    }

    return {
      ...record,
      ...metadata,
      runtimeInstallationId,
    };
  }

  private async startServer(): Promise<RuntimeServerConnection> {
    this.deps.ensureStateDirectory(this.options.stateFilePath);
    const runtimeInstallationId = this.deps.getRuntimeInstallationId(this.options.projectRoot);
    const distributionScriptPath = this.deps.ensureServerInstalled(this.options.projectRoot);
    const port = await this.deps.pickFreePort();
    const token = this.deps.randomToken();
    const baseUrl = `http://127.0.0.1:${port}`;
    const child = this.deps.spawnServer({
      ...createRuntimeServerLaunchSpec(
        distributionScriptPath,
        this.deps.resolveJavaCommand(),
      ),
      cwd: this.options.projectRoot,
      env: {
        MULEHANG_RUNTIME_HOST: "127.0.0.1",
        MULEHANG_RUNTIME_PORT: String(port),
        MULEHANG_RUNTIME_TOKEN: token,
      },
    });
    const metadata = await this.waitForServer({
      baseUrl,
      token,
    });
    const record: RuntimeServerStateRecord = {
      pid: child.pid,
      port,
      baseUrl,
      token,
      startedAt: this.deps.now(),
      protocolVersion: metadata.protocolVersion,
      serverVersion: metadata.serverVersion,
      authMode: metadata.authMode,
      runtimeInstallationId,
    };
    this.deps.writeStateFile(
      this.options.stateFilePath,
      JSON.stringify(record, null, 2),
    );
    return {
      ...record,
      ...metadata,
      runtimeInstallationId,
    };
  }

  private async waitForServer(input: {
    baseUrl: string;
    token?: string;
  }): Promise<RuntimeServerMetadata> {
    const deadline = Date.now() + this.options.startupTimeoutMs;
    while (Date.now() <= deadline) {
      const metadata = await this.deps.probeServer({
        ...input,
        expectedProtocolVersion: this.options.expectedProtocolVersion,
      });
      if (metadata != null) {
        return metadata;
      }
      await this.deps.sleep(this.options.pollIntervalMs);
    }
    throw new Error("Timed out waiting for shared runtime server to become healthy.");
  }
}

/**
 * 返回 CLI 仓库根目录。
 */
export function resolveProjectRoot(): string {
  const currentFile = fileURLToPath(import.meta.url);
  return resolve(dirname(currentFile), "..", "..");
}

/**
 * 返回默认 shared runtime server 状态文件路径。
 */
export function resolveDefaultRuntimeStateFilePath(): string {
  const localAppData = process.env.LOCALAPPDATA
    ?? join(process.env.USERPROFILE ?? resolveProjectRoot(), "AppData", "Local");
  return join(localAppData, "mulehang-agent", "runtime-server.json");
}

/**
 * 返回默认 server manager 配置。
 */
export function createDefaultRuntimeServerManagerOptions(
  projectRoot: string = resolveProjectRoot(),
): RuntimeServerManagerOptions {
  return {
    projectRoot,
    stateFilePath: resolveDefaultRuntimeStateFilePath(),
    expectedProtocolVersion: "2026-05-06",
    startupTimeoutMs: 15_000,
    pollIntervalMs: 200,
  };
}

/**
 * 确保 runtime HTTP server 的本地分发脚本已生成。
 */
export function ensureRuntimeServerInstalled(projectRoot: string): string {
  const scriptPath = join(
    projectRoot,
    "runtime",
    "build",
    "install",
    "runtime",
    "bin",
    "runtime.bat",
  );
  const result = spawnSync(
    process.env.ComSpec ?? "cmd.exe",
    ["/c", "gradlew.bat", ":runtime:installDist", "--quiet"],
    {
      cwd: projectRoot,
      stdio: "inherit",
    },
  ).status;
  if (result !== 0 || !existsSync(scriptPath)) {
    throw new Error("Failed to install runtime HTTP server distribution.");
  }
  return scriptPath;
}

/**
 * 解析 shared runtime server 的真实启动命令。
 * Windows 下避免直接运行 gradle 生成的 .bat 包装脚本，防止 classpath 过长导致 cmd 退出。
 */
export function createRuntimeServerLaunchSpec(
  distributionScriptPath: string,
  javaCommand: string,
): Pick<RuntimeServerLaunchSpec, "command" | "args"> {
  return {
    command: javaCommand,
    args: [
      "-classpath",
      resolveRuntimeClasspath(distributionScriptPath),
      "com.agent.runtime.server.RuntimeHttpServerKt",
    ],
  };
}

const defaultRuntimeServerManagerDeps: RuntimeServerManagerDeps = {
  ensureServerInstalled: ensureRuntimeServerInstalled,
  resolveJavaCommand() {
    const javaHome = process.env.JAVA_HOME?.replaceAll("\"", "").trim();
    if (javaHome != null && javaHome.length > 0) {
      const javaFromHome = join(javaHome, "bin", "java.exe");
      if (existsSync(javaFromHome)) {
        return javaFromHome;
      }
    }
    return "java.exe";
  },
  readStateFile(path) {
    if (!existsSync(path)) {
      return undefined;
    }
    return readFileSync(path, "utf8");
  },
  writeStateFile(path, contents) {
    writeFileSync(path, contents, "utf8");
  },
  removeStateFile(path) {
    if (existsSync(path)) {
      rmSync(path, { force: true });
    }
  },
  ensureStateDirectory(path) {
    mkdirSync(dirname(path), { recursive: true });
  },
  async pickFreePort() {
    return new Promise<number>((resolvePort, rejectPort) => {
      const server = createServer();
      server.listen(0, "127.0.0.1", () => {
        const address = server.address();
        if (address == null || typeof address === "string") {
          server.close();
          rejectPort(new Error("Failed to allocate a local runtime server port."));
          return;
        }
        const { port } = address;
        server.close(() => resolvePort(port));
      });
      server.once("error", rejectPort);
    });
  },
  randomToken() {
    return randomBytes(16).toString("hex");
  },
  getRuntimeInstallationId: createRuntimeInstallationId,
  spawnServer(spec) {
    const child = spawn(spec.command, spec.args, {
      cwd: spec.cwd,
      env: {
        ...process.env,
        ...spec.env,
      },
      detached: true,
      stdio: "ignore",
      windowsHide: true,
    });
    child.unref();
    return {
      pid: child.pid ?? 0,
    };
  },
  async probeServer({ baseUrl, token, expectedProtocolVersion }) {
    try {
      const response = await fetch(`${baseUrl}/meta`, {
        headers: token == null
          ? undefined
          : {
              authorization: `Bearer ${token}`,
            },
      });
      if (!response.ok) {
        return null;
      }
      const payload = await response.json() as {
        code: number;
        data: RuntimeServerMetadata;
      };
      if (
        payload.code !== 1
        || payload.data.protocolVersion !== expectedProtocolVersion
      ) {
        return null;
      }
      return payload.data;
    } catch {
      return null;
    }
  },
  now() {
    return new Date().toISOString();
  },
  sleep(ms) {
    return new Promise((resolveSleep) => {
      setTimeout(resolveSleep, ms);
    });
  },
};

/**
 * 基于 runtime 源码和构建脚本生成本地安装指纹，用于避免复用旧构建产物启动的 server。
 */
export function createRuntimeInstallationId(projectRoot: string): string {
  const hash = createHash("sha256");
  for (const filePath of listRuntimeInstallationInputs(projectRoot)) {
    const stat = statSync(filePath);
    hash.update(toPortableRelativePath(projectRoot, filePath));
    hash.update("\0");
    hash.update(String(stat.size));
    hash.update("\0");
    hash.update(String(Math.trunc(stat.mtimeMs)));
    hash.update("\0");
  }
  return hash.digest("hex");
}

function parseServerStateRecord(encoded: string): RuntimeServerStateRecord | undefined {
  try {
    return JSON.parse(encoded) as RuntimeServerStateRecord;
  } catch {
    return undefined;
  }
}

function resolveRuntimeClasspath(distributionScriptPath: string): string {
  return distributionScriptPath
    .replaceAll("\\", "/")
    .replace(/\/bin\/[^/]+$/, "/lib/*");
}

function listRuntimeInstallationInputs(projectRoot: string): string[] {
  const inputs = [
    join(projectRoot, "build.gradle.kts"),
    join(projectRoot, "settings.gradle.kts"),
    join(projectRoot, "gradle.properties"),
    join(projectRoot, "runtime", "build.gradle.kts"),
  ].filter(existsSync);
  const sourceRoot = join(projectRoot, "runtime", "src", "main");
  if (existsSync(sourceRoot)) {
    inputs.push(...listFilesRecursively(sourceRoot));
  }
  return inputs.sort((left, right) => left.localeCompare(right));
}

function listFilesRecursively(directoryPath: string): string[] {
  return readdirSync(directoryPath, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = join(directoryPath, entry.name);
    return entry.isDirectory() ? listFilesRecursively(entryPath) : [entryPath];
  });
}

function toPortableRelativePath(projectRoot: string, filePath: string): string {
  return filePath
    .slice(projectRoot.length)
    .replace(/^[/\\]+/, "")
    .replaceAll("\\", "/");
}
