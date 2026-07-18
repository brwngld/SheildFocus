package com.shieldfocus.android.vpn

import java.io.ByteArrayOutputStream

object DnsFailureResponse {
    fun servFail(query: DnsQueryPacket): ByteArray {
        val output = ByteArrayOutputStream()
        writeUint16(output, query.transactionId)
        writeUint16(output, 0x8182) // Standard response with SERVFAIL.
        writeUint16(output, 1)
        writeUint16(output, 0)
        writeUint16(output, 0)
        writeUint16(output, 0)
        output.write(query.questionBytes)
        return output.toByteArray()
    }

    private fun writeUint16(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }
}
