import assert from "node:assert/strict";

let capturedUpdate = null;

globalThis.chrome = {
  declarativeNetRequest: {
    async getDynamicRules() {
      return [{ id: 1 }, { id: 2 }];
    },
    async updateDynamicRules(payload) {
      capturedUpdate = payload;
    }
  },
  runtime: {
    getURL(path) {
      return `chrome-extension://shieldfocus/${path}`;
    }
  }
};

const { createRuleManager } = await import("../background/rule-manager.js");

const manager = createRuleManager();
await manager.syncBlockingRules({
  blockedDomains: ["example-adult.com"],
  allowedDomains: ["wikipedia.org"],
  blockedPageUrl: chrome.runtime.getURL("pages/blocked.html"),
  redirectDelaySeconds: 5,
  redirectTarget: "previous"
});

assert.ok(capturedUpdate);
assert.deepEqual(capturedUpdate.removeRuleIds, [1, 2]);
assert.equal(capturedUpdate.addRules.length, 2);
assert.equal(capturedUpdate.addRules[0].action.type, "allow");
assert.equal(capturedUpdate.addRules[1].action.type, "redirect");
assert.equal(
  capturedUpdate.addRules[1].action.redirect.extensionPath,
  "/pages/blocked.html"
);

console.log("rule-manager-redirect: ok");
