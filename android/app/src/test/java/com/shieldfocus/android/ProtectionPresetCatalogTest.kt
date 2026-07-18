package com.shieldfocus.android

import com.shieldfocus.android.model.ProtectionPresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtectionPresetCatalogTest {
    @Test
    fun disablingOnePresetRetainsCategoriesFromOtherEnabledPresets() {
        val initiallyEnabled = setOf("shieldfocus-default", "family-protection", "kids-mode")
        val afterDefaultDisabled = initiallyEnabled - "shieldfocus-default"

        val categories = ProtectionPresetCatalog.categoriesFor(afterDefaultDisabled)

        assertTrue("adult-content" in categories)
        assertTrue("gambling" in categories)
        assertTrue("violence" in categories)
        assertTrue("social-media" in categories)
        assertTrue("youtube" in categories)
        assertEquals(false, "malware-phishing" in categories)
    }

    @Test
    fun shieldFocusDefaultDoesNotContainGambling() {
        val categories = ProtectionPresetCatalog.categoryIdsByPreset.getValue("shieldfocus-default")
        assertFalse("gambling" in categories)
    }

    @Test
    fun categoryStateDoesNotDependOnEnabledPresets() {
        val enabled = setOf("shieldfocus-default", "family-protection")
        val selected = setOf("adult-content", "gambling", "trackers")
        val effective = ProtectionPresetCatalog.effectiveCategories(enabled, selected)

        assertTrue("adult-content" in effective)
        assertTrue("gambling" in effective)
        assertTrue("trackers" in effective)
        assertFalse("malware-phishing" in effective)
        assertEquals(setOf("shieldfocus-default", "family-protection"), enabled)
    }

    @Test
    fun categoryCanBeDisabledWhilePresetRemainsEnabled() {
        assertTrue(
            ProtectionPresetCatalog.canDisableCategory(
                categoryId = "malware-phishing",
                enabledPresetIds = setOf("malware-list"),
                activeCategoryIds = setOf("malware-phishing")
            )
        )
        assertFalse(
            "malware-phishing" in ProtectionPresetCatalog.effectiveCategories(
                enabledPresetIds = setOf("malware-list"),
                activeCategoryIds = emptySet()
            )
        )
    }

    @Test
    fun categoryWithoutAnEnabledPresetIsStillEnforced() {
        assertEquals(
            setOf("gambling"),
            ProtectionPresetCatalog.effectiveCategories(
                enabledPresetIds = emptySet(),
                activeCategoryIds = setOf("gambling")
            )
        )
    }
}
