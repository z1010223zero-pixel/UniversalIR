package com.zero.universalir

import com.zero.universalir.core.codec.SmartIrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartIrParserTest {

    private val json = """
        {
          "manufacturer": "GREE",
          "minTemperature": 16,
          "maxTemperature": 30,
          "precision": 1,
          "operationModes": ["off", "cool", "heat", "fan_only"],
          "fanModes": ["auto", "low", "high"],
          "commands": {
            "off": "OFFCODE",
            "cool": { "auto": { "16": "C16", "17": "C17" }, "low": { "26": "C26" } },
            "fan_only": { "18": "F18" }
          }
        }
    """.trimIndent()

    @Test
    fun `parses metadata`() {
        val table = SmartIrParser().parse(json, "test.json")!!
        assertEquals("GREE", table.manufacturer)
        assertEquals(16, table.minTemperature)
        assertEquals(30, table.maxTemperature)
        assertEquals(listOf("off", "cool", "heat", "fan_only"), table.operationModes)
    }

    @Test
    fun `looks up mode fan temp codes`() {
        val table = SmartIrParser().parse(json)!!
        assertEquals("C26", SmartIrParser.lookup(table, "cool", "low", 26))
        assertEquals("C16", SmartIrParser.lookup(table, "cool", "auto", 16))
        assertEquals(null, SmartIrParser.lookup(table, "cool", "low", 16))
        assertEquals(null, SmartIrParser.lookup(table, "heat", "low", 26))
    }

    @Test
    fun `direct temperature files normalize to FAN_ANY`() {
        val table = SmartIrParser().parse(json)!!
        assertEquals("F18", SmartIrParser.lookup(table, "fan_only", SmartIrParser.FAN_ANY, 18))
        assertEquals("F18", SmartIrParser.lookup(table, "fan_only", "whatever", 18))
    }

    @Test
    fun `off code lookup`() {
        val table = SmartIrParser().parse(json)!!
        assertEquals("OFFCODE", SmartIrParser.codeForOff(table))
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(SmartIrParser().parse("{ broken"))
        assertNull(SmartIrParser().parse("""{"commands": {}}"""))
    }
}
