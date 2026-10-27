package com.zero.universalir

import com.zero.universalir.core.ir.RcToggleMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RcToggleMemoryTest {

    @Test
    fun `toggle alternates per key press`() {
        val first = RcToggleMemory.next(1L, "power")
        val second = RcToggleMemory.next(1L, "power")
        val third = RcToggleMemory.next(1L, "power")
        assertNotEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `keys are independent`() {
        val a1 = RcToggleMemory.next(2L, "power")
        val b1 = RcToggleMemory.next(2L, "vol")
        // 按另一个键不改变本键的相位：各自独立交替
        assertEquals(!a1, RcToggleMemory.next(2L, "power"))
        assertEquals(!b1, RcToggleMemory.next(2L, "vol"))
    }
}
