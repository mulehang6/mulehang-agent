import type { RuntimeCliRunRequest } from "./protocol";

/**
 * 构造发送给 runtime 的最小运行请求；provider 解析由 runtime 自己负责。
 */
export function createRuntimeRunRequest(
  prompt: string,
  sessionId?: string,
): RuntimeCliRunRequest {
  return {
    type: "run",
    prompt,
    sessionId,
  };
}
