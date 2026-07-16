import assert from "node:assert/strict";

globalThis.chrome = {
  runtime: {
    getURL(path) {
      return `chrome-extension://shieldfocus/${path}`;
    }
  }
};

const { createDecisionEngine } = await import("../background/decision-engine.js");

const engine = createDecisionEngine({
  settings: {
    enabled: true,
    strictMode: true,
    aiEnabled: false
  },
  blockedDomains: ["plain-blocked.com"],
  allowedDomains: ["allowed.com"],
  defaultBlockedDomains: ["adult.example"],
  safeDomains: ["wikipedia.org"],
  categories: [
    {
      id: "cat-social",
      name: "social",
      domains: ["social.example"]
    }
  ],
  schedules: [
    {
      id: "schedule-always",
      name: "Always",
      days: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
      startTime: "00:00",
      endTime: "00:00"
    }
  ],
  blockedRules: [
    {
      id: "rule-social",
      domain: "video.example",
      categoryId: "cat-social",
      scheduleId: "schedule-always",
      enabled: true
    }
  ]
});

const allowDecision = await engine.decide({
  url: "https://allowed.com/",
  hostname: "allowed.com",
  title: "Allowed"
});

assert.equal(allowDecision.action, "ALLOW");
assert.equal(allowDecision.reason, "allowlist");

const categoryDecision = await engine.decide({
  url: "https://social.example/",
  hostname: "social.example",
  title: "Category"
});

assert.equal(categoryDecision.action, "BLOCK");
assert.equal(categoryDecision.reason, "category:social");
assert.equal(categoryDecision.category, "category-block");

const ruleDecision = await engine.decide({
  url: "https://video.example/",
  hostname: "video.example",
  title: "Rule"
});

assert.equal(ruleDecision.action, "BLOCK");
assert.equal(ruleDecision.reason, "category:social");
assert.equal(ruleDecision.category, "custom-rule");

const blockDecision = await engine.decide({
  url: "https://plain-blocked.com/",
  hostname: "plain-blocked.com",
  title: "Plain block"
});

assert.equal(blockDecision.action, "BLOCK");
assert.equal(blockDecision.reason, "known-blocked-domain");

const safeDecision = await engine.decide({
  url: "https://wikipedia.org/",
  hostname: "wikipedia.org",
  title: "Safe"
});

assert.equal(safeDecision.action, "ALLOW");

console.log("decision-engine-smoke: ok");
