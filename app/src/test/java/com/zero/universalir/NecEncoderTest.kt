package com.zero.universalir

import com.zero.universalir.core.codec.NecEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class NecEncoderTest {

    @Test
    fun `standard NEC zero address zero command golden vector`() {
        val s = NecEncoder.encodeStandard(0x00, 0x00)
        val expected = mutableListOf<Int>()
        expected += 9000
        expected += 4500
        // 地址 0x00（8 个 0）
        repeat(8) { expected += 560; expected += 560 }
        // 地址反码 0xFF（8 个 1）
        repeat(8) { expected += 560; expected += 1690 }
        // 命令 0x00
        repeat(8) { expected += 560; expected += 560 }
        // 命令反码 0xFF
        repeat(8) { expected += 560; expected += 1690 }
        expected += 560
        assertEquals(expected, s.patternUs.toList())
        assertEquals(38000, s.carrierHz)
    }

    @Test
    fun `bits are LSB first`() {
        // 地址 0x01：LSB 第一位是 1，第一个数据对应为长 space
        val s = NecEncoder.encodeStandard(0x01, 0x00)
        assertEquals(9000, s.patternUs[0])
        assertEquals(4500, s.patternUs[1])
        assertEquals(560, s.patternUs[2])
        assertEquals(1690, s.patternUs[3])
        // 第二位开始是 0
        assertEquals(560, s.patternUs[4])
        assertEquals(560, s.patternUs[5])
    }

    @Test
    fun `extended NEC sends 16 bit address without inversion`() {
        val s = NecEncoder.encodeExtended(0x1234, 0x56)
        // 16 位地址 + 8 命令 + 8 反码 = 32 位
        val dataBits = (s.patternUs.size - 3) / 2
        assertEquals(32, dataBits)
        // 0x1234 LSB=0 → 第一个数据对为 0 位
        assertEquals(560, s.patternUs[3])
        // 0x1234 bit4=1 → 第 5 个数据对（index 10/11）为 1 位
        assertEquals(1690, s.patternUs[11])
    }

    @Test
    fun `repeat code`() {
        val s = NecEncoder.encodeRepeat()
        assertEquals(listOf(9000, 2250, 560), s.patternUs.toList())
    }
}
