import { describe, expect, test } from "bun:test";

import {
  APP_VERSION,
  buildComposerMetadata,
  buildWorkspaceLabel,
  createWelcomeMetadata,
  formatGitBranchRef,
} from "../welcome-metadata";

describe("welcome metadata", () => {
  test("builds kilo-style composer metadata from mode model and provider", () => {
    expect(
      buildComposerMetadata({
        modeLabel: "Code",
        modelLabel: "DeepSeek Reasoner",
        providerLabel: "DeepSeek",
      }),
    ).toEqual({
      footerText: "Code",
      helperText: "DeepSeek Reasoner   DeepSeek",
    });
  });

  test("shows workspace directory with the current git branch", () => {
    expect(
      buildWorkspaceLabel({
        cwd: "D:\\JetBrains\\projects\\idea_projects\\mulehang-agent",
        homeDir: "C:\\Users\\666",
        gitBranch: "heads/main",
      }),
    ).toBe("D:\\JetBrains\\projects\\idea_projects\\mulehang-agent:heads/main");
  });

  test("formats git symbolic refs like kilo branch labels", () => {
    expect(formatGitBranchRef("refs/heads/main")).toBe("heads/main");
    expect(formatGitBranchRef("main")).toBe("main");
  });

  test("compacts the home directory prefix like kilo", () => {
    expect(
      buildWorkspaceLabel({
        cwd: "C:\\Users\\666\\work\\mulehang-agent",
        homeDir: "C:\\Users\\666",
      }),
    ).toBe("~\\work\\mulehang-agent");
  });

  test("creates welcome footer and composer metadata together", () => {
    expect(
      createWelcomeMetadata({
        cwd: "D:\\repo",
        homeDir: "C:\\Users\\666",
        gitBranch: "main",
        modeLabel: "Code",
        modelLabel: "runtime default",
        providerLabel: "runtime managed",
      }),
    ).toEqual({
      workspaceText: "D:\\repo:main",
      versionText: APP_VERSION,
      composerFooterText: "Code",
      composerHelperText: "runtime default   runtime managed",
    });
  });
});
