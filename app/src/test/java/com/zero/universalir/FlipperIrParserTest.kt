package com.zero.universalir

import com.zero.universalir.core.codec.FlipperIrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlipperIrParserTest {

    private val sample = """
        #
        Filetype: Flipper IR signal file
        Version: 1

        #
        name: Power
        type: parsed
        protocol: NEC
        address: 9F 00 00 00
        command: E6 19 00 00

        #
        name: Vol+
        type: raw
        frequency: 38000
        duty_cycle: 0.330000
        data: 5000 5000 5000 5000
    """.trimIndent()

    @Test
    fun `parses parsed and raw entries`() {
        val signals = FlipperIrParser().parse(sample)
        assertEquals(2, signals.size)

        val power = signals[0]
        assertEquals("Power", power.name)
        assertEquals("parsed", power.type)
        assertEquals("NEC", power.protocol)
        assertEquals(0x9FL, power.addressValue)
        assertEquals(0x19E6L, power.commandValue) // 字节 E6 19 按发送序小端合成

        val vol = signals[1]
        assertEquals("raw", vol.type)
        assertEquals(38000, vol.frequencyHz)
        assertEquals(listOf(5000L, 5000L, 5000L, 5000L), vol.dataUs)
    }

    @Test
    fun `renders parsed via NEC encoder`() {
        val signals = FlipperIrParser().parse(sample)
        val signal = FlipperIrParser.renderParsed(signals[0])!!
        assertEquals(38000, signal.carrierHz)
        assertEquals(9000, signal.patternUs[0]) // NEC 头
        assertEquals(67, signal.patternUs.size) // 2 + 32*2 + 1
    }

    @Test
    fun `unsupported protocol returns null`() {
        val text = sample.replace("protocol: NEC", "protocol: JVC")
        val signals = FlipperIrParser().parse(text)
        assertNull(FlipperIrParser.renderParsed(signals[0]))
    }

    @Test
    fun `renders raw directly`() {
        val signals = FlipperIrParser().parse(sample)
        val signal = FlipperIrParser.renderRaw(signals[1])!!
        assertEquals(38000, signal.carrierHz)
        assertEquals(4, signal.patternUs.size)
    }
}
