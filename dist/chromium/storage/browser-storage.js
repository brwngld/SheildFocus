const FALLBACK_STORAGE_KEY = "shieldfocus.state";

function hasChromeStorage() {
  return typeof chrome !== "undefined" && chrome?.storage?.local;
}

function readFallbackState() {
  try {
    const raw = window.localStorage.getItem(FALLBACK_STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function writeFallbackState(state) {
  try {
    window.localStorage.setItem(FALLBACK_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Ignore storage failures in private/locked-down contexts.
  }
}

export async function storageGet(keys) {
  if (hasChromeStorage()) {
    return await chrome.storage.local.get(keys);
  }

  const state = readFallbackState();
  const result = {};
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(state, key)) {
      result[key] = state[key];
    }
  }

  return result;
}

export async function storageSet(patch) {
  if (hasChromeStorage()) {
    await chrome.storage.local.set(patch);
    return;
  }

  const state = readFallbackState();
  writeFallbackState({
    ...state,
    ...patch
  });
}
