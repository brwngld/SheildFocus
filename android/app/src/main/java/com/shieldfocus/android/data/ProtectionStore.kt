package com.shieldfocus.android.data

import android.content.Context
import com.shieldfocus.android.domain.DomainNormalizer
import com.shieldfocus.android.model.ProtectionSettings

private const val PREFS_NAME = "shieldfocus_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_STRICT_MODE = "strict_mode"
private const val KEY_REDIRECT_DELAY = "redirect_delay"
private const val KEY_BLOCKED_DOMAINS = "blocked_domains"
private const val KEY_ALLOWED_DOMAINS = "allowed_domains"

class ProtectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): ProtectionSettings {
        return ProtectionSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, true),
            strictMode = preferences.getBoolean(KEY_STRICT_MODE, true),
            redirectDelaySeconds = preferences.getInt(KEY_REDIRECT_DELAY, 5)
        )
    }

    fun saveSettings(settings: ProtectionSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_STRICT_MODE, settings.strictMode)
            .putInt(KEY_REDIRECT_DELAY, settings.redirectDelaySeconds)
            .apply()
    }

    fun loadBlockedDomains(): Set<String> = preferences.getStringSet(KEY_BLOCKED_DOMAINS, emptySet())?.toSet().orEmpty()

    fun loadAllowedDomains(): Set<String> = preferences.getStringSet(KEY_ALLOWED_DOMAINS, emptySet())?.toSet().orEmpty()

    fun saveBlockedDomains(domains: Set<String>) {
        preferences.edit().putStringSet(KEY_BLOCKED_DOMAINS, domains.mapNotNull { normalizeDomain(it) }.toSet()).apply()
    }

    fun saveAllowedDomains(domains: Set<String>) {
        preferences.edit().putStringSet(KEY_ALLOWED_DOMAINS, domains.mapNotNull { normalizeDomain(it) }.toSet()).apply()
    }

    fun addBlockedDomain(domain: String): Set<String> {
        val next = loadBlockedDomains().toMutableSet()
        normalizeDomain(domain)?.let(next::add)
        saveBlockedDomains(next)
        return next
    }

    fun removeBlockedDomain(domain: String): Set<String> {
        val normalized = normalizeDomain(domain)
        val next = loadBlockedDomains().filterNot { it == normalized }.toSet()
        saveBlockedDomains(next)
        return next
    }

    fun addAllowedDomain(domain: String): Set<String> {
        val next = loadAllowedDomains().toMutableSet()
        normalizeDomain(domain)?.let(next::add)
        saveAllowedDomains(next)
        return next
    }

    fun removeAllowedDomain(domain: String): Set<String> {
        val normalized = normalizeDomain(domain)
        val next = loadAllowedDomains().filterNot { it == normalized }.toSet()
        saveAllowedDomains(next)
        return next
    }

    private fun normalizeDomain(value: String): String? {
        val normalized = DomainNormalizer.normalize(value)
        return normalized.takeIf { it.isNotBlank() }
    }
}
