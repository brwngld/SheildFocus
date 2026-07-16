import {
  addBlockedRule,
  addCategory,
  addCategoryDomain,
  addDomain,
  addSchedule,
  exportSettings,
  getBlockedRules,
  getCategories,
  getSchedules,
  getState,
  importSettings,
  removeCategory,
  removeCategoryDomain,
  removeDomain,
  removeBlockedRule,
  removeSchedule,
  updateBlockedRule,
  setSettings
} from "../storage/settings-store.js";
import { getRecentDecisions } from "../storage/decision-log.js";
import { compactText } from "../shared/domain-utils.js";
import { applyTheme } from "../shared/theme.js";

const VIEW_META = {
  "block-list": {
    title: "Block List",
    subtitle: "Manage your blocked websites"
  },
  categories: {
    title: "Categories",
    subtitle: "Organise sites into groups"
  },
  schedules: {
    title: "Schedules",
    subtitle: "Set time-based blocking rules"
  }
};

const CATEGORY_COLORS = [
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
];

const DAY_OPTIONS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const elements = {};
let categoryColor = CATEGORY_COLORS[0];
let scheduleDays = ["Mon", "Tue", "Wed", "Thu", "Fri"];
let editingCategoryId = "";

function cacheElements() {
  elements.viewTitle = document.getElementById("viewTitle");
  elements.viewSubtitle = document.getElementById("viewSubtitle");
  elements.statusMessage = document.getElementById("statusMessage");
  elements.enabledToggle = document.getElementById("enabledToggle");
  elements.strictToggle = document.getElementById("strictToggle");
  elements.aiToggle = document.getElementById("aiToggle");
  elements.themeSelect = document.getElementById("themeSelect");
  elements.navItems = Array.from(document.querySelectorAll("[data-view]"));
  elements.views = Array.from(document.querySelectorAll("[data-view-panel]"));
  elements.blockedForm = document.getElementById("blockedForm");
  elements.blockedInput = document.getElementById("blockedInput");
  elements.blockedCategorySelect = document.getElementById("blockedCategorySelect");
  elements.blockedScheduleSelect = document.getElementById("blockedScheduleSelect");
  elements.allowedForm = document.getElementById("allowedForm");
  elements.allowedInput = document.getElementById("allowedInput");
  elements.blockedList = document.getElementById("blockedList");
  elements.allowedList = document.getElementById("allowedList");
  elements.decisionList = document.getElementById("decisionList");
  elements.categoryList = document.getElementById("categoryList");
  elements.scheduleList = document.getElementById("scheduleList");
  elements.categoriesEmptyTitle = document.getElementById("categoriesEmptyTitle");
  elements.categoriesEmptyText = document.getElementById("categoriesEmptyText");
  elements.categoriesPanel = document.getElementById("categoriesPanel");
  elements.categoriesShell = document.getElementById("categoriesShell");
  elements.schedulesEmptyTitle = document.getElementById("schedulesEmptyTitle");
  elements.schedulesEmptyText = document.getElementById("schedulesEmptyText");
  elements.schedulesPanel = document.getElementById("schedulesPanel");
  elements.schedulesShell = document.getElementById("schedulesShell");
  elements.exportButton = document.getElementById("exportButton");
  elements.importInput = document.getElementById("importInput");
  elements.openCategoryDialogButton = document.getElementById("openCategoryDialogButton");
  elements.openScheduleDialogButton = document.getElementById("openScheduleDialogButton");
  elements.categoryDialog = document.getElementById("categoryDialog");
  elements.scheduleDialog = document.getElementById("scheduleDialog");
  elements.categoryForm = document.getElementById("categoryForm");
  elements.categoryName = document.getElementById("categoryName");
  elements.categoryColorPicker = document.getElementById("categoryColorPicker");
  elements.categoryPreviewDot = document.getElementById("categoryPreviewDot");
  elements.scheduleForm = document.getElementById("scheduleForm");
  elements.scheduleName = document.getElementById("scheduleName");
  elements.scheduleStart = document.getElementById("scheduleStart");
  elements.scheduleEnd = document.getElementById("scheduleEnd");
  elements.dayGrid = document.getElementById("dayGrid");
}

