package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/** RC5 / RC5X（双相编码，规范参考 SB-Projects）。 */
object Rc5Encoder {
    const val CARRIER_HZ = 36000
    const val HALF_BIT_US = 889

    /**
     * RC5：起始位(1) + 起始位(1) + 翻转位 + 5 位地址 + 6 位命令，MSB 优先。
     * RC5X：第二个起始位承载地址第 6 位的取反值（此时 [address] 取 6 位有效）。
     * 双相约定：逻辑 1 = mark+space，逻辑 0 = space+mark，各 889µs 半位。
     */
    fun encode(address: Int, command: Int, toggle: Boolean, extended: Boolean = false): IrSignal {
        val addr5 = address and 0x1F
        val cmd = command and 0x3F
        val bits = mutableListOf<Boolean>()
        bits += true
        bits += if (extended) (address and 0x20) == 0 else true
        bits += toggle
        for (i in 4 downTo 0) bits += (addr5 shr i) and 1 == 1
        for (i in 5 downTo 0) bits += (cmd shr i) and 1 == 1

        val halves = ArrayList<Int>(bits.size * 2)
        for (bit in bits) {
            halves += MARK
            halves += SPACE
            if (!bit) {
                halves[halves.lastIndex] = MARK
                halves[halves.lastIndex - 1] = SPACE
            }
        }
        // 游程合并相邻同态半位
        val pattern = mutableListOf<Int>()
        var i = 0
        while (i < halves.size) {
            var j = i
            while (j < halves.size && halves[j] == halves[i]) j++
            pattern += HALF_BIT_US * (j - i)
            i = j
        }
        // 以 mark 收尾（去掉末尾 space）
        if (pattern.size % 2 == 0) pattern.removeAt(pattern.lastIndex)
        return IrSignal(CARRIER_HZ, pattern.toIntArray(), source = if (extended) "rc5x" else "rc5")
    }

    private const val MARK = 1
    private const val SPACE = -1
}
