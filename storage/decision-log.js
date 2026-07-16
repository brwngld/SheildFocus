import { DEFAULT_SETTINGS, STORAGE_KEYS } from "../shared/constants.js";
import { compactText } from "../shared/domain-utils.js";

function sanitizeEntry(entry = {}) {
  return {
    hostname: compactText(entry.hostname ?? "", 120),
    decision: compactText(entry.decision ?? "", 20),
    reason: compactText(entry.reason ?? "", 120),
    score: Number.isFinite(Number(entry.score)) ? Number(entry.score) : 0,
    timestamp: entry.timestamp ?? new Date().toISOString()
  };
}

export async function appendDecision(entry) {
  const raw = await chrome.storage.local.get([STORAGE_KEYS.logs, STORAGE_KEYS.settings]);
  const settings = raw[STORAGE_KEYS.settings] ?? DEFAULT_SETTINGS;
  const logs = Array.isArray(raw[STORAGE_KEYS.logs]) ? raw[STORAGE_KEYS.logs] : [];
  const nextLogs = [sanitizeEntry(entry), ...logs].slice(0, Math.max(1, settings.logLimit ?? DEFAULT_SETTINGS.logLimit));

  await chrome.storage.local.set({
    [STORAGE_KEYS.logs]: nextLogs
  });

  return nextLogs;
}

export async function getRecentDecisions(limit = 20) {
  const raw = await chrome.storage.local.get([STORAGE_KEYS.logs]);
  const logs = Array.isArray(raw[STORAGE_KEYS.logs]) ? raw[STORAGE_KEYS.logs] : [];
  return logs.slice(0, Math.max(1, limit));
}

export async function getBlockedTodayCount() {
  const raw = await chrome.storage.local.get([STORAGE_KEYS.logs]);
  const logs = Array.isArray(raw[STORAGE_KEYS.logs]) ? raw[STORAGE_KEYS.logs] : [];
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();

  return logs.filter((entry) => {
    const timestamp = Date.parse(entry?.timestamp ?? "");
    return entry?.decision === "BLOCK" && Number.isFinite(timestamp) && timestamp >= start;
  }).length;
}

