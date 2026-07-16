import { cp, mkdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";

const root = path.resolve(process.cwd());
const outDir = path.join(root, "dist", "chromium");
const includePaths = [
  "manifest.json",
  "assets",
  "background",
  "classifiers",
  "content",
  "data",
  "pages",
  "popup",
  "shared",
  "storage"
];

async function main() {
  await rm(outDir, { recursive: true, force: true });
  await mkdir(outDir, { recursive: true });

  for (const entry of includePaths) {
    const source = path.join(root, entry);
    const target = path.join(outDir, entry);
    await cp(source, target, { recursive: true });
  }

  await writeFile(
    path.join(outDir, "README.txt"),
    [
      "ShieldFocus Chromium package",
      "",
      "This folder is ready to load unpacked in Chrome, Microsoft Edge, or Opera desktop.",
      "Use the browser's extension manager to load this directory.",
      "Opera Mini and UC Browser are currently unsupported."
    ].join("\n"),
    "utf8"
  );

  console.log(`Chromium package staged at ${outDir}`);
}

void main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
