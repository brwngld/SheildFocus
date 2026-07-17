package com.shieldfocus.android.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.shieldfocus.android.domain.DomainNormalizer
import com.shieldfocus.android.model.BlockingCategory
import com.shieldfocus.android.model.BlockingSchedule
import com.shieldfocus.android.model.Decision
import com.shieldfocus.android.model.ProtectionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val DATA_STORE_NAME = "shieldfocus_prefs"
private const val MAX_LOG_ENTRIES = 50

private val KEY_ENABLED = booleanPreferencesKey("enabled")
private val KEY_STRICT_MODE = booleanPreferencesKey("strict_mode")
private val KEY_REDIRECT_DELAY = intPreferencesKey("redirect_delay")
private val KEY_AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
private val KEY_LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
private val KEY_BLOCKED_DOMAINS = stringSetPreferencesKey("blocked_domains")
private val KEY_ALLOWED_DOMAINS = stringSetPreferencesKey("allowed_domains")
private val KEY_CATEGORIES = stringPreferencesKey("blocking_categories")
private val KEY_SCHEDULES = stringPreferencesKey("blocking_schedules")
private val KEY_DECISION_LOGS = stringPreferencesKey("decision_logs")

private val CATEGORY_COLORS = listOf(
    "#6671FF",
    "#EC4899",
    "#10B981",
    "#F59E0B",
    "#8B5CF6",
    "#14B8A6"
)

