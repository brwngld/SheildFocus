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
  blockedDomains: ["blocked.example"],
  allowedDomains: ["shared.example"],
  categories: [
    {
      id: "cat-social",
      name: "social",
      domains: ["shared.example", "social.example"]
    }
  ],
  schedules: [
    {
      id: "schedule-active",
      name: "Always On",
      days: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
      startTime: "00:00",
      endTime: "00:00"
    },
    {
      id: "schedule-inactive",
      name: "Inactive",
      days: [],
      startTime: "09:00",
      endTime: "17:00"
    }
  ],
  blockedRules: [
    {
      id: "rule-active",
      domain: "active-rule.example",
      categoryId: "cat-social",
      scheduleId: "schedule-active",
      enabled: true
    },
    {
      id: "rule-inactive",
      domain: "inactive-rule.example",
      categoryId: "cat-social",
      scheduleId: "schedule-inactive",
      enabled: true
    }
  ]
});

const allowWins = await engine.decide({
  url: "https://shared.example/",
  hostname: "shared.example",
  title: "Shared"
});

assert.equal(allowWins.action, "ALLOW");
assert.equal(allowWins.reason, "allowlist");

const categoryBlock = await engine.decide({
  url: "https://social.example/",
  hostname: "social.example",
  title: "Social"
});

assert.equal(categoryBlock.action, "BLOCK");
assert.equal(categoryBlock.reason, "category:social");

const activeRuleBlock = await engine.decide({
  url: "https://active-rule.example/",
  hostname: "active-rule.example",
  title: "Active Rule"
});

assert.equal(activeRuleBlock.action, "BLOCK");
assert.equal(activeRuleBlock.category, "custom-rule");
assert.equal(activeRuleBlock.reason, "category:social");

const inactiveRuleFallsThrough = await engine.decide({
  url: "https://inactive-rule.example/",
  hostname: "inactive-rule.example",
  title: "Inactive Rule"
});

assert.equal(inactiveRuleFallsThrough.action, "ALLOW");

const blockedDomain = await engine.decide({
  url: "https://blocked.example/",
  hostname: "blocked.example",
  title: "Blocked"
});

assert.equal(blockedDomain.action, "BLOCK");
assert.equal(blockedDomain.reason, "known-blocked-domain");

console.log("decision-engine-precedence: ok");
