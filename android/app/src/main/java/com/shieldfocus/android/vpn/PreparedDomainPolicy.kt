package com.shieldfocus.android.vpn

import com.shieldfocus.android.domain.DomainNormalizer
import java.util.Collections
import java.util.LinkedHashMap

enum class BlockCategory(val reasonKey: String) {
    Adult("adult"),
    Malware("malware"),
    Advertising("advertising"),
    Tracking("tracking"),
    Gambling("gambling"),
    Custom("custom")
}

enum class DecisionReason {
    Allow,
    Allowlist,
    ExactRule,
    ParentRule,
    StrictVariant,
    InvalidHostname
}

data class IndexedDomainDecision(
    val allow: Boolean,
    val matchedRule: String? = null,
    val category: BlockCategory? = null,
    val reason: DecisionReason
) {
    fun logReason(): String = when {
        allow && reason == DecisionReason.Allowlist -> "allowlist"
        allow -> "allow"
        reason == DecisionReason.StrictVariant -> "blocked:strict:${category?.reasonKey ?: "custom"}"
        else -> "blocked:${category?.reasonKey ?: "custom"}"
    }
}

data class CategorizedDomainRules(
    val category: BlockCategory,
    val domains: Collection<String>
)

class PreparedDomainPolicy private constructor(
    val version: Long,
    private val strictMode: Boolean,
    private val blockedRules: Map<String, BlockCategory>,
    private val allowedRules: Set<String>,
    private val strictRootIndex: Map<String, BlockCategory>,
    private val strictLabelIndex: Map<String, BlockCategory>,
    private val cache: MutableMap<String, IndexedDomainDecision>
) {
    fun decide(hostname: String): IndexedDomainDecision {
        val normalized = DomainNormalizer.normalize(hostname)
        if (normalized.isBlank()) {
            return IndexedDomainDecision(true, reason = DecisionReason.InvalidHostname)
        }
        synchronized(cache) { cache[normalized]?.let { return it } }
        val decision = decideNormalized(normalized)
        synchronized(cache) { cache[normalized] = decision }
        return decision
    }

    private fun decideNormalized(hostname: String): IndexedDomainDecision {
        val suffixes = suffixes(hostname)
        suffixes.firstOrNull { it in allowedRules }?.let { rule ->
            return IndexedDomainDecision(true, matchedRule = rule, reason = DecisionReason.Allowlist)
        }
        suffixes.forEachIndexed { index, rule ->
            blockedRules[rule]?.let { category ->
                return IndexedDomainDecision(
                    allow = false,
                    matchedRule = rule,
                    category = category,
                    reason = if (index == 0) DecisionReason.ExactRule else DecisionReason.ParentRule
                )
            }
        }
        if (strictMode) {
            strictCandidate(hostname)?.let { candidate ->
                val matchedKey = "${candidate.first}.${candidate.second}"
                val category = strictRootIndex[matchedKey] ?: strictLabelIndex[candidate.first]
                category?.let {
                    return IndexedDomainDecision(false, matchedKey, it, DecisionReason.StrictVariant)
                }
            }
        }
        return IndexedDomainDecision(true, reason = DecisionReason.Allow)
    }

    private fun strictCandidate(hostname: String): Pair<String, String>? {
        val root = rootParts(hostname) ?: return null
        val stripped = stripReviewedAffixes(root.first)
        if (stripped == root.first || stripped.length < 5) return null
        return stripped to root.second
    }

    private fun stripReviewedAffixes(label: String): String {
        var value = label.trim('-').trimEnd { it.isDigit() }.trim('-')
        STRICT_AFFIXES.forEach { affix ->
            value = value.removePrefix("$affix-").removePrefix(affix)
            value = value.removeSuffix("-$affix").removeSuffix(affix)
        }
        return value.trim('-').trimEnd { it.isDigit() }
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 2_048
        private val STRICT_AFFIXES = listOf("mirror", "proxy", "unblocked", "official", "online", "alt", "free", "go", "my", "new")
        private val COUNTRY_SECOND_LEVEL = setOf("co", "com", "net", "org")

        fun build(
            version: Long,
            strictMode: Boolean,
            categorizedRules: Collection<CategorizedDomainRules>,
            manualRules: Collection<String>,
            allowedRules: Collection<String>
        ): PreparedDomainPolicy {
            val blocked = LinkedHashMap<String, BlockCategory>()
            categorizedRules.forEach { source ->
                source.domains.forEach { raw ->
                    normalizeRule(raw)?.let { blocked.putIfAbsent(it, source.category) }
                }
            }
            manualRules.forEach { raw -> normalizeRule(raw)?.let { blocked[it] = BlockCategory.Custom } }
            val allowed = allowedRules.mapNotNullTo(linkedSetOf(), ::normalizeRule)
            val strictIndex = LinkedHashMap<String, BlockCategory>()
            val strictLabels = LinkedHashMap<String, BlockCategory>()
            blocked.forEach { (rule, category) ->
                rootParts(rule)?.let {
                    strictIndex.putIfAbsent("${it.first}.${it.second}", category)
                    strictLabels.putIfAbsent(it.first, category)
                }
            }
            val lru = Collections.synchronizedMap(object : LinkedHashMap<String, IndexedDomainDecision>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IndexedDomainDecision>?): Boolean =
                    size > MAX_CACHE_ENTRIES
            })
            return PreparedDomainPolicy(
                version,
                strictMode,
                blocked.toMap(),
                allowed.toSet(),
                strictIndex.toMap(),
                strictLabels.toMap(),
                lru
            )
        }

        private fun normalizeRule(raw: String): String? {
            val normalized = DomainNormalizer.normalize(raw.trim().trimEnd('.'))
            if (normalized.isBlank() || normalized.length > 253 || '.' !in normalized || normalized.any(Char::isWhitespace)) return null
            val labels = normalized.split('.')
            if (labels.any { label ->
                    label.isEmpty() || label.length > 63 ||
                        label.startsWith('-') || label.endsWith('-') ||
                        label.any { character -> !character.isLetterOrDigit() && character != '-' && character != '_' }
                }) return null
            return normalized
        }

        private fun suffixes(hostname: String): List<String> {
            val labels = hostname.split('.')
            if (labels.size < 2) return listOf(hostname)
            return (0 until labels.lastIndex).map { labels.drop(it).joinToString(".") }
        }

        private fun rootParts(domain: String): Pair<String, String>? {
            val labels = domain.split('.')
            val rootIndex = when {
                labels.size == 2 -> 0
                labels.size == 3 && labels.last().length == 2 && labels[1] in COUNTRY_SECOND_LEVEL -> 0
                else -> return null
            }
            val label = labels[rootIndex].filter(Char::isLetterOrDigit)
            if (label.length < 5) return null
            return label to labels.drop(rootIndex + 1).joinToString(".")
        }
    }
}
