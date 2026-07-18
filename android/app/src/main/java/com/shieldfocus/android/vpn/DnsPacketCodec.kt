package com.shieldfocus.android.vpn

import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DnsQueryPacket(
    val ipVersion: Int,
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress,
    val sourcePort: Int,
    val destinationPort: Int,
    val transactionId: Int,
    val hostname: String,
    val questionBytes: ByteArray,
    val dnsPayload: ByteArray
)

object DnsPacketCodec {
    private const val IPV4_HEADER_MIN_LENGTH = 20
    private const val IPV6_HEADER_LENGTH = 40
    private const val UDP_HEADER_LENGTH = 8
    private const val UDP_PROTOCOL = 17

    fun parse(packet: ByteArray): DnsQueryPacket? {
        if (packet.isEmpty()) return null
        return when (packet[0].toInt() ushr 4) {
            4 -> parseIpv4(packet)
            6 -> parseIpv6(packet)
            else -> null
        }
    }

    fun buildBlockedDnsPayload(query: DnsQueryPacket): ByteArray {
        val output = ByteArrayOutputStream()
        val flags = 0x8183

        writeUint16(output, query.transactionId)
        writeUint16(output, flags)
        writeUint16(output, 1)
        writeUint16(output, 0)
        writeUint16(output, 0)
        writeUint16(output, 0)
        output.write(query.questionBytes)

        return output.toByteArray()
    }

    fun buildCnameDnsPayload(query: DnsQueryPacket, targetHostname: String): ByteArray {
        val output = ByteArrayOutputStream()
        val targetBytes = encodeHostname(targetHostname)

        writeUint16(output, query.transactionId)
        writeUint16(output, 0x8180)
        writeUint16(output, 1)
        writeUint16(output, 1)
        writeUint16(output, 0)
        writeUint16(output, 0)
        output.write(query.questionBytes)
        writeUint16(output, 0xC00C)
        writeUint16(output, 5)
        writeUint16(output, 1)
        output.write(byteArrayOf(0, 0, 1, 44)) // 300-second TTL
        writeUint16(output, targetBytes.size)
        output.write(targetBytes)
        return output.toByteArray()
    }

    private fun encodeHostname(hostname: String): ByteArray {
        val output = ByteArrayOutputStream()
        hostname.trim().trimEnd('.').lowercase().split('.').forEach { label ->
            require(label.isNotEmpty() && label.length <= 63) { "Invalid DNS label" }
            val bytes = label.toByteArray(Charsets.US_ASCII)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        return output.toByteArray()
    }

    fun buildResponsePacket(query: DnsQueryPacket, dnsPayload: ByteArray): ByteArray {
        return when (query.ipVersion) {
            4 -> buildIpv4ResponsePacket(query, dnsPayload)
            6 -> buildIpv6ResponsePacket(query, dnsPayload)
            else -> error("Unsupported IP version ${query.ipVersion}")
        }
    }

    private fun parseIpv4(packet: ByteArray): DnsQueryPacket? {
        if (packet.size < IPV4_HEADER_MIN_LENGTH + UDP_HEADER_LENGTH) return null

        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_HEADER_MIN_LENGTH || packet.size < headerLength + UDP_HEADER_LENGTH) return null
        if ((packet[9].toInt() and 0xFF) != UDP_PROTOCOL) return null

        val sourceAddress = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val destinationAddress = InetAddress.getByAddress(packet.copyOfRange(16, 20))
        if (sourceAddress !is Inet4Address || destinationAddress !is Inet4Address) return null

        return parseDnsQuery(
            packet = packet,
            ipVersion = 4,
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            udpOffset = headerLength
        )
    }

    private fun parseIpv6(packet: ByteArray): DnsQueryPacket? {
        if (packet.size < IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH) return null
        if ((packet[6].toInt() and 0xFF) != UDP_PROTOCOL) return null

        val payloadLength = readUint16(packet, 4)
        if (payloadLength < UDP_HEADER_LENGTH || packet.size < IPV6_HEADER_LENGTH + payloadLength) return null

        val sourceAddress = InetAddress.getByAddress(packet.copyOfRange(8, 24))
        val destinationAddress = InetAddress.getByAddress(packet.copyOfRange(24, 40))
        if (sourceAddress !is Inet6Address || destinationAddress !is Inet6Address) return null

        return parseDnsQuery(
            packet = packet.copyOf(IPV6_HEADER_LENGTH + payloadLength),
            ipVersion = 6,
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            udpOffset = IPV6_HEADER_LENGTH
        )
    }

