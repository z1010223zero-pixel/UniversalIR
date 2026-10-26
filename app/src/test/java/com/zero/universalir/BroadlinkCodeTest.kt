package com.zero.universalir

import com.zero.universalir.core.codec.BroadlinkCode
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalEncodingApi::class)
class BroadlinkCodeTest {

    @Test
    fun `decodes escape and plain ticks`() {
        // 构造：294 ticks（escape 0x00 0x01 0x26）+ 18 ticks（0x12）
        // 长度字段 = 4（00 01 26 12）
        val bytes = byteArrayOf(0x26, 0x00, 0x04, 0x00, 0x00, 0x01, 0x26, 0x12)
        val b64 = Base64.encode(bytes)
        val us = BroadlinkCode.decodeUs(b64)!!
        val tick = 1_000_000.0 / 32768.0
        assertEquals(Math.round(294 * tick).toInt(), us[0])
        assertEquals(Math.round(18 * tick).toInt(), us[1])
        assertEquals(2, us.size)
    }

    @Test
    fun `rejects rf and malformed codes`() {
        // 0xb2 是 RF 433 码，手机红外发不了
        val rf = Base64.encode(byteArrayOf(0xb2.toByte(), 0x00, 0x02, 0x00, 0x0a, 0x0b))
        assertNull(BroadlinkCode.decodeUs(rf))
        assertNull(BroadlinkCode.decodeUs("!!!not base64!!!"))
        assertNull(BroadlinkCode.decodeUs(""))
    }

    @Test
    fun `tick constant is the true 2 to -15 second value`() {
        assertEquals(1_000_000.0 / 32768.0, BroadlinkCode.TICK_US, 1e-9)
        assertEquals(30.5176, BroadlinkCode.TICK_US, 0.0001)
    }

    @Test
    fun `roundtrip known NEC-like timing`() {
        // 294 ticks escape 编码（0x00 0x01 0x26）+ 18 ticks（0x12），长度字段 = 4
        val bytes = byteArrayOf(0x26, 0x00, 0x04, 0x00, 0x00, 0x01, 0x26, 0x12)
        val us = BroadlinkCode.decodeUs(Base64.encode(bytes))!!
        assertEquals(2, us.size)
        assertEquals(294.0 * BroadlinkCode.TICK_US, us[0].toDouble(), 1.0)
    }
}
