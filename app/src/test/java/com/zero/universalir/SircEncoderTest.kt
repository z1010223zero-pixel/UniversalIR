package com.zero.universalir

import com.zero.universalir.core.codec.SircEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class SircEncoderTest {

    @Test
    fun `sirc12 frame structure`() {
        // 命令 0x15 LSB: 1,0,1,0,1,0,0 地址 0x01 LSB: 1,0,0,0,0
        val s = SircEncoder.encode(command = 0x15, address = 0x01, totalBits = 12)
        assertEquals(40000, s.carrierHz)
        assertEquals(3, s.repeat)
        // 头 + 12 位
        val expected = mutableListOf(2400, 600)
        // 0x15: 1,0,1,0,1,0,0
        for (bit in listOf(true, false, true, false, true, false, false)) {
            expected += 600
            expected += if (bit) 1200 else 600
        }
        // 0x01: 1,0,0,0,0
        for (bit in listOf(true, false, false, false, false)) {
            expected += 600
            expected += if (bit) 1200 else 600
        }
        assertEquals(expected, s.patternUs.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported bit count`() {
        SircEncoder.encode(0, 0, totalBits = 14)
    }
}
