package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/** Sony SIRC（12/15/20 位，规范参考 SB-Projects）。 */
object SircEncoder {
    const val CARRIER_HZ = 40000
    private const val HEADER_MARK_US = 2400
    private const val BIT_MARK_US = 600
    private const val ZERO_SPACE_US = 600
    private const val ONE_SPACE_US = 1200

    /** 命令 7 位 + 地址 5/8/8 位（+扩展 5 位），LSB 优先，无反码；依赖 repeat 连发保证可靠性。 */
    fun encode(command: Int, address: Int, extended: Int = 0, totalBits: Int): IrSignal {
        require(totalBits == 12 || totalBits == 15 || totalBits == 20) { "SIRC 仅支持 12/15/20 位" }
        val pattern = mutableListOf(HEADER_MARK_US, ZERO_SPACE_US)
        fun append(value: Int, count: Int) {
            for (i in 0 until count) {
                pattern += BIT_MARK_US
                pattern += if ((value shr i) and 1 == 1) ONE_SPACE_US else ZERO_SPACE_US
            }
        }
        append(command and 0x7F, 7)
        when (totalBits) {
            12 -> append(address and 0x1F, 5)
            15 -> append(address and 0xFF, 8)
            20 -> {
                append(address and 0xFF, 8)
                append(extended and 0x1F, 5)
            }
        }
        return IrSignal(CARRIER_HZ, pattern.toIntArray(), repeat = 3, source = "sirc$totalBits")
    }
}
