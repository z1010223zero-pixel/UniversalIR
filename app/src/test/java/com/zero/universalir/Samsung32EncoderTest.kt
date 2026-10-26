package com.zero.universalir

import com.zero.universalir.core.codec.Samsung32Encoder
import org.junit.Assert.assertEquals
import org.junit.Test

class Samsung32EncoderTest {

    @Test
    fun `frame structure`() {
        val s = Samsung32Encoder.encode(address = 0x0707, command = 0x0203)
        assertEquals(38000, s.carrierHz)
        // 头 2 + 32 位×(mark+space) + 尾 mark
        assertEquals(67, s.patternUs.size)
        assertEquals(listOf(4500, 4500), s.patternUs.take(2))
        assertEquals(550, s.patternUs.last())
        // 地址 0x0707 LSB=1 → 第一个数据位是 1
        assertEquals(1550, s.patternUs[3])
    }
}
