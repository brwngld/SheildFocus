import assert from "node:assert/strict";

const state = {};

globalThis.chrome = {
  storage: {
    local: {
      async get(keys) {
        const result = {};
        if (Array.isArray(keys)) {
          for (const key of keys) {
            if (Object.prototype.hasOwnProperty.call(state, key)) {
              result[key] = state[key];
            }
          }
          return result;
        }

        if (typeof keys === "string") {
          if (Object.prototype.hasOwnProperty.call(state, keys)) {
            result[keys] = state[keys];
          }
          return result;
        }

        for (const key of Object.keys(keys ?? {})) {
          result[key] = Object.prototype.hasOwnProperty.call(state, key) ? state[key] : keys[key];
        }

        return result;
      },
      async set(patch) {
        Object.assign(state, patch);
      }
    }
  }
};

const { importSettings, exportSettings, getState, removeCategory, removeSchedule } = await import("../storage/settings-store.js");

const imported = await importSettings({
  settings: {
    enabled: true,
    strictMode: true,
    aiEnabled: false,
    theme: "light",
    redirectDelaySeconds: 5,
    redirectTarget: "google"
  },
  blockedDomains: [" Example.COM ", "www.MIXED.com"],
  blockedRules: [
    {
      id: "rule-1",
      domain: "HTTPS://Adult.Example/watch",
      categoryId: "cat-1",
      scheduleId: "schedule-1",
      enabled: true
    }
  ],
  allowedDomains: [" wikipedia.org "],
  categories: [
    {
      id: "cat-1",
      name: "Social",
      color: "#6b7cff",
      domains: ["https://Social.Example", "social.example"]
    }
  ],
  schedules: [
    {
      id: "schedule-1",
      name: "Work Hours",
      days: ["Mon", "Tue", "Tue", "Fri", "Nope"],
      startTime: "09:00",
      endTime: "17:00"
    }
  ]
});

assert.equal(imported.blockedDomains[0], "example.com");
assert.equal(imported.blockedRules[0].domain, "adult.example");
assert.equal(imported.categories[0].domains[0], "social.example");
assert.deepEqual(imported.schedules[0].days, ["Mon", "Tue", "Fri"]);

const exported = await exportSettings();
assert.equal(exported.blockedRules[0].domain, "adult.example");
assert.equal(exported.categories[0].domains[0], "social.example");
assert.equal(exported.allowedDomains[0], "wikipedia.org");

const stateAfter = await getState();
assert.equal(stateAfter.blockedRules[0].scheduleId, "schedule-1");
assert.equal(stateAfter.settings.theme, "light");
assert.equal(stateAfter.settings.redirectDelaySeconds, 5);
assert.equal(stateAfter.settings.redirectTarget, "google");

await removeCategory("cat-1");
await removeSchedule("schedule-1");

const cleaned = await getState();
assert.equal(cleaned.categories.length, 0);
assert.equal(cleaned.schedules.length, 0);
assert.equal(cleaned.blockedRules[0].categoryId, "");
assert.equal(cleaned.blockedRules[0].scheduleId, "");

console.log("settings-store-roundtrip: ok");
