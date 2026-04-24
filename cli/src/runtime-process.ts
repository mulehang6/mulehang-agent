import { spawn, spawnSync, type ChildProcessWithoutNullStreams } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  parseRuntimeCliOutbound,
  serializeRuntimeCliInbound,
  type RuntimeCliInboundMessage,
  type RuntimeCliOutboundMessage,
} from "./protocol";

/**
 * 表示启动 runtime 子进程所需的命令信息。
 */
export interface RuntimeLaunchSpec {
  command: string;
  args: string[];
  cwd: string;
}

/**
 * 表示安装 runtime host 分发所需的最小文件与进程操作。
 */
interface RuntimeHostInstallOps {
  existsSync(path: string): boolean;
  install(projectRoot: string): number | null;
}

/**
 * 负责把独立的 runtime 子进程桥接成可发送/接收消息的客户端。
 */
export class RuntimeProcessClient {
  private readonly messageListeners = new Set<
    (message: RuntimeCliOutboundMessage) => void
  >();
  private readonly errorListeners = new Set<(error: string) => void>();
  private child?: ChildProcessWithoutNullStreams;
  private stdoutBuffer = "";

  constructor(private readonly launchSpec: RuntimeLaunchSpec) {}

  /**
   * 启动 runtime 子进程，并开始监听标准输出事件流。
   */
  async start(): Promise<void> {
    if (this.child) {
      return;
    }

    const child = spawn(this.launchSpec.command, this.launchSpec.args, {
      cwd: this.launchSpec.cwd,
      stdio: ["pipe", "pipe", "pipe"],
    });

    child.stdout.setEncoding("utf8");
    child.stdout.on("data", (chunk: string) => {
      this.handleStdoutChunk(chunk);
    });

    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk: string) => {
      this.errorListeners.forEach((listener) => listener(chunk.trim()));
    });

    this.child = child;

    await new Promise<void>((resolveStart, rejectStart) => {
      child.once("spawn", () => resolveStart());
      child.once("error", rejectStart);
    });
  }

  /**
   * 注册 runtime 出站消息监听器。
   */
  onMessage(
    listener: (message: RuntimeCliOutboundMessage) => void,
  ): () => void {
    this.messageListeners.add(listener);
    return () => {
      this.messageListeners.delete(listener);
    };
  }

  /**
   * 注册 runtime 标准错误输出监听器。
   */
  onError(listener: (error: string) => void): () => void {
    this.errorListeners.add(listener);
    return () => {
      this.errorListeners.delete(listener);
    };
  }

  /**
   * 向 runtime 子进程写入一条单行 JSON 请求。
   */
  send(message: RuntimeCliInboundMessage): void {
    if (!this.child?.stdin.writable) {
      throw new Error("Runtime process is not started.");
    }

    this.child.stdin.write(`${serializeRuntimeCliInbound(message)}\n`);
  }

  /**
   * 停止 runtime 子进程并清理本地状态。
   */
  dispose(): void {
    this.child?.kill();
    this.child = undefined;
    this.stdoutBuffer = "";
  }

  /**
   * 把 stdout 的文本块拆成协议行，并逐条派发给上层监听器。
   */
  private handleStdoutChunk(chunk: string): void {
    this.stdoutBuffer += chunk;

    while (true) {
      const newlineIndex = this.stdoutBuffer.indexOf("\n");
      if (newlineIndex === -1) {
        return;
      }

      const line = this.stdoutBuffer.slice(0, newlineIndex).trim();
      this.stdoutBuffer = this.stdoutBuffer.slice(newlineIndex + 1);

      if (!line) {
        continue;
      }

      if (!looksLikeProtocolMessage(line)) {
        continue;
      }

      try {
        const message = parseRuntimeCliOutbound(line);
        this.messageListeners.forEach((listener) => listener(message));
      } catch (error) {
        this.errorListeners.forEach((listener) =>
          listener(error instanceof Error ? error.message : String(error)),
        );
      }
    }
  }
}

/**
 * 只把形如单行 JSON 对象的 stdout 当作 runtime 协议消息处理。
 */
function looksLikeProtocolMessage(line: string): boolean {
  return line.startsWith("{");
}

/**
 * 返回 CLI 所在仓库根目录的绝对路径。
 */
export function resolveProjectRoot(): string {
  const currentFile = fileURLToPath(import.meta.url);
  return resolve(dirname(currentFile), "..", "..");
}

/**
 * 确保 runtime CLI host 的本地分发脚本已经生成。
 */
export function ensureRuntimeHostInstalled(
  projectRoot: string,
  ops: RuntimeHostInstallOps = defaultRuntimeHostInstallOps,
): string {
  const scriptPath = join(
    projectRoot,
    "runtime",
    "build",
    "cli-host",
    "bin",
    "runtime-cli-host.bat",
  );

  const result = ops.install(projectRoot);

  if (result !== 0 || !ops.existsSync(scriptPath)) {
    throw new Error("Failed to install runtime CLI host distribution.");
  }

  return scriptPath;
}

/**
 * 提供默认的 runtime host 安装实现；始终走 Gradle 增量任务以避免复用过期分发产物。
 */
const defaultRuntimeHostInstallOps: RuntimeHostInstallOps = {
  existsSync,
  install(projectRoot) {
    return spawnSync(
      process.env.ComSpec ?? "cmd.exe",
      ["/c", "gradlew.bat", ":runtime:installCliHostDist", "--quiet"],
      {
        cwd: projectRoot,
        stdio: "inherit",
      },
    ).status;
  },
};

/**
 * 生成默认的 runtime host 启动命令，供真实 TUI 会话直接复用。
 */
export function createDefaultRuntimeLaunchSpec(
  projectRoot: string = resolveProjectRoot(),
): RuntimeLaunchSpec {
  const scriptPath = ensureRuntimeHostInstalled(projectRoot);

  return {
    command: process.env.ComSpec ?? "cmd.exe",
    args: ["/c", scriptPath],
    cwd: projectRoot,
  };
}
