import { CLASSIFICATION_THRESHOLDS, DECISION_ACTIONS } from "../shared/constants.js";
import { createDomainClassifier } from "../classifiers/domain-classifier.js";
import { createTextClassifier } from "../classifiers/text-classifier.js";
import { createLocalAIClassifier } from "../classifiers/local-ai-classifier.js";
import { normalizeHostname } from "../shared/domain-utils.js";

function mergeReasons(...groups) {
  return [...new Set(groups.flat().filter(Boolean))];
}

export class DecisionEngine {
  constructor({
    settings = {},
    blockedDomains = [],
    allowedDomains = [],
    defaultBlockedDomains = [],
    safeDomains = []
  } = {}) {
    this.settings = settings;
    this.domainClassifier = createDomainClassifier({
      blockedDomains,
      allowedDomains,
      defaultBlockedDomains,
      safeDomains
    });
    this.textClassifier = createTextClassifier();
    this.aiClassifier = createLocalAIClassifier({ enabled: settings.aiEnabled === true });
  }

  async decide(page = {}) {
    const hostname = normalizeHostname(page.hostname ?? page.url ?? "");

    if (!this.settings.enabled) {
      return {
        action: DECISION_ACTIONS.allow,
        category: "disabled",
        confidence: 1,
        score: 0,
        hostname,
        reason: "protection-disabled",
        reasons: ["protection-disabled"]
      };
    }

    const domainResult = this.domainClassifier.classify(page);

    if (domainResult.hardAllow) {
      return {
        action: DECISION_ACTIONS.allow,
        category: "trusted",
        confidence: 0.99,
        score: domainResult.score,
        hostname,
        reason: "allowlist",
        reasons: domainResult.reasons
      };
    }

    if (domainResult.hardBlock) {
      return {
        action: DECISION_ACTIONS.block,
        category: "blocked-domain",
        confidence: domainResult.confidence,
        score: Math.max(domainResult.score, CLASSIFICATION_THRESHOLDS.block),
        hostname,
        reason: "known-blocked-domain",
        reasons: domainResult.reasons
      };
    }

    const textResult = this.textClassifier.classify(page);
    const aiResult = await this.aiClassifier.classify(page);

    const score = domainResult.score + textResult.score + aiResult.score;
    const reasons = mergeReasons(domainResult.reasons, textResult.reasons, aiResult.reasons);

    let action = DECISION_ACTIONS.allow;
    if (score >= CLASSIFICATION_THRESHOLDS.block) {
      action = DECISION_ACTIONS.block;
    } else if (score >= CLASSIFICATION_THRESHOLDS.warn) {
      action = DECISION_ACTIONS.warn;
    }

    if (this.settings.strictMode && action === DECISION_ACTIONS.warn) {
      action = DECISION_ACTIONS.block;
    }

    return {
      action,
      category: action === DECISION_ACTIONS.block ? "adult" : action === DECISION_ACTIONS.warn ? "uncertain" : "safe",
      confidence: Math.min(0.99, Math.max(domainResult.confidence, textResult.confidence, aiResult.confidence, Math.abs(score) / 100)),
      score,
      hostname,
      reason: reasons[0] ?? "no-signals",
      reasons
    };
  }
}

export function createDecisionEngine(options) {
  return new DecisionEngine(options);
}

