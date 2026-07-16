export const APP_NAME = "ShieldFocus";

export const STORAGE_KEYS = Object.freeze({
  settings: "settings",
  blockedDomains: "blockedDomains",
  allowedDomains: "allowedDomains",
  logs: "logs"
});

export const DEFAULT_SETTINGS = Object.freeze({
  enabled: true,
  strictMode: true,
  aiEnabled: false,
  logLimit: 200
});

export const CLASSIFICATION_THRESHOLDS = Object.freeze({
  block: 70,
  warn: 35
});

export const MESSAGE_TYPES = Object.freeze({
  pageScan: "SHIELD_FOCUS_PAGE_SCAN"
});

export const DECISION_ACTIONS = Object.freeze({
  allow: "ALLOW",
  block: "BLOCK",
  warn: "WARN"
});

export const BLOCK_PAGE_PATH = "pages/blocked.html";

export const RULE_PRIORITIES = Object.freeze({
  allow: 2,
  block: 1
});

