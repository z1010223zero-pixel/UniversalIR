package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/** NEC 与 NEC 扩展协议编码（规范参考 SB-Projects）。 */
object NecEncoder {
    const val CARRIER_HZ = 38000
    private const val HEADER_MARK_US = 9000
    private const val HEADER_SPACE_US = 4500
    private const val BIT_MARK_US = 560
    private const val ZERO_SPACE_US = 560
    private const val ONE_SPACE_US = 1690
    private const val TRAILER_MARK_US = 560

    /** 标准 NEC：地址(8) + 地址反码 + 命令(8) + 命令反码，LSB 优先。 */
    fun encodeStandard(address: Int, command: Int): IrSignal {
        val addr = address and 0xFF
        val cmd = command and 0xFF
        val bits = mutableListOf<Boolean>()
        bits.appendLsb(addr, 8)
        bits.appendLsb(addr.inv() and 0xFF, 8)
        bits.appendLsb(cmd, 8)
        bits.appendLsb(cmd.inv() and 0xFF, 8)
        return frame(bits, "nec")
    }

    /** NEC 扩展：16 位地址（不取反）+ 命令(8) + 命令反码，LSB 优先。 */
    fun encodeExtended(address: Int, command: Int): IrSignal {
        val addr = address and 0xFFFF
        val cmd = command and 0xFF
        val bits = mutableListOf<Boolean>()
        bits.appendLsb(addr, 16)
        bits.appendLsb(cmd, 8)
        bits.appendLsb(cmd.inv() and 0xFF, 8)
        return frame(bits, "necext")
    }

    /** NEC 长按重复帧。 */
    fun encodeRepeat(): IrSignal =
        IrSignal(CARRIER_HZ, intArrayOf(HEADER_MARK_US, 2250, TRAILER_MARK_US), source = "nec-repeat")

    private fun frame(bits: List<Boolean>, source: String): IrSignal {
        val pattern = mutableListOf<Int>()
        pattern += HEADER_MARK_US
        pattern += HEADER_SPACE_US
        for (bit in bits) {
            pattern += BIT_MARK_US
            pattern += if (bit) ONE_SPACE_US else ZERO_SPACE_US
        }
        pattern += TRAILER_MARK_US
        return IrSignal(CARRIER_HZ, pattern.toIntArray(), source = source)
    }

    private fun MutableList<Boolean>.appendLsb(value: Int, count: Int) {
        for (i in 0 until count) add((value shr i) and 1 == 1)
    }
}
