const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");
const Module = require("module");

function loadGlobalNodeModules() {
  const extraModuleDirs = [];
  const envModuleDir = process.env.PPTX_NODE_MODULES;

  if (envModuleDir) {
    extraModuleDirs.push(envModuleDir);
  }

  const globalModules = execSync("npm root -g", { encoding: "utf8" }).trim();
  extraModuleDirs.push(globalModules);

  process.env.NODE_PATH = process.env.NODE_PATH
    ? `${process.env.NODE_PATH}${path.delimiter}${extraModuleDirs.join(path.delimiter)}`
    : extraModuleDirs.join(path.delimiter);
  Module._initPaths();
}

async function main() {
  loadGlobalNodeModules();

  const pptxgen = require("pptxgenjs");
  const html2pptx = require("C:/Users/666/.agents/skills/pptx/scripts/html2pptx.js");

  const deckDir = __dirname;
  const outputDir = path.join(deckDir, "output");
  const slideFiles = [
    "01-cover.html",
    "02-background-goals.html",
    "03-technical-route.html",
    "04-architecture.html",
    "05-provider-model-discovery.html",
    "06-agent-capability.html",
    "07-cli-streaming.html",
    "08-progress-validation.html",
    "09-video-placeholder.html",
    "10-thank-you.html",
  ];

  fs.mkdirSync(outputDir, { recursive: true });

  const pptx = new pptxgen();
  pptx.layout = "LAYOUT_16x9";
  pptx.author = "OpenAI Codex";
  pptx.company = "mulehang-agent";
  pptx.subject = "mulehang-agent technical presentation";
  pptx.title = "mulehang-agent 技术实现汇报";
  pptx.lang = "zh-CN";
  pptx.theme = {
    headFontFace: "Microsoft YaHei",
    bodyFontFace: "Microsoft YaHei",
    lang: "zh-CN",
  };

  for (const file of slideFiles) {
    await html2pptx(path.join(deckDir, file), pptx, {
      tmpDir: path.join(deckDir, ".tmp"),
    });
  }

  await pptx.writeFile({
    fileName: path.join(outputDir, "mulehang-agent-technical-presentation.pptx"),
  });
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
