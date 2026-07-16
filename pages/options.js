import { addDomain, exportSettings, getState, importSettings, removeDomain, setSettings } from "../storage/settings-store.js";
import { getBlockedTodayCount, getRecentDecisions } from "../storage/decision-log.js";
import { compactText } from "../shared/domain-utils.js";

const elements = {};

function cacheElements() {
  elements.enabledToggle = document.getElementById("enabledToggle");
  elements.strictToggle = document.getElementById("strictToggle");
  elements.aiToggle = document.getElementById("aiToggle");
  elements.blockedTodayCount = document.getElementById("blockedTodayCount");
  elements.blockedForm = document.getElementById("blockedForm");
  elements.blockedInput = document.getElementById("blockedInput");
  elements.allowedForm = document.getElementById("allowedForm");
  elements.allowedInput = document.getElementById("allowedInput");
  elements.blockedList = document.getElementById("blockedList");
  elements.allowedList = document.getElementById("allowedList");
  elements.decisionList = document.getElementById("decisionList");
  elements.exportButton = document.getElementById("exportButton");
  elements.importInput = document.getElementById("importInput");
}

function renderDomainList(target, domains, listKey) {
  target.innerHTML = "";

  if (!domains.length) {
    const empty = document.createElement("li");
    empty.textContent = "No domains yet.";
    target.appendChild(empty);
    return;
  }

  for (const domain of domains) {
    const item = document.createElement("li");
    const label = document.createElement("span");
    label.className = "label";
    label.textContent = domain;

    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Remove";
    button.addEventListener("click", async () => {
      await removeDomain(listKey, domain);
      await refresh();
    });

    item.append(label, button);
    target.appendChild(item);
  }
}

function formatTimestamp(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit"
  }).format(date);
}

function renderDecisions(entries) {
  elements.decisionList.innerHTML = "";

  if (!entries.length) {
    const empty = document.createElement("li");
    empty.textContent = "No recent decisions yet.";
    elements.decisionList.appendChild(empty);
    return;
  }

  for (const entry of entries) {
    const item = document.createElement("li");
    const meta = document.createElement("div");
    meta.className = "meta";

    const label = document.createElement("strong");
    label.textContent = `${compactText(entry.hostname || "unknown", 32)} · ${entry.decision || "ALLOW"}`;

    const reason = document.createElement("span");
    reason.textContent = compactText(entry.reason || "no reason", 64);

    const time = document.createElement("time");
    time.dateTime = entry.timestamp || "";
    time.textContent = formatTimestamp(entry.timestamp);

    meta.append(label, reason);
    item.append(meta, time);
    elements.decisionList.appendChild(item);
  }
}

async function refresh() {
  const state = await getState();
  const recent = await getRecentDecisions(12);
  const blockedToday = await getBlockedTodayCount();

  elements.enabledToggle.checked = state.settings.enabled;
  elements.strictToggle.checked = state.settings.strictMode;
  elements.aiToggle.checked = state.settings.aiEnabled;
  elements.blockedTodayCount.textContent = String(blockedToday);

  renderDomainList(elements.blockedList, state.blockedDomains, "blockedDomains");
  renderDomainList(elements.allowedList, state.allowedDomains, "allowedDomains");
  renderDecisions(recent);
}

function bindForm(form, input, listKey) {
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const value = input.value.trim();
    if (!value) {
      return;
    }

    await addDomain(listKey, value);
    input.value = "";
    await refresh();
  });
}

async function exportToFile() {
  const payload = await exportSettings();
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "shieldfocus-settings.json";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function importFromFile(file) {
  const text = await file.text();
  const payload = JSON.parse(text);
  await importSettings(payload);
  await refresh();
}

function bindControls() {
  elements.enabledToggle.addEventListener("change", async () => {
    await setSettings({ enabled: elements.enabledToggle.checked });
    await refresh();
  });

  elements.strictToggle.addEventListener("change", async () => {
    await setSettings({ strictMode: elements.strictToggle.checked });
    await refresh();
  });

  elements.aiToggle.addEventListener("change", async () => {
    await setSettings({ aiEnabled: elements.aiToggle.checked });
    await refresh();
  });

  bindForm(elements.blockedForm, elements.blockedInput, "blockedDomains");
  bindForm(elements.allowedForm, elements.allowedInput, "allowedDomains");

  elements.exportButton.addEventListener("click", () => {
    void exportToFile();
  });

  elements.importInput.addEventListener("change", async () => {
    const [file] = elements.importInput.files ?? [];
    if (!file) {
      return;
    }

    await importFromFile(file);
    elements.importInput.value = "";
  });
}

cacheElements();
bindControls();
void refresh();

