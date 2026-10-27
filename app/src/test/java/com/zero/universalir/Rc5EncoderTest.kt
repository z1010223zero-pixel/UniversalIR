package com.zero.universalir

import com.zero.universalir.core.codec.Rc5Encoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Rc5EncoderTest {

    @Test
    fun `leading idle half bit of S1 is dropped`() {
        val s = Rc5Encoder.encode(address = 0, command = 0, toggle = false)
        // 28 个半位 - 首半位静默 - 末位(0)的静默半位 = 26 个半位
        assertEquals(26L * Rc5Encoder.HALF_BIT_US, s.patternUs.sum().toLong())
    }

    @Test
    fun `zero address zero command run length`() {
        // 位流 S1=1 S2=1 T=0 addr=00000 cmd=000000
        // 半位流(逻辑 1 = space+mark，逻辑 0 = mark+space)：
        // S M S M | M S | (M S)×11，去掉首尾 space 后：
        // M S MM S (M S)×10 M
        val s = Rc5Encoder.encode(address = 0, command = 0, toggle = false)
        val expected = mutableListOf(889, 889, 1778, 889)
        repeat(21) { expected += 889 }
        assertEquals(expected, s.patternUs.toList())
    }

    @Test
    fun `logical one starts with space and logical zero starts with mark`() {
        // 标准 RC5 黄金向量：addr=0x11，cmd=0x2A，toggle=false
        val s = Rc5Encoder.encode(address = 0x11, command = 0x2A, toggle = false)
        assertArrayEquals(
            intArrayOf(
                889, 889, 1778, 1778, 1778, 889, 889, 889, 889, 1778, 889, 889,
                1778, 1778, 1778, 1778, 1778,
            ),
            s.patternUs,
        )
    }

    @Test
    fun `trailing idle half bit is dropped when last bit is zero`() {
        val s = Rc5Encoder.encode(address = 0x1F, command = 0x3E, toggle = true)
        assertEquals(26L * Rc5Encoder.HALF_BIT_US, s.patternUs.sum().toLong())
    }

    @Test
    fun `no trailing idle when last bit is one`() {
        val s = Rc5Encoder.encode(address = 0x1F, command = 0x3F, toggle = true)
        assertEquals(27L * Rc5Encoder.HALF_BIT_US, s.patternUs.sum().toLong())
    }

    @Test
    fun `ends on mark`() {
        val s = Rc5Encoder.encode(address = 0x11, command = 0x2A, toggle = false)
        assertEquals(1, s.patternUs.size % 2) // 奇数个元素 → 末位是 mark
    }

    @Test
    fun `rc5x s2 is inverted command bit6 with 7-bit command`() {
        // command=0x40：bit6=1 → S2=0；S1=1 的 mark 与 S2=0 的 mark 相邻合并
        val s = Rc5Encoder.encode(address = 0, command = 0x40, toggle = false, extended = true)
        assertEquals(28L * Rc5Encoder.HALF_BIT_US, s.patternUs.sum().toLong())
        assertArrayEquals(
            intArrayOf(
                1778, 889, 889, 889, 889, 889, 889, 889, 889, 889, 889, 889, 889,
                1778, 1778, 889, 889, 889, 889, 889, 889, 889, 889, 889, 889,
            ),
            s.patternUs,
        )
    }

    @Test
    fun `rc5x command below 64 keeps s2 high`() {
        val s = Rc5Encoder.encode(address = 0, command = 0, toggle = false, extended = true)
        assertEquals(28L * Rc5Encoder.HALF_BIT_US, s.patternUs.sum().toLong())
        // S2=1 与 T=0 相邻：仅 T 的 mark 与 addr 最高位 mark 合并出唯一一个 1778
        assertEquals(1, s.patternUs.count { it == 1778 })
        assertEquals(889, s.patternUs[0])
        assertEquals(889, s.patternUs[1])
        assertEquals(1778, s.patternUs[2])
    }
}
