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
        strictDnsResolvers: List<InetAddress>,
        strictMode: Boolean,
        ipv4Enabled: Boolean,
        ipv6Enabled: Boolean
    ): DnsRouteSelection {
        val enabledNetworkServers = networkDnsServers
            .filter { isAddressFamilyEnabled(it, ipv4Enabled, ipv6Enabled) }
            .ifEmpty {
                fallbackDnsServers.filter { isAddressFamilyEnabled(it, ipv4Enabled, ipv6Enabled) }
            }
            .distinctBy { it.hostAddress }

        val enabledStrictResolvers = if (strictMode) {
            strictDnsResolvers.filter { isAddressFamilyEnabled(it, ipv4Enabled, ipv6Enabled) }
        } else {
            emptyList()
        }

        return DnsRouteSelection(
            networkDnsServers = enabledNetworkServers,
            routedDnsServers = (enabledNetworkServers + enabledStrictResolvers).distinctBy { it.hostAddress }
        )
    }

    fun isAddressFamilyEnabled(address: InetAddress, ipv4Enabled: Boolean, ipv6Enabled: Boolean): Boolean {
        return if (address.address.size == 4) ipv4Enabled else ipv6Enabled
    }
}
