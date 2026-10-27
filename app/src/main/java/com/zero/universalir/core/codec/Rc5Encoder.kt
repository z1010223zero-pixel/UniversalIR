package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/** RC5 / RC5X（双相编码，规范参考 SB-Projects）。 */
object Rc5Encoder {
    const val CARRIER_HZ = 36000
    const val HALF_BIT_US = 889

    /**
     * RC5：起始位(1) + 起始位(1) + 翻转位 + 5 位地址 + 6 位命令，MSB 优先。
     * RC5X（SB-Projects）：只保留一个起始位，S2 复用为命令第 6 位的反码，命令扩为 7 位，
     * 低 64 条命令与标准 RC5 保持兼容。
     * 双相约定（SB-Projects）：逻辑 1 = space+mark，逻辑 0 = mark+space，各 889µs 半位。
     */
    fun encode(address: Int, command: Int, toggle: Boolean, extended: Boolean = false): IrSignal {
        val addr5 = address and 0x1F
        val cmd = if (extended) command and 0x7F else command and 0x3F
        val bits = mutableListOf<Boolean>()
        bits += true
        bits += if (extended) (command and 0x40) == 0 else true
        bits += toggle
        for (i in 4 downTo 0) bits += (addr5 shr i) and 1 == 1
        for (i in (if (extended) 6 else 5) downTo 0) bits += (cmd shr i) and 1 == 1

        val halves = ArrayList<Int>(bits.size * 2)
        for (bit in bits) {
            if (bit) {
                halves += SPACE
                halves += MARK
            } else {
                halves += MARK
                halves += SPACE
            }
        }
        // 起始位 S1 的首半位是静默，而 transmit 的 pattern 必须从 mark 开始，丢弃它
        if (halves.firstOrNull() == SPACE) halves.removeAt(0)
        // 末位为 0 时结尾半位是静默，不含信息，去掉以保证以 mark 收尾
        if (halves.lastOrNull() == SPACE) halves.removeAt(halves.lastIndex)
        // 游程合并相邻同态半位
        val pattern = mutableListOf<Int>()
        var i = 0
        while (i < halves.size) {
            var j = i
            while (j < halves.size && halves[j] == halves[i]) j++
            pattern += HALF_BIT_US * (j - i)
            i = j
        }
        return IrSignal(CARRIER_HZ, pattern.toIntArray(), source = if (extended) "rc5x" else "rc5")
    }

    private const val MARK = 1
    private const val SPACE = -1
}
