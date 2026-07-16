export function normalizeHostname(input) {
  if (!input) {
    return "";
  }

  let hostname = String(input).trim().toLowerCase();

  try {
    const url = hostname.includes("://") ? new URL(hostname) : new URL(`https://${hostname}`);
    hostname = url.hostname.toLowerCase();
  } catch {
    hostname = hostname.split("/")[0].split("?")[0].split("#")[0];
  }

  hostname = hostname.replace(/^\.+/, "").replace(/\.+$/, "");

  if (hostname.startsWith("www.")) {
    hostname = hostname.slice(4);
  }

  return hostname;
}

export function normalizeDomainInput(input) {
  if (!input) {
    return "";
  }

  let value = String(input).trim().toLowerCase();

  if (!value) {
    return "";
  }

  value = value
    .replace(/^https?:\/\//, "")
    .split("/")[0]
    .split("?")[0]
    .split("#")[0];

  return normalizeHostname(value);
}

export function dedupeDomains(domains = []) {
  const set = new Set();

  for (const domain of domains) {
    const normalized = normalizeDomainInput(domain);
    if (normalized) {
      set.add(normalized);
    }
  }

  return [...set].sort();
}

export function matchesDomainRule(hostname, ruleDomain) {
  const normalizedHostname = normalizeHostname(hostname);
  const normalizedRule = normalizeDomainInput(ruleDomain);

  if (!normalizedHostname || !normalizedRule) {
    return false;
  }

  return (
    normalizedHostname === normalizedRule ||
    normalizedHostname.endsWith(`.${normalizedRule}`)
  );
}

export function buildBlockedPageUrl({ hostname = "", reason = "" } = {}) {
  const params = new URLSearchParams();

  if (hostname) {
    params.set("hostname", hostname);
  }

  if (reason) {
    params.set("reason", reason);
  }

  const query = params.toString();
  return chrome.runtime.getURL(query ? `pages/blocked.html?${query}` : "pages/blocked.html");
}

export function stableHash32(value) {
  let hash = 2166136261;
  const input = String(value);

  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }

  return hash >>> 0;
}

export function compactText(value, maxLength = 160) {
  const text = String(value ?? "").replace(/\s+/g, " ").trim();
  if (text.length <= maxLength) {
    return text;
  }
  return `${text.slice(0, maxLength - 1)}…`;
}

