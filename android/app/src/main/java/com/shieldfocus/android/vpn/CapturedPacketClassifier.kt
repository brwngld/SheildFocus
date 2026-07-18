package com.shieldfocus.android.vpn

enum class CapturedPacketType {
    UdpDns,
    TcpDns,
    DnsOverTls,
    Unsupported,
    Malformed
}

object CapturedPacketClassifier {
    fun classify(packet: ByteArray): CapturedPacketType {
        if (packet.isEmpty()) return CapturedPacketType.Malformed
        val version = packet[0].toInt() ushr 4
        val protocolOffset: Int
        val transportOffset: Int
        when (version) {
            4 -> {
                if (packet.size < 20) return CapturedPacketType.Malformed
                val headerLength = (packet[0].toInt() and 0x0F) * 4
                if (headerLength < 20 || packet.size < headerLength + 4) return CapturedPacketType.Malformed
                protocolOffset = 9
                transportOffset = headerLength
            }
            6 -> {
                if (packet.size < 44) return CapturedPacketType.Malformed
                protocolOffset = 6
                transportOffset = 40
            }
            else -> return CapturedPacketType.Malformed
        }
        val protocol = packet[protocolOffset].toInt() and 0xFF
        if (protocol != 6 && protocol != 17) return CapturedPacketType.Unsupported
        val destinationPort = readUint16(packet, transportOffset + 2)
        return when {
            protocol == 17 && destinationPort == 53 -> CapturedPacketType.UdpDns
            protocol == 6 && destinationPort == 53 -> CapturedPacketType.TcpDns
            protocol == 6 && destinationPort == 853 -> CapturedPacketType.DnsOverTls
            else -> CapturedPacketType.Unsupported
        }
    }

    private fun readUint16(buffer: ByteArray, offset: Int): Int {
        if (offset + 1 >= buffer.size) return -1
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
