package com.shieldfocus.android.vpn

import com.shieldfocus.android.domain.DomainNormalizer

data class PolicyDecision(
    val allow: Boolean,
    val reason: String
)

object DomainPolicy {
    private val defaultBlockedDomains = setOf(
        "example-adult.com",
        "adult.example"
    )

    fun decide(
        hostname: String,
        blockedDomains: Set<String>,
        allowedDomains: Set<String>,
        strictMode: Boolean
    ): PolicyDecision {
        val normalized = DomainNormalizer.normalize(hostname)
        if (normalized.isBlank()) {
            return PolicyDecision(allow = true, reason = "invalid-hostname")
        }

        if (allowedDomains.any { matches(normalized, it) }) {
            return PolicyDecision(allow = true, reason = "allowlist")
        }

        if (blockedDomains.any { matches(normalized, it) } || defaultBlockedDomains.any { matches(normalized, it) }) {
            return PolicyDecision(allow = false, reason = "blocked-domain")
        }

        return PolicyDecision(allow = true, reason = if (strictMode) "allow-strict" else "allow")
    }

    private fun matches(hostname: String, rule: String): Boolean {
        val normalizedRule = DomainNormalizer.normalize(rule)
        return normalizedRule.isNotBlank() && (hostname == normalizedRule || hostname.endsWith(".$normalizedRule"))
    }
}
