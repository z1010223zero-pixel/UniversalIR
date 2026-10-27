package com.zero.universalir

import com.zero.universalir.core.codec.Rc6Encoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 黄金向量与 Flipper 固件算法逐位一致（LSB-first：start+mode+toggle+reverse(addr)+reverse(cmd)）。 */
class Rc6EncoderTest {

    @Test
    fun `golden vector addr 11 cmd 2a`() {
        val s = Rc6Encoder.encode(address = 0x11, command = 0x2A)
        assertEquals(36_000, s.carrierHz)
        assertEquals(31, s.patternUs.size)
        assertEquals(19_536L, s.patternUs.sum().toLong())
        assertArrayEquals(
            intArrayOf(
                444, 888, 444, 444, 444, 444, 444, 888, 888, 444, 444, 444, 444, 444, 888,
                888, 444, 444, 444, 444, 888, 888, 444, 444, 888, 888, 888, 888, 888, 888, 444,
            ),
            s.patternUs,
        )
    }

    @Test
    fun `zero address zero command structure`() {
        // start=1 后 mode/toggle/addr/cmd 全 0：开头 [444,888,444,444,444,444,444,888,888]，
        // 之后 mode/addr/cmd 位交替 mark/space（S888 之后是 M444 S444 …，翻转位双倍 M888 S888）
        val s = Rc6Encoder.encode(address = 0, command = 0)
        assertEquals(41, s.patternUs.size)
        assertEquals(19_536L, s.patternUs.sum().toLong())
        assertArrayEquals(
            intArrayOf(444, 888, 444, 444, 444, 444, 444, 888, 888),
            s.patternUs.copyOfRange(0, 9),
        )
        assertEquals(888, s.patternUs[7])  // 翻转位 space（start 后 3 个 mode 位与位 0 的 space 合并）
        assertEquals(888, s.patternUs[8])  // 翻转位 mark
    }
}
