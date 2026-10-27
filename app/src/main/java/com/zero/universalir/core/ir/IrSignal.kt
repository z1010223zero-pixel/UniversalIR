package com.zero.universalir.core.ir

/**
 * 统一红外码模型。
 *
 * [patternUs] 是交替的 mark(载波突发)/space(静默) 微秒时长，pattern[0] 为 mark，
 * 与 ConsumerIrManager.transmit 的约定一致。
 */
class IrSignal(
    val carrierHz: Int,
    val patternUs: IntArray,
    /** 对不复位状态的设备（如 Sony SIRC）通常连发 3 帧。 */
    val repeat: Int = 1,
    val source: String? = null,
) {
    val durationUs: Long by lazy { patternUs.sumOf { it.toLong() } }

    /** 各帧之间插入 gap 后的完整发射序列（每帧仍需单独 transmit，规避 2s 单次上限）。 */
    fun framesWithGap(gapUs: Long = DEFAULT_REPEAT_GAP_US): List<IntArray> {
        if (repeat <= 1) return listOf(patternUs)
        return (0 until repeat).map { i ->
            when {
                i == repeat - 1 -> patternUs
                // pattern 以 space 结尾：gap 并入帧尾 space。
                // 否则 gap 会落在偶数下标，被 ConsumerIrManager 当成 45ms 载波。
                patternUs.size % 2 == 0 ->
                    patternUs.copyOf().also { it[it.lastIndex] += gapUs.toInt() }
                else -> patternUs + gapUs.toInt()
            }
        }
    }

    override fun toString(): String =
        "IrSignal(carrier=$carrierHz Hz, pattern=${patternUs.size} edges, ${durationUs / 1000}ms, repeat=$repeat, source=$source)"

    companion object {
        const val DEFAULT_REPEAT_GAP_US = 45_000L
    }
}
