package com.shieldfocus.android.vpn

import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DnsQueryPacket(
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
    private const val UDP_HEADER_LENGTH = 8

    fun parse(packet: ByteArray): DnsQueryPacket? {
        if (packet.size < IPV4_HEADER_MIN_LENGTH + UDP_HEADER_LENGTH) {
            return null
        }

        val version = packet[0].toInt() ushr 4
        if (version != 4) {
            return null
        }

        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_HEADER_MIN_LENGTH || packet.size < headerLength + UDP_HEADER_LENGTH) {
            return null
        }

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) {
            return null
        }

        val sourceAddress = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val destinationAddress = InetAddress.getByAddress(packet.copyOfRange(16, 20))
        if (sourceAddress !is Inet4Address || destinationAddress !is Inet4Address) {
            return null
        }

        val udpOffset = headerLength
        val sourcePort = readUint16(packet, udpOffset)
        val destinationPort = readUint16(packet, udpOffset + 2)
        if (sourcePort <= 0 || destinationPort <= 0) {
            return null
        }

        val dnsOffset = udpOffset + UDP_HEADER_LENGTH
        if (dnsOffset >= packet.size) {
            return null
        }

        val dnsPayload = packet.copyOfRange(dnsOffset, packet.size)
        if (dnsPayload.size < 12) {
            return null
        }

        val transactionId = readUint16(dnsPayload, 0)
        val questionCount = readUint16(dnsPayload, 4)
        if (questionCount < 1) {
            return null
        }

        var index = 12
        val labels = mutableListOf<String>()

        while (index < dnsPayload.size) {
            val labelLength = dnsPayload[index].toInt() and 0xFF
            index += 1

            if (labelLength == 0) {
                break
            }

            if (index + labelLength > dnsPayload.size) {
                return null
            }

            labels += String(dnsPayload, index, labelLength, Charsets.UTF_8)
            index += labelLength
        }

        if (index + 4 > dnsPayload.size) {
            return null
        }

        val hostname = labels.joinToString(".").lowercase()
        val questionEnd = index + 4
        val questionBytes = dnsPayload.copyOfRange(12, questionEnd)

        return DnsQueryPacket(
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            transactionId = transactionId,
            hostname = hostname,
            questionBytes = questionBytes,
            dnsPayload = dnsPayload
        )
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

    fun buildResponsePacket(query: DnsQueryPacket, dnsPayload: ByteArray): ByteArray {
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
        header.put(17.toByte())
        header.putShort(0.toShort())
        header.put(query.destinationAddress.address)
        header.put(query.sourceAddress.address)

        val checksum = ipv4Checksum(packet, 0, IPV4_HEADER_MIN_LENGTH)
        packet[10] = ((checksum ushr 8) and 0xFF).toByte()
        packet[11] = (checksum and 0xFF).toByte()

        val udpOffset = IPV4_HEADER_MIN_LENGTH
        writeUint16(packet, udpOffset, query.destinationPort)
        writeUint16(packet, udpOffset + 2, query.sourcePort)
        writeUint16(packet, udpOffset + 4, udpLength)
        writeUint16(packet, udpOffset + 6, 0)
        System.arraycopy(dnsPayload, 0, packet, udpOffset + UDP_HEADER_LENGTH, dnsPayload.size)

        return packet
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
        if (offset + 1 >= buffer.size) {
            return 0
        }
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }

    private fun ipv4Checksum(buffer: ByteArray, start: Int, length: Int): Int {
        var sum = 0
        var index = start
        val end = start + length

        while (index < end) {
            val high = buffer[index].toInt() and 0xFF
            val low = if (index + 1 < end) buffer[index + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            index += 2
        }

        return sum.inv() and 0xFFFF
    }
}
