package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/**
 * Pioneer（NEC 家族变体），Flipper 固件同款：载频 40k，preamble 8500/4225，
 * mark 恒 500，space 500(0)/1500(1)。40 位 LSB-first：addr + ~addr + cmd + ~cmd + 0x00。
 * 固件要求最少连发 2 帧。
 */
object PioneerEncoder {
    const val CARRIER_HZ = 40_000

    fun encode(address: Int, command: Int): IrSignal {
        val addr = address and 0xFF
        val cmd = command and 0xFF
        val bytes = intArrayOf(addr, addr.inv() and 0xFF, cmd, cmd.inv() and 0xFF, 0)
        val bits = ArrayList<Boolean>(40)
        for (byte in bytes) {
            for (i in 0 until 8) bits += ((byte shr i) and 1) == 1
        }
        return IrSignal(
            CARRIER_HZ,
            pwmFrame(
                bits,
                headerMarkUs = 8_500,
                headerSpaceUs = 4_225,
                markUs = 500,
                zeroSpaceUs = 500,
                oneSpaceUs = 1_500,
            ),
            repeat = 2,
            source = "pioneer",
        )
    }
}
