package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/**
 * RCA（24 位），Flipper 固件同款：载频 38k，preamble 4000/4000，mark 恒 500，
 * space 1000(0)/2000(1)。24 位 LSB-first：addr 低 4 位 + cmd 8 位 + ~addr 低 4 位 + ~cmd 8 位。
 */
object RcaEncoder {
    const val CARRIER_HZ = 38_000

    fun encode(address: Int, command: Int): IrSignal {
        val addr = address and 0xFF
        val cmd = command and 0xFF
        val data = (addr and 0xF) or
            ((cmd and 0xFF) shl 4) or
            ((addr.inv() and 0xF) shl 12) or
            ((cmd.inv() and 0xFF) shl 16)
        val bits = (0 until 24).map { ((data shr it) and 1) == 1 }
        return IrSignal(
            CARRIER_HZ,
            pwmFrame(
                bits,
                headerMarkUs = 4_000,
                headerSpaceUs = 4_000,
                markUs = 500,
                zeroSpaceUs = 1_000,
                oneSpaceUs = 2_000,
            ),
            source = "rca",
        )
    }
}
