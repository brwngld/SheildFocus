package com.shieldfocus.android.vpn

import com.shieldfocus.android.domain.DomainNormalizer

data class PolicyDecision(
    val allow: Boolean,
    val reason: String
)

object DomainPolicy {
    private val recognizedVariantAffixes = setOf(
        "alt",
        "free",
        "go",
        "mirror",
        "my",
        "new",
        "official",
        "online",
        "proxy",
        "site",
        "the",
        "tv",
        "unblocked",
        "xxx"
    )
    private val commonCountryCodeSecondLevelDomains = setOf("co", "com", "net", "org")

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

        if (strictMode && allBlockedDomains.any { matchesDomainFamilyVariant(normalized, it) }) {
            return PolicyDecision(allow = false, reason = "blocked-domain-variant")
        }

        return PolicyDecision(allow = true, reason = if (strictMode) "allow-strict" else "allow")
    }

    private fun matches(hostname: String, rule: String): Boolean {
        val normalizedRule = DomainNormalizer.normalize(rule)
        return normalizedRule.isNotBlank() && (hostname == normalizedRule || hostname.endsWith(".$normalizedRule"))
    }

    private fun matchesDomainFamilyVariant(hostname: String, rule: String): Boolean {
        val normalizedRule = DomainNormalizer.normalize(rule)
        if (normalizedRule.isBlank()) return false

        val blockedLabel = normalizedRule.substringBefore('.').filter(Char::isLetterOrDigit)
        if (blockedLabel.length < 5) return false

        val hostnameLabels = hostname.split('.')
        if (hostnameLabels.size < 2) return false

        val registrableLabelIndex = if (
            hostnameLabels.last().length == 2 &&
            hostnameLabels.size >= 3 &&
            hostnameLabels[hostnameLabels.lastIndex - 1] in commonCountryCodeSecondLevelDomains
        ) {
            hostnameLabels.lastIndex - 2
        } else {
            hostnameLabels.lastIndex - 1
        }
        val candidateLabel = hostnameLabels[registrableLabelIndex].filter(Char::isLetterOrDigit)
        if (candidateLabel == blockedLabel) return true

        val blockedIndex = candidateLabel.indexOf(blockedLabel)
        if (blockedIndex < 0) return false

        val prefix = candidateLabel.substring(0, blockedIndex)
        val suffix = candidateLabel.substring(blockedIndex + blockedLabel.length)
        if (prefix.isEmpty() && suffix.isEmpty()) return true

        return isRecognizedVariantAffix(prefix) && isRecognizedVariantAffix(suffix)
    }

    private fun isRecognizedVariantAffix(value: String): Boolean {
        if (value.isEmpty() || value.all(Char::isDigit)) return true
        return recognizedVariantAffixes.any { marker ->
            value == marker ||
                value.removePrefix(marker).all(Char::isDigit) ||
                value.removeSuffix(marker).all(Char::isDigit)
        }
    }
}
