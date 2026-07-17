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

        val allBlockedDomains = blockedDomains + defaultBlockedDomains
        if (allBlockedDomains.any { matches(normalized, it) }) {
            return PolicyDecision(allow = false, reason = "blocked-domain")
        }

        if (strictMode && allBlockedDomains.any { matchesEmbeddedVariant(normalized, it) }) {
            return PolicyDecision(allow = false, reason = "blocked-domain-variant")
        }

        return PolicyDecision(allow = true, reason = if (strictMode) "allow-strict" else "allow")
    }

    private fun matches(hostname: String, rule: String): Boolean {
        val normalizedRule = DomainNormalizer.normalize(rule)
        return normalizedRule.isNotBlank() && (hostname == normalizedRule || hostname.endsWith(".$normalizedRule"))
    }

    private fun matchesEmbeddedVariant(hostname: String, rule: String): Boolean {
        val normalizedRule = DomainNormalizer.normalize(rule)
        if (normalizedRule.isBlank()) return false

        val blockedLabel = normalizedRule.substringBefore('.').filter(Char::isLetterOrDigit)
        if (blockedLabel.length < 5) return false

        val hostnameLabels = hostname.split('.')
        if (hostnameLabels.size < 2) return false

        return hostnameLabels
            .dropLast(1)
            .map { it.filter(Char::isLetterOrDigit) }
            .any { candidateLabel ->
                candidateLabel.length > blockedLabel.length && blockedLabel in candidateLabel
            }
    }
}
