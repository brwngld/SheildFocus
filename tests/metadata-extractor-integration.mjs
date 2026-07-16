import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const source = readFileSync(new URL("../content/metadata-extractor.js", import.meta.url), "utf8");

const metaTags = new Map([
  ["description", "  This is a long description that should be trimmed.  "],
  ["keywords", "adult blocker, privacy, local first"]
]);

const context = {
  console,
  location: {
    href: "https://www.example.com/path?query=1",
    hostname: "www.example.com"
  },
  document: {
    title: "  ShieldFocus   Title   ",
    querySelector(selector) {
      const match = /^meta\[name="([^"]+)"\]$/.exec(selector);
      if (!match) {
        return null;
      }

      const name = match[1];
      const content = metaTags.get(name);
      return content
        ? {
            getAttribute(attribute) {
              return attribute === "content" ? content : null;
            }
          }
        : null;
    }
  },
  globalThis: null
};

context.globalThis = context;
vm.runInNewContext(source, context, { filename: "metadata-extractor.js" });

const metadata = context.ShieldFocusMetadataExtractor.extractPageMetadata();

assert.equal(metadata.url, "https://www.example.com/path?query=1");
assert.equal(metadata.hostname, "www.example.com");
assert.equal(metadata.title, "ShieldFocus Title");
assert.equal(metadata.description, "This is a long description that should be trimmed.");
assert.equal(metadata.keywords, "adult blocker, privacy, local first");

console.log("metadata-extractor-integration: ok");
