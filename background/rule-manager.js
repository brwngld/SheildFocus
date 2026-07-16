import { RULE_PRIORITIES } from "../shared/constants.js";
import { dedupeDomains, stableHash32 } from "../shared/domain-utils.js";

function buildRuleId(domain, kind) {
  const base = stableHash32(`${kind}:${domain}`);
  return (base % 60000) + 1;
}

function toRule(domain, action, priority, kind) {
  return {
    id: buildRuleId(domain, kind),
    priority,
    action: {
      type: action
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
    defaultBlockedDomains = []
  } = {}) {
    const nextBlocked = dedupeDomains([...defaultBlockedDomains, ...blockedDomains]);
    const nextAllowed = dedupeDomains(allowedDomains);

    const nextRules = [
      ...nextAllowed.map((domain) => toRule(domain, "allow", RULE_PRIORITIES.allow, "allow")),
      ...nextBlocked.map((domain) => toRule(domain, "block", RULE_PRIORITIES.block, "block"))
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

