package com.shieldfocus.android

import com.shieldfocus.android.domain.DomainNormalizer
import com.shieldfocus.android.model.BlockingSchedule
import com.shieldfocus.android.vpn.DomainPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DomainLogicTest {

    @Test
    fun normalizeStripsSchemeWwwAndPath() {
        assertEquals(
            "example.com",
            DomainNormalizer.normalize("https://www.Example.com/path?q=1")
        )
    }

    @Test
    fun allowlistOverridesBlockedDomains() {
        val decision = DomainPolicy.decide(
            hostname = "sub.example.com",
            blockedDomains = setOf("example.com"),
            allowedDomains = setOf("example.com"),
            strictMode = true
        )

        assertTrue(decision.allow)
        assertEquals("allowlist", decision.reason)
    }

    @Test
    fun knownAdultDomainIsBlocked() {
        val decision = DomainPolicy.decide(
            hostname = "adult.example",
            blockedDomains = emptySet(),
            allowedDomains = emptySet(),
            strictMode = false
        )

        assertFalse(decision.allow)
        assertEquals("blocked-domain", decision.reason)
    }

    @Test
    fun scheduleIsActiveDuringConfiguredWindow() {
        val schedule = BlockingSchedule(
            id = "work-hours",
            name = "Work Hours",
            activeDays = setOf(Calendar.MONDAY),
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            enabled = true
        )

        val activeTime = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 5)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val inactiveTime = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 5)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertTrue(schedule.isActiveAt(activeTime))
        assertFalse(schedule.isActiveAt(inactiveTime))
    }
}
