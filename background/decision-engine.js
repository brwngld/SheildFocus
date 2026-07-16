import { CLASSIFICATION_THRESHOLDS, DECISION_ACTIONS } from "../shared/constants.js";
import { createDomainClassifier } from "../classifiers/domain-classifier.js";
import { createTextClassifier } from "../classifiers/text-classifier.js";
import { createLocalAIClassifier } from "../classifiers/local-ai-classifier.js";
import { matchesDomainRule, normalizeHostname } from "../shared/domain-utils.js";
import { isAnyScheduleActive, isScheduleActive } from "../shared/schedule.js";

function mergeReasons(...groups) {
  return [...new Set(groups.flat().filter(Boolean))];
}

export class DecisionEngine {
  constructor({
    settings = {},
    blockedDomains = [],
    blockedRules = [],
    allowedDomains = [],
    defaultBlockedDomains = [],
    safeDomains = [],
    categories = [],
    schedules = []
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
    this.categories = Array.isArray(categories) ? categories : [];
    this.schedules = Array.isArray(schedules) ? schedules : [];
    this.blockedRules = Array.isArray(blockedRules) ? blockedRules : [];
    this.scheduleById = new Map(this.schedules.map((schedule) => [schedule.id, schedule]));
  }

  isWithinActiveSchedule() {
    if (!this.schedules.length) {
      return true;
    }

    return isAnyScheduleActive(this.schedules);
  }

  findCategoryMatch(hostname) {
    for (const category of this.categories) {
      if (!Array.isArray(category.domains)) {
        continue;
      }

      if (category.domains.some((domain) => matchesDomainRule(hostname, domain))) {
        return category;
      }
    }

    return null;
  }

  findRuleMatch(hostname) {
    for (const rule of this.blockedRules) {
      if (!rule || rule.enabled === false) {
        continue;
      }

      if (!matchesDomainRule(hostname, rule.domain)) {
        continue;
      }

      if (rule.scheduleId) {
        const schedule = this.scheduleById.get(rule.scheduleId);
        if (!schedule || !isScheduleActive(schedule)) {
          continue;
        }
      }

      return rule;
    }

    return null;
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

    if (!this.isWithinActiveSchedule()) {
      return {
        action: DECISION_ACTIONS.allow,
        category: "outside-schedule",
        confidence: 1,
        score: 0,
        hostname,
        reason: "schedule-inactive",
        reasons: ["schedule-inactive"]
      };
    }

    const ruleMatch = this.findRuleMatch(hostname);
    if (ruleMatch) {
      const category = this.categories.find((item) => item.id === ruleMatch.categoryId);
      return {
        action: DECISION_ACTIONS.block,
        category: "custom-rule",
        confidence: 0.99,
        score: CLASSIFICATION_THRESHOLDS.block,
        hostname,
        reason: category ? `category:${category.name}` : `rule:${ruleMatch.domain}`,
        reasons: [category ? `category:${category.name}` : `rule:${ruleMatch.domain}`]
      };
    }

    const categoryMatch = this.findCategoryMatch(hostname);
    if (categoryMatch) {
      return {
        action: DECISION_ACTIONS.block,
        category: "category-block",
        confidence: 0.99,
        score: CLASSIFICATION_THRESHOLDS.block,
        hostname,
        reason: `category:${categoryMatch.name}`,
        reasons: [`category:${categoryMatch.name}`]
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
