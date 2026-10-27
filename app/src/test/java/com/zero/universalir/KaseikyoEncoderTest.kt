package com.zero.universalir

import com.zero.universalir.core.codec.KaseikyoEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 黄金向量与 Flipper 固件算法逐位一致：
 * address=0x00325441（Denon，4 字节 LE 反序列化值），command=5
 * → 帧 6 字节 [54, 32, 40, 51, 00, 11]（vendor=0x3254, genre1=4, genre2=1, id=0）。
 */
class KaseikyoEncoderTest {

    @Test
    fun `golden vector denon command 5`() {
        val s = KaseikyoEncoder.encode(address = 0x00325441L, command = 5L)
        assertEquals(38_000, s.carrierHz)
        assertEquals(98, s.patternUs.size)
        assertEquals(57_024L, s.patternUs.sum().toLong())
        assertArrayEquals(
            intArrayOf(
                3456, 1728, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 432, 432, 1296,
                432, 432, 432, 432, 432, 1296, 432, 432, 432, 432, 432, 1296, 432, 1296, 432, 432,
                432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 1296,
                432, 432, 432, 1296, 432, 432, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 1296,
                432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432,
                432, 432, 432, 1296, 432, 432, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 432,
                432, 432,
            ),
            s.patternUs,
        )
    }

    @Test
    fun `panasonic vendor parity nibble`() {
        // vendor 0x4004 在 address 的 bits 8-23：address=0x00400400
        // d0=0x04 d1=0x40 → 折叠奇偶 = 0x4^0x4 = 0 → d2 低 4 位 = 0
        val s = KaseikyoEncoder.encode(address = 0x00400400L, command = 0L)
        // bits: d0=0x04 → LSB 先行 [0,0,1,0,0,0,0,0]；第 2 位为 1 → 对应 space 1296
        assertEquals(1296, s.patternUs[7])
        assertEquals(432, s.patternUs[9])
    }
}
