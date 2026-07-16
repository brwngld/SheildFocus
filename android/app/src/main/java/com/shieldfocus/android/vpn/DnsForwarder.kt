package com.shieldfocus.android.vpn

import android.net.VpnService
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object DnsForwarder {
    fun forward(query: DnsQueryPacket, vpnService: VpnService): ByteArray? {
        val socket = DatagramSocket()

        return try {
            if (!vpnService.protect(socket)) {
                return null
            }

            socket.soTimeout = 2_000

            val request = DatagramPacket(
                query.dnsPayload,
                query.dnsPayload.size,
                query.destinationAddress,
                query.destinationPort
            )
            socket.send(request)

            val buffer = ByteArray(1500)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            response.data.copyOf(response.length)
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }
}