private val DEFAULT_SCHEDULE_DAYS = setOf(2, 3, 4, 5, 6)

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

    fun loadCategories(): List<BlockingCategory> = runBlocking {
        decodeCategories(dataStore.data.first()[KEY_CATEGORIES].orEmpty())
    }

    fun loadSchedules(): List<BlockingSchedule> = runBlocking {
        decodeSchedules(dataStore.data.first()[KEY_SCHEDULES].orEmpty())
    }

    fun loadActiveScheduleIds(nowMillis: Long = System.currentTimeMillis()): Set<String> = runBlocking {
        loadSchedules().filter { it.isActiveAt(nowMillis) }.mapTo(mutableSetOf()) { it.id }
    }

    fun loadEffectiveBlockedDomains(activeScheduleIds: Set<String>): Set<String> = runBlocking {
        val preferences = dataStore.data.first()
        val manual = preferences[KEY_BLOCKED_DOMAINS].orEmpty()
        val categoryDomains = decodeCategories(preferences[KEY_CATEGORIES].orEmpty())
            .filter { it.enabled && (it.scheduleId.isBlank() || it.scheduleId in activeScheduleIds) }
            .flatMap { it.domains }
            .toSet()
        manual + categoryDomains
    }

    fun decisionHistoryFlow(): Flow<List<Decision>> {
        return dataStore.data.map { preferences ->
            decodeDecisionHistory(preferences[KEY_DECISION_LOGS].orEmpty())
        }
    }

    fun loadDecisionHistory(): List<Decision> = runBlocking {
        decodeDecisionHistory(dataStore.data.first()[KEY_DECISION_LOGS].orEmpty())
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

    fun saveCategories(categories: List<BlockingCategory>) = runBlocking {
        dataStore.edit { preferences ->
            preferences[KEY_CATEGORIES] = encodeCategories(categories)
        }
    }

    fun saveSchedules(schedules: List<BlockingSchedule>) = runBlocking {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEDULES] = encodeSchedules(schedules)
        }
    }

    fun updateSchedule(
        scheduleId: String,
        name: String,
        activeDays: Set<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        enabled: Boolean
    ): List<BlockingSchedule> {
        val next = loadSchedules().map { schedule ->
            if (schedule.id == scheduleId) {
                schedule.copy(
                    name = name.trim().ifBlank { schedule.name },
                    activeDays = if (activeDays.isEmpty()) schedule.activeDays else activeDays,
                    startMinuteOfDay = startMinuteOfDay.coerceIn(0, 23 * 60 + 59),
                    endMinuteOfDay = endMinuteOfDay.coerceIn(0, 23 * 60 + 59),
                    enabled = enabled
                )
            } else {
                schedule
            }
        }
        saveSchedules(next)
        return next
    }

    fun addCategory(name: String): List<BlockingCategory> {
        val next = loadCategories().toMutableList()
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return next

        val category = BlockingCategory(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            colorHex = CATEGORY_COLORS[next.size % CATEGORY_COLORS.size]
        )
        next.add(category)
        saveCategories(next)
        return next
    }

    fun removeCategory(categoryId: String): List<BlockingCategory> {
        val next = loadCategories().filterNot { it.id == categoryId }
        saveCategories(next)
        return next
    }

    fun assignScheduleToCategory(categoryId: String, scheduleName: String): List<BlockingCategory> {
        val scheduleId = scheduleName.trim().takeIf { it.isNotBlank() }?.let { name ->
            loadSchedules().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
        }.orEmpty()

        val next = loadCategories().map { category ->
            if (category.id == categoryId) category.copy(scheduleId = scheduleId) else category
        }
        saveCategories(next)
        return next
    }

    fun addDomainToCategory(categoryId: String, domain: String): List<BlockingCategory> {
        val normalized = normalizeDomain(domain) ?: return loadCategories()
        val next = loadCategories().map { category ->
            if (category.id != categoryId) {
                category
            } else {
                category.copy(domains = (category.domains + normalized).distinct())
            }
        }
        saveCategories(next)
        return next
    }

    fun removeDomainFromCategory(categoryId: String, domain: String): List<BlockingCategory> {
        val normalized = normalizeDomain(domain) ?: return loadCategories()
        val next = loadCategories().map { category ->
            if (category.id != categoryId) {
                category
            } else {
                category.copy(domains = category.domains.filterNot { it == normalized })
            }
        }
        saveCategories(next)
        return next
    }

    fun addSchedule(name: String): List<BlockingSchedule> {
        val next = loadSchedules().toMutableList()
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return next

        val schedule = BlockingSchedule(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            activeDays = DEFAULT_SCHEDULE_DAYS,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            enabled = true
        )
        next.add(schedule)
        saveSchedules(next)
        return next
    }

    fun removeSchedule(scheduleId: String): List<BlockingSchedule> {
        val next = loadSchedules().filterNot { it.id == scheduleId }
        saveSchedules(next)
        val updatedCategories = loadCategories().map { category ->
            if (category.scheduleId == scheduleId) category.copy(scheduleId = "") else category
        }
        saveCategories(updatedCategories)
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

    fun exportBackup(): String = runBlocking {
        val preferences = dataStore.data.first()
        val backup = JSONObject()
            .put("version", 1)
            .put("settings", JSONObject().apply {
                val settings = loadSettings()
                put("enabled", settings.enabled)
                put("strictMode", settings.strictMode)
                put("redirectDelaySeconds", settings.redirectDelaySeconds)
                put("autoStartOnBoot", settings.autoStartOnBoot)
                put("loggingEnabled", settings.loggingEnabled)
            })
            .put("blockedDomains", JSONArray(loadBlockedDomains().sorted()))
            .put("allowedDomains", JSONArray(loadAllowedDomains().sorted()))
            .put("categories", JSONArray().apply {
                decodeCategories(preferences[KEY_CATEGORIES].orEmpty()).forEach { category ->
                    put(JSONObject().apply {
                        put("id", category.id)
                        put("name", category.name)
                        put("colorHex", category.colorHex)
                        put("scheduleId", category.scheduleId)
                        put("enabled", category.enabled)
                        put("domains", JSONArray(category.domains))
                    })
                }
            })
            .put("schedules", JSONArray().apply {
                decodeSchedules(preferences[KEY_SCHEDULES].orEmpty()).forEach { schedule ->
                    put(JSONObject().apply {
                        put("id", schedule.id)
                        put("name", schedule.name)
                        put("enabled", schedule.enabled)
                        put("startMinuteOfDay", schedule.startMinuteOfDay)
                        put("endMinuteOfDay", schedule.endMinuteOfDay)
                        put("activeDays", JSONArray(schedule.activeDays.sorted()))
                    })
                }
            })
            .put("decisionLogs", JSONArray().apply {
                decodeDecisionHistory(preferences[KEY_DECISION_LOGS].orEmpty()).forEach { decision ->
                    put(JSONObject().apply {
                        put("domain", decision.domain)
                        put("allow", decision.allow)
                        put("reason", decision.reason)
                        put("timestampMillis", decision.timestampMillis)
                    })
                }
            })

        backup.toString(2)
    }

    fun importBackup(payload: String): Boolean = runBlocking {
        runCatching {
            val root = JSONObject(payload)
            val settingsObject = root.optJSONObject("settings") ?: JSONObject()
            val settings = ProtectionSettings(
                enabled = settingsObject.optBoolean("enabled", true),
                strictMode = settingsObject.optBoolean("strictMode", true),
                redirectDelaySeconds = settingsObject.optInt("redirectDelaySeconds", 5),
                autoStartOnBoot = settingsObject.optBoolean("autoStartOnBoot", false),
                loggingEnabled = settingsObject.optBoolean("loggingEnabled", true)
            )

            val blockedDomains = readJsonStringSet(root.optJSONArray("blockedDomains"))
            val allowedDomains = readJsonStringSet(root.optJSONArray("allowedDomains"))
            val categories = readCategoriesFromJson(root.optJSONArray("categories"))
            val schedules = readSchedulesFromJson(root.optJSONArray("schedules"))
            val decisions = readDecisionsFromJson(root.optJSONArray("decisionLogs"))

            saveSettings(settings)
            saveBlockedDomains(blockedDomains)
            saveAllowedDomains(allowedDomains)
            saveCategories(categories)
            saveSchedules(schedules)
            dataStore.edit { preferences ->
                if (decisions.isEmpty()) {
                    preferences.remove(KEY_DECISION_LOGS)
                } else {
                    preferences[KEY_DECISION_LOGS] = encodeDecisionHistory(decisions.take(MAX_LOG_ENTRIES))
                }
            }
        }.isSuccess
    }

    private fun normalizeDomain(value: String): String? {
        val normalized = DomainNormalizer.normalize(value)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun encodeCategories(categories: List<BlockingCategory>): String {
        return categories.joinToString(separator = "\n") { category ->
            val domainSegment = category.domains.joinToString(separator = ",") { escape(it) }
            listOf(
                escape(category.id),
                escape(category.name),
                escape(category.colorHex),
                escape(category.scheduleId),
                if (category.enabled) "1" else "0",
                domainSegment
            ).joinToString(separator = "|")
        }
    }

    private fun decodeCategories(payload: String): List<BlockingCategory> {
        if (payload.isBlank()) return emptyList()
        return payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = splitEncoded(line, '|')
                if (parts.size == 5) {
                    val domains = if (parts[4].isBlank()) emptyList() else splitEncoded(parts[4], ',')
                    BlockingCategory(
                        id = parts[0],
                        name = parts[1],
                        colorHex = parts[2],
                        domains = domains,
                        enabled = parts[3] == "1"
                    )
                } else if (parts.size >= 6) {
                    val domains = if (parts[5].isBlank()) emptyList() else splitEncoded(parts[5], ',')
                    BlockingCategory(
                        id = parts[0],
                        name = parts[1],
                        colorHex = parts[2],
                        scheduleId = parts[3],
                        enabled = parts[4] == "1",
                        domains = domains
                    )
                } else {
                    null
                }
            }
            .toList()
    }

    private fun encodeSchedules(schedules: List<BlockingSchedule>): String {
        return schedules.joinToString(separator = "\n") { schedule ->
            listOf(
                escape(schedule.id),
                escape(schedule.name),
                if (schedule.enabled) "1" else "0",
                schedule.startMinuteOfDay.toString(),
                schedule.endMinuteOfDay.toString(),
                schedule.activeDays.sorted().joinToString(separator = ",")
            ).joinToString(separator = "|")
        }
    }

    private fun decodeSchedules(payload: String): List<BlockingSchedule> {
        if (payload.isBlank()) return emptyList()
        return payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = splitEncoded(line, '|')
                if (parts.size != 6) {
                    return@mapNotNull null
                }

                val startMinute = parts[3].toIntOrNull() ?: return@mapNotNull null
                val endMinute = parts[4].toIntOrNull() ?: return@mapNotNull null
                val activeDays = if (parts[5].isBlank()) {
                    emptySet()
                } else {
                    splitEncoded(parts[5], ',').mapNotNull { it.toIntOrNull() }.toSet()
                }

                BlockingSchedule(
                    id = parts[0],
                    name = parts[1],
                    enabled = parts[2] == "1",
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    activeDays = activeDays
                )
            }
            .toList()
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
                val parts = splitEncoded(line, '|')
                if (parts.size != 4) {
                    return@mapNotNull null
                }

                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val allow = parts[1] == "1"
                Decision(
                    domain = parts[2],
                    allow = allow,
                    reason = parts[3],
                    timestampMillis = timestamp
                )
            }
            .toList()
    }

    private fun splitEncoded(input: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false

        input.forEach { char ->
            when {
                escaping -> {
                    current.append(
                        when (char) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '\\' -> '\\'
                            '|' -> '|'
                            ',' -> ','
                            else -> char
                        }
                    )
                    escaping = false
                }
                char == '\\' -> escaping = true
                char == delimiter -> {
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
                    ',' -> append("\\,")
                    else -> append(char)
                }
            }
        }
    }

    private fun readJsonStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optString(index).takeIf { it.isNotBlank() } ?: continue
                normalizeDomain(value)?.let(::add)
            }
        }
    }

    private fun readCategoriesFromJson(array: JSONArray?): List<BlockingCategory> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val domains = buildList {
                    val domainArray = item.optJSONArray("domains")
                    if (domainArray != null) {
                        for (d in 0 until domainArray.length()) {
                            val domain = domainArray.optString(d).takeIf { it.isNotBlank() } ?: continue
                            normalizeDomain(domain)?.let(::add)
                        }
                    }
                }
                add(
                    BlockingCategory(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        name = item.optString("name", "Category"),
                        colorHex = item.optString("colorHex", CATEGORY_COLORS.first()),
                        scheduleId = item.optString("scheduleId", ""),
                        enabled = item.optBoolean("enabled", true),
                        domains = domains
                    )
                )
            }
        }
    }

    private fun readSchedulesFromJson(array: JSONArray?): List<BlockingSchedule> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val activeDays = buildSet {
                    val daysArray = item.optJSONArray("activeDays")
                    if (daysArray != null) {
                        for (d in 0 until daysArray.length()) {
                            daysArray.optInt(d).let(::add)
                        }
                    }
                }
                add(
                    BlockingSchedule(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        name = item.optString("name", "Schedule"),
                        enabled = item.optBoolean("enabled", true),
                        startMinuteOfDay = item.optInt("startMinuteOfDay", 9 * 60),
                        endMinuteOfDay = item.optInt("endMinuteOfDay", 17 * 60),
                        activeDays = activeDays.ifEmpty { DEFAULT_SCHEDULE_DAYS }
                    )
                )
            }
        }
    }

    private fun readDecisionsFromJson(array: JSONArray?): List<Decision> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    Decision(
                        domain = item.optString("domain", ""),
                        allow = item.optBoolean("allow", true),
                        reason = item.optString("reason", ""),
                        timestampMillis = item.optLong("timestampMillis", System.currentTimeMillis())
                    )
                )
            }
        }
    }
}
