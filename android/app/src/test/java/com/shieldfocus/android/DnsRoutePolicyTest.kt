package com.shieldfocus.android

import com.shieldfocus.android.vpn.DnsRoutePolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DnsRoutePolicyTest {
    private val network = addresses("192.0.2.53", "2001:db8::53")
    private val fallback = addresses("1.1.1.1", "2606:4700:4700::1111")
    @Test
    fun ipv4OnlySelectsOnlyIpv4NetworkRoutes() {
        val selection = DnsRoutePolicy.select(network, fallback, true, false)

        assertEquals(listOf("192.0.2.53"), selection.networkDnsServers.mapNotNull { it.hostAddress })
        assertTrue(selection.routedDnsServers.all { it.address.size == 4 })
        assertEquals(1, selection.routedDnsServers.size)
        assertEquals("10.10.0.1", selection.routedDnsServers.single().hostAddress)
    }

    @Test
    fun ipv6OnlySelectsOnlyIpv6NetworkRoutes() {
        val selection = DnsRoutePolicy.select(network, fallback, false, true)

        assertTrue(selection.networkDnsServers.all { it.address.size == 16 })
        assertTrue(selection.routedDnsServers.all { it.address.size == 16 })
        assertEquals(1, selection.routedDnsServers.size)
        assertEquals(InetAddress.getByName("fd00:1:fd00:1::2"), selection.routedDnsServers.single())
    }

    @Test
    fun dualStackKeepsBothFamiliesAndDeduplicatesRoutes() {
        val selection = DnsRoutePolicy.select(
            networkDnsServers = network,
            fallbackDnsServers = fallback,
            ipv4Enabled = true,
            ipv6Enabled = true
        )

        assertEquals(2, selection.networkDnsServers.size)
        assertEquals(2, selection.routedDnsServers.size)
    }

    @Test
    fun usesEnabledFamilyFallbackWhenNetworkHasNoCompatibleResolver() {
        val selection = DnsRoutePolicy.select(
            networkDnsServers = addresses("192.0.2.53"),
            fallbackDnsServers = fallback,
            ipv4Enabled = false,
            ipv6Enabled = true
        )

        assertEquals(1, selection.networkDnsServers.size)
        assertArrayEquals(fallback[1].address, selection.networkDnsServers.single().address)
        assertEquals(InetAddress.getByName("fd00:1:fd00:1::2"), selection.routedDnsServers.single())
    }

    private fun addresses(vararg values: String): List<InetAddress> = values.map(InetAddress::getByName)
}
