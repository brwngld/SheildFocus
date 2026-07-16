import { RULE_PRIORITIES } from "../shared/constants.js";
import { dedupeDomains, stableHash32 } from "../shared/domain-utils.js";

function buildRuleId(domain, kind) {
  const base = stableHash32(`${kind}:${domain}`);
  return (base % 60000) + 1;
}

function buildRedirectTarget(baseUrl, domain, reason, redirectDelaySeconds, redirectTarget) {
  const url = new URL(baseUrl);
  url.searchParams.set("hostname", domain);
  url.searchParams.set("reason", reason);
  url.searchParams.set("delay", String(redirectDelaySeconds));
  url.searchParams.set("target", redirectTarget);
  return url.toString();
}

function toRule(domain, action, priority, kind, options = {}) {
  const redirect = action === "redirect"
    ? {
        url: buildRedirectTarget(
          options.blockedPageUrl,
          domain,
          options.reason ?? "known-blocked-domain",
          options.redirectDelaySeconds ?? 5,
          options.redirectTarget ?? "previous"
        )
      }
    : undefined;

  return {
    id: buildRuleId(domain, kind),
    priority,
    action: {
      type: action,
      ...(redirect ? { redirect } : {})
    },
    condition: {
      urlFilter: `||${domain}^`,
      resourceTypes: ["main_frame"]
    }
  };
}

export class RuleManager {
  async syncBlockingRules({
    blockedDomains = [],
    allowedDomains = [],
    defaultBlockedDomains = [],
    blockedPageUrl = "",
    redirectDelaySeconds = 5,
    redirectTarget = "previous"
  } = {}) {
    const nextBlocked = dedupeDomains([...defaultBlockedDomains, ...blockedDomains]);
    const nextAllowed = dedupeDomains(allowedDomains);

    const nextRules = [
      ...nextAllowed.map((domain) => toRule(domain, "allow", RULE_PRIORITIES.allow, "allow")),
      ...nextBlocked.map((domain) => toRule(domain, "redirect", RULE_PRIORITIES.block, "block", {
        blockedPageUrl,
        redirectDelaySeconds,
        redirectTarget,
        reason: "known-blocked-domain"
      }))
    ];

    const existingRules = await chrome.declarativeNetRequest.getDynamicRules();
    const existingIds = existingRules.map((rule) => rule.id);

    await chrome.declarativeNetRequest.updateDynamicRules({
      removeRuleIds: existingIds,
      addRules: nextRules
    });

    return nextRules.length;
  }
}

export function createRuleManager() {
  return new RuleManager();
}
