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
    private val strict = addresses("8.8.8.8", "2001:4860:4860::8888")

    @Test
    fun ipv4OnlySelectsOnlyIpv4NetworkAndStrictRoutes() {
        val selection = DnsRoutePolicy.select(network, fallback, strict, true, true, false)

        assertEquals(listOf("192.0.2.53"), selection.networkDnsServers.mapNotNull { it.hostAddress })
        assertTrue(selection.routedDnsServers.all { it.address.size == 4 })
        assertEquals(2, selection.routedDnsServers.size)
    }

    @Test
    fun ipv6OnlySelectsOnlyIpv6NetworkAndStrictRoutes() {
        val selection = DnsRoutePolicy.select(network, fallback, strict, true, false, true)

        assertTrue(selection.networkDnsServers.all { it.address.size == 16 })
        assertTrue(selection.routedDnsServers.all { it.address.size == 16 })
        assertEquals(2, selection.routedDnsServers.size)
    }

    @Test
    fun dualStackKeepsBothFamiliesAndDeduplicatesRoutes() {
        val selection = DnsRoutePolicy.select(
            networkDnsServers = network,
            fallbackDnsServers = fallback,
            strictDnsResolvers = network + strict,
            strictMode = true,
            ipv4Enabled = true,
            ipv6Enabled = true
        )

        assertEquals(2, selection.networkDnsServers.size)
        assertEquals(4, selection.routedDnsServers.size)
    }

    @Test
    fun usesEnabledFamilyFallbackWhenNetworkHasNoCompatibleResolver() {
        val selection = DnsRoutePolicy.select(
            networkDnsServers = addresses("192.0.2.53"),
            fallbackDnsServers = fallback,
            strictDnsResolvers = strict,
            strictMode = false,
            ipv4Enabled = false,
            ipv6Enabled = true
        )

        assertEquals(1, selection.networkDnsServers.size)
        assertArrayEquals(fallback[1].address, selection.networkDnsServers.single().address)
        assertEquals(selection.networkDnsServers, selection.routedDnsServers)
    }

    private fun addresses(vararg values: String): List<InetAddress> = values.map(InetAddress::getByName)
}
