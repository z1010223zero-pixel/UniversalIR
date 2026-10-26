package com.zero.universalir

import com.zero.universalir.core.codec.Rc5Encoder
import org.junit.Assert.assertEquals
import org.junit.Test

class Rc5EncoderTest {

    @Test
    fun `frame total is 14 bits of 1778us`() {
        val s = Rc5Encoder.encode(address = 0, command = 0, toggle = false)
        assertEquals(14L * 2 * Rc5Encoder.HALF_BIT_US, s.patternUs.sum().toLong())
    }

    @Test
    fun `zero address zero command run length`() {
        // 位流 S1=1 S2=1 T=0 addr=00000 cmd=000000
        // 半位流: M S M S S M (S M)×11
        // 游程: M889 S889 M889 S1778 M889 (S889 M889)×11
        val s = Rc5Encoder.encode(address = 0, command = 0, toggle = false)
        val expected = mutableListOf(889, 889, 889, 1778, 889)
        repeat(11) { expected += 889; expected += 889 }
        assertEquals(expected, s.patternUs.toList())
    }

    @Test
    fun `mark and space totals are equal when last bit is zero`() {
        // 末位为 0 → 帧自然以 mark 收尾，mark/space 半位数相等
        val s = Rc5Encoder.encode(address = 0x1F, command = 0x3E, toggle = true)
        val marks = s.patternUs.filterIndexed { i, _ -> i % 2 == 0 }.sum()
        val spaces = s.patternUs.filterIndexed { i, _ -> i % 2 == 1 }.sum()
        assertEquals(marks, spaces)
    }

    @Test
    fun `ends on mark`() {
        val s = Rc5Encoder.encode(address = 0x11, command = 0x2A, toggle = false)
        assertEquals(1, s.patternUs.size % 2) // 奇数个元素 → 末位是 mark
    }
}
