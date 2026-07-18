package com.shieldfocus.android.vpn

object SafeSearchPolicy {
    const val GOOGLE_SAFE_SEARCH_TARGET = "forcesafesearch.google.com"

    private val googleSearchDomain = Regex("^(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?$")

    fun cnameTarget(hostname: String, enabled: Boolean): String? {
        if (!enabled) return null
        val normalized = hostname.trim().trimEnd('.').lowercase()
        return GOOGLE_SAFE_SEARCH_TARGET.takeIf { googleSearchDomain.matches(normalized) }
    }
}
