import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const metadataSource = readFileSync(new URL("../content/metadata-extractor.js", import.meta.url), "utf8");
const scannerSource = readFileSync(new URL("../content/page-scanner.js", import.meta.url), "utf8");

const sendMessageCalls = [];
const replaceCalls = [];

const context = {
  console,
  location: {
    href: "https://adult.example/watch",
    hostname: "adult.example",
    replace(url) {
      replaceCalls.push(url);
    }
  },
  document: {
    readyState: "complete",
    title: "Adult Example",
    querySelector() {
      return null;
    },
    addEventListener() {}
  },
  window: {
    location: {
      replace(url) {
        replaceCalls.push(url);
      }
    },
    addEventListener() {}
  },
  chrome: {
    runtime: {
      async sendMessage(message) {
        sendMessageCalls.push(message);
        return {
          action: "BLOCK",
          blockPageUrl: "chrome-extension://shieldfocus/pages/blocked.html?hostname=adult.example&reason=known-blocked-domain"
        };
      }
    }
  },
  globalThis: null
};

context.globalThis = context;
vm.runInNewContext(metadataSource, context, { filename: "metadata-extractor.js" });
vm.runInNewContext(scannerSource, context, { filename: "page-scanner.js" });

await new Promise((resolve) => setImmediate(resolve));

assert.equal(sendMessageCalls.length, 1);
assert.equal(sendMessageCalls[0].type, "SHIELD_FOCUS_PAGE_SCAN");
assert.equal(sendMessageCalls[0].page.hostname, "adult.example");
assert.equal(replaceCalls[0], "chrome-extension://shieldfocus/pages/blocked.html?hostname=adult.example&reason=known-blocked-domain");

console.log("page-scanner-integration: ok");
