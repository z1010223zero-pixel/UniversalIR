package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/**
 * Kaseikyo（松下系 48 位，覆盖 Panasonic/Denon/Sharp/JVC 等）。
 * 与 Flipper 固件完全一致：载频 38k，preamble 3456/1728，mark 恒 432，
 * space 432(0)/1296(1)，6 字节逐字节 LSB-first 发送。
 */
object KaseikyoEncoder {
    const val CARRIER_HZ = 38_000

    /**
     * [address] 32 位布局（Flipper 约定）：id(2b)<<24 | vendor(16b)<<8 | genre1(4b)<<4 | genre2(4b)。
     * [command] 取低 12 位。帧 6 字节：
     * d0=vendor 低字节, d1=vendor 高字节, d2=vendor 奇偶(4b)|genre1<<4,
     * d3=genre2|(cmd&0xF)<<4, d4=id<<6|cmd>>4, d5=d2^d3^d4。
     */
    fun encode(address: Long, command: Long): IrSignal {
        val id = ((address shr 24) and 0x3).toInt()
        val vendor = ((address shr 8) and 0xFFFF).toInt()
        val genre1 = ((address shr 4) and 0xF).toInt()
        val genre2 = (address and 0xF).toInt()
        val cmd = (command and 0xFFF).toInt()

        val d0 = vendor and 0xFF
        val d1 = (vendor shr 8) and 0xFF
        var vendorParity = d0 xor d1
        vendorParity = (vendorParity and 0xF) xor (vendorParity shr 4)
        val d2 = (vendorParity and 0xF) or (genre1 shl 4)
        val d3 = genre2 or ((cmd and 0xF) shl 4)
        val d4 = (id shl 6) or (cmd shr 4)
        val d5 = d2 xor d3 xor d4

        val bits = ArrayList<Boolean>(48)
        for (byte in intArrayOf(d0, d1, d2, d3, d4, d5)) {
            for (i in 0 until 8) bits += ((byte shr i) and 1) == 1
        }
        return IrSignal(
            CARRIER_HZ,
            pwmFrame(
                bits,
                headerMarkUs = 3_456,
                headerSpaceUs = 1_728,
                markUs = 432,
                zeroSpaceUs = 432,
                oneSpaceUs = 1_296,
            ),
            source = "kaseikyo",
        )
    }
}
