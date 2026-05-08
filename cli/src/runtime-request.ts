/**
 * 表示发送给 runtime HTTP server 的最小运行请求。
 */
export interface RuntimeRunRequest {
  sessionId?: string;
  prompt: string;
}

/**
 * 构造发送给 runtime 的最小运行请求；provider 解析由 runtime 自己负责。
 */
export function createRuntimeRunRequest(
  prompt: string,
  sessionId?: string,
) : RuntimeRunRequest {
  return {
    prompt,
    sessionId,
  };
}
