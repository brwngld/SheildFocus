package com.shieldfocus.android

import com.shieldfocus.android.vpn.DnsPacketCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress

class DnsPacketCodecTest {

    @Test
    fun parsesDirectIpv6UdpDnsQuery() {
        val source = InetAddress.getByName("fd00:1:fd00:1::2")
        val destination = InetAddress.getByName("2606:4700:4700::1111")
        val packet = ipv6DnsQuery(source.address, destination.address, "adult.example")

        val query = DnsPacketCodec.parse(packet)

        assertNotNull(query)
        assertEquals(6, query?.ipVersion)
        assertEquals("adult.example", query?.hostname)
        assertEquals(53000, query?.sourcePort)
        assertEquals(53, query?.destinationPort)
        assertArrayEquals(source.address, query?.sourceAddress?.address)
        assertArrayEquals(destination.address, query?.destinationAddress?.address)
    }

    @Test
    fun buildsValidBlockedIpv6UdpResponse() {
        val source = InetAddress.getByName("fd00:1:fd00:1::2")
        val destination = InetAddress.getByName("2001:4860:4860::8888")
        val query = requireNotNull(
            DnsPacketCodec.parse(ipv6DnsQuery(source.address, destination.address, "adult.example"))
        )

        val response = DnsPacketCodec.buildResponsePacket(
            query,
            DnsPacketCodec.buildBlockedDnsPayload(query)
        )

        assertEquals(6, response[0].toInt() ushr 4)
        assertArrayEquals(destination.address, response.copyOfRange(8, 24))
        assertArrayEquals(source.address, response.copyOfRange(24, 40))
        assertEquals(53, readUint16(response, 40))
        assertEquals(53000, readUint16(response, 42))
        assertNotEquals(0, readUint16(response, 46))
        assertEquals(0xFFFF, ipv6UdpChecksumSum(response))
        assertEquals(3, response[51].toInt() and 0x0F)
    }

    @Test
    fun preservesIpv4DnsParsingAndResponseBehavior() {
        val source = InetAddress.getByName("10.10.0.2")
        val destination = InetAddress.getByName("1.1.1.1")
        val query = requireNotNull(
            DnsPacketCodec.parse(ipv4DnsQuery(source.address, destination.address, "example.com"))
        )

        val response = DnsPacketCodec.buildResponsePacket(
            query,
            DnsPacketCodec.buildBlockedDnsPayload(query)
        )

        assertEquals(4, query.ipVersion)
        assertEquals("example.com", query.hostname)
        assertEquals(4, response[0].toInt() ushr 4)
        assertArrayEquals(destination.address, response.copyOfRange(12, 16))
        assertArrayEquals(source.address, response.copyOfRange(16, 20))
        assertEquals(53, readUint16(response, 20))
        assertEquals(53000, readUint16(response, 22))
    }

    private fun ipv6DnsQuery(source: ByteArray, destination: ByteArray, hostname: String): ByteArray {
        val dns = dnsQuery(hostname)
        val udpLength = 8 + dns.size
        val packet = ByteArray(40 + udpLength)
        packet[0] = 0x60
        writeUint16(packet, 4, udpLength)
        packet[6] = 17
        packet[7] = 64
        System.arraycopy(source, 0, packet, 8, 16)
        System.arraycopy(destination, 0, packet, 24, 16)
        writeUdpQuery(packet, 40, dns)
        return packet
    }

    private fun ipv4DnsQuery(source: ByteArray, destination: ByteArray, hostname: String): ByteArray {
        val dns = dnsQuery(hostname)
        val udpLength = 8 + dns.size
        val packet = ByteArray(20 + udpLength)
        packet[0] = 0x45
        writeUint16(packet, 2, packet.size)
        packet[8] = 64
        packet[9] = 17
        System.arraycopy(source, 0, packet, 12, 4)
        System.arraycopy(destination, 0, packet, 16, 4)
        writeUdpQuery(packet, 20, dns)
        return packet
    }

    private fun writeUdpQuery(packet: ByteArray, offset: Int, dns: ByteArray) {
        writeUint16(packet, offset, 53000)
        writeUint16(packet, offset + 2, 53)
        writeUint16(packet, offset + 4, 8 + dns.size)
        writeUint16(packet, offset + 6, 0)
        System.arraycopy(dns, 0, packet, offset + 8, dns.size)
    }

    private fun dnsQuery(hostname: String): ByteArray {
        val output = ByteArrayOutputStream()
        writeUint16(output, 0x1234)
        writeUint16(output, 0x0100)
        writeUint16(output, 1)
        writeUint16(output, 0)
        writeUint16(output, 0)
        writeUint16(output, 0)
        hostname.split('.').forEach { label ->
            output.write(label.length)
            output.write(label.toByteArray(Charsets.UTF_8))
        }
        output.write(0)
        writeUint16(output, 1)
        writeUint16(output, 1)
        return output.toByteArray()
    }

    private fun ipv6UdpChecksumSum(packet: ByteArray): Int {
        val udpLength = readUint16(packet, 4)
        var sum = 0L
        sum = addWords(sum, packet, 8, 16)
        sum = addWords(sum, packet, 24, 16)
        sum += (udpLength ushr 16) and 0xFFFF
        sum += udpLength and 0xFFFF
        sum += 17
        sum = addWords(sum, packet, 40, udpLength)
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.toInt() and 0xFFFF
    }

    private fun addWords(initial: Long, buffer: ByteArray, start: Int, length: Int): Long {
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

    private fun writeUint16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUint16(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun readUint16(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }
}
