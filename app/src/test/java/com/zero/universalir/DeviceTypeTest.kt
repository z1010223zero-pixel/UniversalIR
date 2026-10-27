package com.zero.universalir

import com.zero.universalir.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTypeTest {

    @Test
    fun `type key is case insensitive`() {
        assertEquals(DeviceType.AC, DeviceType.from("AC"))
        assertEquals(DeviceType.AC, DeviceType.from("ac"))
    }

    @Test
    fun `unknown type falls back to generic`() {
        assertEquals(DeviceType.GENERIC, DeviceType.from("unknown"))
    }
}
