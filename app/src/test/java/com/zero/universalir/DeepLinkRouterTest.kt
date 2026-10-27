package com.zero.universalir

import com.zero.universalir.voice.DeepLinkRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkRouterTest {

    // ---- parsePower（P3-3）----

    @Test
    fun `power param is case insensitive`() {
        assertEquals(true, DeepLinkRouter.parsePower("ON"))
        assertEquals(true, DeepLinkRouter.parsePower(" On "))
        assertEquals(false, DeepLinkRouter.parsePower("OFF"))
        assertEquals(true, DeepLinkRouter.parsePower("1"))
        assertEquals(false, DeepLinkRouter.parsePower("0"))
    }

    @Test
    fun `unknown power value falls back to null instead of off`() {
        assertNull(DeepLinkRouter.parsePower("garbage"))
        assertNull(DeepLinkRouter.parsePower("nominal"))
        assertNull(DeepLinkRouter.parsePower(null))
    }

    // ---- fuzzyMatch（P2-3）----

    @Test
    fun `exact match wins`() {
        assertEquals("台灯", DeepLinkRouter.fuzzyMatch("台灯", listOf("台灯", "客厅灯带")))
    }

    @Test
    fun `shorter name wins when only partially overlapping`() {
        // "灯" 不应命中"客厅灯带"（覆盖 1/4），而应命中"台灯"（覆盖 1/2）
        assertEquals("台灯", DeepLinkRouter.fuzzyMatch("灯", listOf("客厅灯带", "台灯")))
        assertEquals("台灯", DeepLinkRouter.fuzzyMatch("打开台灯", listOf("客厅灯带", "台灯")))
    }

    @Test
    fun `full key inside name wins on equal coverage`() {
        // "客厅灯" 命中"客厅灯带"（覆盖 3/4）而不是"台灯"（1/3）
        assertEquals("客厅灯带", DeepLinkRouter.fuzzyMatch("客厅灯", listOf("客厅灯带", "台灯")))
    }

    @Test
    fun `higher coverage wins among partial overlaps`() {
        assertEquals("灯带2", DeepLinkRouter.fuzzyMatch("灯带", listOf("灯带2", "客厅灯带")))
    }

    @Test
    fun `no match returns null`() {
        assertNull(DeepLinkRouter.fuzzyMatch("音响", listOf("台灯", "客厅灯带")))
    }
}
