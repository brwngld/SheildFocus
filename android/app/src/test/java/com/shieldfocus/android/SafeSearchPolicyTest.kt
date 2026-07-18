package com.shieldfocus.android

import com.shieldfocus.android.vpn.SafeSearchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeSearchPolicyTest {
    @Test
    fun mapsGoogleSearchDomainsWhenEnabled() {
        listOf("google.com", "www.google.com", "www.google.co.uk", "google.com.gh").forEach { domain ->
            assertEquals(SafeSearchPolicy.GOOGLE_SAFE_SEARCH_TARGET, SafeSearchPolicy.cnameTarget(domain, true))
        }
    }

    @Test
    fun doesNotMapUnrelatedServicesOrDisabledSearch() {
        assertNull(SafeSearchPolicy.cnameTarget("mail.google.com", true))
        assertNull(SafeSearchPolicy.cnameTarget("notgoogle.com", true))
        assertNull(SafeSearchPolicy.cnameTarget("www.google.com", false))
    }
}
