package com.shieldfocus.android.model

object ProtectionPresetCatalog {
    val categoryIdsByPreset: Map<String, Set<String>> = mapOf(
        "shieldfocus-default" to setOf("adult-content", "malware-phishing"),
        "family-protection" to setOf("adult-content", "gambling", "violence"),
        "kids-mode" to setOf("adult-content", "social-media", "youtube"),
        "hagezi-pro" to setOf("ad-networks", "trackers", "malware-phishing"),
        "oisd-big" to setOf("ad-networks", "trackers", "telemetry"),
        "malware-list" to setOf("malware-phishing"),
        "energized-ultimate" to setOf("adult-content", "malware-phishing", "gambling", "ad-networks", "trackers", "social-media")
    )

    fun categoriesFor(presetIds: Set<String>): Set<String> =
        presetIds.flatMapTo(mutableSetOf()) { categoryIdsByPreset[it].orEmpty() }

    val allCategoryIds: Set<String> = categoryIdsByPreset.values.flatten().toSet()

    fun effectiveCategories(enabledPresetIds: Set<String>, activeCategoryIds: Set<String>): Set<String> =
        activeCategoryIds

    fun canDisableCategory(
        categoryId: String,
        enabledPresetIds: Set<String>,
        activeCategoryIds: Set<String>
    ): Boolean = categoryId in activeCategoryIds
}
