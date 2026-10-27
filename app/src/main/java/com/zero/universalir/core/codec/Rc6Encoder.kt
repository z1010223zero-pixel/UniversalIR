package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/**
 * RC6 mode 0（飞利浦，36k）：preamble 2666/889 后跟 21 位 Manchester（半位 444µs）。
 * 位序 LSB-first：start(恒 1) + mode(000) + 翻转位 + 地址 8 位 + 命令 8 位；
 * 地址/命令以 reverse8 填充使其按 MSB 先行（与 Flipper 固件一致）。
 * Manchester 约定（Flipper）：1 = mark+space，0 = space+mark；翻转位双倍时长。
 * toggle 无状态恒为 false（与 RC5 同一限制）。
 */
object Rc6Encoder {
    const val CARRIER_HZ = 36_000
    const val HALF_BIT_US = 444

    /** RC6 mode 0 引导头：6 倍半位的 mark + 2 倍半位的 space；规范值 2666/889（半位取整为 444，勿用 2×444）。 */
    private const val LEADER_MARK_US = 2_666
    private const val LEADER_SPACE_US = 889
    private const val TOGGLE_INDEX = 4
    private const val BIT_COUNT = 21

    fun encode(address: Int, command: Int, toggle: Boolean = false): IrSignal {
        var data = 1 // start bit
        if (toggle) data = data or (1 shl TOGGLE_INDEX)
        data = data or (reverse8(address and 0xFF) shl 5)
        data = data or (reverse8(command and 0xFF) shl 13)

        val halves = ArrayList<Pair<Int, Int>>(BIT_COUNT * 2 + 2)
        halves += LEADER_MARK_US to LEVEL_MARK
        halves += LEADER_SPACE_US to LEVEL_SPACE
        for (i in 0 until BIT_COUNT) {
            val half = if (i == TOGGLE_INDEX) 2 * HALF_BIT_US else HALF_BIT_US
            val one = ((data shr i) and 1) == 1
            halves += half to (if (one) LEVEL_MARK else LEVEL_SPACE)
            halves += half to (if (one) LEVEL_SPACE else LEVEL_MARK)
        }
        return IrSignal(CARRIER_HZ, mergeRuns(halves), source = "rc6")
    }

    private fun reverse8(v: Int): Int {
        var result = 0
        for (i in 0 until 8) result = (result shl 1) or ((v shr i) and 1)
        return result
    }
}
