import { DECISION_ACTIONS, CLASSIFICATION_THRESHOLDS } from "../shared/constants.js";
import { matchesDomainRule, normalizeHostname } from "../shared/domain-utils.js";

const URL_HINTS = /\b(porn|xxx|adult|nsfw|escort|cams?)\b/i;
const SAFE_HOST_HINTS = /\.edu$|\.gov$/i;

function confidenceFromScore(score) {
  const absolute = Math.min(100, Math.abs(score));
  if (absolute >= CLASSIFICATION_THRESHOLDS.block) {
    return 0.95;
  }
  if (absolute >= CLASSIFICATION_THRESHOLDS.warn) {
    return 0.65;
  }
  return 0.25;
}

export class DomainClassifier {
  constructor({
    blockedDomains = [],
    allowedDomains = [],
    defaultBlockedDomains = [],
    safeDomains = []
  } = {}) {
    this.blockedDomains = blockedDomains;
    this.allowedDomains = allowedDomains;
    this.defaultBlockedDomains = defaultBlockedDomains;
    this.safeDomains = safeDomains;
  }

  classify(page = {}) {
    const hostname = normalizeHostname(page.hostname ?? page.url ?? "");
    const reasons = [];
    let score = 0;

    if (!hostname) {
      return {
        category: "unknown",
        confidence: 0,
        reasons: ["missing-hostname"],
        score: 0,
        hardAllow: false,
        hardBlock: false
      };
    }

    const allowMatch = this.allowedDomains.some((domain) =>
      matchesDomainRule(hostname, domain)
    );

    if (allowMatch) {
      reasons.push("allowlist");
      score -= 100;
    }

    const safeMatch = this.safeDomains.some((domain) =>
      matchesDomainRule(hostname, domain)
    );

    if (safeMatch) {
      reasons.push("safe-domain");
      score -= 60;
    }

    const blockMatch = [...this.blockedDomains, ...this.defaultBlockedDomains].some((domain) =>
      matchesDomainRule(hostname, domain)
    );

    if (blockMatch) {
      reasons.push("known-blocked-domain");
      score += 100;
    }

    if (SAFE_HOST_HINTS.test(hostname)) {
      reasons.push("educational-or-public-domain");
      score -= 60;
    }

    if (URL_HINTS.test(page.url ?? hostname)) {
      reasons.push("url-indicator");
      score += 40;
    }

    return {
      category: score >= CLASSIFICATION_THRESHOLDS.block ? "adult" : score >= CLASSIFICATION_THRESHOLDS.warn ? "uncertain" : "safe",
      confidence: confidenceFromScore(score),
      reasons,
      score,
      hardAllow: allowMatch,
      hardBlock: blockMatch
    };
  }
}

export function createDomainClassifier(options) {
  return new DomainClassifier(options);
}
