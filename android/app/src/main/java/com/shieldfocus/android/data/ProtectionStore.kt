package com.shieldfocus.android.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.shieldfocus.android.domain.DomainNormalizer
import com.shieldfocus.android.model.Decision
import com.shieldfocus.android.model.ProtectionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private const val DATA_STORE_NAME = "shieldfocus_prefs"
private const val MAX_LOG_ENTRIES = 50

private val KEY_ENABLED = booleanPreferencesKey("enabled")
private val KEY_STRICT_MODE = booleanPreferencesKey("strict_mode")
private val KEY_REDIRECT_DELAY = intPreferencesKey("redirect_delay")
private val KEY_AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
private val KEY_LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
private val KEY_BLOCKED_DOMAINS = stringSetPreferencesKey("blocked_domains")
private val KEY_ALLOWED_DOMAINS = stringSetPreferencesKey("allowed_domains")
private val KEY_DECISION_LOGS = stringPreferencesKey("decision_logs")

class ProtectionStore(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) }
    )

    fun loadSettings(): ProtectionSettings = runBlocking {
        val preferences = dataStore.data.first()
        ProtectionSettings(
            enabled = preferences[KEY_ENABLED] ?: true,
            strictMode = preferences[KEY_STRICT_MODE] ?: true,
            redirectDelaySeconds = preferences[KEY_REDIRECT_DELAY] ?: 5,
            autoStartOnBoot = preferences[KEY_AUTO_START_ON_BOOT] ?: false,
            loggingEnabled = preferences[KEY_LOGGING_ENABLED] ?: true
        )
    }

    fun saveSettings(settings: ProtectionSettings) = runBlocking {
        dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = settings.enabled
            preferences[KEY_STRICT_MODE] = settings.strictMode
            preferences[KEY_REDIRECT_DELAY] = settings.redirectDelaySeconds
            preferences[KEY_AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            preferences[KEY_LOGGING_ENABLED] = settings.loggingEnabled
        }
    }

    fun loadBlockedDomains(): Set<String> = runBlocking {
        dataStore.data.first()[KEY_BLOCKED_DOMAINS].orEmpty()
    }

    fun loadAllowedDomains(): Set<String> = runBlocking {
        dataStore.data.first()[KEY_ALLOWED_DOMAINS].orEmpty()
    }

    fun loadDecisionHistory(): List<Decision> = runBlocking {
        decodeDecisionHistory(dataStore.data.first()[KEY_DECISION_LOGS].orEmpty())
    }

    fun decisionHistoryFlow(): Flow<List<Decision>> {
        return dataStore.data.map { preferences ->
            decodeDecisionHistory(preferences[KEY_DECISION_LOGS].orEmpty())
        }
    }

    fun saveBlockedDomains(domains: Set<String>) = runBlocking {
        dataStore.edit { preferences ->
            preferences[KEY_BLOCKED_DOMAINS] = domains.mapNotNull { normalizeDomain(it) }.toSet()
        }
    }

    fun saveAllowedDomains(domains: Set<String>) = runBlocking {
        dataStore.edit { preferences ->
            preferences[KEY_ALLOWED_DOMAINS] = domains.mapNotNull { normalizeDomain(it) }.toSet()
        }
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

    fun appendDecision(decision: Decision) = runBlocking {
        dataStore.edit { preferences ->
            val current = decodeDecisionHistory(preferences[KEY_DECISION_LOGS].orEmpty()).toMutableList()
            current.add(0, decision)
            preferences[KEY_DECISION_LOGS] = encodeDecisionHistory(current.take(MAX_LOG_ENTRIES))
        }
    }

    fun clearDecisionHistory() = runBlocking {
        dataStore.edit { preferences ->
            preferences.remove(KEY_DECISION_LOGS)
        }
    }

    private fun normalizeDomain(value: String): String? {
        val normalized = DomainNormalizer.normalize(value)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun encodeDecisionHistory(history: List<Decision>): String {
        return history.joinToString(separator = "\n") { decision ->
            listOf(
                decision.timestampMillis.toString(),
                if (decision.allow) "1" else "0",
                escape(decision.domain),
                escape(decision.reason)
            ).joinToString(separator = "|")
        }
    }

    private fun decodeDecisionHistory(payload: String): List<Decision> {
        if (payload.isBlank()) return emptyList()
        return payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = splitEncoded(line)
                if (parts.size != 4) {
                    return@mapNotNull null
                }

                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val allow = parts[1] == "1"
                Decision(
                    domain = unescape(parts[2]),
                    allow = allow,
                    reason = unescape(parts[3]),
                    timestampMillis = timestamp
                )
            }
            .toList()
    }

    private fun splitEncoded(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false
        line.forEach { char ->
            when {
                escaping -> {
                    current.append(
                        when (char) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '\\' -> '\\'
                            '|' -> '|'
                            else -> char
                        }
                    )
                    escaping = false
                }
                char == '\\' -> escaping = true
                char == '|' -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '|' -> append("\\|")
                    else -> append(char)
                }
            }
        }
    }

    private fun unescape(value: String): String = value
}
