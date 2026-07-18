package com.shieldfocus.android.vpn

data class PolicyDecision(
    val allow: Boolean,
    val reason: String
)

object DomainPolicy {
    private val defaultBlockedDomains = setOf("example-adult.com", "adult.example")

    /** Compatibility entry point for existing callers and tests. Runtime traffic uses a prepared snapshot. */
    fun decide(
        hostname: String,
        blockedDomains: Set<String>,
        allowedDomains: Set<String>,
        strictMode: Boolean
    ): PolicyDecision {
        val policy = PreparedDomainPolicy.build(
            version = 0,
            strictMode = strictMode,
            categorizedRules = listOf(
                CategorizedDomainRules(BlockCategory.Adult, blockedDomains + defaultBlockedDomains)
            ),
            manualRules = emptySet(),
            allowedRules = allowedDomains
        )
        val decision = policy.decide(hostname)
        val legacyReason = when {
            decision.allow && decision.reason == DecisionReason.Allowlist -> "allowlist"
            decision.allow && decision.reason == DecisionReason.InvalidHostname -> "invalid-hostname"
            decision.allow -> if (strictMode) "allow-strict" else "allow"
            decision.reason == DecisionReason.StrictVariant -> "blocked-domain-variant"
            else -> "blocked-domain"
        }
        return PolicyDecision(decision.allow, legacyReason)
    }
}
