package com.zero.universalir.core.codec

/** 半位电平标记：mark（载波开）= 正电平，space（静默）= 负电平。 */
internal const val LEVEL_MARK = 1
internal const val LEVEL_SPACE = -1

/** 相邻同电平半位做游程合并，输出从 mark 开始的 transmit pattern。 */
internal fun mergeRuns(halves: List<Pair<Int, Int>>): IntArray {
    val out = ArrayList<Int>(halves.size)
    var i = 0
    while (i < halves.size) {
        var duration = 0
        val level = halves[i].second
        while (i < halves.size && halves[i].second == level) {
            duration += halves[i].first
            i++
        }
        out += duration
    }
    return out.toIntArray()
}

/**
 * Flipper 固件风格的 PWM 帧：preamble + 逐位 mark/space（每位恒一 mark 一 space）。
 * 位序由调用方展开（Flipper 按字节 LSB-first 读位）。
 */
internal fun pwmFrame(
    bits: List<Boolean>,
    headerMarkUs: Int,
    headerSpaceUs: Int,
    markUs: Int,
    zeroSpaceUs: Int,
    oneSpaceUs: Int,
): IntArray = buildList(bits.size * 2 + 2) {
    add(headerMarkUs)
    add(headerSpaceUs)
    for (bit in bits) {
        add(markUs)
        add(if (bit) oneSpaceUs else zeroSpaceUs)
    }
}.toIntArray()
