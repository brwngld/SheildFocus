import assert from "node:assert/strict";

globalThis.chrome = {
  runtime: {
    getURL(path) {
      return `chrome-extension://shieldfocus/${path}`;
    }
  }
};

const { createDecisionEngine } = await import("../background/decision-engine.js");
const { buildBlockedPageUrl } = await import("../shared/domain-utils.js");

const engine = createDecisionEngine({
  settings: {
    enabled: true,
    strictMode: true,
    aiEnabled: false,
    redirectDelaySeconds: 10,
    redirectTarget: "previous"
  },
  blockedDomains: ["example-adult.com"],
  allowedDomains: ["wikipedia.org"],
  defaultBlockedDomains: ["pornhub.com"],
  safeDomains: ["khanacademy.org"]
});

const blockedDecision = await engine.decide({
  url: "https://www.pornhub.com/watch/123",
  hostname: "www.pornhub.com",
  title: "Video"
});

assert.equal(blockedDecision.action, "BLOCK");
assert.equal(blockedDecision.hostname, "pornhub.com");
assert.ok(blockedDecision.reasons.includes("known-blocked-domain"));

const blockedUrl = buildBlockedPageUrl({
  hostname: blockedDecision.hostname,
  reason: blockedDecision.reason,
  redirectDelaySeconds: 10,
  redirectTarget: "previous"
});

assert.equal(
  blockedUrl,
  "chrome-extension://shieldfocus/pages/blocked.html?hostname=pornhub.com&reason=known-blocked-domain&delay=10&target=previous"
);

const allowedDecision = await engine.decide({
  url: "https://www.wikipedia.org/wiki/History",
  hostname: "www.wikipedia.org",
  title: "History"
});

assert.equal(allowedDecision.action, "ALLOW");
assert.ok(allowedDecision.reasons.includes("allowlist"));

console.log("redirect-flow-smoke: ok");
