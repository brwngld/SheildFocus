const THEMES = new Set(["system", "dark", "light"]);

export function normalizeTheme(theme) {
  return THEMES.has(theme) ? theme : "system";
}

export function applyTheme(theme) {
  const normalized = normalizeTheme(theme);
  const root = document.documentElement;
  const body = document.body;

  if (normalized === "system") {
    root.removeAttribute("data-theme");
    body?.removeAttribute("data-theme");
    return normalized;
  }

  root.setAttribute("data-theme", normalized);
  body?.setAttribute("data-theme", normalized);
  return normalized;
}

export function getEffectiveTheme(theme) {
  const normalized = normalizeTheme(theme);

  if (normalized !== "system") {
    return normalized;
  }

  return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}
