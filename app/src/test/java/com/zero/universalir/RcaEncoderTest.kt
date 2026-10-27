package com.zero.universalir

import com.zero.universalir.core.codec.RcaEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 黄金向量与 Flipper 固件算法逐位一致：addr=0x0F，cmd=0x54 → data24=0xAB054F。 */
class RcaEncoderTest {

    @Test
    fun `golden vector addr 0f cmd 54`() {
        val s = RcaEncoder.encode(address = 0x0F, command = 0x54)
        assertEquals(38_000, s.carrierHz)
        assertEquals(50, s.patternUs.size)
        assertEquals(56_000L, s.patternUs.sum().toLong())
        assertArrayEquals(
            intArrayOf(
                4000, 4000, 500, 2000, 500, 2000, 500, 2000, 500, 2000, 500, 1000, 500, 1000,
                500, 2000, 500, 1000, 500, 2000, 500, 1000, 500, 2000, 500, 1000, 500, 1000,
                500, 1000, 500, 1000, 500, 1000, 500, 2000, 500, 2000, 500, 1000, 500, 2000,
                500, 1000, 500, 2000, 500, 1000, 500, 2000,
            ),
            s.patternUs,
        )
    }

    @Test
    fun `address low nibble only`() {
        // addr=0xA5 → 只取低 4 位 0x5；cmd=0x00 → data = 0x5 | (~0x5&0xF)<<12 = 0xA005
        val s = RcaEncoder.encode(address = 0xA5, command = 0x00)
        // 前 4 位 = 0x5 LSB 先行 [1,0,1,0] → space 2000/1000/2000/1000
        assertEquals(2000, s.patternUs[3])
        assertEquals(1000, s.patternUs[5])
        assertEquals(2000, s.patternUs[7])
        assertEquals(1000, s.patternUs[9])
    }
}
