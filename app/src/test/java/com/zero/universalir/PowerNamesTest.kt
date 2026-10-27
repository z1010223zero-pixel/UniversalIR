package com.zero.universalir

import com.zero.universalir.voice.PowerNames
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电源信号名判断。用例覆盖内置码库的真实命名
 * （Power/Off/Power_on/Power_off/Standby）与陷阱名（Powerful 等）。
 */
class PowerNamesTest {

    @Test
    fun `码库常见开机命名`() {
        for (n in listOf("On", "ON", "on", "Power", "POWER", "power",
                "Power_on", "Power_On", "POWER_ON", "power_on", "Power On", "开机", "开")) {
            assertTrue("应识别为开机: $n", PowerNames.isOn(n))
        }
    }

    @Test
    fun `码库常见关机命名`() {
        for (n in listOf("Off", "OFF", "off", "Power_off", "Power_Off", "POWER_OFF",
                "power_off", "Power Off", "Standby", "关机", "关闭", "关", "关闭电源")) {
            assertTrue("应识别为关机: $n", PowerNames.isOff(n))
        }
    }

    @Test
    fun `陷阱名不算电源键`() {
        // 三菱空调强力模式、功放分区、风扇电源、带编号的 Power
        for (n in listOf("Powerful", "POWERFUL", "Powerful_quiet", "POWER_Zone2",
                "POWER_Receiver", "POWER_Source", "POWER_TV", "Fan_power", "Power2",
                "Lasko_power", "关闭背光", "开瓶器", "Volume", "Mute")) {
            assertFalse("不应识别为开机: $n", PowerNames.isOn(n))
            assertFalse("不应识别为关机: $n", PowerNames.isOff(n))
        }
    }

    @Test
    fun `二合一开关键归属不明，两边都不命中`() {
        assertFalse(PowerNames.isOn("ON/OFF"))
        assertFalse(PowerNames.isOff("ON/OFF"))
        assertFalse(PowerNames.isOn("Game_on/off"))
        assertFalse(PowerNames.isOff("Game_on/off"))
    }
}
