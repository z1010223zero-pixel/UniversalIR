package com.zero.universalir

import com.zero.universalir.core.codec.ProtocolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 此前码库中不可用（静默丢弃）的协议现在都应能渲染。 */
class ProtocolRegistryTest {

    @Test
    fun `previously unsupported protocols now render`() {
        assertNotNull(ProtocolRegistry.encode("Kaseikyo", 0x325441, 5))
        assertNotNull(ProtocolRegistry.encode("RCA", 0x0F, 0x54))
        assertNotNull(ProtocolRegistry.encode("Pioneer", 0xA5, 0x1C))
        assertNotNull(ProtocolRegistry.encode("RC6", 0x11, 0x2A))
    }

    @Test
    fun `known encoders unchanged`() {
        assertEquals(38_000, ProtocolRegistry.encode("NEC", 0x00, 0x01)!!.carrierHz)
        assertEquals(36_000, ProtocolRegistry.encode("RC5X", 0x00, 0x40)!!.carrierHz)
        assertNull(ProtocolRegistry.encode("NoSuchProtocol", 0, 0))
    }

    @Test
    fun `toggle bit flips rc5 pattern`() {
        val a = ProtocolRegistry.encode("RC5", 0x11, 0x2A, toggle = false)!!
        val b = ProtocolRegistry.encode("RC5", 0x11, 0x2A, toggle = true)!!
        assertFalse(a.patternUs.contentEquals(b.patternUs))
        assertTrue(ProtocolRegistry.supportsToggle("RC5"))
        assertTrue(ProtocolRegistry.supportsToggle("RC6"))
        assertFalse(ProtocolRegistry.supportsToggle("NEC"))
    }
}
