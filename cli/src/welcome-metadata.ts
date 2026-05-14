import { execFileSync } from "node:child_process";
import { homedir } from "node:os";

/** 表示 CLI 当前发布版本，用于欢迎页右下角显示。 */
export const APP_VERSION = "0.1.0";

/** 表示欢迎页输入框和底部状态栏需要展示的全部元信息。 */
export interface WelcomeMetadata {
  workspaceText: string;
  versionText: string;
  composerFooterText: string;
  composerHelperText: string;
}

/** 表示构造欢迎页元信息所需的运行时输入。 */
export interface WelcomeMetadataInput {
  cwd?: string;
  homeDir?: string;
  gitBranch?: string;
  modeLabel: string;
  modelLabel: string;
  providerLabel: string;
  reasoningEffort?: string;
}

/** 把 mode、model、provider 拆成 Composer 左右两段，贴近 kilo 的输入框元信息布局。 */
export function buildComposerMetadata(input: {
  modeLabel: string;
  modelLabel: string;
  providerLabel: string;
  reasoningEffort?: string;
}): { footerText: string; helperText: string } {
  const runtimeIdentity = [
    input.providerLabel,
    input.modelLabel,
    input.reasoningEffort,
  ]
    .filter((part) => part != null && part.length > 0)
    .join(" ");

  return {
    footerText:
      runtimeIdentity.length > 0
        ? `${input.modeLabel}   ${runtimeIdentity}`
        : input.modeLabel,
    helperText: "",
  };
}

/** 把当前工作区路径格式化为 kilo 风格；位于用户目录下时用 ~ 缩写，并在有分支时追加 :branch。 */
export function buildWorkspaceLabel(input: {
  cwd: string;
  homeDir?: string;
  gitBranch?: string;
}): string {
  const normalizedHome = input.homeDir?.replace(/[\\/]+$/, "");
  const workspace =
    normalizedHome && isSameOrNestedPath(input.cwd, normalizedHome)
      ? `~${input.cwd.slice(normalizedHome.length)}`
      : input.cwd;
  const branch = input.gitBranch?.trim();

  return branch ? `${workspace}:${branch}` : workspace;
}

/** 读取当前 git 分支；不在 git 工作区或 git 不可用时返回 undefined，避免影响 TUI 启动。 */
export function readCurrentGitBranch(cwd = process.cwd()): string | undefined {
  try {
    const output = execFileSync("git", ["symbolic-ref", "HEAD"], {
      cwd,
      encoding: "utf8",
      windowsHide: true,
    }).trim();

    return output.length > 0 ? formatGitBranchRef(output) : undefined;
  } catch {
    return undefined;
  }
}

/** 把 git symbolic ref 转为与 kilo 截图一致的短分支标签，例如 refs/heads/main -> heads/main。 */
export function formatGitBranchRef(ref: string): string {
  return ref.replace(/^refs\//, "");
}

/** 聚合欢迎页需要的底部工作区、版本号以及输入框元信息。 */
export function createWelcomeMetadata(input: WelcomeMetadataInput): WelcomeMetadata {
  const composer = buildComposerMetadata(input);

  return {
    workspaceText: buildWorkspaceLabel({
      cwd: input.cwd ?? process.cwd(),
      homeDir: input.homeDir ?? homedir(),
      gitBranch: input.gitBranch,
    }),
    versionText: APP_VERSION,
    composerFooterText: composer.footerText,
    composerHelperText: composer.helperText,
  };
}

/** 判断路径是否等于 home 或位于 home 之下；仅做大小写不敏感的 Windows/终端路径前缀判断。 */
function isSameOrNestedPath(path: string, parent: string): boolean {
  const normalizedPath = path.toLowerCase();
  const normalizedParent = parent.toLowerCase();

  return (
    normalizedPath === normalizedParent ||
    normalizedPath.startsWith(`${normalizedParent}\\`) ||
    normalizedPath.startsWith(`${normalizedParent}/`)
  );
}
