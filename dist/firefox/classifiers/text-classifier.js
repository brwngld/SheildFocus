import { CLASSIFICATION_THRESHOLDS } from "../shared/constants.js";
import { compactText } from "../shared/domain-utils.js";

const STRONG_TERMS = [
  "porn",
  "xxx",
  "adult",
  "nsfw",
  "nude",
  "sex"
];

const MODERATE_TERMS = [
  "cam",
  "escort",
  "explicit",
  "erotic"
];

function countTermHits(text, terms) {
  let hits = 0;
  const lower = text.toLowerCase();
  for (const term of terms) {
    if (lower.includes(term)) {
      hits += 1;
    }
  }
  return hits;
}

export class TextClassifier {
  classify(page = {}) {
    const title = compactText(page.title ?? "", 240);
    const description = compactText(page.description ?? "", 320);
    const keywords = compactText(page.keywords ?? "", 240);

    const titleHits = countTermHits(title, STRONG_TERMS);
    const metaHits = countTermHits(`${description} ${keywords}`, STRONG_TERMS);
    const moderateHits = countTermHits(`${title} ${description} ${keywords}`, MODERATE_TERMS);

    let score = 0;
    const reasons = [];

    if (titleHits > 0) {
      score += 30 * titleHits;
      reasons.push("title-indicator");
    }

    if (metaHits > 0) {
      score += 50 * metaHits;
      reasons.push("metadata-indicator");
    }

    if (moderateHits > 0) {
      score += 15 * moderateHits;
      reasons.push("secondary-indicator");
    }

    return {
      category: score >= CLASSIFICATION_THRESHOLDS.block ? "adult" : score >= CLASSIFICATION_THRESHOLDS.warn ? "uncertain" : "safe",
      confidence: score >= CLASSIFICATION_THRESHOLDS.block ? 0.9 : score >= CLASSIFICATION_THRESHOLDS.warn ? 0.6 : 0.2,
      reasons,
      score
    };
  }
}

export function createTextClassifier() {
  return new TextClassifier();
}

