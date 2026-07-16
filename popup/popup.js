import { getBlockedTodayCount, getTotalBlockedCount } from "../storage/decision-log.js";
import { getState, setSettings } from "../storage/settings-store.js";
import { applyTheme } from "../shared/theme.js";

const elements = {};

function cacheElements() {
  elements.openOptionsButton = document.getElementById("openOptionsButton");
  elements.enabledToggle = document.getElementById("enabledToggle");
  elements.strictToggle = document.getElementById("strictToggle");
  elements.themeSelect = document.getElementById("themeSelect");
  elements.protectionStatus = document.getElementById("protectionStatus");
  elements.blockedTodayCount = document.getElementById("blockedTodayCount");
  elements.activeRulesCount = document.getElementById("activeRulesCount");
  elements.statusMessage = document.getElementById("statusMessage");
}

function setStatus(message, tone = "") {
  if (!elements.statusMessage) {
    return;
  }

  elements.statusMessage.textContent = message;
  elements.statusMessage.dataset.tone = tone;
}

async function refresh() {
  const state = await getState();
  const blockedToday = await getBlockedTodayCount();
  const totalBlocked = await getTotalBlockedCount();
  const activeRules =
    state.blockedDomains.length +
    state.allowedDomains.length +
    state.blockedRules.length +
    state.categories.length +
    state.schedules.length;

  elements.enabledToggle.checked = state.settings.enabled;
  elements.strictToggle.checked = state.settings.strictMode;
  elements.themeSelect.value = state.settings.theme;
  elements.blockedTodayCount.textContent = String(blockedToday);
  elements.activeRulesCount.textContent = String(activeRules);
  elements.protectionStatus.textContent = state.settings.enabled ? "On" : "Off";
  applyTheme(state.settings.theme);

  if (Number.isFinite(totalBlocked) && totalBlocked > blockedToday) {
    setStatus(`${totalBlocked} total blocks recorded.`, "");
  } else {
    setStatus("Ready.", "");
  }
}

function bindControls() {
  elements.openOptionsButton.addEventListener("click", () => {
    void chrome.runtime.openOptionsPage();
  });

  elements.enabledToggle.addEventListener("change", async () => {
    await setSettings({ enabled: elements.enabledToggle.checked });
    await refresh();
  });

  elements.strictToggle.addEventListener("change", async () => {
    await setSettings({ strictMode: elements.strictToggle.checked });
    await refresh();
  });

  elements.themeSelect.addEventListener("change", async () => {
    await setSettings({ theme: elements.themeSelect.value });
    await refresh();
  });
}

cacheElements();
bindControls();
void refresh();
