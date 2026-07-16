import { MESSAGE_TYPES, STORAGE_KEYS } from "../shared/constants.js";
import { buildBlockedPageUrl } from "../shared/domain-utils.js";
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

  try {
    await ruleManager.syncBlockingRules({
      blockedDomains: state.blockedDomains,
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

chrome.storage.onChanged.addListener((changes, areaName) => {
  if (areaName !== "local") {
    return;
  }

  if (
    changes[STORAGE_KEYS.settings] ||
    changes[STORAGE_KEYS.blockedDomains] ||
    changes[STORAGE_KEYS.allowedDomains]
  ) {
    void syncRulesFromStorage();
  }
});

chrome.action.onClicked.addListener(() => {
  void chrome.runtime.openOptionsPage();
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
      allowedDomains: state.allowedDomains,
      defaultBlockedDomains: catalogs.defaultBlockedDomains,
      safeDomains: catalogs.safeDomains
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
