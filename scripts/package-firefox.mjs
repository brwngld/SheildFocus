import { cp, mkdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";

const root = path.resolve(process.cwd());
const outDir = path.join(root, "dist", "firefox");
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
      "ShieldFocus Firefox package",
      "",
      "This folder is ready for Firefox desktop extension testing.",
      "Firefox may require you to enable the extension in private browsing if you want incognito-style behavior.",
      "Opera Mini and UC Browser are currently unsupported."
    ].join("\n"),
    "utf8"
  );

  console.log(`Firefox package staged at ${outDir}`);
}

void main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
