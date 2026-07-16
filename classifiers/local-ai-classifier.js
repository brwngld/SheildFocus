export class LocalAIClassifier {
  constructor({ enabled = false } = {}) {
    this.enabled = enabled;
  }

  async classify() {
    if (!this.enabled) {
      return {
        category: "disabled",
        confidence: 0,
        reasons: ["local-ai-disabled"],
        score: 0
      };
    }

    return {
      category: "neutral",
      confidence: 0,
      reasons: ["local-ai-not-implemented-yet"],
      score: 0
    };
  }
}

export function createLocalAIClassifier(options) {
  return new LocalAIClassifier(options);
}

