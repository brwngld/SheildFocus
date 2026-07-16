import { DEFAULT_SETTINGS, STORAGE_KEYS } from "../shared/constants.js";
import { dedupeDomains, normalizeDomainInput } from "../shared/domain-utils.js";
import { storageGet, storageSet } from "./browser-storage.js";

const CATEGORY_COLORS = new Set([
  "#6b7cff",
  "#8b5cf6",
  "#ec4899",
  "#ef4444",
  "#f97316",
  "#eab308",
  "#22c55e",
  "#14b8a6",
  "#0ea5e9",
  "#64748b"
]);

const SCHEDULE_DAYS = new Set(["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]);

function createLocalId(prefix) {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `${prefix}-${crypto.randomUUID()}`;
  }

  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function normalizeText(value) {
  return String(value ?? "").replace(/\s+/g, " ").trim();
}

function normalizeCategory(category = {}) {
  const name = normalizeText(category.name);
  const color = CATEGORY_COLORS.has(category.color) ? category.color : "#3167ff";
  const domains = dedupeDomains(Array.isArray(category.domains) ? category.domains : []);

  if (!name) {
    return null;
  }

  return {
    id: normalizeText(category.id) || createLocalId("category"),
    name,
    color,
    domains
  };
}

function normalizeSchedule(schedule = {}) {
  const name = normalizeText(schedule.name);
  const days = Array.isArray(schedule.days)
    ? [...new Set(schedule.days.map((day) => normalizeText(day)).filter((day) => SCHEDULE_DAYS.has(day)))]
    : [];
  const startTime = normalizeText(schedule.startTime) || "09:00";
  const endTime = normalizeText(schedule.endTime) || "17:00";

  if (!name) {
    return null;
  }

  return {
    id: normalizeText(schedule.id) || createLocalId("schedule"),
    name,
    days,
    startTime,
    endTime
  };
}

function normalizeBlockedRule(rule = {}) {
  const domain = normalizeDomainInput(rule.domain);
  const scheduleId = normalizeText(rule.scheduleId);
  const categoryId = normalizeText(rule.categoryId);

  if (!domain) {
    return null;
  }

  return {
    id: normalizeText(rule.id) || createLocalId("rule"),
    domain,
    categoryId: categoryId || "",
    scheduleId: scheduleId || "",
    enabled: rule.enabled !== false
  };
}

function clampLogLimit(value) {
  const parsed = Number(value);

  if (!Number.isFinite(parsed) || parsed < 1) {
    return DEFAULT_SETTINGS.logLimit;
  }

  return Math.min(1000, Math.floor(parsed));
}

function clampRedirectDelay(value) {
  const parsed = Number(value);

  if (!Number.isFinite(parsed)) {
    return DEFAULT_SETTINGS.redirectDelaySeconds;
  }

  return Math.min(60, Math.max(0, Math.floor(parsed)));
}

export function normalizeSettings(rawSettings = {}) {
  const theme = ["system", "dark", "light"].includes(rawSettings.theme) ? rawSettings.theme : DEFAULT_SETTINGS.theme;
  const redirectTarget = ["previous", "google"].includes(rawSettings.redirectTarget)
    ? rawSettings.redirectTarget
    : DEFAULT_SETTINGS.redirectTarget;

  return {
    ...DEFAULT_SETTINGS,
    ...rawSettings,
    enabled: rawSettings.enabled !== false,
    strictMode: rawSettings.strictMode !== false,
    aiEnabled: rawSettings.aiEnabled === true,
    theme,
    redirectDelaySeconds: clampRedirectDelay(rawSettings.redirectDelaySeconds),
    redirectTarget,
    logLimit: clampLogLimit(rawSettings.logLimit)
  };
}

function normalizeState(raw = {}) {
  return {
    settings: normalizeSettings(raw[STORAGE_KEYS.settings]),
    blockedDomains: dedupeDomains(raw[STORAGE_KEYS.blockedDomains] ?? []),
    blockedRules: Array.isArray(raw[STORAGE_KEYS.blockedRules])
      ? raw[STORAGE_KEYS.blockedRules].map(normalizeBlockedRule).filter(Boolean)
      : [],
    allowedDomains: dedupeDomains(raw[STORAGE_KEYS.allowedDomains] ?? []),
    logs: Array.isArray(raw[STORAGE_KEYS.logs]) ? raw[STORAGE_KEYS.logs] : [],
    categories: Array.isArray(raw[STORAGE_KEYS.categories])
      ? raw[STORAGE_KEYS.categories].map(normalizeCategory).filter(Boolean)
      : [],
    schedules: Array.isArray(raw[STORAGE_KEYS.schedules])
      ? raw[STORAGE_KEYS.schedules].map(normalizeSchedule).filter(Boolean)
      : []
  };
}

function normalizeImportState(payload = {}) {
  return {
    settings: normalizeSettings(payload?.settings),
    blockedDomains: dedupeDomains(payload?.blockedDomains ?? []),
    blockedRules: Array.isArray(payload?.blockedRules)
      ? payload.blockedRules.map(normalizeBlockedRule).filter(Boolean)
      : [],
    allowedDomains: dedupeDomains(payload?.allowedDomains ?? []),
    logs: Array.isArray(payload?.logs) ? payload.logs : [],
    categories: Array.isArray(payload?.categories)
      ? payload.categories.map(normalizeCategory).filter(Boolean)
      : [],
    schedules: Array.isArray(payload?.schedules)
      ? payload.schedules.map(normalizeSchedule).filter(Boolean)
      : []
  };
}

export async function getState() {
  const raw = await storageGet([
    STORAGE_KEYS.settings,
    STORAGE_KEYS.blockedDomains,
    STORAGE_KEYS.blockedRules,
    STORAGE_KEYS.allowedDomains,
    STORAGE_KEYS.logs,
    STORAGE_KEYS.categories,
    STORAGE_KEYS.schedules
  ]);

  return normalizeState(raw);
}

export async function setSettings(patch) {
  const state = await getState();
  const nextSettings = normalizeSettings({
    ...state.settings,
    ...patch
  });

  await storageSet({
    [STORAGE_KEYS.settings]: nextSettings
  });

  return nextSettings;
}

export async function replaceDomainList(listKey, domains) {
  const nextDomains = dedupeDomains(domains);
  await storageSet({
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

  await storageSet({
    [STORAGE_KEYS[listKey]]: [...current].sort(),
    [STORAGE_KEYS[oppositeKey]]: state[oppositeKey].filter((item) => item !== normalized)
  });

  return [...current].sort();
}

export async function removeDomain(listKey, domain) {
  const normalized = normalizeDomainInput(domain);
  const state = await getState();
  const next = state[listKey].filter((item) => item !== normalized);

  await storageSet({
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
    blockedRules: state.blockedRules,
    allowedDomains: state.allowedDomains,
    logs: state.logs,
    categories: state.categories,
    schedules: state.schedules
  };
}

export async function importSettings(payload) {
  if (!payload || typeof payload !== "object") {
    throw new Error("Invalid import payload.");
  }

  const nextState = normalizeImportState(payload);

  await storageSet({
    [STORAGE_KEYS.settings]: nextState.settings,
    [STORAGE_KEYS.blockedDomains]: nextState.blockedDomains,
    [STORAGE_KEYS.blockedRules]: nextState.blockedRules,
    [STORAGE_KEYS.allowedDomains]: nextState.allowedDomains,
    [STORAGE_KEYS.logs]: nextState.logs,
    [STORAGE_KEYS.categories]: nextState.categories,
    [STORAGE_KEYS.schedules]: nextState.schedules
  });

  return nextState;
}

export async function getBlockedRules() {
  const state = await getState();
  return state.blockedRules;
}

export async function addBlockedRule(rule) {
  const normalized = normalizeBlockedRule(rule);
  if (!normalized) {
    return await getBlockedRules();
  }

  const state = await getState();
  const next = [...state.blockedRules.filter((item) => item.id !== normalized.id), normalized];

  await storageSet({
    [STORAGE_KEYS.blockedRules]: next
  });

  return next;
}

export async function removeBlockedRule(id) {
  const state = await getState();
  const next = state.blockedRules.filter((item) => item.id !== id);

  await storageSet({
    [STORAGE_KEYS.blockedRules]: next
  });

  return next;
}

export async function updateBlockedRule(id, patch) {
  const state = await getState();
  const next = state.blockedRules.map((item) => {
    if (item.id !== id) {
      return item;
    }

    return normalizeBlockedRule({
      ...item,
      ...patch,
      id: item.id
    });
  }).filter(Boolean);

  await storageSet({
    [STORAGE_KEYS.blockedRules]: next
  });

  return next;
}

export async function getCategories() {
  const state = await getState();
  return state.categories;
}

export async function addCategory(category) {
  const normalized = normalizeCategory(category);
  if (!normalized) {
    return await getCategories();
  }

  const state = await getState();
  const next = [...state.categories.filter((item) => item.id !== normalized.id), normalized];

  await storageSet({
    [STORAGE_KEYS.categories]: next
  });

  return next;
}

export async function removeCategory(id) {
  const state = await getState();
  const next = state.categories.filter((item) => item.id !== id);
  const nextRules = state.blockedRules.map((rule) => (
    rule.categoryId === id ? { ...rule, categoryId: "" } : rule
  ));

  await storageSet({
    [STORAGE_KEYS.categories]: next,
    [STORAGE_KEYS.blockedRules]: nextRules
  });

  return next;
}

export async function addCategoryDomain(categoryId, domain) {
  const normalized = normalizeDomainInput(domain);
  if (!normalized) {
    return await getCategories();
  }

  const state = await getState();
  const next = state.categories.map((category) => {
    if (category.id !== categoryId) {
      return category;
    }

    return {
      ...category,
      domains: dedupeDomains([...(category.domains ?? []), normalized])
    };
  });

  await storageSet({
    [STORAGE_KEYS.categories]: next
  });

  return next;
}

export async function removeCategoryDomain(categoryId, domain) {
  const normalized = normalizeDomainInput(domain);
  const state = await getState();
  const next = state.categories.map((category) => {
    if (category.id !== categoryId) {
      return category;
    }

    return {
      ...category,
      domains: dedupeDomains((category.domains ?? []).filter((item) => item !== normalized))
    };
  });

  await storageSet({
    [STORAGE_KEYS.categories]: next
  });

  return next;
}

export async function getSchedules() {
  const state = await getState();
  return state.schedules;
}

export async function addSchedule(schedule) {
  const normalized = normalizeSchedule(schedule);
  if (!normalized) {
    return await getSchedules();
  }

  const state = await getState();
  const next = [...state.schedules.filter((item) => item.id !== normalized.id), normalized];

  await storageSet({
    [STORAGE_KEYS.schedules]: next
  });

  return next;
}

export async function removeSchedule(id) {
  const state = await getState();
  const next = state.schedules.filter((item) => item.id !== id);
  const nextRules = state.blockedRules.map((rule) => (
    rule.scheduleId === id ? { ...rule, scheduleId: "" } : rule
  ));

  await storageSet({
    [STORAGE_KEYS.schedules]: next,
    [STORAGE_KEYS.blockedRules]: nextRules
  });

  return next;
}
