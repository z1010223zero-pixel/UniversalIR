package com.zero.universalir

import com.zero.universalir.voice.TriggerAuth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 外部广播触发的 PIN 鉴权：未启用放行，启用后必须精确匹配。 */
class TriggerAuthTest {

    @Test
    fun `未设置 pin 时一律放行`() {
        assertTrue(TriggerAuth.authOk("", null))
        assertTrue(TriggerAuth.authOk("", "anything"))
        assertTrue(TriggerAuth.authOk("   ", null))
    }

    @Test
    fun `启用后缺失或不匹配一律拒绝`() {
        assertFalse(TriggerAuth.authOk("1234", null))
        assertFalse(TriggerAuth.authOk("1234", ""))
        assertFalse(TriggerAuth.authOk("1234", "123"))
        assertFalse(TriggerAuth.authOk("1234", " 1234"))
        assertFalse(TriggerAuth.authOk("1234", "1234 "))
    }

    @Test
    fun `启用后精确匹配放行`() {
        assertTrue(TriggerAuth.authOk("1234", "1234"))
        assertTrue(TriggerAuth.authOk("abc", "abc"))
    }
}