function setStatus(message, tone = "") {
  if (!elements.statusMessage) {
    return;
  }

  elements.statusMessage.textContent = message;
  elements.statusMessage.dataset.tone = tone;
}

function setBusy(nextBusy) {
  const disabled = Boolean(nextBusy);

  [
    elements.enabledToggle,
    elements.strictToggle,
    elements.aiToggle,
    elements.themeSelect,
    elements.blockedInput,
    elements.blockedCategorySelect,
    elements.blockedScheduleSelect,
    elements.allowedInput,
    elements.exportButton,
    elements.importInput,
    elements.openCategoryDialogButton,
    elements.openScheduleDialogButton
  ].forEach((element) => {
    if (element) {
      element.disabled = disabled;
    }
  });

  if (elements.blockedForm) {
    elements.blockedForm.querySelectorAll("button").forEach((button) => {
      button.disabled = disabled;
    });
  }

  if (elements.allowedForm) {
    elements.allowedForm.querySelectorAll("button").forEach((button) => {
      button.disabled = disabled;
    });
  }
}

function getCurrentView() {
  return window.location.hash.replace("#", "") || "block-list";
}

function setCurrentView(view) {
  const nextView = VIEW_META[view] ? view : "block-list";
  document.body.dataset.activeView = nextView;
  window.location.hash = nextView;
}