    private fun parseDnsQuery(
        packet: ByteArray,
        ipVersion: Int,
        sourceAddress: InetAddress,
        destinationAddress: InetAddress,
        udpOffset: Int
    ): DnsQueryPacket? {
        if (packet.size < udpOffset + UDP_HEADER_LENGTH) return null

        val sourcePort = readUint16(packet, udpOffset)
        val destinationPort = readUint16(packet, udpOffset + 2)
        val udpLength = readUint16(packet, udpOffset + 4)
        if (sourcePort <= 0 || destinationPort <= 0 || udpLength < UDP_HEADER_LENGTH) return null
        if (udpOffset + udpLength > packet.size) return null

        val dnsOffset = udpOffset + UDP_HEADER_LENGTH
        val dnsPayload = packet.copyOfRange(dnsOffset, udpOffset + udpLength)
        if (dnsPayload.size < 12) return null

        val transactionId = readUint16(dnsPayload, 0)
        if (readUint16(dnsPayload, 4) < 1) return null

        var index = 12
        val labels = mutableListOf<String>()
        while (index < dnsPayload.size) {
            val labelLength = dnsPayload[index].toInt() and 0xFF
            index += 1
            if (labelLength == 0) break
            if (labelLength > 63 || index + labelLength > dnsPayload.size) return null
            labels += String(dnsPayload, index, labelLength, Charsets.UTF_8)
            index += labelLength
        }

        if (labels.isEmpty() || index + 4 > dnsPayload.size) return null
        val questionEnd = index + 4

        return DnsQueryPacket(
            ipVersion = ipVersion,
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            transactionId = transactionId,
            hostname = labels.joinToString(".").lowercase(),
            questionBytes = dnsPayload.copyOfRange(12, questionEnd),
            dnsPayload = dnsPayload
        )
    }

    private fun buildIpv4ResponsePacket(query: DnsQueryPacket, dnsPayload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + dnsPayload.size
        val totalLength = IPV4_HEADER_MIN_LENGTH + udpLength
        val packet = ByteArray(totalLength)
        val header = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        header.put(((4 shl 4) or 5).toByte())
        header.put(0)
        header.putShort(totalLength.toShort())
        header.putShort(0.toShort())
        header.putShort(0.toShort())
        header.put(64.toByte())
        header.put(UDP_PROTOCOL.toByte())
        header.putShort(0.toShort())
        header.put(query.destinationAddress.address)
        header.put(query.sourceAddress.address)

        val checksum = internetChecksum(packet, 0, IPV4_HEADER_MIN_LENGTH)
        writeUint16(packet, 10, checksum)

        val udpOffset = IPV4_HEADER_MIN_LENGTH
        writeUdpResponse(packet, udpOffset, query, dnsPayload)
        return packet
    }

    private fun buildIpv6ResponsePacket(query: DnsQueryPacket, dnsPayload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + dnsPayload.size
        val packet = ByteArray(IPV6_HEADER_LENGTH + udpLength)
        val header = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        header.putInt(6 shl 28)
        header.putShort(udpLength.toShort())
        header.put(UDP_PROTOCOL.toByte())
        header.put(64.toByte())
        header.put(query.destinationAddress.address)
        header.put(query.sourceAddress.address)

        val udpOffset = IPV6_HEADER_LENGTH
        writeUdpResponse(packet, udpOffset, query, dnsPayload)
        val checksum = ipv6UdpChecksum(
            sourceAddress = query.destinationAddress.address,
            destinationAddress = query.sourceAddress.address,
            packet = packet,
            udpOffset = udpOffset,
            udpLength = udpLength
        )
        writeUint16(packet, udpOffset + 6, if (checksum == 0) 0xFFFF else checksum)
        return packet
    }

    private fun writeUdpResponse(
        packet: ByteArray,
        udpOffset: Int,
        query: DnsQueryPacket,
        dnsPayload: ByteArray
    ) {
        val udpLength = UDP_HEADER_LENGTH + dnsPayload.size
        writeUint16(packet, udpOffset, query.destinationPort)
        writeUint16(packet, udpOffset + 2, query.sourcePort)
        writeUint16(packet, udpOffset + 4, udpLength)
        writeUint16(packet, udpOffset + 6, 0)
        System.arraycopy(dnsPayload, 0, packet, udpOffset + UDP_HEADER_LENGTH, dnsPayload.size)
    }

    private fun ipv6UdpChecksum(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L
        sum = addChecksumBytes(sum, sourceAddress, 0, sourceAddress.size)
        sum = addChecksumBytes(sum, destinationAddress, 0, destinationAddress.size)
        sum += (udpLength ushr 16) and 0xFFFF
        sum += udpLength and 0xFFFF
        sum += UDP_PROTOCOL
        sum = addChecksumBytes(sum, packet, udpOffset, udpLength)
        return finalizeChecksum(sum)
    }

    private fun internetChecksum(buffer: ByteArray, start: Int, length: Int): Int {
        return finalizeChecksum(addChecksumBytes(0, buffer, start, length))
    }

    private fun addChecksumBytes(initial: Long, buffer: ByteArray, start: Int, length: Int): Long {
        var sum = initial
        var index = start
        val end = start + length
        while (index < end) {
            val high = buffer[index].toInt() and 0xFF
            val low = if (index + 1 < end) buffer[index + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            index += 2
        }
        return sum
    }

    private fun finalizeChecksum(initial: Long): Int {
        var sum = initial
        while (sum ushr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.toInt().inv() and 0xFFFF
    }

    private fun writeUint16(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun writeUint16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun readUint16(buffer: ByteArray, offset: Int): Int {
        if (offset + 1 >= buffer.size) return 0
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
