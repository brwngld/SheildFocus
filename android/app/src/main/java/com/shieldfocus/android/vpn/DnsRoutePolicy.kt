package com.shieldfocus.android.vpn

import java.net.InetAddress

data class DnsRouteSelection(
    val networkDnsServers: List<InetAddress>,
    val routedDnsServers: List<InetAddress>
)

object DnsRoutePolicy {
    fun select(
        networkDnsServers: List<InetAddress>,
        fallbackDnsServers: List<InetAddress>,
        ipv4Enabled: Boolean,
        ipv6Enabled: Boolean
    ): DnsRouteSelection {
        val enabledNetworkServers = networkDnsServers
            .filter { isAddressFamilyEnabled(it, ipv4Enabled, ipv6Enabled) }
            .ifEmpty {
                fallbackDnsServers.filter { isAddressFamilyEnabled(it, ipv4Enabled, ipv6Enabled) }
            }
            .distinctBy { it.hostAddress }

        return DnsRouteSelection(
            networkDnsServers = enabledNetworkServers,
            // Only virtual DNS endpoints belong in the TUN. Routing a real resolver's
            // entire IP captures TCP DNS, DoT, HTTPS and other traffic that this
            // DNS-only packet loop cannot forward.
            routedDnsServers = buildList {
                if (ipv4Enabled) add(InetAddress.getByName(VIRTUAL_IPV4_DNS))
                if (ipv6Enabled) add(InetAddress.getByName(VIRTUAL_IPV6_DNS))
            }
        )
    }

    fun isAddressFamilyEnabled(address: InetAddress, ipv4Enabled: Boolean, ipv6Enabled: Boolean): Boolean {
        return if (address.address.size == 4) ipv4Enabled else ipv6Enabled
    }

    const val VIRTUAL_IPV4_DNS = "10.10.0.1"
    const val VIRTUAL_IPV6_DNS = "fd00:1:fd00:1::2"
}
