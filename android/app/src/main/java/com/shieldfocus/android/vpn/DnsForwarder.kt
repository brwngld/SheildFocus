package com.shieldfocus.android.vpn

import android.net.VpnService
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException

sealed class DnsForwardResult {
    data class Success(val payload: ByteArray) : DnsForwardResult()
    data object Timeout : DnsForwardResult()
    data object Truncated : DnsForwardResult()
    data object MalformedResponse : DnsForwardResult()
    data object SocketFailure : DnsForwardResult()
    data object ResolverFailure : DnsForwardResult()
}

object DnsForwarder {
    private const val TAG = "ShieldFocusDns"
    private const val MAX_RESOLVER_ATTEMPTS = 2
    private const val MAX_INTERACTIVE_TIMEOUT_MILLIS = 1_200

    /** Compatibility API. New runtime code uses [forwardResult] to retain failure details. */
    fun forward(
        query: DnsQueryPacket,
        vpnService: VpnService,
        timeoutMillis: Int = 2_000,
        fallbackServers: List<InetAddress> = emptyList()
    ): ByteArray? = (forwardResult(query, vpnService, timeoutMillis, fallbackServers) as? DnsForwardResult.Success)?.payload

    fun forwardResult(
        query: DnsQueryPacket,
        vpnService: VpnService,
        timeoutMillis: Int,
        fallbackServers: List<InetAddress>
    ): DnsForwardResult {
        val servers = (listOf(query.destinationAddress) + fallbackServers)
            .filter { it.address.size == query.destinationAddress.address.size }
            .distinctBy { it.hostAddress }
            .take(MAX_RESOLVER_ATTEMPTS)
        var lastFailure: DnsForwardResult = DnsForwardResult.ResolverFailure
        servers.forEach { server ->
            when (val result = forwardTo(query, server, vpnService, timeoutMillis)) {
                is DnsForwardResult.Success -> return result
                DnsForwardResult.Truncated -> {
                    // TCP fallback is intentionally not attempted: this DNS-only VPN has no TCP stack.
                    Log.w(TAG, "Resolver returned a truncated UDP DNS response; trying bounded fallback")
                    lastFailure = result
                }
                else -> lastFailure = result
            }
        }
        return lastFailure
    }

    private fun forwardTo(
        query: DnsQueryPacket,
        server: InetAddress,
        vpnService: VpnService,
        timeoutMillis: Int
    ): DnsForwardResult {
        val socket = DatagramSocket()
        return try {
            if (!vpnService.protect(socket)) {
                Log.w(TAG, "Could not protect resolver socket from the VPN")
                return DnsForwardResult.SocketFailure
            }
            socket.soTimeout = timeoutMillis.coerceIn(300, MAX_INTERACTIVE_TIMEOUT_MILLIS)
            socket.send(DatagramPacket(query.dnsPayload, query.dnsPayload.size, server, 53))
            val buffer = ByteArray(4_096)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val payload = response.data.copyOf(response.length)
            when {
                !isValidResponse(query, payload) -> {
                    Log.w(TAG, "Resolver returned a malformed or mismatched DNS response")
                    DnsForwardResult.MalformedResponse
                }
                isTruncated(payload) -> DnsForwardResult.Truncated
                else -> DnsForwardResult.Success(payload)
            }
        } catch (_: SocketTimeoutException) {
            Log.w(TAG, "DNS resolver timed out")
            DnsForwardResult.Timeout
        } catch (_: SocketException) {
            Log.w(TAG, "DNS resolver socket failed")
            DnsForwardResult.SocketFailure
        } catch (_: Exception) {
            Log.w(TAG, "DNS resolver request failed")
            DnsForwardResult.ResolverFailure
        } finally {
            socket.close()
        }
    }

    private fun isValidResponse(query: DnsQueryPacket, payload: ByteArray): Boolean {
        if (payload.size < 12) return false
        val transactionId = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val isResponse = payload[2].toInt() and 0x80 != 0
        return transactionId == query.transactionId && isResponse
    }

    private fun isTruncated(payload: ByteArray): Boolean =
        payload.size >= 4 && payload[2].toInt() and 0x02 != 0
}