function renderDomainList(target, domains, emptyMessage, listKey) {
  target.innerHTML = "";

  if (!domains.length) {
    const empty = document.createElement("li");
    empty.className = "empty-row";
    empty.textContent = emptyMessage;
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

function renderSelectOptions(select, options, { placeholder = "", includeBlank = true, blankLabel = "All" } = {}) {
  if (!select) {
    return;
  }

  select.innerHTML = "";

  if (includeBlank) {
    const blank = document.createElement("option");
    blank.value = "";
    blank.textContent = blankLabel;
    select.appendChild(blank);
  }

  for (const option of options) {
    const item = document.createElement("option");
    item.value = option.value;
    item.textContent = option.label;
    select.appendChild(item);
  }

  if (placeholder) {
    select.dataset.placeholder = placeholder;
  }
}

function getCategoryLabel(categories, id) {
  if (!id) {
    return "";
  }

  return categories.find((category) => category.id === id)?.name ?? "";
}

function getScheduleLabel(schedules, id) {
  if (!id) {
    return "";
  }

  return schedules.find((schedule) => schedule.id === id)?.name ?? "";
}

function buildIconSvg(kind = "domain") {
  const icon = document.createElement("span");
  icon.className = "block-card-icon";
  icon.setAttribute("aria-hidden", "true");

  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("role", "presentation");

  const path1 = document.createElementNS("http://www.w3.org/2000/svg", "path");
  const path2 = document.createElementNS("http://www.w3.org/2000/svg", "path");
  const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");

  if (kind === "rule") {
    path1.setAttribute("d", "M12 3 5 6v5c0 4.6 3 8.5 7 9.8 4-1.3 7-5.2 7-9.8V6l-7-3Z");
    path2.setAttribute("d", "M8 11h8");
  } else {
    circle.setAttribute("cx", "12");
    circle.setAttribute("cy", "12");
    circle.setAttribute("r", "8.5");
    path1.setAttribute("d", "M3.5 12h17");
    path2.setAttribute("d", "M12 3.5c2.8 3 4.5 6.5 4.5 8.5S14.8 19 12 20.5C9.2 19 7.5 14 7.5 12S9.2 6.5 12 3.5Z");
  }

  svg.append(path1, path2, circle);
  icon.appendChild(svg);
  return icon;
}

function buildBlockCard(entry, categories, schedules) {
  const item = document.createElement("li");
  item.className = "block-card";
  if (entry.kind === "rule") {
    item.classList.add("block-card-rule");
  }

  const head = document.createElement("div");
  head.className = "block-card-head";

  head.appendChild(buildIconSvg(entry.kind === "rule" ? "rule" : "domain"));

  const body = document.createElement("div");
  body.className = "block-card-body";

  const titleRow = document.createElement("div");
  titleRow.className = "block-card-title-row";

  const title = document.createElement("strong");
  title.textContent = entry.domain;
  titleRow.appendChild(title);

  const actions = document.createElement("div");
  actions.className = "block-card-actions";

  if (entry.kind === "rule") {
    const toggleLabel = document.createElement("label");
    toggleLabel.className = "toggle-inline";

    const toggleInput = document.createElement("input");
    toggleInput.type = "checkbox";
    toggleInput.checked = entry.enabled !== false;
    toggleInput.addEventListener("change", async () => {
      await updateBlockedRule(entry.id, { enabled: toggleInput.checked });
      await refresh();
    });

    const toggleTrack = document.createElement("span");
    toggleTrack.setAttribute("aria-hidden", "true");

    toggleLabel.append(toggleInput, toggleTrack);
    actions.appendChild(toggleLabel);
  }

  const removeButton = document.createElement("button");
  removeButton.type = "button";
  removeButton.className = "icon-button";
  removeButton.setAttribute("aria-label", "Remove");
  removeButton.innerHTML = "<svg viewBox='0 0 24 24' role='presentation'><path d='M5 7h14'/><path d='M10 11v6'/><path d='M14 11v6'/><path d='M9 7l1-2h4l1 2'/><path d='M7 7l1 12h8l1-12'/></svg>";
  removeButton.addEventListener("click", async () => {
    if (entry.kind === "rule") {
      if (entry.categoryId) {
        await removeCategoryDomain(entry.categoryId, entry.domain);
      }
      await removeBlockedRule(entry.id);
    } else {
      await removeDomain("blockedDomains", entry.domain);
    }
    await refresh();
  });

  actions.appendChild(removeButton);
  titleRow.appendChild(actions);
  body.appendChild(titleRow);

  if (entry.kind === "rule") {
    const tags = document.createElement("div");
    tags.className = "block-card-tags";

    const categoryName = getCategoryLabel(categories, entry.categoryId);
    const scheduleName = getScheduleLabel(schedules, entry.scheduleId);

    if (categoryName) {
      const chip = document.createElement("span");
      chip.className = "chip";
      chip.textContent = categoryName;
      tags.appendChild(chip);
    }

    if (scheduleName) {
      const chip = document.createElement("span");
      chip.className = "chip";
      chip.textContent = scheduleName;
      tags.appendChild(chip);
    }

    if (!tags.childElementCount) {
      const chip = document.createElement("span");
      chip.className = "chip";
      chip.textContent = "Always";
      tags.appendChild(chip);
    }

    body.appendChild(tags);
  }

  head.appendChild(body);
  item.appendChild(head);

  if (entry.kind === "rule") {
    const footer = document.createElement("div");
    footer.className = "block-card-footer";

    const categoryField = document.createElement("label");
    categoryField.className = "field inline-field";
    const categorySpan = document.createElement("span");
    categorySpan.textContent = "Category";
    const categorySelect = document.createElement("select");
    const categoryOptions = [
      { value: "", label: "No category" },
      ...categories.map((category) => ({ value: category.id, label: category.name }))
    ];
    renderSelectOptions(categorySelect, categoryOptions, { includeBlank: false });
    categorySelect.value = entry.categoryId || "";
    categorySelect.addEventListener("change", async () => {
      const previousCategoryId = entry.categoryId || "";
      const nextCategoryId = categorySelect.value;

      if (previousCategoryId && previousCategoryId !== nextCategoryId) {
        await removeCategoryDomain(previousCategoryId, entry.domain);
      }

      if (nextCategoryId) {
        await addCategoryDomain(nextCategoryId, entry.domain);
      }

      await updateBlockedRule(entry.id, { categoryId: nextCategoryId });
      await refresh();
    });
    categoryField.append(categorySpan, categorySelect);

    const scheduleField = document.createElement("label");
    scheduleField.className = "field inline-field";
    const scheduleSpan = document.createElement("span");
    scheduleSpan.textContent = "Schedule";
    const scheduleSelect = document.createElement("select");
    const scheduleOptions = [
      { value: "", label: "Always" },
      ...schedules.map((schedule) => ({ value: schedule.id, label: schedule.name }))
    ];
    renderSelectOptions(scheduleSelect, scheduleOptions, { includeBlank: false });
    scheduleSelect.value = entry.scheduleId || "";
    scheduleSelect.addEventListener("change", async () => {
      await updateBlockedRule(entry.id, { scheduleId: scheduleSelect.value });
      await refresh();
    });
    scheduleField.append(scheduleSpan, scheduleSelect);

    footer.append(categoryField, scheduleField);
    item.appendChild(footer);
  }

  return item;
}

function renderBlockedEntries(state, categories, schedules) {
  elements.blockedList.innerHTML = "";

  const entries = [
    ...state.blockedDomains.map((domain) => ({
      id: `manual:${domain}`,
      kind: "domain",
      domain,
      enabled: true
    })),
    ...state.blockedRules.map((rule) => ({
      ...rule,
      kind: "rule"
    }))
  ];

  if (!entries.length) {
    const empty = document.createElement("li");
    empty.className = "empty-row";
    empty.textContent = "No sites blocked yet";
    elements.blockedList.appendChild(empty);
    return;
  }

  for (const entry of entries) {
    elements.blockedList.appendChild(buildBlockCard(entry, categories, schedules));
  }
}

function renderCategories(categories) {
  elements.categoryList.innerHTML = "";
  elements.categoriesPanel?.classList.toggle("is-filled-layout", categories.length > 0);
  elements.categoriesShell.classList.toggle("filled-state", categories.length > 0);

  if (!categories.length) {
    elements.categoriesEmptyTitle.textContent = "No categories yet";
    elements.categoriesEmptyText.textContent = "Create categories to organise your blocked sites";
    return;
  }

  elements.categoriesEmptyTitle.textContent = `${categories.length} saved categories`;
  elements.categoriesEmptyText.textContent = "Use categories to group the same kind of sites together";

  for (const category of categories) {
    const item = document.createElement("li");
    item.className = "category-card";

    const top = document.createElement("div");
    top.className = "category-card-top";

    const badge = document.createElement("span");
    badge.className = "category-card-badge";
    const badgeIcon = document.createElement("span");
    badgeIcon.className = "category-dot";
    badgeIcon.style.background = category.color;
    badge.appendChild(badgeIcon);

    const body = document.createElement("div");
    body.className = "category-card-body";

    const titleRow = document.createElement("div");
    titleRow.className = "category-card-title-row";

    const title = document.createElement("strong");
    title.textContent = category.name;

    const actions = document.createElement("div");
    actions.className = "category-card-actions";

    const editButton = document.createElement("button");
    editButton.type = "button";
    editButton.className = "icon-button";
    editButton.setAttribute("aria-label", "Edit category");
    editButton.innerHTML = "<svg viewBox='0 0 24 24' role='presentation'><path d='M4 20h4l10-10-4-4L4 16z'/><path d='M13 7l4 4'/></svg>";
    editButton.addEventListener("click", () => {
      openCategoryDialog(category);
    });

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "icon-button";
    removeButton.setAttribute("aria-label", "Remove category");
    removeButton.innerHTML = "<svg viewBox='0 0 24 24' role='presentation'><path d='M5 7h14'/><path d='M10 11v6'/><path d='M14 11v6'/><path d='M9 7l1-2h4l1 2'/><path d='M7 7l1 12h8l1-12'/></svg>";
    removeButton.addEventListener("click", async () => {
      await removeCategory(category.id);
      await refresh();
    });

    actions.append(editButton, removeButton);
    titleRow.append(title, actions);

    const meta = document.createElement("div");
    meta.className = "category-card-meta";
    const count = Array.isArray(category.domains) ? category.domains.length : 0;
    meta.textContent = `${count} site${count === 1 ? "" : "s"}`;

    const subtitle = document.createElement("div");
    subtitle.className = "category-card-subtitle";
    subtitle.textContent = count ? "site" : "No sites yet";

    body.append(titleRow, meta, subtitle);

    top.append(badge, body);

    const progress = document.createElement("div");
    progress.className = "category-card-progress";
    const fill = document.createElement("span");
    const width = Math.min(100, Math.max(24, count * 18 + 24));
    fill.style.width = `${width}%`;
    progress.appendChild(fill);

    const chip = document.createElement("span");
    chip.className = "chip";
    chip.textContent = category.name;

    const footer = document.createElement("div");
    footer.className = "category-card-footer";
    footer.append(progress, chip);

    item.append(top, footer);
    elements.categoryList.appendChild(item);
  }
}

function renderSchedules(schedules) {
  elements.scheduleList.innerHTML = "";
  elements.schedulesPanel?.classList.toggle("is-filled-layout", schedules.length > 0);
  if (elements.schedulesShell) {
    elements.schedulesShell.classList.toggle("filled-state", schedules.length > 0);
  }

  if (!schedules.length) {
    elements.schedulesEmptyTitle.textContent = "No schedules yet";
    elements.schedulesEmptyText.textContent = "Create a schedule to block sites only during set hours";

    const empty = document.createElement("li");
    empty.className = "empty-row";
    empty.textContent = "Nothing saved yet.";
    elements.scheduleList.appendChild(empty);
    return;
  }

  elements.schedulesEmptyTitle.textContent = `${schedules.length} saved schedules`;
  elements.schedulesEmptyText.textContent = "Use schedules to automatically change blocking by time";

  for (const schedule of schedules) {
    const item = document.createElement("li");
    item.className = "schedule-card";
    const meta = document.createElement("div");
    meta.className = "meta";

    const title = document.createElement("strong");
    title.textContent = schedule.name;

    const detail = document.createElement("span");
    const days = schedule.days.length ? schedule.days.join(", ") : "No days selected";
    detail.textContent = `${days} - ${schedule.startTime} to ${schedule.endTime}`;

    meta.append(title, detail);

    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Remove";
    button.addEventListener("click", async () => {
      await removeSchedule(schedule.id);
      await refresh();
    });

    item.append(meta, button);
    elements.scheduleList.appendChild(item);
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
    empty.className = "empty-row";
    empty.textContent = "No recent decisions yet.";
    elements.decisionList.appendChild(empty);
    return;
  }

  for (const entry of entries) {
    const item = document.createElement("li");
    const meta = document.createElement("div");
    meta.className = "meta";

    const label = document.createElement("strong");
    label.textContent = `${compactText(entry.hostname || "unknown", 32)} - ${entry.decision || "ALLOW"}`;

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

function syncView(view) {
  const nextView = VIEW_META[view] ? view : "block-list";
  document.body.dataset.activeView = nextView;

  for (const button of elements.navItems) {
    const active = button.dataset.view === nextView;
    button.classList.toggle("active", active);
    button.setAttribute("aria-current", active ? "page" : "false");
  }

  for (const panel of elements.views) {
    const active = panel.dataset.viewPanel === nextView;
    panel.classList.toggle("active", active);
    panel.hidden = !active;
  }

  const meta = VIEW_META[nextView];
  elements.viewTitle.textContent = meta.title;
  elements.viewSubtitle.textContent = meta.subtitle;
}

function renderCategoryPalette() {
  elements.categoryColorPicker.innerHTML = "";

  for (const color of CATEGORY_COLORS) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "color-option";
    button.style.background = color;
    button.dataset.color = color;
    button.setAttribute("aria-label", color);
    button.addEventListener("click", () => {
      categoryColor = color;
      updateCategoryPalette();
      updateCategoryPreview();
    });

    elements.categoryColorPicker.appendChild(button);
  }
}

function updateCategoryPalette() {
  elements.categoryColorPicker.querySelectorAll(".color-option").forEach((button) => {
    const active = button.dataset.color === categoryColor;
    button.classList.toggle("selected", active);
    button.setAttribute("aria-pressed", active ? "true" : "false");
  });
}

function updateCategoryPreview() {
  if (elements.categoryPreviewDot) {
    elements.categoryPreviewDot.style.background = categoryColor;
  }
}

function renderDayPicker() {
  elements.dayGrid.innerHTML = "";

  for (const day of DAY_OPTIONS) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "day-pill";
    button.textContent = day;
    button.dataset.day = day;
    button.addEventListener("click", () => {
      if (scheduleDays.includes(day)) {
        scheduleDays = scheduleDays.filter((item) => item !== day);
      } else {
        scheduleDays = [...scheduleDays, day];
      }
      updateDayPicker();
    });

    elements.dayGrid.appendChild(button);
  }
}

function updateDayPicker() {
  elements.dayGrid.querySelectorAll(".day-pill").forEach((button) => {
    const active = scheduleDays.includes(button.dataset.day);
    button.classList.toggle("selected", active);
    button.setAttribute("aria-pressed", active ? "true" : "false");
  });
}

function openDialog(dialog) {
  if (dialog?.showModal) {
    dialog.showModal();
  }
}

function closeDialog(dialog) {
  if (dialog?.open) {
    dialog.close();
  }
}

async function refresh() {
  const state = await getState();
  const recent = await getRecentDecisions(12);
  const categories = await getCategories();
  const schedules = await getSchedules();
  const blockedRules = await getBlockedRules();

  elements.enabledToggle.checked = state.settings.enabled;
  elements.strictToggle.checked = state.settings.strictMode;
  elements.aiToggle.checked = state.settings.aiEnabled;
  elements.themeSelect.value = state.settings.theme;
  applyTheme(state.settings.theme);

  renderSelectOptions(elements.blockedCategorySelect, [
    { value: "", label: "No category" },
    ...categories.map((category) => ({ value: category.id, label: category.name }))
  ], { includeBlank: false });
  renderSelectOptions(elements.blockedScheduleSelect, [
    { value: "", label: "Always" },
    ...schedules.map((schedule) => ({ value: schedule.id, label: schedule.name }))
  ], { includeBlank: false });

  renderBlockedEntries({ blockedDomains: state.blockedDomains, blockedRules }, categories, schedules);
  renderDomainList(elements.allowedList, state.allowedDomains, "No allowed sites yet", "allowedDomains");
  renderCategories(categories);
  renderSchedules(schedules);
  renderDecisions(recent);
}

function bindForm(form, input, listKey) {
  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const value = input.value.trim();
    if (!value) {
      return;
    }

    try {
      setBusy(true);
      if (listKey === "blockedDomains") {
        const categoryId = elements.blockedCategorySelect?.value ?? "";
        const scheduleId = elements.blockedScheduleSelect?.value ?? "";

        if (categoryId || scheduleId) {
          await removeDomain("blockedDomains", value);
          await removeDomain("allowedDomains", value);
          await addBlockedRule({
            domain: value,
            categoryId,
            scheduleId
          });

          if (categoryId) {
            await addCategoryDomain(categoryId, value);
          }
        } else {
          await addDomain(listKey, value);
        }
      } else {
        await addDomain(listKey, value);
      }

      input.value = "";
      setStatus(`Added ${value}.`, "success");
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Could not save domain.", "error");
    } finally {
      setBusy(false);
    }
  });
}

