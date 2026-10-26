package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/** Samsung32：16 位地址 + 16 位命令（无反码），LSB 优先，单帧连续发送。 */
object Samsung32Encoder {
    const val CARRIER_HZ = 38000
    private const val HEADER_MARK_US = 4500
    private const val HEADER_SPACE_US = 4500
    private const val BIT_MARK_US = 550
    private const val ZERO_SPACE_US = 550
    private const val ONE_SPACE_US = 1550

    fun encode(address: Int, command: Int): IrSignal {
        val pattern = mutableListOf(HEADER_MARK_US, HEADER_SPACE_US)
        for (value in intArrayOf(address and 0xFFFF, command and 0xFFFF)) {
            for (i in 0 until 16) {
                pattern += BIT_MARK_US
                pattern += if ((value shr i) and 1 == 1) ONE_SPACE_US else ZERO_SPACE_US
            }
        }
        pattern += BIT_MARK_US
        return IrSignal(CARRIER_HZ, pattern.toIntArray(), source = "samsung32")
    }
}
