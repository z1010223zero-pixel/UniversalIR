package com.zero.universalir

import com.zero.universalir.core.codec.PioneerEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 黄金向量与 Flipper 固件算法逐位一致：addr=0xA5，cmd=0x1C → 字节 [A5,5A,1C,E3,00]。 */
class PioneerEncoderTest {

    @Test
    fun `golden vector addr a5 cmd 1c`() {
        val s = PioneerEncoder.encode(address = 0xA5, command = 0x1C)
        assertEquals(40_000, s.carrierHz)
        assertEquals(2, s.repeat) // 固件 REPEAT_COUNT_MIN=2
        assertEquals(82, s.patternUs.size)
        assertEquals(68_725L, s.patternUs.sum().toLong())
        assertArrayEquals(
            intArrayOf(
                8500, 4225, 500, 1500, 500, 500, 500, 1500, 500, 500, 500, 500, 500, 1500,
                500, 500, 500, 1500, 500, 500, 500, 1500, 500, 500, 500, 1500, 500, 1500,
                500, 500, 500, 1500, 500, 500, 500, 500, 500, 500, 500, 1500, 500, 1500,
                500, 1500, 500, 500, 500, 500, 500, 500, 500, 1500, 500, 1500, 500, 500,
                500, 500, 500, 500, 500, 1500, 500, 1500, 500, 1500, 500, 500, 500, 500,
                500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500,
            ),
            s.patternUs,
        )
    }
}
