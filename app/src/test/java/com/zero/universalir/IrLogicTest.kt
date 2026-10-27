package com.zero.universalir

import com.zero.universalir.core.ir.IrLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrLogicTest {

    @Test
    fun `pattern under 2s passes`() {
        val check = IrLogic.checkPattern(intArrayOf(9000, 4500, 560))
        assertEquals(IrLogic.SendCheck.Ok, check)
    }

    @Test
    fun `pattern over 2s is rejected`() {
        val check = IrLogic.checkPattern(IntArray(100) { 30_000 }) // 3s
        assertTrue(check is IrLogic.SendCheck.TooLong)
        assertEquals(3_000_000L, (check as IrLogic.SendCheck.TooLong).durationUs)
    }

    @Test
    fun `empty pattern rejected`() {
        assertEquals(IrLogic.SendCheck.EmptyPattern, IrLogic.checkPattern(IntArray(0)))
    }

    @Test
    fun `carrier in range passes through`() {
        val ranges = listOf(IrLogic.CarrierRange(30_000, 56_000))
        assertEquals(38_000, IrLogic.pickCarrier(38_000, ranges))
    }

    @Test
    fun `carrier out of range falls back to 38k range`() {
        val ranges = listOf(IrLogic.CarrierRange(30_000, 36_000), IrLogic.CarrierRange(37_200, 39_000))
        assertEquals(38_000, IrLogic.pickCarrier(40_000, ranges))
    }

    @Test
    fun `no 38k range falls back to nearest midpoint`() {
        val ranges = listOf(IrLogic.CarrierRange(30_000, 36_000), IrLogic.CarrierRange(42_000, 56_000))
        assertEquals(33_000, IrLogic.pickCarrier(40_000, ranges))
    }

    @Test
    fun `no ranges returns requested`() {
        assertEquals(40_000, IrLogic.pickCarrier(40_000, emptyList()))
    }

    @Test
    fun `repeat frames merge gap into trailing space`() {
        val signal = com.zero.universalir.core.ir.IrSignal(
            38000, intArrayOf(2400, 600), repeat = 3,
        )
        val frames = signal.framesWithGap()
        assertEquals(3, frames.size)
        // pattern 以 space 结尾：gap 并入帧尾 space，避免落到偶数下标变成 45ms 载波
        assertEquals(2, frames[0].size)
        assertEquals(45_600, frames[0][1])
        assertEquals(2, frames[2].size)
        assertEquals(600, frames[2][1])
    }

    @Test
    fun `repeat frames append gap when pattern ends on mark`() {
        val signal = com.zero.universalir.core.ir.IrSignal(
            38000, intArrayOf(2400, 600, 560), repeat = 2,
        )
        val frames = signal.framesWithGap()
        assertEquals(2, frames.size)
        assertEquals(4, frames[0].size)
        assertEquals(45_000, frames[0][3])
        assertEquals(3, frames[1].size) // 最后一帧不追加
    }
}