async function exportToFile() {
  try {
    setBusy(true);
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
    setStatus("Settings exported.", "success");
  } catch (error) {
    setStatus(error instanceof Error ? error.message : "Export failed.", "error");
  } finally {
    setBusy(false);
  }
}

async function importFromFile(file) {
  try {
    setBusy(true);
    const text = await file.text();
    const payload = JSON.parse(text);
    const imported = await importSettings(payload);
    await refresh();
    setStatus(
      `Imported ${imported.blockedDomains.length} blocked and ${imported.allowedDomains.length} allowed domains.`,
      "success"
    );
  } catch (error) {
    setStatus(error instanceof Error ? error.message : "Import failed.", "error");
  } finally {
    setBusy(false);
  }
}

function resetCategoryDialog() {
  elements.categoryForm.reset();
  categoryColor = CATEGORY_COLORS[0];
  editingCategoryId = "";
  updateCategoryPalette();
  updateCategoryPreview();
}

function openCategoryDialog(category = null) {
  resetCategoryDialog();

  if (category) {
    editingCategoryId = category.id;
    elements.categoryName.value = category.name;
    categoryColor = category.color;
    updateCategoryPalette();
    updateCategoryPreview();
  }

  openDialog(elements.categoryDialog);
}

function resetScheduleDialog() {
  elements.scheduleForm.reset();
  elements.scheduleStart.value = "09:00";
  elements.scheduleEnd.value = "17:00";
  scheduleDays = ["Mon", "Tue", "Wed", "Thu", "Fri"];
  updateDayPicker();
}

