import { MESSAGE_TYPES, STORAGE_KEYS } from "../shared/constants.js";
import { buildBlockedPageUrl } from "../shared/domain-utils.js";
import { isAnyScheduleActive, isScheduleActive } from "../shared/schedule.js";
import { createDecisionEngine } from "./decision-engine.js";
import { createRuleManager } from "./rule-manager.js";
import { appendDecision } from "../storage/decision-log.js";
import { getState } from "../storage/settings-store.js";

const ruleManager = createRuleManager();
let cachedCatalogs = null;

async function loadJsonResource(path) {
  try {
    const response = await fetch(chrome.runtime.getURL(path));
    if (!response.ok) {
      return {};
    }
    return await response.json();
  } catch {
    return {};
  }
}

async function loadCatalogs() {
  if (cachedCatalogs) {
    return cachedCatalogs;
  }

  const [defaultBlocklist, safeDomains] = await Promise.all([
    loadJsonResource("data/default-blocklist.json"),
    loadJsonResource("data/safe-domains.json")
  ]);

  cachedCatalogs = {
    defaultBlockedDomains: Array.isArray(defaultBlocklist?.domains) ? defaultBlocklist.domains : [],
    safeDomains: Array.isArray(safeDomains?.domains) ? safeDomains.domains : []
  };

  return cachedCatalogs;
}

async function syncRulesFromStorage() {
  const state = await getState();
  const catalogs = await loadCatalogs();
  const activeSchedules = state.schedules.length === 0 || isAnyScheduleActive(state.schedules);
  const categoryBlockedDomains = state.categories.flatMap((category) => Array.isArray(category.domains) ? category.domains : []);
  const activeRuleDomains = activeSchedules
    ? state.blockedRules
        .filter((rule) => {
          if (rule.enabled === false) {
            return false;
          }

          if (!rule.scheduleId) {
            return true;
          }

          const schedule = state.schedules.find((item) => item.id === rule.scheduleId);
          return Boolean(schedule && isScheduleActive(schedule));
        })
        .map((rule) => rule.domain)
    : [];
  const blockedDomains = [...new Set([
    ...state.blockedDomains,
    ...categoryBlockedDomains,
    ...activeRuleDomains
  ])];

  try {
    await ruleManager.syncBlockingRules({
      blockedDomains: activeSchedules ? blockedDomains : [],
      allowedDomains: state.allowedDomains,
      defaultBlockedDomains: catalogs.defaultBlockedDomains
    });
  } catch (error) {
    console.error("ShieldFocus failed to sync blocking rules", error);
  }
}

async function bootstrap() {
  try {
    await syncRulesFromStorage();
  } catch (error) {
    console.error("ShieldFocus failed to bootstrap", error);
  }
}

chrome.runtime.onInstalled.addListener(() => {
  void bootstrap();
});

chrome.runtime.onStartup.addListener(() => {
  void bootstrap();
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === "shieldfocus-schedule-refresh") {
    void syncRulesFromStorage();
  }
});

chrome.alarms.create("shieldfocus-schedule-refresh", { periodInMinutes: 1 });

chrome.storage.onChanged.addListener((changes, areaName) => {
  if (areaName !== "local") {
    return;
  }

  if (
    changes[STORAGE_KEYS.settings] ||
    changes[STORAGE_KEYS.blockedDomains] ||
    changes[STORAGE_KEYS.blockedRules] ||
    changes[STORAGE_KEYS.allowedDomains] ||
    changes[STORAGE_KEYS.categories] ||
    changes[STORAGE_KEYS.schedules]
  ) {
    void syncRulesFromStorage();
  }
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type !== MESSAGE_TYPES.pageScan) {
    return false;
  }

  (async () => {
    const state = await getState();
    const catalogs = await loadCatalogs();
    const engine = createDecisionEngine({
      settings: state.settings,
      blockedDomains: state.blockedDomains,
      blockedRules: state.blockedRules,
      allowedDomains: state.allowedDomains,
      defaultBlockedDomains: catalogs.defaultBlockedDomains,
      safeDomains: catalogs.safeDomains,
      categories: state.categories,
      schedules: state.schedules
    });

    const decision = await engine.decide(message.page ?? {});

    await appendDecision({
      hostname: decision.hostname,
      decision: decision.action,
      reason: decision.reason,
      score: decision.score
    });

    if (decision.action === "BLOCK") {
      sendResponse({
        ...decision,
        blockPageUrl: buildBlockedPageUrl({
          hostname: decision.hostname,
          reason: decision.reason
        })
      });
      return;
    }

    sendResponse(decision);
  })();

  return true;
});
