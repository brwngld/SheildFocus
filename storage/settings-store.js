import { DEFAULT_SETTINGS, STORAGE_KEYS } from "../shared/constants.js";
import { dedupeDomains, normalizeDomainInput } from "../shared/domain-utils.js";

function clampLogLimit(value) {
  const parsed = Number(value);

  if (!Number.isFinite(parsed) || parsed < 1) {
    return DEFAULT_SETTINGS.logLimit;
  }

  return Math.min(1000, Math.floor(parsed));
}

export function normalizeSettings(rawSettings = {}) {
  return {
    ...DEFAULT_SETTINGS,
    ...rawSettings,
    enabled: rawSettings.enabled !== false,
    strictMode: rawSettings.strictMode !== false,
    aiEnabled: rawSettings.aiEnabled === true,
    logLimit: clampLogLimit(rawSettings.logLimit)
  };
}

function normalizeState(raw = {}) {
  return {
    settings: normalizeSettings(raw[STORAGE_KEYS.settings]),
    blockedDomains: dedupeDomains(raw[STORAGE_KEYS.blockedDomains] ?? []),
    allowedDomains: dedupeDomains(raw[STORAGE_KEYS.allowedDomains] ?? []),
    logs: Array.isArray(raw[STORAGE_KEYS.logs]) ? raw[STORAGE_KEYS.logs] : []
  };
}

export async function getState() {
  const raw = await chrome.storage.local.get([
    STORAGE_KEYS.settings,
    STORAGE_KEYS.blockedDomains,
    STORAGE_KEYS.allowedDomains,
    STORAGE_KEYS.logs
  ]);

  return normalizeState(raw);
}

export async function setSettings(patch) {
  const state = await getState();
  const nextSettings = normalizeSettings({
    ...state.settings,
    ...patch
  });

  await chrome.storage.local.set({
    [STORAGE_KEYS.settings]: nextSettings
  });

  return nextSettings;
}

export async function replaceDomainList(listKey, domains) {
  const nextDomains = dedupeDomains(domains);
  await chrome.storage.local.set({
    [STORAGE_KEYS[listKey]]: nextDomains
  });
  return nextDomains;
}

export async function addDomain(listKey, domain) {
  const normalized = normalizeDomainInput(domain);
  if (!normalized) {
    return await getDomainList(listKey);
  }

  const state = await getState();
  const current = new Set(state[listKey]);
  const oppositeKey = listKey === "blockedDomains" ? "allowedDomains" : "blockedDomains";

  current.add(normalized);

  await chrome.storage.local.set({
    [STORAGE_KEYS[listKey]]: [...current].sort(),
    [STORAGE_KEYS[oppositeKey]]: state[oppositeKey].filter((item) => item !== normalized)
  });

  return [...current].sort();
}

export async function removeDomain(listKey, domain) {
  const normalized = normalizeDomainInput(domain);
  const state = await getState();
  const next = state[listKey].filter((item) => item !== normalized);

  await chrome.storage.local.set({
    [STORAGE_KEYS[listKey]]: next
  });

  return next;
}

export async function getDomainList(listKey) {
  const state = await getState();
  return state[listKey];
}

export async function exportSettings() {
  const state = await getState();

  return {
    version: 1,
    exportedAt: new Date().toISOString(),
    settings: state.settings,
    blockedDomains: state.blockedDomains,
    allowedDomains: state.allowedDomains,
    logs: state.logs
  };
}

export async function importSettings(payload) {
  if (!payload || typeof payload !== "object") {
    throw new Error("Invalid import payload.");
  }

  const nextState = {
    settings: normalizeSettings(payload?.settings),
    blockedDomains: dedupeDomains(payload?.blockedDomains ?? []),
    allowedDomains: dedupeDomains(payload?.allowedDomains ?? []),
    logs: Array.isArray(payload?.logs) ? payload.logs : []
  };

  await chrome.storage.local.set({
    [STORAGE_KEYS.settings]: nextState.settings,
    [STORAGE_KEYS.blockedDomains]: nextState.blockedDomains,
    [STORAGE_KEYS.allowedDomains]: nextState.allowedDomains,
    [STORAGE_KEYS.logs]: nextState.logs
  });

  return nextState;
}