function bindControls() {
  elements.navItems.forEach((button) => {
    button.addEventListener("click", () => {
      const nextView = button.dataset.view;
      syncView(nextView);
      setCurrentView(nextView);
    });
  });

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

  elements.themeSelect.addEventListener("change", async () => {
    await setSettings({ theme: elements.themeSelect.value });
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

  elements.openCategoryDialogButton.addEventListener("click", () => {
    openCategoryDialog();
  });

  elements.openScheduleDialogButton.addEventListener("click", () => {
    resetScheduleDialog();
    openDialog(elements.scheduleDialog);
  });

  elements.categoryForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const name = elements.categoryName.value.trim();
    if (!name) {
      return;
    }

    try {
      setBusy(true);
      await addCategory({ id: editingCategoryId, name, color: categoryColor });
      closeDialog(elements.categoryDialog);
      setStatus(`${editingCategoryId ? "Updated" : "Created"} category ${name}.`, "success");
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Could not create category.", "error");
    } finally {
      setBusy(false);
    }
  });

  elements.scheduleForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const name = elements.scheduleName.value.trim();
    if (!name) {
      return;
    }

    try {
      setBusy(true);
      await addSchedule({
        name,
        days: scheduleDays,
        startTime: elements.scheduleStart.value,
        endTime: elements.scheduleEnd.value
      });
      closeDialog(elements.scheduleDialog);
      setStatus(`Created schedule ${name}.`, "success");
      await refresh();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Could not create schedule.", "error");
    } finally {
      setBusy(false);
    }
  });

  elements.categoryDialog.querySelectorAll("[data-dialog-close]").forEach((button) => {
    button.addEventListener("click", () => closeDialog(elements.categoryDialog));
  });

  elements.scheduleDialog.querySelectorAll("[data-dialog-close]").forEach((button) => {
    button.addEventListener("click", () => closeDialog(elements.scheduleDialog));
  });

  window.addEventListener("hashchange", () => {
    syncView(getCurrentView());
  });
}

cacheElements();
renderCategoryPalette();
updateCategoryPalette();
updateCategoryPreview();
renderDayPicker();
updateDayPicker();
bindControls();
syncView(getCurrentView());
setStatus("Ready.");
void refresh();
